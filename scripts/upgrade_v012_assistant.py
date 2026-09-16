from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def must_replace(text, old, new, label):
    if old not in text:
        raise SystemExit(f"Patch marker not found: {label}")
    return text.replace(old, new, 1)

# ---------- NextMainActivity ----------
ui_path = ROOT / "app/src/main/java/com/aracdefteri/app/NextMainActivity.java"
ui = ui_path.read_text(encoding="utf-8")

ui = must_replace(ui,
'''    private FormRefs pendingSmartForm;\n    private String pendingSmartModule;\n    private Uri pendingOcrCameraUri;\n''',
'''    private FormRefs pendingSmartForm;\n    private String pendingSmartModule;\n    private Uri pendingOcrCameraUri;\n    private boolean assistantInputActive = false;\n    private TextView assistantStatus;\n''',
"assistant fields")

ui = must_replace(ui,
'''        db = new AppDatabase(this);\n        db.seedDemoIfNeeded();\n        resolveTheme();\n''',
'''        db = new AppDatabase(this);\n        if (!prefs.getBoolean("legacy_demo_cleanup_v12", false)) {\n            db.clearLegacyDemoIfPresent();\n            prefs.edit().putBoolean("legacy_demo_cleanup_v12", true).apply();\n        }\n        resolveTheme();\n''',
"remove demo seed")

old_nav = '''private void buildBottomNav() {\n        navBar.removeAllViews();\n        String[] labels = {"Ana Sayfa", "Kayıtlar", "Araç CV", "Ayarlar"};\n        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_records, R.drawable.ic_nav_cv, R.drawable.ic_nav_settings};\n'''
new_nav = '''private void buildBottomNav() {\n        navBar.removeAllViews();\n        String[] labels = {"Ana Sayfa", "Kayıtlar", "Asistan", "Araç CV", "Ayarlar"};\n        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_records, R.drawable.ic_nav_assistant, R.drawable.ic_nav_cv, R.drawable.ic_nav_settings};\n'''
ui = must_replace(ui, old_nav, new_nav, "5 item bottom nav")

# Old CV/settings page references must shift by one before adding assistant page.
ui = ui.replace("renderPage(2)", "renderPage(__CV_PAGE__)")
ui = ui.replace("renderPage(3)", "renderPage(4)")
ui = ui.replace("renderPage(__CV_PAGE__)", "renderPage(3)")

old_render = '''        if (page == 0) renderHome(content);\n        else if (page == 1) renderRecordsHub(content);\n        else if (page == 2) renderCv(content);\n        else renderSettings(content);\n'''
new_render = '''        if (page == 0) renderHome(content);\n        else if (page == 1) renderRecordsHub(content);\n        else if (page == 2) renderAssistant(content);\n        else if (page == 3) renderCv(content);\n        else renderSettings(content);\n'''
ui = must_replace(ui, old_render, new_render, "render page mapping")

old_home_vehicle = '''        AppDatabase.Vehicle vehicle = db.getVehicle();\n        content.addView(vehicleHero(vehicle));\n        gap(content, 20);\n'''
new_home_vehicle = '''        AppDatabase.Vehicle vehicle = db.getVehicle();\n        if (vehicle.brand == null || vehicle.brand.trim().isEmpty() || vehicle.model == null || vehicle.model.trim().isEmpty()) {\n            LinearLayout emptyVehicle = infoCard("Henüz araç eklenmedi", "Araç bilgilerini eklediğinde kilometre, kayıtlar ve Araç CV burada oluşmaya başlar.", accent);\n            content.addView(emptyVehicle);\n            gap(content, 10);\n            Button addVehicle = primaryButton("Araç bilgilerini ekle");\n            addVehicle.setOnClickListener(v -> openVehicleForm());\n            content.addView(addVehicle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));\n        } else {\n            content.addView(vehicleHero(vehicle));\n        }\n        gap(content, 20);\n'''
ui = must_replace(ui, old_home_vehicle, new_home_vehicle, "empty vehicle home")

# Remove smart input panel from individual record forms. Assistant owns all smart entry.
ui = must_replace(ui,
'''        form.addView(formSection("Temel bilgiler", "Zorunlu alanları kısa tuttuk"));\n        form.addView(smartInputPanel(module, f));\n        gap(form, 12);\n        f.title = formField(form, titleHint(module), initialTitle, InputType.TYPE_CLASS_TEXT);\n''',
'''        form.addView(formSection("Temel bilgiler", "Zorunlu alanları kısa tuttuk"));\n        f.title = formField(form, titleHint(module), initialTitle, InputType.TYPE_CLASS_TEXT);\n''',
"remove per-form smart panel")

