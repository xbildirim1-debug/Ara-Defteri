from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
activity_path = root / 'app/src/main/java/com/aracdefteri/app/NextMainActivity.java'
gradle_path = root / 'app/build.gradle'

src = activity_path.read_text(encoding='utf-8')

# 1) Vehicle form: catalog variant -> automatic technical data.
start = src.index('private void openVehicleForm() {')
end = src.index('    private static class DateInput {', start)
new_vehicle_form = r'''private void openVehicleForm() {
        currentPage = 0;
        currentModule = "Araç";
        selectedRecordId = -1;
        buildBottomNav();
        LinearLayout content = newContent();
        addBackHeader(content,"Araç bilgileri","Türkiye kataloğundan seç; teknik bilgiler otomatik dolsun",()->renderPage(0));
        AppDatabase.Vehicle v = db.getVehicle();

        String savedType = prefs.getString("vehicle_type", TurkeyVehicleCatalog.TYPE_CAR);
        LinearLayout form = card();
        form.setPadding(dp(16),dp(16),dp(16),dp(16));
        form.addView(formSection("Araç seçimi", "Araç türü → marka → model → yıl → motor / paket"));

        Spinner type = formSpinner(form,"Araç türü",TurkeyVehicleCatalog.vehicleTypes(),savedType);
        Spinner brand = formSpinner(form,"Marka",TurkeyVehicleCatalog.brandsForType(savedType),v.brand);
        Spinner model = formSpinner(form,"Model",TurkeyVehicleCatalog.modelsFor(savedType,v.brand),v.model);
        Spinner year = formSpinner(form,"Model yılı",TurkeyVehicleCatalog.years(),String.valueOf(v.year));
        String savedVariant = prefs.getString("vehicle_catalog_variant", "");
        Spinner variant = formSpinner(form,"Motor / paket / versiyon",
                TurkeyVehicleSpecs.variantLabels(v.brand, v.model, v.year), savedVariant);

        EditText manualBrand = formField(form,"Manuel marka (yalnız listede yoksa)","Diğer / Manuel".equals(String.valueOf(brand.getSelectedItem())) ? v.brand : "",InputType.TYPE_CLASS_TEXT);
        EditText manualModel = formField(form,"Manuel model (yalnız listede yoksa)","Listede yok / Manuel".equals(String.valueOf(model.getSelectedItem())) ? v.model : "",InputType.TYPE_CLASS_TEXT);

        form.addView(formSection("Otomatik teknik bilgiler", "Eşleşen varyant seçildiğinde aşağıdaki alanlar katalogdan doldurulur"));
        Spinner body = formSpinner(form,"Kasa tipi",TurkeyVehicleCatalog.bodyTypesFor(savedType),prefs.getString("vehicle_body", ""));
        Spinner fuel = formSpinner(form,"Yakıt / güç tipi",TurkeyVehicleCatalog.fuelTypes(),v.fuelType);
        Spinner transmission = formSpinner(form,"Şanzıman",TurkeyVehicleCatalog.transmissions(),prefs.getString("vehicle_transmission", ""));
        EditText generation = formField(form,"Nesil / seri",prefs.getString("vehicle_generation", ""),InputType.TYPE_CLASS_TEXT);
        EditText trim = formField(form,"Paket / versiyon",prefs.getString("vehicle_trim", ""),InputType.TYPE_CLASS_TEXT);
        EditText engine = formField(form,"Motor",prefs.getString("vehicle_engine", ""),InputType.TYPE_CLASS_TEXT);
        EditText power = formField(form,"Motor gücü",prefs.getString("vehicle_power", ""),InputType.TYPE_CLASS_TEXT);
        EditText drivetrain = formField(form,"Çekiş",prefs.getString("vehicle_drivetrain", ""),InputType.TYPE_CLASS_TEXT);
        TextView catalogState = tv("Model ve yılı seçtiğinde uygun motor/paket seçenekleri otomatik yüklenir.", accent, 10, true);
        catalogState.setPadding(dp(2),dp(2),dp(2),dp(12));
        form.addView(catalogState);

        form.addView(formSection("Sana özel bilgiler", "Bunlar katalogdan gelmez"));
        EditText color = formField(form,"Renk (isteğe bağlı)",prefs.getString("vehicle_color", ""),InputType.TYPE_CLASS_TEXT);
        EditText plate = formField(form,"Plaka (isteğe bağlı)",v.plate,InputType.TYPE_CLASS_TEXT);
        EditText km = formField(form,"Güncel kilometre",String.valueOf(v.km),InputType.TYPE_CLASS_NUMBER);

        final boolean[] updating = {false};

        Runnable refreshVariant = () -> {
            if (updating[0]) return;
            updating[0] = true;
            String b = String.valueOf(brand.getSelectedItem());
            String m = String.valueOf(model.getSelectedItem());
            int y = safeInt(String.valueOf(year.getSelectedItem()), v.year);
            updateVariantChoices(variant, b, m, y, "");
            applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                    generation, trim, engine, power, drivetrain, catalogState);
            updating[0] = false;
        };

        type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating[0]) return;
                updating[0] = true;
                String selectedType = String.valueOf(type.getSelectedItem());
                String preferredBrand = first ? v.brand : "";
                first = false;
                setSpinnerItems(brand, TurkeyVehicleCatalog.brandsForType(selectedType), preferredBrand);
                String selectedBrand = String.valueOf(brand.getSelectedItem());
                setSpinnerItems(model, TurkeyVehicleCatalog.modelsFor(selectedType, selectedBrand), "");
                setSpinnerItems(body, TurkeyVehicleCatalog.bodyTypesFor(selectedType), "");
                String selectedModel = String.valueOf(model.getSelectedItem());
                int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), v.year);
                updateVariantChoices(variant, selectedBrand, selectedModel, selectedYear, "");
                applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                        generation, trim, engine, power, drivetrain, catalogState);
                updating[0] = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        brand.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean first = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating[0]) return;
                updating[0] = true;
                String selectedType = String.valueOf(type.getSelectedItem());
                String selectedBrand = String.valueOf(brand.getSelectedItem());
                String preferred = first ? v.model : "";
                first = false;
                setSpinnerItems(model, TurkeyVehicleCatalog.modelsFor(selectedType, selectedBrand), preferred);
                String selectedModel = String.valueOf(model.getSelectedItem());
                int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), v.year);
                updateVariantChoices(variant, selectedBrand, selectedModel, selectedYear, "");
                applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                        generation, trim, engine, power, drivetrain, catalogState);
                updating[0] = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        model.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                refreshVariant.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        year.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                refreshVariant.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        variant.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (updating[0]) return;
                applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                        generation, trim, engine, power, drivetrain, catalogState);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        // İlk açılışta kayıtlı varyantı koru ve teknik alanları otomatik doldur.
        updateVariantChoices(variant, v.brand, v.model, v.year, savedVariant);
        applySelectedVehicleSpec(type, brand, model, year, variant, body, fuel, transmission,
                generation, trim, engine, power, drivetrain, catalogState);

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
                    .putString("vehicle_catalog_variant", String.valueOf(variant.getSelectedItem()))
                    .putString("vehicle_body", String.valueOf(body.getSelectedItem()))
                    .putString("vehicle_transmission", String.valueOf(transmission.getSelectedItem()))
                    .putString("vehicle_generation", generation.getText().toString().trim())
                    .putString("vehicle_trim", trim.getText().toString().trim())
                    .putString("vehicle_engine", engine.getText().toString().trim())
                    .putString("vehicle_power", power.getText().toString().trim())
                    .putString("vehicle_drivetrain", drivetrain.getText().toString().trim())
                    .putString("vehicle_color", color.getText().toString().trim())
                    .apply();
            toast("Araç profili ve teknik bilgiler kaydedildi");
            renderPage(0);
        });
        content.addView(save,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
    }

'''
src = src[:start] + new_vehicle_form + src[end:]

