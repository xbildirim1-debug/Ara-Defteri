from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ACTIVITY = ROOT / "app/src/main/java/com/aracdefteri/app/NextMainActivity.java"
GRADLE = ROOT / "app/build.gradle"
WORKFLOW = ROOT / ".github/workflows/build-apk.yml"


def replace_method(src: str, signature: str, replacement: str) -> str:
    start = src.find(signature)
    if start < 0:
        raise RuntimeError(f"Method not found: {signature}")
    brace = src.find("{", start)
    if brace < 0:
        raise RuntimeError(f"Opening brace not found: {signature}")
    depth = 0
    i = brace
    in_string = False
    in_char = False
    escape = False
    while i < len(src):
        ch = src[i]
        if escape:
            escape = False
        elif ch == "\\" and (in_string or in_char):
            escape = True
        elif ch == '"' and not in_char:
            in_string = not in_string
        elif ch == "'" and not in_string:
            in_char = not in_char
        elif not in_string and not in_char:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    return src[:start] + replacement.strip() + src[end:]
        i += 1
    raise RuntimeError(f"Closing brace not found: {signature}")


src = ACTIVITY.read_text(encoding="utf-8")

if "PICK_CV_IMAGES" not in src:
    src = src.replace(
        '    private static final int PICK_VEHICLE_IMAGE = 3103;\n',
        '    private static final int PICK_VEHICLE_IMAGE = 3103;\n    private static final int PICK_CV_IMAGES = 3104;\n'
    )

if "cvPhotoUris" not in src:
    src = src.replace(
        '    private boolean dark;\n',
        '    private boolean dark;\n    private final ArrayList<Uri> cvPhotoUris = new ArrayList<>();\n    private TextView cvPhotoCountView;\n'
    )

src = replace_method(src, "    private void buildBottomNav()", r'''
    private void buildBottomNav() {
        navBar.removeAllViews();
        String[] labels = {"Ana Sayfa", "Kayıtlar", "Araç CV", "Ayarlar"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_records, R.drawable.ic_nav_cv, R.drawable.ic_nav_settings};
        for (int i = 0; i < labels.length; i++) {
            final int page = i;
            boolean active = currentModule == null && currentPage == i && selectedRecordId < 0;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(5), dp(5), dp(5), dp(3));
            if (active) item.setBackground(cardDrawable(accentSoft, dp(18), Color.TRANSPARENT));
            ImageView icon = new ImageView(this);
            icon.setImageResource(icons[i]);
            icon.setColorFilter(active ? accent : muted);
            item.addView(icon, new LinearLayout.LayoutParams(dp(23), dp(23)));
            TextView label = tv(labels[i], active ? text : muted, 9, active);
            label.setGravity(Gravity.CENTER);
            item.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(19)));
            item.setOnClickListener(v -> renderPage(page));
            navBar.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }
''')

src = replace_method(src, "    private void renderPage(int page)", r'''
    private void renderPage(int page) {
        currentPage = page;
        currentModule = null;
        selectedRecordId = -1;
        buildBottomNav();
        LinearLayout content = newContent();
        if (page == 0) renderHome(content);
        else if (page == 1) renderRecordsHub(content);
        else if (page == 2) renderCv(content);
        else renderSettings(content);
    }
''')