assistant_method = r'''
    private void renderAssistant(LinearLayout content) {
        addHeader(content, "Asistan", "Konuş, fotoğraf çek veya belge seç; kayıt türünü Taşıtım bulsun");

        LinearLayout hero = card();
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        hero.addView(tv("Ne yaptığını söylemen yeterli", text, 17, true));
        TextView help = tv("Örnek: “Kilometrem 321.609 oldu.” • “10 litre yakıt aldım, 873 lira.” • “Kasko poliçem 12 Mart 2027'de bitiyor.”", muted, 10, false);
        help.setPadding(0, dp(5), 0, dp(13));
        hero.addView(help);

        Button voice = primaryButton("🎤  Konuşarak ekle");
        voice.setOnClickListener(v -> {
            assistantInputActive = true;
            pendingSmartForm = null;
            pendingSmartModule = null;
            startSmartVoice();
        });
        hero.addView(voice, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        gap(hero, 9);

        LinearLayout imageActions = new LinearLayout(this);
        imageActions.setOrientation(LinearLayout.HORIZONTAL);
        Button camera = secondaryButton("📷 Kamera");
        Button file = secondaryButton("▣ Fotoğraf / PDF");
        camera.setOnClickListener(v -> {
            assistantInputActive = true;
            pendingSmartForm = null;
            pendingSmartModule = null;
            captureOcrImage();
        });
        file.setOnClickListener(v -> {
            assistantInputActive = true;
            pendingSmartForm = null;
            pendingSmartModule = null;
            pickOcrImage();
        });
        imageActions.addView(camera, new LinearLayout.LayoutParams(0, dp(48), 1f));
        gapHorizontal(imageActions, 8);
        imageActions.addView(file, new LinearLayout.LayoutParams(0, dp(48), 1f));
        hero.addView(imageActions);

        assistantStatus = tv("Hazır. Veriyi söyle veya belge/gösterge fotoğrafını okut.", muted, 10, false);
        assistantStatus.setPadding(0, dp(12), 0, 0);
        hero.addView(assistantStatus);
        content.addView(hero);

        gap(content, 16);
        sectionTitle(content, "Neleri anlayabilir?", "Tek merkezden araç kayıtlarını hazırlar");
        content.addView(infoCard("Kilometre ve yakıt", "Gösterge paneli, ODO değeri, yakıt fişi, litre/kWh, birim fiyat ve toplam tutar.", accent));
        gap(content, 8);
        content.addView(infoCard("Bakım ve hasar", "Servis faturası, yapılan işlemler, değişen parçalar, hasar ve onarım bilgileri.", accent));
        gap(content, 8);
        content.addView(infoCard("Muayene, sigorta, vergi, ekspertiz", "Belge türünü ayırır ve uygun kayıt formunu doldurur; kaydetmeden önce sen kontrol edersin.", accent));
    }

    private void handleAssistantResult(RecordParser.Parsed parsed, String source) {
        assistantInputActive = false;
        if (parsed == null) {
            if (assistantStatus != null) assistantStatus.setText(source + " sonucu okunamadı.");
            toast("Veri okunamadı");
            return;
        }

        boolean odometerOnly = "ODOMETER".equals(parsed.documentKind) ||
                ((parsed.type == null || parsed.type.trim().isEmpty()) && parsed.km > 0);
        if (odometerOnly) {
            if (parsed.km <= 0) {
                if (assistantStatus != null) assistantStatus.setText("Kilometre değeri güvenle okunamadı. Manuel giriş yapabilirsin.");
                toast("Kilometre okunamadı");
                return;
            }
            AppDatabase.Vehicle current = db.getVehicle();
            String message = "Okunan kilometre: " + formatInt(parsed.km) + " km";
            if (current.km > 0) message += "\nMevcut kilometre: " + formatInt(current.km) + " km";
            new AlertDialog.Builder(this)
                    .setTitle("Kilometreyi güncelle?")
                    .setMessage(message)
                    .setNegativeButton("Vazgeç", null)
                    .setPositiveButton("Güncelle", (d, w) -> {
                        db.updateVehicle(current.brand, current.model, current.year, current.plate, parsed.km, current.fuelType);
                        toast("Kilometre " + formatInt(parsed.km) + " km olarak güncellendi");
                        renderPage(2);
                    })
                    .show();
            return;
        }

        String detected = parsed.type == null ? "" : normalizeModule(parsed.type.trim());
        if (detected.isEmpty()) {
            if (assistantStatus != null) assistantStatus.setText("Kayıt türü belirlenemedi. Daha net söyle veya farklı bir fotoğraf dene.");
            toast("Kayıt türü belirlenemedi");
            return;
        }
        if ("Giderler".equals(detected)) {
            if (assistantStatus != null) assistantStatus.setText("Gider kaydı algılandı; ayrıntıları manuel kontrol et.");
            openExpenseForm(null);
            return;
        }
        openRecordForm(detected, null);
        applyParsedToCurrentForm(parsed, source);
    }
'''
marker = '''    private void addModuleTile(GridLayout grid, String module) {\n'''
if assistant_method.strip() not in ui:
    ui = must_replace(ui, marker, assistant_method + "\n" + marker, "insert assistant page")