# 2) CV photo UI: thumbnails + additive selection.
photo_start = src.index('        sectionTitle(content, "CV fotoğrafları"')
photo_end = src.index('        sectionTitle(content, "PDF seçenekleri"', photo_start)
new_photo_ui = r'''        sectionTitle(content, "CV fotoğrafları", "Ana araç fotoğrafına ek olarak en fazla 10 fotoğraf seç");
        LinearLayout photoCard = card();
        photoCard.setPadding(dp(15),dp(14),dp(15),dp(14));
        cvPhotoCountView = tv(cvPhotoUris.size() + " / " + VehicleCvPdf.MAX_EXTRA_PHOTOS + " fotoğraf hazır", text, 13, true);
        photoCard.addView(cvPhotoCountView);
        photoCard.addView(tv("Seçtiklerin sadece bu CV PDF'sinde kullanılır. Tek tek ekleyebilir veya çoklu seçebilirsin.", muted, 10, false));
        gap(photoCard,10);

        if (!cvPhotoUris.isEmpty()) {
            android.widget.HorizontalScrollView scrollPhotos = new android.widget.HorizontalScrollView(this);
            scrollPhotos.setHorizontalScrollBarEnabled(false);
            LinearLayout thumbs = new LinearLayout(this);
            thumbs.setOrientation(LinearLayout.HORIZONTAL);
            thumbs.setPadding(0,dp(2),0,dp(6));
            for (Uri uri : new ArrayList<>(cvPhotoUris)) thumbs.addView(cvPhotoThumb(uri));
            scrollPhotos.addView(thumbs);
            photoCard.addView(scrollPhotos,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(94)));
        }

        LinearLayout photoActions = new LinearLayout(this);
        photoActions.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = secondaryButton(cvPhotoUris.isEmpty() ? "Fotoğraf seç" : "+ Fotoğraf ekle");
        choose.setOnClickListener(vw -> pickCvImages());
        photoActions.addView(choose, new LinearLayout.LayoutParams(0,dp(44),1f));
        gapHorizontal(photoActions,8);
        Button clear = secondaryButton("Tümünü kaldır");
        clear.setEnabled(!cvPhotoUris.isEmpty());
        clear.setAlpha(cvPhotoUris.isEmpty() ? 0.45f : 1f);
        clear.setOnClickListener(vw -> {
            cvPhotoUris.clear();
            renderPage(2);
        });
        photoActions.addView(clear, new LinearLayout.LayoutParams(0,dp(44),1f));
        photoCard.addView(photoActions);
        content.addView(photoCard);
        gap(content,16);

'''
src = src[:photo_start] + new_photo_ui + src[photo_end:]

