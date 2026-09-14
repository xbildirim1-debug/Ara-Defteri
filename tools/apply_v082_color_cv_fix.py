from pathlib import Path

# CV oluşturma ekranına araç rengini görünür ve düzenlenebilir ekle.
p = Path('app/src/main/java/com/aracdefteri/app/NextMainActivity.java')
s = p.read_text(encoding='utf-8')
old = '''        EditText phone = formField(options, "Telefon (isteğe bağlı)", prefs.getString("cv_phone", ""), InputType.TYPE_CLASS_PHONE);
        EditText note = formMultiline(options, "Açıklama (isteğe bağlı)", prefs.getString("cv_note", ""));
'''
new = '''        EditText color = formField(options, "Araç rengi", prefs.getString("vehicle_color", ""), InputType.TYPE_CLASS_TEXT);
        color.setHint("Örn: Beyaz, İnci Beyazı, Metalik Gri");
        EditText phone = formField(options, "Telefon (isteğe bağlı)", prefs.getString("cv_phone", ""), InputType.TYPE_CLASS_PHONE);
        EditText note = formMultiline(options, "Açıklama (isteğe bağlı)", prefs.getString("cv_note", ""));
'''
if old not in s:
    raise SystemExit('CV phone/note bloğu bulunamadı')
s = s.replace(old, new, 1)
old = '''                    .putString("cv_sale_price", salePrice.getText().toString().trim())
                    .putString("cv_phone", phone.getText().toString().trim())
                    .putString("cv_note", note.getText().toString().trim())
'''
new = '''                    .putString("cv_sale_price", salePrice.getText().toString().trim())
                    .putString("vehicle_color", color.getText().toString().trim())
                    .putString("cv_phone", phone.getText().toString().trim())
                    .putString("cv_note", note.getText().toString().trim())
'''
if old not in s:
    raise SystemExit('CV prefs bloğu bulunamadı')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# PDF kapağında rengi ve teknik alanları kompakt biçimde göster.
p = Path('app/src/main/java/com/aracdefteri/app/VehicleCvPdf.java')
s = p.read_text(encoding='utf-8')
old = '''        String drivetrain = resolvedValue(prefs, "vehicle_drivetrain", spec == null ? "" : spec.drivetrain);
        String fuel = v.fuelType == null ? "" : v.fuelType.trim();
'''
new = '''        String drivetrain = resolvedValue(prefs, "vehicle_drivetrain", spec == null ? "" : spec.drivetrain);
        String color = pref(prefs, "vehicle_color");
        String fuel = v.fuelType == null ? "" : v.fuelType.trim();
'''
if old not in s:
    raise SystemExit('PDF color insertion point bulunamadı')
s = s.replace(old, new, 1)
old = '''        w.coverChip(38, y + 4, 250, "GÜNCEL KM", formatInt(v.km) + " km");
        w.coverChip(307, y + 4, 250, "YAKIT", blankFallback(fuel, "Belirtilmedi"));
        w.coverChip(38, y + 50, 250, "MOTOR / GÜÇ", joinNonEmpty(" • ", engine, power));
        w.coverChip(307, y + 50, 250, "ŞANZIMAN", trans);
        w.coverChip(38, y + 96, 250, "KASA / ÇEKİŞ", joinNonEmpty(" • ", body, drivetrain));
        w.coverChip(307, y + 96, 250, "PAKET / NESİL", joinNonEmpty(" • ", trim, generation));
        y += 143;
'''
new = '''        w.coverChip(38, y + 4, 250, "GÜNCEL KM", formatInt(v.km) + " km");
        w.coverChip(307, y + 4, 250, "YAKIT", blankFallback(fuel, "Belirtilmedi"));
        w.coverChip(38, y + 50, 250, "MOTOR / GÜÇ", joinNonEmpty(" • ", engine, power));
        w.coverChip(307, y + 50, 250, "ŞANZIMAN", blankFallback(trans, "Belirtilmedi"));
        w.coverChip(38, y + 96, 250, "KASA / RENK", joinNonEmpty(" • ", body, color));
        w.coverChip(307, y + 96, 250, "ÇEKİŞ", blankFallback(drivetrain, "Belirtilmedi"));
        w.coverChip(38, y + 142, 250, "PAKET / VERSİYON", blankFallback(trim, "Belirtilmedi"));
        w.coverChip(307, y + 142, 250, "NESİL / SERİ", blankFallback(generation, "Belirtilmedi"));
        y += 189;
'''
if old not in s:
    raise SystemExit('PDF teknik chip bloğu bulunamadı')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Sürüm yükselt.
p = Path('app/build.gradle')
s = p.read_text(encoding='utf-8')
s = s.replace('versionCode 9', 'versionCode 10')
s = s.replace("versionName '0.8.1-pdf-spec-fix-dev'", "versionName '0.8.2-color-cv-fix-dev'")
p.write_text(s, encoding='utf-8')