# Voice feedback works both from assistant and (legacy) form route.
ui = must_replace(ui,
'''        if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n            pendingSmartForm.smartStatus.setText("Dinlemeye hazır… Kaydı doğal şekilde anlat.");\n            pendingSmartForm.smartStatus.setTextColor(accent);\n        }\n''',
'''        if (assistantInputActive && assistantStatus != null) {\n            assistantStatus.setText("Dinliyorum… Kaydı doğal şekilde anlat.");\n            assistantStatus.setTextColor(accent);\n        } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n            pendingSmartForm.smartStatus.setText("Dinlemeye hazır… Kaydı doğal şekilde anlat.");\n            pendingSmartForm.smartStatus.setTextColor(accent);\n        }\n''',
"assistant voice status")

ui = must_replace(ui,
'''        if (uri == null || pendingSmartForm == null || pendingSmartModule == null) {\n            if (temporaryCameraImage) cleanupOcrCameraImage();\n            return;\n        }\n        if (pendingSmartForm.smartStatus != null) {\n            pendingSmartForm.smartStatus.setText("Belge cihaz üzerinde okunuyor…");\n            pendingSmartForm.smartStatus.setTextColor(accent);\n        }\n''',
'''        if (uri == null || (!assistantInputActive && (pendingSmartForm == null || pendingSmartModule == null))) {\n            if (temporaryCameraImage) cleanupOcrCameraImage();\n            return;\n        }\n        if (assistantInputActive && assistantStatus != null) {\n            assistantStatus.setText("Belge cihaz üzerinde okunuyor…");\n            assistantStatus.setTextColor(accent);\n        } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n            pendingSmartForm.smartStatus.setText("Belge cihaz üzerinde okunuyor…");\n            pendingSmartForm.smartStatus.setTextColor(accent);\n        }\n''',
"assistant OCR precondition")

ui = must_replace(ui,
'''                    if (temporaryCameraImage) cleanupOcrCameraImage();\n                    handleSmartResult(result, "Fotoğraf");\n''',
'''                    if (temporaryCameraImage) cleanupOcrCameraImage();\n                    if (assistantInputActive) handleAssistantResult(result, "Fotoğraf");\n                    else handleSmartResult(result, "Fotoğraf");\n''',
"assistant OCR result")

ui = must_replace(ui,
'''                    if (temporaryCameraImage) cleanupOcrCameraImage();\n                    if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n                        pendingSmartForm.smartStatus.setText("Fotoğraf okunamadı — alanları manuel girebilirsin.");\n                        pendingSmartForm.smartStatus.setTextColor(warning);\n                    }\n                    toast("Belge okunamadı");\n''',
'''                    if (temporaryCameraImage) cleanupOcrCameraImage();\n                    if (assistantInputActive && assistantStatus != null) {\n                        assistantInputActive = false;\n                        assistantStatus.setText("Fotoğraf okunamadı — daha net çek veya manuel giriş yap.");\n                        assistantStatus.setTextColor(warning);\n                    } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n                        pendingSmartForm.smartStatus.setText("Fotoğraf okunamadı — alanları manuel girebilirsin.");\n                        pendingSmartForm.smartStatus.setTextColor(warning);\n                    }\n                    toast("Belge okunamadı");\n''',
"assistant OCR error")

