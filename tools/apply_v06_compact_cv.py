from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
activity_path = root / 'app/src/main/java/com/aracdefteri/app/NextMainActivity.java'
pdf_path = root / 'app/src/main/java/com/aracdefteri/app/VehicleCvPdf.java'
specs_path = root / 'app/src/main/java/com/aracdefteri/app/TurkeyVehicleSpecs.java'
gradle_path = root / 'app/build.gradle'


def replace_between(src: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = src.index(start_marker)
    end = src.index(end_marker, start)
    return src[:start] + replacement + src[end:]

# ---------------- NextMainActivity ----------------
src = activity_path.read_text(encoding='utf-8')

new_render_cv = r'''private void renderCv(LinearLayout content) {
        AppDatabase.Vehicle v = db.getVehicle();
        TextView title = tv("Araç CV", text, 23, true);
        content.addView(title);
        TextView subtitle = tv(v.year + " " + v.brand + " " + v.model + " için paylaşılabilir araç geçmişi", muted, 10, false);
        subtitle.setPadding(0,dp(2),0,dp(11));
        content.addView(subtitle);

        LinearLayout summary = card();
        summary.setPadding(dp(14),dp(13),dp(14),dp(13));
        summary.addView(tv(v.year + "  " + v.brand + " " + v.model, text, 18, true));
        String meta = prefs.getString("vehicle_trim", "") + "  •  " + prefs.getString("vehicle_engine", "");
        meta = meta.replaceFirst("^\\s*•\\s*", "").replaceFirst("\\s*•\\s*$", "");
        if (!meta.trim().isEmpty()) summary.addView(tv(meta, muted, 10, false));
        gap(summary,9);
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.HORIZONTAL);
        mini.addView(statCard("BAKIM", String.valueOf(db.countRecordsByType("Bakım")), "kayıt"), new LinearLayout.LayoutParams(0,dp(74),1f));
        gapHorizontal(mini,7);
        mini.addView(statCard("HASAR", String.valueOf(db.countRecordsByType("Hasar")), "kayıt"), new LinearLayout.LayoutParams(0,dp(74),1f));
        gapHorizontal(mini,7);
        mini.addView(statCard("TOPLAM", String.valueOf(db.countAllRecords()), "kayıt"), new LinearLayout.LayoutParams(0,dp(74),1f));
        summary.addView(mini);
        content.addView(summary);
        gap(content,13);

        sectionTitle(content, "CV fotoğrafları", "Ana araç fotoğrafına ek olarak en fazla 10 fotoğraf");
        LinearLayout photoCard = card();
        photoCard.setPadding(dp(12),dp(11),dp(12),dp(11));
        cvPhotoCountView = tv(cvPhotoUris.size() + " / " + VehicleCvPdf.MAX_EXTRA_PHOTOS + " fotoğraf seçildi", text, 12, true);
        photoCard.addView(cvPhotoCountView);
        photoCard.addView(tv("Bu fotoğraflar yalnız oluşturulan PDF içinde kullanılır.", muted, 9, false));
        gap(photoCard,8);

        if (!cvPhotoUris.isEmpty()) {
            android.widget.HorizontalScrollView scrollPhotos = new android.widget.HorizontalScrollView(this);
            scrollPhotos.setHorizontalScrollBarEnabled(false);
            LinearLayout thumbs = new LinearLayout(this);
            thumbs.setOrientation(LinearLayout.HORIZONTAL);
            for (Uri uri : new ArrayList<>(cvPhotoUris)) thumbs.addView(cvPhotoThumb(uri));
            scrollPhotos.addView(thumbs);
            photoCard.addView(scrollPhotos,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(88)));
        }

        LinearLayout photoActions = new LinearLayout(this);
        photoActions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = secondaryButton(cvPhotoUris.isEmpty() ? "Fotoğraf seç" : "+ Fotoğraf ekle");
        choose.setOnClickListener(vw -> pickCvImages());
        photoActions.addView(choose, new LinearLayout.LayoutParams(0,dp(41),1f));
        gapHorizontal(photoActions,7);
        Button clear = secondaryButton("Temizle");
        clear.setEnabled(!cvPhotoUris.isEmpty());
        clear.setAlpha(cvPhotoUris.isEmpty() ? 0.45f : 1f);
        clear.setOnClickListener(vw -> { cvPhotoUris.clear(); renderPage(2); });
        photoActions.addView(clear, new LinearLayout.LayoutParams(0,dp(41),1f));
        photoCard.addView(photoActions);
        content.addView(photoCard);
        gap(content,13);

        sectionTitle(content, "PDF seçenekleri", "İsteğe bağlı bilgileri seç");
        LinearLayout options = card();
        options.setPadding(dp(12),dp(10),dp(12),dp(11));

        CheckBox showCosts = new CheckBox(this);
        showCosts.setText("Maliyetleri göster");
        showCosts.setTextColor(text);
        showCosts.setTextSize(11);
        showCosts.setChecked(prefs.getBoolean("cv_show_costs", false));
        options.addView(showCosts);

        CheckBox showPrice = new CheckBox(this);
        showPrice.setText("Satış fiyatını CV'ye ekle");
        showPrice.setTextColor(text);
        showPrice.setTextSize(11);
        showPrice.setChecked(prefs.getBoolean("cv_show_price", false));
        options.addView(showPrice);

        EditText salePrice = formField(options, "İstenen satış fiyatı (₺)", prefs.getString("cv_sale_price", ""), InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        salePrice.setEnabled(showPrice.isChecked());
        salePrice.setAlpha(showPrice.isChecked() ? 1f : 0.45f);
        showPrice.setOnCheckedChangeListener((buttonView, isChecked) -> {
            salePrice.setEnabled(isChecked);
            salePrice.setAlpha(isChecked ? 1f : 0.45f);
        });

        EditText phone = formField(options, "Telefon (isteğe bağlı)", prefs.getString("cv_phone", ""), InputType.TYPE_CLASS_PHONE);
        EditText note = formMultiline(options, "Açıklama (isteğe bağlı)", prefs.getString("cv_note", ""));
        content.addView(options);
        gap(content,12);

        Button create = primaryButton("PDF oluştur");
        create.setOnClickListener(vw -> {
            if (showPrice.isChecked() && salePrice.getText().toString().trim().isEmpty()) {
                toast("Satış fiyatını gir veya fiyat seçeneğini kapat");
                return;
            }
            prefs.edit()
                    .putBoolean("cv_show_plate", false)
                    .putBoolean("cv_show_costs", showCosts.isChecked())
                    .putBoolean("cv_show_price", showPrice.isChecked())
                    .putString("cv_sale_price", salePrice.getText().toString().trim())
                    .putString("cv_phone", phone.getText().toString().trim())
                    .putString("cv_note", note.getText().toString().trim())
                    .apply();
            createVehiclePdf();
        });
        content.addView(create, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));
    }

'''
src = replace_between(src, 'private void renderCv(LinearLayout content) {', '    private void renderSettings(LinearLayout content) {', new_render_cv)

# Remove plate from visible vehicle UI. The DB column is intentionally retained for migration compatibility.
src = src.replace('        EditText plate = formField(form,"Plaka (isteğe bağlı)",v.plate,InputType.TYPE_CLASS_TEXT);\n', '')
src = src.replace('                    plate.getText().toString().trim(),\n', '                    "",\n')
src = src.replace('overlay.addView(tv(formatInt(v.km) + " km  •  " + v.fuelType + (v.plate.isEmpty() ? "" : "  •  " + v.plate), Color.argb(220,255,255,255), 11, false));',
                  'overlay.addView(tv(formatInt(v.km) + " km  •  " + v.fuelType, Color.argb(220,255,255,255), 11, false));')
activity_path.write_text(src, encoding='utf-8')

# ---------------- TurkeyVehicleSpecs ----------------
spec = specs_path.read_text(encoding='utf-8')
old_specs_for = r'''    public static List<Spec> specsFor(String brand, String model, int year) {
        ArrayList<Spec> out = new ArrayList<>();
        List<Spec> list = DATA.get(key(brand, model));
        if (list == null) return out;
        for (Spec s : list) if (year >= s.fromYear && year <= s.toYear) out.add(s);
        if (out.isEmpty()) out.addAll(list);
        return out;
    }
'''
new_specs_for = r'''    public static List<Spec> specsFor(String brand, String model, int year) {
        ArrayList<Spec> out = new ArrayList<>();
        List<Spec> list = DATA.get(key(brand, model));
        if (list != null) {
            for (Spec s : list) if (year >= s.fromYear && year <= s.toYear) out.add(s);
        }
        out.addAll(TurkeyVehicleSpecsExtra.specsFor(brand, model, year));
        if (out.isEmpty() && list != null) out.addAll(list);
        return out;
    }
'''
if old_specs_for in spec:
    spec = spec.replace(old_specs_for, new_specs_for)
elif 'TurkeyVehicleSpecsExtra.specsFor' not in spec:
    raise RuntimeError('TurkeyVehicleSpecs.specsFor pattern not found')
specs_path.write_text(spec, encoding='utf-8')

# ---------------- VehicleCvPdf ----------------
pdf = pdf_path.read_text(encoding='utf-8')
pdf = pdf.replace('        boolean showPlate = prefs.getBoolean("cv_show_plate", true);', '        boolean showPlate = false;')
pdf = pdf.replace('        Writer w = new Writer(activity, document, documentNo);', '        Writer w = new Writer(activity, document, documentNo);\n        w.headerTitle = vehicle.year + " " + vehicle.brand + " " + vehicle.model;')

new_cover = r'''    private static void drawCover(Activity activity, Writer w, AppDatabase.Vehicle v,
                                  SharedPreferences prefs, String documentNo, boolean showPlate) {
        w.newPage(false);
        w.paint.setColor(Writer.ACCENT_DARK);
        w.canvas.drawRect(0, 0, Writer.PAGE_W, 108, w.paint);

        Bitmap icon = w.appIconBitmap(27);
        if (icon != null) {
            w.canvas.drawBitmap(icon, 38, 26, w.paint);
            icon.recycle();
        }
        w.text("ARAÇ DEFTERİ", 73, 43, 10.5f, Color.WHITE, true);
        w.text("Aracının dijital hafızası", 73, 59, 7.5f, 0xFFD8F5EB, false);
        w.text(v.year + " " + v.brand + " " + v.model, 38, 88, 18, Color.WHITE, true);

        int y = 128;
        String mainPhoto = pref(prefs, "vehicle_photo_uri");
        Bitmap photo = mainPhoto.isEmpty() ? null : loadBitmap(activity, Uri.parse(mainPhoto), 1800);
        if (photo != null) {
            w.drawImageCover(photo, 38, y, 519, 250);
            photo.recycle();
            y += 270;
        } else {
            w.paint.setColor(0xFFF0F5F3);
            w.canvas.drawRoundRect(new RectF(38, y, 557, y + 138), 14, 14, w.paint);
            w.text("ARAÇ FOTOĞRAFI EKLENMEMİŞ", 58, y + 61, 11, Writer.DARK, true);
            w.text("Ana araç fotoğrafı CV kapağında burada görünür.", 58, y + 83, 8, Writer.MUTED, false);
            y += 158;
        }

        String sub = joinNonEmpty("  •  ", pref(prefs, "vehicle_trim"), pref(prefs, "vehicle_engine"), pref(prefs, "vehicle_power"));
        if (!sub.isEmpty()) {
            w.text(sub, 38, y, 9, Writer.MUTED, false);
            y += 18;
        }
        String phone = pref(prefs, "cv_phone");
        if (!phone.isEmpty()) {
            w.text("İletişim: " + phone, 38, y, 9, Writer.ACCENT_DARK, true);
            y += 20;
        }

        String body = pref(prefs, "vehicle_body");
        String trans = pref(prefs, "vehicle_transmission");
        boolean showPrice = prefs.getBoolean("cv_show_price", false);
        String price = pref(prefs, "cv_sale_price");
        w.coverChip(38, y + 6, 250, "GÜNCEL KM", formatInt(v.km) + " km");
        w.coverChip(307, y + 6, 250, "YAKIT", blankFallback(v.fuelType, "Belirtilmedi"));
        w.coverChip(38, y + 62, 250, "KASA / VİTES", joinNonEmpty(" • ", body, trans));
        if (showPrice && !price.isEmpty())
            w.coverChip(307, y + 62, 250, "İSTENEN SATIŞ FİYATI", price + " ₺");
        else
            w.coverChip(307, y + 62, 250, "MOTOR / GÜÇ", joinNonEmpty(" • ", pref(prefs, "vehicle_engine"), pref(prefs, "vehicle_power")));

        w.text("Bu belge Araç Defteri uygulaması ile oluşturulmuştur.", 38, 772, 7.5f, Writer.MUTED, true);
        w.text("Bilgiler kullanıcı kayıtlarından derlenmiştir; resmî doğrulama belgesi değildir.", 38, 786, 7, Writer.MUTED, false);
    }

'''
pdf = replace_between(pdf, '    private static void drawCover(', '    private static void drawVehicleIdentity(', new_cover)

new_identity = r'''    private static void drawVehicleIdentity(Writer w, AppDatabase.Vehicle v, SharedPreferences prefs,
                                            String phone, String note, boolean showPlate,
                                            AppDatabase db, boolean showCosts) {
        w.newPage(true);
        w.section("Araç bilgileri", "Teknik bilgiler ve kayıt özeti");

        w.keyValue("Araç türü", prefOr(prefs, "vehicle_type", "Otomobil"));
        w.keyValue("Marka / Model", v.brand + " / " + v.model);
        w.keyValue("Model yılı", String.valueOf(v.year));
        w.keyValue("Nesil / Seri", pref(prefs, "vehicle_generation"));
        w.keyValue("Paket / Versiyon", pref(prefs, "vehicle_trim"));
        w.keyValue("Kasa tipi", pref(prefs, "vehicle_body"));
        w.keyValue("Yakıt", v.fuelType);
        w.keyValue("Şanzıman", pref(prefs, "vehicle_transmission"));
        w.keyValue("Motor", pref(prefs, "vehicle_engine"));
        w.keyValue("Motor gücü", pref(prefs, "vehicle_power"));
        w.keyValue("Çekiş", pref(prefs, "vehicle_drivetrain"));
        w.keyValue("Renk", pref(prefs, "vehicle_color"));
        w.keyValue("Güncel kilometre", formatInt(v.km) + " km");

        w.ensure(180);
        w.y += 8;
        w.section("Kayıt özeti", null);
        w.summaryGrid(new String[][]{
                {"Bakım", db.countRecordsByType("Bakım") + " kayıt"},
                {"Hasar", db.countRecordsByType("Hasar") + " kayıt"},
                {"Ekspertiz", db.countRecordsByType("Ekspertiz") + " kayıt"},
                {"Muayene", db.countRecordsByType("Muayene") + " kayıt"},
                {"Sigorta / Kasko", db.countRecordsByType("Sigorta/Kasko") + " kayıt"},
                {"Vergi", db.countRecordsByType("Vergi") + " kayıt"},
                {"Yakıt", db.countRecordsByType("Yakıt") + " kayıt"},
                {showCosts ? "Toplam gider" : "Toplam kayıt", showCosts ? formatMoney(db.getExpenseTotal()) : db.countAllRecords() + " kayıt"}
        });
    }

'''
pdf = replace_between(pdf, '    private static void drawVehicleIdentity(', '    private static void drawHistory(', new_identity)

new_history = r'''    private static void drawHistory(Writer w, AppDatabase db, boolean showCosts) {
        List<AppDatabase.Record> all = db.getRecords(500);
        String[] types = {"Bakım", "Hasar", "Ekspertiz", "Muayene", "Sigorta/Kasko", "Vergi", "Yakıt"};
        String[] titles = {"Bakım & Onarım", "Hasar Geçmişi", "Ekspertiz", "Muayene", "Sigorta & Kasko", "Vergi / Resmî Ödemeler", "Yakıt / Enerji"};
        for (int i = 0; i < types.length; i++) {
            List<AppDatabase.Record> group = recordsOfType(all, types[i]);
            if (group.isEmpty()) continue;
            w.ensure(92);
            w.y += 8;
            w.section(titles[i], null);
            for (AppDatabase.Record r : group) w.recordCard(r, showCosts);
        }

        List<AppDatabase.Expense> expenses = db.getExpenses();
        if (!expenses.isEmpty()) {
            w.ensure(92);
            w.y += 8;
            w.section("Diğer Giderler", null);
            int n = 0;
            for (AppDatabase.Expense e : expenses) {
                if (n++ >= 60) break;
                String value = e.date + (e.note.isEmpty() ? "" : " • " + e.note);
                String right = showCosts ? formatMoney(e.amount) : "";
                w.simpleCard(e.category, value, right);
            }
        }
    }

'''
pdf = replace_between(pdf, '    private static void drawHistory(', '    private static void drawPhotos(', new_history)

new_about = r'''    private static void drawAbout(Writer w) {
        w.ensure(170);
        w.y += 10;
        w.section("Açıklama", null);
        w.paragraph("Bu PDF Araç Defteri uygulaması ile kullanıcı tarafından girilen araç kayıtlarından oluşturulmuştur.", 8.5f, Writer.DARK);
        w.y += 5;
        w.paragraph("Belgedeki satış fiyatı varsa kullanıcı tarafından girilen istenen fiyattır; bağımsız değerleme değildir.", 8.5f, Writer.DARK);
        w.y += 5;
        w.paragraph("Araç geçmişi, hasar, kilometre ve diğer bilgiler resmî doğrulama niteliği taşımaz. Şase/VIN bilgisi tutulmaz.", 8.5f, Writer.DARK);
    }

'''
pdf = replace_between(pdf, '    private static void drawAbout(', '    private static Uri writeDocument(', new_about)

# Compact writer and put vehicle model in the inner-page header.
pdf = pdf.replace('        final String documentNo;\n        final Paint paint', '        final String documentNo;\n        String headerTitle = "";\n        final Paint paint')
pdf = pdf.replace('        static final int PAGE_W = 595, PAGE_H = 842, LEFT = 42, RIGHT = 553, CONTENT_W = RIGHT - LEFT;',
                  '        static final int PAGE_W = 595, PAGE_H = 842, LEFT = 38, RIGHT = 557, CONTENT_W = RIGHT - LEFT;')

new_header = r'''        void drawHeader() {
            Bitmap icon = appIconBitmap(19);
            if (icon != null) {
                canvas.drawBitmap(icon, LEFT, 27, paint);
                icon.recycle();
            }
            text("ARAÇ DEFTERİ", LEFT + 27, 41, 8.5f, ACCENT, true);
            String h = headerTitle == null ? "" : headerTitle;
            if (h.length() > 34) h = h.substring(0, 33) + "…";
            text(h, RIGHT - 190, 41, 7.5f, DARK, true);
            paint.setColor(0xFFE6ECEA);
            canvas.drawRect(LEFT, 56, RIGHT, 57, paint);
            y = 76;
        }

'''
pdf = replace_between(pdf, '        void drawHeader() {', '        Bitmap appIconBitmap(', new_header)

new_section = r'''        void section(String title, String subtitle) {
            ensure(47);
            text(title, LEFT, y, 14, ACCENT, true);
            y += 15;
            if (subtitle != null && !subtitle.isEmpty()) {
                text(subtitle, LEFT, y, 7.5f, MUTED, false);
                y += 12;
            } else y += 4;
            paint.setColor(0xFFE6ECEA);
            canvas.drawRect(LEFT, y, RIGHT, y + 1, paint);
            y += 11;
        }

'''
pdf = replace_between(pdf, '        void section(String title, String subtitle) {', '        void label(String value) {', new_section)

new_key_value = r'''        void keyValue(String key, String value) {
            if (value == null || value.trim().isEmpty()) return;
            int h = Math.max(24, measuredWrappedHeight(value, 8.8f, 325) + 8);
            ensure(h + 2);
            if ((y / 24) % 2 == 0) {
                paint.setColor(0xFFF8FAF9);
                canvas.drawRoundRect(new RectF(LEFT, y - 6, RIGHT, y + h - 5), 6, 6, paint);
            }
            text(key.toUpperCase(new Locale("tr", "TR")), LEFT + 7, y + 4, 6.8f, MUTED, true);
            drawWrapped(value, LEFT + 155, y + 4, 8.8f, DARK, true, 350, 11);
            y += h;
        }

'''
pdf = replace_between(pdf, '        void keyValue(String key, String value) {', '        void summaryGrid(String[][] items) {', new_key_value)

new_summary_grid = r'''        void summaryGrid(String[][] items) {
            int boxW = 251, boxH = 43, gap = 17;
            for (int i = 0; i < items.length; i += 2) {
                ensure(boxH + 8);
                summaryBox(LEFT, y, boxW, boxH, items[i][0], items[i][1]);
                if (i + 1 < items.length) summaryBox(LEFT + boxW + gap, y, boxW, boxH, items[i + 1][0], items[i + 1][1]);
                y += boxH + 8;
            }
        }

'''
pdf = replace_between(pdf, '        void summaryGrid(String[][] items) {', '        void summaryBox(', new_summary_grid)
pdf = pdf.replace('            text(title.toUpperCase(new Locale("tr", "TR")), x + 13, yy + 19, 7, MUTED, true);\n            text(value, x + 13, yy + 40, 13, DARK, true);',
                  '            text(title.toUpperCase(new Locale("tr", "TR")), x + 11, yy + 15, 6.5f, MUTED, true);\n            text(value, x + 11, yy + 33, 10.5f, DARK, true);')

new_record = r'''        void recordCard(AppDatabase.Record r, boolean showCosts) {
            String meta = r.date + " • " + formatInt(r.km) + " km";
            if (!r.subtype.isEmpty()) meta += " • " + r.subtype;
            if (showCosts && r.cost > 0) meta += " • " + formatMoney(r.cost);
            String detail = r.detail == null ? "" : r.detail.trim();
            String next = "";
            if (!r.nextDate.isEmpty() || r.nextKm > 0) next = "Sonraki: " + joinNonEmpty(" • ", r.nextDate, r.nextKm > 0 ? formatInt(r.nextKm) + " km" : "");
            int detailH = detail.isEmpty() ? 0 : measuredWrappedHeight(detail, 7.8f, CONTENT_W - 26) + 5;
            int h = 50 + detailH + (next.isEmpty() ? 0 : 14);
            ensure(h + 7);
            paint.setColor(0xFFF7FAF9);
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + h), 9, 9, paint);
            text(r.title, LEFT + 13, y + 18, 9.5f, DARK, true);
            text(meta, LEFT + 13, y + 33, 7, MUTED, false);
            int yy = y + 46;
            if (!detail.isEmpty()) yy = drawWrapped(detail, LEFT + 13, yy, 7.8f, DARK, false, CONTENT_W - 26, 10) + 2;
            if (!next.isEmpty()) text(next, LEFT + 13, yy + 8, 7.2f, ACCENT_DARK, true);
            y += h + 7;
        }

'''
pdf = replace_between(pdf, '        void recordCard(AppDatabase.Record r, boolean showCosts) {', '        void simpleCard(', new_record)

new_simple = r'''        void simpleCard(String title, String subtitle, String right) {
            int h = 45;
            ensure(h + 6);
            paint.setColor(0xFFF7FAF9);
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + h), 8, 8, paint);
            text(title, LEFT + 12, y + 18, 9, DARK, true);
            text(subtitle, LEFT + 12, y + 33, 7, MUTED, false);
            if (right != null && !right.isEmpty()) text(right, RIGHT - 95, y + 18, 8, ACCENT_DARK, true);
            y += h + 6;
        }

'''
pdf = replace_between(pdf, '        void simpleCard(String title, String subtitle, String right) {', '        void coverChip(', new_simple)

new_chip = r'''        void coverChip(int x, int yy, int w, String label, String value) {
            paint.setColor(0xFFF1F6F4);
            canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 46), 10, 10, paint);
            text(label, x + 11, yy + 15, 6.2f, MUTED, true);
            String v = value == null || value.trim().isEmpty() ? "Belirtilmedi" : value;
            if (v.length() > 31) v = v.substring(0, 30) + "…";
            text(v, x + 11, yy + 34, 9.2f, DARK, true);
        }

'''
pdf = replace_between(pdf, '        void coverChip(int x, int yy, int w, String label, String value) {', '        void infoBox(', new_chip)

pdf = pdf.replace('            if (y + required > 785) newPage(true);', '            if (y + required > 790) newPage(true);')
pdf = pdf.replace('            y = 44;\n            if (header) drawHeader();', '            y = 38;\n            if (header) drawHeader();')
pdf = pdf.replace('            text("Araç Defteri • Belge No: " + documentNo, LEFT, 820, 7, MUTED, false);\n            text("Sayfa " + pageNo, RIGHT - 37, 820, 7, MUTED, false);',
                  '            text("Araç Defteri • Belge No: " + documentNo, LEFT, 820, 6.5f, MUTED, false);\n            text("Sayfa " + pageNo, RIGHT - 34, 820, 6.5f, MUTED, false);')
pdf_path.write_text(pdf, encoding='utf-8')

# ---------------- Version ----------------
gradle = gradle_path.read_text(encoding='utf-8')
gradle = re.sub(r'versionCode\s+\d+', 'versionCode 6', gradle)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '0.6.0-compact-catalog-dev'", gradle)
gradle_path.write_text(gradle, encoding='utf-8')

print('Araç Defteri v0.6 compact CV + expanded Türkiye catalog patches applied.')