# 3) Insert catalog helper methods after setSpinnerItems.
helper_marker = '    private TextView formLabel(String value) {'
helper_pos = src.index(helper_marker)
helpers = r'''
    private void updateVariantChoices(Spinner variant, String brand, String model, int year, String preferred) {
        String[] values = TurkeyVehicleSpecs.variantLabels(brand, model, year);
        String selected = preferred == null ? "" : preferred;
        if (!selected.isEmpty()) {
            boolean exists = false;
            for (String value : values) if (selected.equals(value)) { exists = true; break; }
            if (!exists) selected = "";
        }
        setSpinnerItems(variant, values, selected);
    }

    private void applySelectedVehicleSpec(Spinner type, Spinner brand, Spinner model, Spinner year, Spinner variant,
                                          Spinner body, Spinner fuel, Spinner transmission,
                                          EditText generation, EditText trim, EditText engine, EditText power,
                                          EditText drivetrain, TextView state) {
        String selectedBrand = String.valueOf(brand.getSelectedItem());
        String selectedModel = String.valueOf(model.getSelectedItem());
        int selectedYear = safeInt(String.valueOf(year.getSelectedItem()), Calendar.getInstance().get(Calendar.YEAR));
        String selectedVariant = String.valueOf(variant.getSelectedItem());
        TurkeyVehicleSpecs.Spec spec = TurkeyVehicleSpecs.find(selectedBrand, selectedModel, selectedYear, selectedVariant);
        boolean exact = spec != null;
        if (spec == null) spec = TurkeyVehicleSpecs.inferred(String.valueOf(type.getSelectedItem()), selectedBrand, selectedModel, selectedYear);

        if (spec != null) {
            if (!spec.body.isEmpty()) selectSpinnerValue(body, spec.body);
            if (!spec.fuel.isEmpty()) selectSpinnerValue(fuel, spec.fuel);
            if (!spec.transmission.isEmpty()) selectSpinnerValue(transmission, spec.transmission);
            if (exact || generation.getText().toString().trim().isEmpty()) generation.setText(spec.generation);
            if (exact || trim.getText().toString().trim().isEmpty()) trim.setText(spec.trim);
            if (exact || engine.getText().toString().trim().isEmpty()) engine.setText(spec.engine);
            if (exact || power.getText().toString().trim().isEmpty()) power.setText(spec.power);
            if (exact || drivetrain.getText().toString().trim().isEmpty()) drivetrain.setText(spec.drivetrain);
        }
        if (exact) {
            state.setText("✓ Teknik veriler katalogdan otomatik dolduruldu. İstersen alanları değiştirebilirsin.");
            state.setTextColor(success);
        } else {
            state.setText("Bu model/yıl için ayrıntılı varyant henüz katalogda yok. Temel alanlar dolduruldu; teknik bilgileri manuel tamamlayabilirsin.");
            state.setTextColor(warning);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void selectSpinnerValue(Spinner spinner, String value) {
        if (value == null || value.trim().isEmpty()) return;
        for (int i = 0; i < spinner.getCount(); i++) {
            if (value.equals(String.valueOf(spinner.getItemAtPosition(i)))) {
                spinner.setSelection(i);
                return;
            }
        }
        if (spinner.getAdapter() instanceof ArrayAdapter) {
            ArrayAdapter adapter = (ArrayAdapter) spinner.getAdapter();
            adapter.add(value);
            adapter.notifyDataSetChanged();
            spinner.setSelection(adapter.getCount() - 1);
        }
    }

    private View cvPhotoThumb(Uri uri) {
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(0,0,dp(8),0);
        ImageView image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setBackground(cardDrawable(surface2,dp(12),stroke));
        try { image.setImageURI(uri); } catch (Exception ignored) { }
        frame.addView(image,new FrameLayout.LayoutParams(dp(92),dp(78)));

        TextView remove = tv("×",Color.WHITE,17,true);
        remove.setGravity(Gravity.CENTER);
        remove.setBackground(cardDrawable(Color.argb(215,35,42,42),dp(14),Color.TRANSPARENT));
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(28),dp(28));
        rp.gravity = Gravity.TOP | Gravity.RIGHT;
        rp.setMargins(0,dp(4),dp(12),0);
        frame.addView(remove,rp);
        remove.setOnClickListener(v -> {
            cvPhotoUris.remove(uri);
            renderPage(2);
        });
        return frame;
    }

'''
src = src[:helper_pos] + helpers + src[helper_pos:]