old_voice_result = '''        if (requestCode == VoiceInput.REQUEST_CODE) {\n            String spoken = VoiceInput.extract(data);\n            if (spoken.isEmpty()) {\n                if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n                    pendingSmartForm.smartStatus.setText("Ses anlaşılamadı — alanları manuel girebilirsin.");\n                    pendingSmartForm.smartStatus.setTextColor(warning);\n                }\n                toast("Ses anlaşılamadı");\n            } else {\n                handleSmartResult(RecordParser.fromText(spoken), "Ses");\n            }\n            return;\n        }\n'''
new_voice_result = '''        if (requestCode == VoiceInput.REQUEST_CODE) {\n            String spoken = VoiceInput.extract(data);\n            if (spoken.isEmpty()) {\n                if (assistantInputActive && assistantStatus != null) {\n                    assistantInputActive = false;\n                    assistantStatus.setText("Ses anlaşılamadı — tekrar deneyebilirsin.");\n                    assistantStatus.setTextColor(warning);\n                } else if (pendingSmartForm != null && pendingSmartForm.smartStatus != null) {\n                    pendingSmartForm.smartStatus.setText("Ses anlaşılamadı — alanları manuel girebilirsin.");\n                    pendingSmartForm.smartStatus.setTextColor(warning);\n                }\n                toast("Ses anlaşılamadı");\n            } else {\n                RecordParser.Parsed parsed = RecordParser.fromText(spoken);\n                if (assistantInputActive) handleAssistantResult(parsed, "Ses");\n                else handleSmartResult(parsed, "Ses");\n            }\n            return;\n        }\n'''
ui = must_replace(ui, old_voice_result, new_voice_result, "assistant voice result")

# Empty CV until a vehicle is added.
old_cv_start = '''private void renderCv(LinearLayout content) {\n        AppDatabase.Vehicle v = db.getVehicle();\n        String cvVariant = prefs.getString("vehicle_catalog_variant", "").trim();\n'''
new_cv_start = '''private void renderCv(LinearLayout content) {\n        AppDatabase.Vehicle v = db.getVehicle();\n        if (v.brand == null || v.brand.trim().isEmpty() || v.model == null || v.model.trim().isEmpty()) {\n            addHeader(content, "Araç CV", "Önce araç bilgilerini ekle");\n            content.addView(infoCard("CV için araç bilgisi gerekli", "Araç profili boş. Marka, model, yıl ve kilometreyi ekledikten sonra CV oluşturabilirsin.", accent));\n            gap(content, 10);\n            Button add = primaryButton("Araç bilgilerini ekle");\n            add.setOnClickListener(x -> openVehicleForm());\n            content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));\n            return;\n        }\n        String cvVariant = prefs.getString("vehicle_catalog_variant", "").trim();\n'''
ui = must_replace(ui, old_cv_start, new_cv_start, "empty CV")

# CV section selectors.
cv_options_marker = '''        LinearLayout options = card();\n        options.setPadding(dp(12),dp(10),dp(12),dp(11));\n\n        CheckBox showCosts = new CheckBox(this);\n'''
cv_options_new = '''        LinearLayout options = card();\n        options.setPadding(dp(12),dp(10),dp(12),dp(11));\n\n        options.addView(tv("CV'ye eklenecek bölümler", text, 12, true));\n        TextView sectionHelp = tv("İstemediğin bölümü kapat; PDF'ye hiç eklenmez.", muted, 9, false);\n        sectionHelp.setPadding(0, dp(2), 0, dp(5));\n        options.addView(sectionHelp);\n        CheckBox cvMaintenance = cvSectionOption(options, "Bakım & Onarım", "cv_include_maintenance", true);\n        CheckBox cvInspection = cvSectionOption(options, "Muayene", "cv_include_inspection", true);\n        CheckBox cvDamage = cvSectionOption(options, "Hasar geçmişi ve hasar fotoğrafları", "cv_include_damage", true);\n        CheckBox cvExpertise = cvSectionOption(options, "Ekspertiz", "cv_include_expertise", true);\n        CheckBox cvInsurance = cvSectionOption(options, "Sigorta & Kasko", "cv_include_insurance", true);\n        CheckBox cvTax = cvSectionOption(options, "Vergi / resmî ödemeler", "cv_include_tax", true);\n        CheckBox cvFuel = cvSectionOption(options, "Yakıt / enerji geçmişi", "cv_include_fuel", false);\n        CheckBox cvExpenses = cvSectionOption(options, "Diğer giderler", "cv_include_expenses", false);\n        CheckBox cvPhotos = cvSectionOption(options, "Seçtiğim araç fotoğrafları", "cv_include_photos", true);\n        gap(options, 6);\n\n        CheckBox showCosts = new CheckBox(this);\n'''
ui = must_replace(ui, cv_options_marker, cv_options_new, "CV section checkboxes")