src = replace_method(src, "    private void renderCv(LinearLayout content)", r'''
    private void renderCv(LinearLayout content) {
        addHeader(content, "Araç CV", "Detaylı, markalı ve paylaşılabilir araç geçmişi");
        AppDatabase.Vehicle v = db.getVehicle();

        LinearLayout summary = card();
        summary.setPadding(dp(18),dp(18),dp(18),dp(18));
        summary.addView(tv("DİJİTAL ARAÇ GEÇMİŞİ", accent, 10, true));
        summary.addView(tv(v.year + "  " + v.brand + " " + v.model, text, 22, true));
        String vehicleMeta = prefs.getString("vehicle_body", "") + "  •  " + v.fuelType;
        summary.addView(tv(vehicleMeta.replaceFirst("^\\s*•\\s*", ""), muted, 11, false));
        gap(summary,14);
        detailRow(summary,"Bakım",db.countRecordsByType("Bakım")+" kayıt");
        detailRow(summary,"Hasar",db.countRecordsByType("Hasar")+" kayıt");
        detailRow(summary,"Ekspertiz",db.countRecordsByType("Ekspertiz")+" kayıt");
        detailRow(summary,"Toplam",db.countAllRecords()+" kayıt");
        String lastNo = prefs.getString("cv_last_document_no", "");
        if (!lastNo.isEmpty()) detailRow(summary, "Son belge no", lastNo);
        content.addView(summary);
        gap(content,14);

        sectionTitle(content, "CV fotoğrafları", "Ana araç fotoğrafına ek olarak en fazla 10 fotoğraf seç");
        LinearLayout photoCard = card();
        photoCard.setPadding(dp(15),dp(14),dp(15),dp(14));
        cvPhotoCountView = tv(cvPhotoUris.size() + " / " + VehicleCvPdf.MAX_EXTRA_PHOTOS + " ekstra fotoğraf seçildi", text, 13, true);
        photoCard.addView(cvPhotoCountView);
        photoCard.addView(tv("Bu fotoğraflar yalnız oluşturulan PDF'de kullanılır; Araç Defteri galerisine veya buluta ayrıca kaydedilmez.", muted, 10, false));
        gap(photoCard,10);
        LinearLayout photoActions = new LinearLayout(this);
        photoActions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = secondaryButton("Fotoğraf seç");
        choose.setOnClickListener(vw -> pickCvImages());
        photoActions.addView(choose, new LinearLayout.LayoutParams(0,dp(44),1f));
        gapHorizontal(photoActions,8);
        Button clear = secondaryButton("Temizle");
        clear.setOnClickListener(vw -> {
            cvPhotoUris.clear();
            renderPage(2);
        });
        photoActions.addView(clear, new LinearLayout.LayoutParams(0,dp(44),1f));
        photoCard.addView(photoActions);
        content.addView(photoCard);
        gap(content,16);

        sectionTitle(content, "PDF seçenekleri", "Belgede ne görüneceğini sen belirle");
        LinearLayout options = card();
        options.setPadding(dp(15),dp(14),dp(15),dp(14));
        CheckBox showPlate = new CheckBox(this);
        showPlate.setText("Plakayı PDF'de göster");
        showPlate.setTextColor(text);
        showPlate.setTextSize(12);
        showPlate.setChecked(prefs.getBoolean("cv_show_plate", true));
        options.addView(showPlate);
        CheckBox showCosts = new CheckBox(this);
        showCosts.setText("Maliyetleri PDF'de göster");
        showCosts.setTextColor(text);
        showCosts.setTextSize(12);
        showCosts.setChecked(prefs.getBoolean("cv_show_costs", false));
        options.addView(showCosts);
        EditText phone = formField(options, "Telefon (isteğe bağlı)", prefs.getString("cv_phone", ""), InputType.TYPE_CLASS_PHONE);
        EditText note = formMultiline(options, "CV özel notu (isteğe bağlı)", prefs.getString("cv_note", ""));
        content.addView(options);
        gap(content,14);

        content.addView(infoCard("Araç Defteri kimliği", "PDF kapağında uygulama adı ve ikonu; her sayfada ayrıca benzersiz belge numarası bulunur.", accent));
        gap(content,14);
        Button create = primaryButton("Detaylı PDF Araç CV oluştur");
        create.setOnClickListener(vw -> {
            prefs.edit()
                    .putBoolean("cv_show_plate", showPlate.isChecked())
                    .putBoolean("cv_show_costs", showCosts.isChecked())
                    .putString("cv_phone", phone.getText().toString().trim())
                    .putString("cv_note", note.getText().toString().trim())
                    .apply();
            createVehiclePdf();
        });
        content.addView(create, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
    }
''')