# 4) Replace CV picker.
picker_start = src.index('    private void pickCvImages() {')
picker_end = src.index('    private void pickRecordAttachment(long id) {', picker_start)
new_picker = r'''    private void pickCvImages() {
        if (cvPhotoUris.size() >= VehicleCvPdf.MAX_EXTRA_PHOTOS) {
            toast("CV için en fazla 10 fotoğraf ekleyebilirsin");
            return;
        }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(Intent.createChooser(i, "CV için fotoğraf seç"), PICK_CV_IMAGES);
    }

'''
src = src[:picker_start] + new_picker + src[picker_end:]

# 5) Replace CV result handler only, leave other result branches intact.
old_result_start = src.index('        if (requestCode == PICK_CV_IMAGES) {')
old_result_end = src.index('        if (data.getData() == null) return;', old_result_start)
new_result = r'''        if (requestCode == PICK_CV_IMAGES) {
            int added = 0;
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount() && cvPhotoUris.size() < VehicleCvPdf.MAX_EXTRA_PHOTOS; i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    if (addCvPhotoUri(u)) added++;
                }
                if (clip.getItemCount() + cvPhotoUris.size() - added > VehicleCvPdf.MAX_EXTRA_PHOTOS)
                    toast("İlk 10 fotoğraf kullanıldı");
            } else if (data.getData() != null) {
                if (addCvPhotoUri(data.getData())) added++;
            }
            if (added > 0) toast(added + " fotoğraf CV'ye eklendi");
            else if (cvPhotoUris.isEmpty()) toast("Fotoğraf seçilemedi");
            renderPage(2);
            return;
        }

'''
src = src[:old_result_start] + new_result + src[old_result_end:]

# 6) Add helper for de-duping selected photo URIs before openAttachment.
insert_marker = '    private void openAttachment(String u) {'
insert_pos = src.index(insert_marker)
photo_helper = r'''    private boolean addCvPhotoUri(Uri uri) {
        if (uri == null || cvPhotoUris.size() >= VehicleCvPdf.MAX_EXTRA_PHOTOS) return false;
        String value = uri.toString();
        for (Uri existing : cvPhotoUris) if (existing != null && value.equals(existing.toString())) return false;
        cvPhotoUris.add(uri);
        return true;
    }

'''
src = src[:insert_pos] + photo_helper + src[insert_pos:]

activity_path.write_text(src, encoding='utf-8')

gradle = gradle_path.read_text(encoding='utf-8')
gradle = re.sub(r'versionCode\s+\d+', 'versionCode 5', gradle)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '0.5.0-fixes-dev'", gradle)
gradle_path.write_text(gradle, encoding='utf-8')

print('Applied v0.5 auto-spec, CV photo picker and UI fixes')