prefs_marker = '''                    .putBoolean("cv_show_plate", false)\n                    .putBoolean("cv_show_costs", showCosts.isChecked())\n'''
prefs_new = '''                    .putBoolean("cv_show_plate", false)\n                    .putBoolean("cv_include_maintenance", cvMaintenance.isChecked())\n                    .putBoolean("cv_include_inspection", cvInspection.isChecked())\n                    .putBoolean("cv_include_damage", cvDamage.isChecked())\n                    .putBoolean("cv_include_expertise", cvExpertise.isChecked())\n                    .putBoolean("cv_include_insurance", cvInsurance.isChecked())\n                    .putBoolean("cv_include_tax", cvTax.isChecked())\n                    .putBoolean("cv_include_fuel", cvFuel.isChecked())\n                    .putBoolean("cv_include_expenses", cvExpenses.isChecked())\n                    .putBoolean("cv_include_photos", cvPhotos.isChecked())\n                    .putBoolean("cv_show_costs", showCosts.isChecked())\n'''
ui = must_replace(ui, prefs_marker, prefs_new, "save CV sections")

helper_marker = '''    private void renderSettings(LinearLayout content) {\n'''
helper = r'''    private CheckBox cvSectionOption(LinearLayout parent, String label, String prefKey, boolean defaultValue) {
        CheckBox box = new CheckBox(this);
        box.setText(label);
        box.setTextColor(text);
        box.setTextSize(11);
        box.setChecked(prefs.getBoolean(prefKey, defaultValue));
        parent.addView(box);
        return box;
    }

'''
ui = must_replace(ui, helper_marker, helper + helper_marker, "CV option helper")

ui_path.write_text(ui, encoding="utf-8")

# ---------- AppDatabase: blank initial database + legacy demo cleanup ----------
db_path = ROOT / "app/src/main/java/com/aracdefteri/app/AppDatabase.java"
db = db_path.read_text(encoding="utf-8")

db = must_replace(db,
'''        db.execSQL("CREATE INDEX idx_record_photos_record_id ON record_photos(record_id)");\n    }\n''',
'''        db.execSQL("CREATE INDEX idx_record_photos_record_id ON record_photos(record_id)");\n        ContentValues blankVehicle = new ContentValues();\n        blankVehicle.put("id", 1);\n        blankVehicle.put("brand", "");\n        blankVehicle.put("model", "");\n        blankVehicle.put("year", 0);\n        blankVehicle.put("plate", "");\n        blankVehicle.put("km", 0);\n        blankVehicle.put("fuel_type", "Benzin");\n        db.insert("vehicle", null, blankVehicle);\n    }\n''',
"blank first vehicle")

old_update = '''    public void updateVehicle(String brand, String model, int year, String plate, int km, String fuelType) {\n        ContentValues v = new ContentValues();\n        v.put("brand", brand); v.put("model", model); v.put("year", year); v.put("plate", plate); v.put("km", km); v.put("fuel_type", fuelType);\n        getWritableDatabase().update("vehicle", v, "id=1", null);\n    }\n'''
new_update = '''    public void updateVehicle(String brand, String model, int year, String plate, int km, String fuelType) {\n        ContentValues v = new ContentValues();\n        v.put("brand", brand); v.put("model", model); v.put("year", year); v.put("plate", plate); v.put("km", km); v.put("fuel_type", fuelType);\n        SQLiteDatabase w = getWritableDatabase();\n        int changed = w.update("vehicle", v, "id=1", null);\n        if (changed == 0) { v.put("id", 1); w.insert("vehicle", null, v); }\n    }\n\n    public boolean clearLegacyDemoIfPresent() {\n        Vehicle v = getVehicle();\n        if (!("Toyota".equals(v.brand) && "Corolla Hybrid".equals(v.model) && v.year == 2021)) return false;\n        Cursor c = getReadableDatabase().rawQuery(\n                "SELECT COUNT(*) FROM records WHERE title IN ('Periyodik bakım','Yıllık genel kontrol','Sağ arka tampon çizik onarımı','Periyodik muayene')", null);\n        c.moveToFirst();\n        int seeded = c.getInt(0);\n        c.close();\n        if (seeded < 2) return false;\n        SQLiteDatabase w = getWritableDatabase();\n        w.delete("record_photos", null, null);\n        w.delete("records", null, null);\n        w.delete("expenses", null, null);\n        w.delete("photos", null, null);\n        updateVehicle("", "", 0, "", 0, "Benzin");\n        return true;\n    }\n'''
db = must_replace(db, old_update, new_update, "vehicle upsert and demo cleanup")
db_path.write_text(db, encoding="utf-8")