src = replace_method(src, "    private void openVehicleForm()", r'''
    private void openVehicleForm() {
        currentPage = 0;
        currentModule = "Araç";
        selectedRecordId = -1;
        buildBottomNav();
        LinearLayout content = newContent();
        addBackHeader(content,"Araç bilgileri","Türkiye araç kataloğundan seç veya manuel gir",()->renderPage(0));
        AppDatabase.Vehicle v = db.getVehicle();

        String savedType = prefs.getString("vehicle_type", TurkeyVehicleCatalog.TYPE_CAR);
        LinearLayout form = card();
        form.setPadding(dp(16),dp(16),dp(16),dp(16));
        form.addView(formSection("Araç kataloğu", "Araç türü → marka → model; listede yoksa manuel alanları kullan"));

        Spinner type = formSpinner(form,"Araç türü",TurkeyVehicleCatalog.vehicleTypes(),savedType);
        Spinner brand = formSpinner(form,"Marka",TurkeyVehicleCatalog.brandsForType(savedType),v.brand);
        Spinner model = formSpinner(form,"Model",TurkeyVehicleCatalog.modelsFor(savedType,v.brand),v.model);
        Spinner year = formSpinner(form,"Model yılı",TurkeyVehicleCatalog.years(),String.valueOf(v.year));

        EditText manualBrand = formField(form,"Manuel marka (yalnız listede yoksa)","Diğer / Manuel".equals(String.valueOf(brand.getSelectedItem())) ? v.brand : "",InputType.TYPE_CLASS_TEXT);
        EditText manualModel = formField(form,"Manuel model (yalnız listede yoksa)","Listede yok / Manuel".equals(String.valueOf(model.getSelectedItem())) ? v.model : "",InputType.TYPE_CLASS_TEXT);

        form.addView(formSection("Teknik bilgiler", "CV kapağı ve araç kimliği sayfasında kullanılacak"));
        Spinner body = formSpinner(form,"Kasa tipi",TurkeyVehicleCatalog.bodyTypesFor(savedType),prefs.getString("vehicle_body", ""));
        Spinner fuel = formSpinner(form,"Yakıt / güç tipi",TurkeyVehicleCatalog.fuelTypes(),v.fuelType);
        Spinner transmission = formSpinner(form,"Şanzıman",TurkeyVehicleCatalog.transmissions(),prefs.getString("vehicle_transmission", ""));
        EditText generation = formField(form,"Nesil / seri (isteğe bağlı)",prefs.getString("vehicle_generation", ""),InputType.TYPE_CLASS_TEXT);
        EditText trim = formField(form,"Paket / versiyon (isteğe bağlı)",prefs.getString("vehicle_trim", ""),InputType.TYPE_CLASS_TEXT);
        EditText engine = formField(form,"Motor (örn. 1.5 dCi / 1.8 Hybrid)",prefs.getString("vehicle_engine", ""),InputType.TYPE_CLASS_TEXT);
        EditText power = formField(form,"Motor gücü (isteğe bağlı)",prefs.getString("vehicle_power", ""),InputType.TYPE_CLASS_TEXT);
        EditText drivetrain = formField(form,"Çekiş (önden / arkadan / 4x4)",prefs.getString("vehicle_drivetrain", ""),InputType.TYPE_CLASS_TEXT);
        EditText color = formField(form,"Renk (isteğe bağlı)",prefs.getString("vehicle_color", ""),InputType.TYPE_CLASS_TEXT);
        EditText plate = formField(form,"Plaka (isteğe bağlı)",v.plate,InputType.TYPE_CLASS_TEXT);
        EditText km = formField(form,"Güncel kilometre",String.valueOf(v.km),InputType.TYPE_CLASS_NUMBER);

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedType = String.valueOf(type.getSelectedItem());
                String currentBrand = first ? v.brand : String.valueOf(brand.getSelectedItem());
                first = false;
                setSpinnerItems(brand, TurkeyVehicleCatalog.brandsForType(selectedType), currentBrand);
                String selectedBrand = String.valueOf(brand.getSelectedItem());
                setSpinnerItems(model, TurkeyVehicleCatalog.modelsFor(selectedType, selectedBrand), v.model);
                setSpinnerItems(body, TurkeyVehicleCatalog.bodyTypesFor(selectedType), prefs.getString("vehicle_body", ""));
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        brand.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedType = String.valueOf(type.getSelectedItem());
                String selectedBrand = String.valueOf(brand.getSelectedItem());
                String preferred = first ? v.model : "";
                first = false;
                setSpinnerItems(model, TurkeyVehicleCatalog.modelsFor(selectedType, selectedBrand), preferred);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        content.addView(form);
        gap(content,14);
        Button save = primaryButton("Araç bilgilerini kaydet");
        save.setOnClickListener(x -> {
            String selectedType = String.valueOf(type.getSelectedItem());
            String selectedBrand = String.valueOf(brand.getSelectedItem());
            String selectedModel = String.valueOf(model.getSelectedItem());
            if ("Diğer / Manuel".equals(selectedBrand)) selectedBrand = manualBrand.getText().toString().trim();
            if ("Listede yok / Manuel".equals(selectedModel)) selectedModel = manualModel.getText().toString().trim();
            if(selectedBrand.isEmpty() || selectedModel.isEmpty()) {
                toast("Marka ve model gerekli");
                return;
            }
            int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), v.year);
            db.updateVehicle(
                    selectedBrand,
                    selectedModel,
                    selectedYear,
                    plate.getText().toString().trim(),
                    safeInt(km.getText().toString(),v.km),
                    String.valueOf(fuel.getSelectedItem())
            );
            prefs.edit()
                    .putString("vehicle_type", selectedType)
                    .putString("vehicle_body", String.valueOf(body.getSelectedItem()))
                    .putString("vehicle_transmission", String.valueOf(transmission.getSelectedItem()))
                    .putString("vehicle_generation", generation.getText().toString().trim())
                    .putString("vehicle_trim", trim.getText().toString().trim())
                    .putString("vehicle_engine", engine.getText().toString().trim())
                    .putString("vehicle_power", power.getText().toString().trim())
                    .putString("vehicle_drivetrain", drivetrain.getText().toString().trim())
                    .putString("vehicle_color", color.getText().toString().trim())
                    .apply();
            toast("Araç profili güncellendi");
            renderPage(0);
        });
        content.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
    }
''')