# ---------- SmartDocumentAnalyzer: ODO-first parsing + speedometer guard ----------
ana_path = ROOT / "app/src/main/java/com/aracdefteri/app/SmartDocumentAnalyzer.java"
ana = ana_path.read_text(encoding="utf-8")
old_extract = '''    private static int extractBestKm(String raw) {\n        String[] lines = raw.split("\\\\r?\\\\n"); int bestValue = 0; int bestScore = Integer.MIN_VALUE;\n        Pattern number = Pattern.compile("(?<!\\\\d)(\\\\d{1,7}(?:[ .]\\\\d{3})*|\\\\d{1,7})(?!\\\\d)");\n'''
new_extract = '''    private static int extractBestKm(String raw) {\n        // Gösterge panelinde ODO etiketi varsa önce hemen yanındaki/altındaki toplam kilometreyi al.\n        Matcher odo = Pattern.compile("(?is)(?:\\\\bODO\\\\b|ODOMETER|ODOMETRE|TOPLAM\\\\s*KM|KILOMETRE)\\\\s*[:=.-]?\\\\s*([0-9][0-9 .]{3,9})").matcher(raw);\n        if (odo.find()) {\n            String digits = odo.group(1).replaceAll("[^0-9]", "");\n            try {\n                int value = Integer.parseInt(digits);\n                if (value >= 1000 && value < 2_000_000 && !looksLikeGaugeScale(value)) return value;\n            } catch (Exception ignored) { }\n        }\n\n        String[] lines = raw.split("\\\\r?\\\\n"); int bestValue = 0; int bestScore = Integer.MIN_VALUE;\n        Pattern number = Pattern.compile("(?<!\\\\d)(\\\\d{1,7}(?:[ .]\\\\d{3})*|\\\\d{1,7})(?!\\\\d)");\n'''
ana = must_replace(ana, old_extract, new_extract, "ODO-first extraction")

ana = must_replace(ana,
'''                if (containsAny(n, "trip", "seyahat", "menzil", "range")) score -= 9;\n                if (containsAny(n, "rpm", "devir", "km/h", "hiz")) score -= 5;\n                if (line.contains(",") || line.matches(".*\\\\d+\\\\.\\\\d+.*")) score -= 3;\n                if (score > bestScore) { bestScore = score; bestValue = value; }\n''',
'''                if (containsAny(n, "trip", "seyahat", "menzil", "range")) score -= 9;\n                if (containsAny(n, "rpm", "devir", "km/h", "hiz")) score -= 5;\n                if (looksLikeGaugeScale(value)) score -= 14;\n                if (line.contains(",") || line.matches(".*\\\\d+\\\\.\\\\d+.*")) score -= 3;\n                if (score > bestScore || (score == bestScore && value > bestValue)) { bestScore = score; bestValue = value; }\n''',
"speedometer scale guard")

insert_before_plate = '''    private static String extractPlate(String raw) {\n'''
helper_scale = '''    private static boolean looksLikeGaugeScale(int value) {\n        int[] suspicious = {100120,120140,140160,160180,180200,200220,2040,4060,6080,80100};\n        for (int v : suspicious) if (value == v) return true;\n        return false;\n    }\n\n'''
ana = must_replace(ana, insert_before_plate, helper_scale + insert_before_plate, "gauge scale helper")
ana_path.write_text(ana, encoding="utf-8")

# ---------- VehicleCvPdf: honor selected sections ----------
pdf_path = ROOT / "app/src/main/java/com/aracdefteri/app/VehicleCvPdf.java"
pdf = pdf_path.read_text(encoding="utf-8")
pdf = must_replace(pdf,
'''            drawCover(activity, w, vehicle, prefs, documentNo, showPlate);\n            drawHistory(activity, w, db, showCosts);\n            drawPhotos(activity, w, selectedPhotos);\n''',
'''            drawCover(activity, w, vehicle, prefs, documentNo, showPlate);\n            drawHistory(activity, w, db, showCosts, prefs);\n            if (prefs.getBoolean("cv_include_photos", true)) drawPhotos(activity, w, selectedPhotos);\n''',
"CV selected photos/history")

old_history = '''    private static void drawHistory(Activity activity, Writer w, AppDatabase db, boolean showCosts) {\n        List<AppDatabase.Record> all = db.getRecords(500);\n        String[] types = {"Bakım", "Hasar", "Muayene", "Sigorta/Kasko", "Vergi", "Yakıt"};\n        String[] titles = {"Bakım & Onarım", "Hasar Geçmişi", "Muayene", "Sigorta & Kasko", "Vergi / Resmî Ödemeler", "Yakıt / Enerji"};\n        for (int i = 0; i < types.length; i++) {\n            List<AppDatabase.Record> group = recordsOfType(all, types[i]);\n            if (group.isEmpty()) continue;\n            w.ensure(70);\n            w.y += 3;\n            w.section(titles[i], null);\n            for (AppDatabase.Record r : group) {\n                w.recordCard(r, showCosts);\n                if ("Hasar".equals(r.type)) drawDamagePhotos(activity, w, db.getRecordPhotos(r.id));\n            }\n        }\n    }\n'''
new_history = '''    private static void drawHistory(Activity activity, Writer w, AppDatabase db, boolean showCosts, SharedPreferences prefs) {\n        List<AppDatabase.Record> all = db.getRecords(500);\n        String[] types = {"Bakım", "Hasar", "Ekspertiz", "Muayene", "Sigorta/Kasko", "Vergi", "Yakıt"};\n        String[] titles = {"Bakım & Onarım", "Hasar Geçmişi", "Ekspertiz", "Muayene", "Sigorta & Kasko", "Vergi / Resmî Ödemeler", "Yakıt / Enerji"};\n        String[] keys = {"cv_include_maintenance", "cv_include_damage", "cv_include_expertise", "cv_include_inspection", "cv_include_insurance", "cv_include_tax", "cv_include_fuel"};\n        boolean[] defaults = {true, true, true, true, true, true, false};\n        for (int i = 0; i < types.length; i++) {\n            if (!prefs.getBoolean(keys[i], defaults[i])) continue;\n            List<AppDatabase.Record> group = recordsOfType(all, types[i]);\n            if (group.isEmpty()) continue;\n            w.ensure(70);\n            w.y += 3;\n            w.section(titles[i], null);\n            for (AppDatabase.Record r : group) {\n                w.recordCard(r, showCosts);\n                if ("Hasar".equals(r.type)) drawDamagePhotos(activity, w, db.getRecordPhotos(r.id));\n            }\n        }\n\n        if (prefs.getBoolean("cv_include_expenses", false)) {\n            List<AppDatabase.Expense> expenses = db.getExpenses();\n            if (!expenses.isEmpty()) {\n                w.ensure(70);\n                w.y += 3;\n                w.section("Diğer Giderler", null);\n                for (AppDatabase.Expense e : expenses) {\n                    String subtitle = e.date + (e.note == null || e.note.trim().isEmpty() ? "" : " • " + e.note.trim());\n                    w.simpleCard(e.category, subtitle, showCosts && e.amount > 0 ? formatMoney(e.amount) : "");\n                }\n            }\n        }\n    }\n'''
pdf = must_replace(pdf, old_history, new_history, "CV section filtering")
pdf_path.write_text(pdf, encoding="utf-8")

# ---------- Version bump ----------
gradle_path = ROOT / "app/build.gradle"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode 13", "versionCode 14", 1)
gradle = gradle.replace("versionName '0.10.0-smart-input-dev'", "versionName '0.12.0-assistant-dev'", 1)
gradle_path.write_text(gradle, encoding="utf-8")

print("v0.12 assistant/CV/empty-first-run upgrade applied")