# Add dynamic spinner helper before formLabel.
helper = r'''
    private void setSpinnerItems(Spinner spinner, String[] values, String selected) {
        ArrayList<String> list = new ArrayList<>(Arrays.asList(values));
        if (selected != null && !selected.isEmpty() && !list.contains(selected)) list.add(0, selected);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, list) {
            @Override public View getView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getView(position, convertView, parent);
                t.setTextColor(text); t.setTextSize(13); t.setPadding(dp(13),0,dp(13),0); return t;
            }
            @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView t = (TextView) super.getDropDownView(position, convertView, parent);
                t.setTextColor(Color.rgb(25,30,30)); t.setTextSize(14); t.setPadding(dp(16),dp(12),dp(16),dp(12)); return t;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected != null && !selected.isEmpty()) {
            int pos = list.indexOf(selected);
            if (pos >= 0) spinner.setSelection(pos);
        }
    }

'''
if "private void setSpinnerItems(Spinner spinner" not in src:
    marker = "    private TextView formLabel(String value)"
    pos = src.find(marker)
    if pos < 0:
        raise RuntimeError("formLabel marker not found")
    src = src[:pos] + helper + src[pos:]

# Add CV image picker next to existing pickers.
picker = r'''
    private void pickCvImages() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(i, PICK_CV_IMAGES);
    }

'''
if "private void pickCvImages()" not in src:
    marker = "    private void pickRecordAttachment(long id)"
    pos = src.find(marker)
    if pos < 0:
        raise RuntimeError("pickRecordAttachment marker not found")
    src = src[:pos] + picker + src[pos:]

src = replace_method(src, "    protected void onActivityResult(int requestCode,int resultCode,Intent data)", r'''
    protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(resultCode!=RESULT_OK || data==null) return;

        if (requestCode == PICK_CV_IMAGES) {
            cvPhotoUris.clear();
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount() && cvPhotoUris.size() < VehicleCvPdf.MAX_EXTRA_PHOTOS; i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (u == null) continue;
                    try { getContentResolver().takePersistableUriPermission(u, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                    cvPhotoUris.add(u);
                }
                if (clip.getItemCount() > VehicleCvPdf.MAX_EXTRA_PHOTOS) toast("En fazla 10 CV fotoğrafı kullanılır");
            } else if (data.getData() != null) {
                Uri u = data.getData();
                try { getContentResolver().takePersistableUriPermission(u, data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) {}
                cvPhotoUris.add(u);
            }
            renderPage(2);
            return;
        }

        if (data.getData() == null) return;
        Uri uri=data.getData();
        try {
            getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch(Exception ignored) {}
        if(requestCode==PICK_VEHICLE_IMAGE) {
            prefs.edit().putString("vehicle_photo_uri",uri.toString()).apply();
            renderPage(0);
        } else if(requestCode==PICK_GALLERY_IMAGE) {
            db.addPhoto(uri.toString(),today());
            renderPage(0);
        } else if(requestCode==PICK_RECORD_ATTACHMENT&&pendingAttachmentRecordId>=0) {
            db.setRecordAttachment(pendingAttachmentRecordId,uri.toString());
            long id=pendingAttachmentRecordId;
            pendingAttachmentRecordId=-1;
            openRecordDetail(id);
        }
    }
''')

src = replace_method(src, "    private void createVehiclePdf()", r'''
    private void createVehiclePdf() {
        try {
            VehicleCvPdf.Result result = VehicleCvPdf.create(this, db, prefs, cvPhotoUris);
            toast("Araç CV oluşturuldu • Belge No: " + result.documentNo);
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.Q) openPdf(result.uri);
        } catch(Exception e) {
            toast("PDF oluşturulamadı: " + e.getMessage());
        }
    }
''')

ACTIVITY.write_text(src, encoding="utf-8")

gradle = GRADLE.read_text(encoding="utf-8")
gradle = gradle.replace("versionCode 3", "versionCode 4")
gradle = gradle.replace("versionName '0.3.0-dev'", "versionName '0.4.0-catalog-dev'")
GRADLE.write_text(gradle, encoding="utf-8")

if WORKFLOW.exists():
    wf = WORKFLOW.read_text(encoding="utf-8")
    wf = wf.replace(
        "Otomatik oluşturulan demo APK. Açık/koyu/sistem tema, bakım, hasar, ekspertiz, gider, galeri, hatırlatma ve Araç CV PDF prototipi.",
        "Araç Defteri v0.4 katalog geliştirme sürümü. Türkiye araç kataloğu, 10 CV fotoğrafı, markalı detaylı PDF ve belge numarası içerir."
    )
    WORKFLOW.write_text(wf, encoding="utf-8")

print("v0.4 catalog/CV patch applied")
