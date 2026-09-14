#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY'
from pathlib import Path
import re

# Catalog: support the actual demo/legacy model name Corolla Hybrid.
p = Path('app/src/main/java/com/aracdefteri/app/TurkeyVehicleCatalog.java')
s = p.read_text()
s = s.replace(
    'add(TYPE_CAR, "Toyota", "Corolla", "Corolla Sedan", "Auris", "Yaris", "Avensis", "Camry", "Prius", "Aygo", "Supra", "Celica");',
    'add(TYPE_CAR, "Toyota", "Corolla", "Corolla Hybrid", "Corolla Sedan", "Auris", "Yaris", "Avensis", "Camry", "Prius", "Aygo", "Supra", "Celica");'
)
p.write_text(s)

# Specs: add 2019-2025 Corolla Hybrid variants and safe inference.
p = Path('app/src/main/java/com/aracdefteri/app/TurkeyVehicleSpecs.java')
s = p.read_text()
marker = '        // TOYOTA COROLLA 2026 Türkiye.\n'
if '"Corolla Hybrid", 2019, 2022' not in s:
    insert = '''        // TOYOTA COROLLA E210 - Türkiye hibrit çekirdek varyantları.\n        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT", "E210", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");\n        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT • Dream", "E210", "Dream", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");\n        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT • Flame", "E210", "Flame", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");\n        add("Toyota", "Corolla Hybrid", 2019, 2022, "1.8 Hybrid 122 HP e-CVT • Passion", "E210", "Passion", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");\n        add("Toyota", "Corolla Hybrid", 2023, 2025, "1.8 Hybrid 140 HP e-CVT", "E210 makyajlı", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");\n        add("Toyota", "Corolla", 2019, 2022, "1.8 Hybrid 122 HP e-CVT", "E210", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "122 hp", "4X2");\n        add("Toyota", "Corolla", 2023, 2025, "1.8 Hybrid 140 HP e-CVT", "E210 makyajlı", "", "Sedan", "Hibrit / Benzin", "CVT / e-CVT", "1.8 L Hybrid", "140 hp", "4X2");\n\n'''
    assert marker in s
    s = s.replace(marker, insert + marker, 1)
if 'm.contains("corolla") && m.contains("hybrid")' not in s:
    infer_marker = '        if (m.contains("id.") || m.contains("model y")'
    block = '''        if (m.contains("corolla") && m.contains("hybrid")) {\n            body = "Sedan";\n            fuel = "Hibrit / Benzin";\n            trans = "CVT / e-CVT";\n            engine = "1.8 L Hybrid";\n            power = year <= 2022 ? "122 hp" : "140 hp";\n            drive = "4X2";\n        }\n'''
    assert infer_marker in s
    s = s.replace(infer_marker, block + infer_marker, 1)
p.write_text(s)

# PDF: compact cover, automatic spec fallback, no giant blank page.
p = Path('app/src/main/java/com/aracdefteri/app/VehicleCvPdf.java')
s = p.read_text()

# Remove separate identity page call. Cover + history now flow continuously.
s = s.replace('            drawVehicleIdentity(w, vehicle, prefs, phone, note, showPlate, db, showCosts);\n            drawHistory(w, db, showCosts);',
              '            drawHistory(w, db, showCosts);')

start = s.index('    private static void drawCover(')
end = s.index('    private static void drawVehicleIdentity(', start)
new_cover = r'''    private static void drawCover(Activity activity, Writer w, AppDatabase.Vehicle v,
                                  SharedPreferences prefs, String documentNo, boolean showPlate) {
        w.newPage(false);
        w.paint.setColor(Writer.ACCENT_DARK);
        w.canvas.drawRect(0, 0, Writer.PAGE_W, 82, w.paint);

        Bitmap icon = w.appIconBitmap(18);
        if (icon != null) {
            w.canvas.drawBitmap(icon, 38, 17, w.paint);
            icon.recycle();
        }
        w.text("ARAÇ DEFTERİ", 63, 31, 8.4f, Color.WHITE, true);
        w.text(fullVehicleName(v, prefs), 38, 61, 13.2f, Color.WHITE, true);

        TurkeyVehicleSpecs.Spec spec = resolvedSpec(v, prefs);
        String body = resolvedValue(prefs, "vehicle_body", spec == null ? "" : spec.body);
        String trans = resolvedValue(prefs, "vehicle_transmission", spec == null ? "" : spec.transmission);
        String engine = resolvedValue(prefs, "vehicle_engine", spec == null ? "" : spec.engine);
        String power = resolvedValue(prefs, "vehicle_power", spec == null ? "" : spec.power);
        String trim = resolvedValue(prefs, "vehicle_trim", spec == null ? "" : spec.trim);
        String generation = resolvedValue(prefs, "vehicle_generation", spec == null ? "" : spec.generation);
        String drivetrain = resolvedValue(prefs, "vehicle_drivetrain", spec == null ? "" : spec.drivetrain);
        String fuel = v.fuelType == null ? "" : v.fuelType.trim();
        if (fuel.isEmpty() && spec != null) fuel = spec.fuel;

        int y = 94;
        String mainPhoto = pref(prefs, "vehicle_photo_uri");
        Bitmap photo = mainPhoto.isEmpty() ? null : loadBitmap(activity, Uri.parse(mainPhoto), 1800);
        if (photo != null) {
            w.drawImageCover(photo, 38, y, 519, 190);
            photo.recycle();
            y += 204;
        } else {
            w.paint.setColor(0xFFF0F5F3);
            w.canvas.drawRoundRect(new RectF(38, y, 557, y + 90), 10, 10, w.paint);
            w.text("ARAÇ FOTOĞRAFI EKLENMEMİŞ", 58, y + 39, 9, Writer.DARK, true);
            w.text("Ana araç fotoğrafı burada görünür.", 58, y + 57, 7, Writer.MUTED, false);
            y += 104;
        }

        String phone = pref(prefs, "cv_phone");
        if (!phone.isEmpty()) {
            w.text("İletişim: " + phone, 38, y, 7.8f, Writer.ACCENT_DARK, true);
            y += 14;
        }

        w.coverChip(38, y + 4, 250, "GÜNCEL KM", formatInt(v.km) + " km");
        w.coverChip(307, y + 4, 250, "YAKIT", blankFallback(fuel, "Belirtilmedi"));
        w.coverChip(38, y + 50, 250, "MOTOR / GÜÇ", joinNonEmpty(" • ", engine, power));
        w.coverChip(307, y + 50, 250, "ŞANZIMAN", trans);
        w.coverChip(38, y + 96, 250, "KASA / ÇEKİŞ", joinNonEmpty(" • ", body, drivetrain));
        w.coverChip(307, y + 96, 250, "PAKET / NESİL", joinNonEmpty(" • ", trim, generation));
        y += 143;

        boolean showPrice = prefs.getBoolean("cv_show_price", false);
        String price = pref(prefs, "cv_sale_price");
        if (showPrice && !price.isEmpty()) {
            w.coverChip(38, y, 519, "İSTENEN SATIŞ FİYATI", price + " ₺");
            y += 47;
        }

        String note = pref(prefs, "cv_note");
        if (!note.isEmpty()) {
            w.text("AÇIKLAMA", 38, y + 10, 7.2f, Writer.ACCENT_DARK, true);
            y = w.drawWrapped(note, 38, y + 25, 7.6f, Writer.DARK, false, Writer.CONTENT_W, 10) + 4;
        }

        w.text("Bu belge kullanıcı tarafından girilen kayıtlardan oluşturulmuştur; resmî doğrulama belgesi değildir.", 38, y + 8, 6.3f, Writer.MUTED, false);
        w.y = y + 24;
    }

'''
s = s[:start] + new_cover + s[end:]

# Keep identity helper compiled but use resolved values if later reused.
identity_start = s.index('    private static void drawVehicleIdentity(')
identity_end = s.index('    private static void drawHistory(', identity_start)
identity_block = s[identity_start:identity_end]
identity_block = identity_block.replace('        w.newPage(true);\n        w.section("Araç bilgileri", "Teknik ve kayıtlı araç bilgileri");\n',
'''        w.newPage(true);\n        w.section("Araç bilgileri", "Teknik ve kayıtlı araç bilgileri");\n        TurkeyVehicleSpecs.Spec spec = resolvedSpec(v, prefs);\n''')
identity_block = identity_block.replace('w.keyValue("Motor / Paket / Versiyon", pref(prefs, "vehicle_catalog_variant"));', 'w.keyValue("Motor / Paket / Versiyon", resolvedValue(prefs, "vehicle_catalog_variant", spec == null ? "" : spec.variant));')
identity_block = identity_block.replace('w.keyValue("Nesil / Seri", pref(prefs, "vehicle_generation"));', 'w.keyValue("Nesil / Seri", resolvedValue(prefs, "vehicle_generation", spec == null ? "" : spec.generation));')
identity_block = identity_block.replace('w.keyValue("Paket / Versiyon", pref(prefs, "vehicle_trim"));', 'w.keyValue("Paket / Versiyon", resolvedValue(prefs, "vehicle_trim", spec == null ? "" : spec.trim));')
identity_block = identity_block.replace('w.keyValue("Kasa tipi", pref(prefs, "vehicle_body"));', 'w.keyValue("Kasa tipi", resolvedValue(prefs, "vehicle_body", spec == null ? "" : spec.body));')
identity_block = identity_block.replace('w.keyValue("Yakıt", v.fuelType);', 'w.keyValue("Yakıt", blankFallback(v.fuelType, spec == null ? "" : spec.fuel));')
identity_block = identity_block.replace('w.keyValue("Şanzıman", pref(prefs, "vehicle_transmission"));', 'w.keyValue("Şanzıman", resolvedValue(prefs, "vehicle_transmission", spec == null ? "" : spec.transmission));')
identity_block = identity_block.replace('w.keyValue("Motor", pref(prefs, "vehicle_engine"));', 'w.keyValue("Motor", resolvedValue(prefs, "vehicle_engine", spec == null ? "" : spec.engine));')
identity_block = identity_block.replace('w.keyValue("Motor gücü", pref(prefs, "vehicle_power"));', 'w.keyValue("Motor gücü", resolvedValue(prefs, "vehicle_power", spec == null ? "" : spec.power));')
identity_block = identity_block.replace('w.keyValue("Çekiş", pref(prefs, "vehicle_drivetrain"));', 'w.keyValue("Çekiş", resolvedValue(prefs, "vehicle_drivetrain", spec == null ? "" : spec.drivetrain));')
s = s[:identity_start] + identity_block + s[identity_end:]

old_full = '''    private static String fullVehicleName(AppDatabase.Vehicle v, SharedPreferences prefs) {
        String variant = pref(prefs, "vehicle_catalog_variant");
        if (variant.isEmpty()) {
            variant = joinNonEmpty(" ", pref(prefs, "vehicle_engine"), pref(prefs, "vehicle_trim"), pref(prefs, "vehicle_transmission"));
        }
        return joinNonEmpty(" ", String.valueOf(v.year), v.brand, v.model, variant);
    }
'''
new_full = '''    private static TurkeyVehicleSpecs.Spec resolvedSpec(AppDatabase.Vehicle v, SharedPreferences prefs) {
        String variant = pref(prefs, "vehicle_catalog_variant");
        TurkeyVehicleSpecs.Spec exact = TurkeyVehicleSpecs.find(v.brand, v.model, v.year, variant);
        if (exact != null) return exact;
        List<TurkeyVehicleSpecs.Spec> specs = TurkeyVehicleSpecs.specsFor(v.brand, v.model, v.year);
        if (!specs.isEmpty()) {
            String fuelHint = v.fuelType == null ? "" : v.fuelType.toLowerCase(new Locale("tr", "TR"));
            for (TurkeyVehicleSpecs.Spec candidate : specs) {
                String cf = candidate.fuel == null ? "" : candidate.fuel.toLowerCase(new Locale("tr", "TR"));
                if (fuelHint.contains("hibrit") && cf.contains("hibrit")) return candidate;
                if (fuelHint.contains("dizel") && cf.contains("dizel")) return candidate;
                if (fuelHint.equals("benzin") && cf.equals("benzin")) return candidate;
                if (fuelHint.contains("elektrik") && cf.contains("elektrik")) return candidate;
            }
            return specs.get(0);
        }
        return TurkeyVehicleSpecs.inferred(prefOr(prefs, "vehicle_type", "Otomobil"), v.brand, v.model, v.year);
    }

    private static String resolvedValue(SharedPreferences prefs, String key, String fallback) {
        String value = pref(prefs, key);
        if (!value.isEmpty() && !"Belirtilmedi".equalsIgnoreCase(value)) return value;
        return fallback == null || fallback.trim().isEmpty() ? "Belirtilmedi" : fallback.trim();
    }

    private static String fullVehicleName(AppDatabase.Vehicle v, SharedPreferences prefs) {
        TurkeyVehicleSpecs.Spec spec = resolvedSpec(v, prefs);
        String variant = pref(prefs, "vehicle_catalog_variant");
        if (variant.startsWith("Teknik bilgiyi")) variant = "";
        if (variant.isEmpty() && spec != null && !spec.variant.isEmpty() && !"Otomatik temel bilgi".equals(spec.variant)) variant = spec.variant;
        return joinNonEmpty(" ", String.valueOf(v.year), v.brand, v.model, variant);
    }
'''
assert old_full in s
s = s.replace(old_full, new_full, 1)

# Make chips slightly shorter to pack the page tighter.
s = s.replace('canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 42), 9, 9, paint);', 'canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 38), 9, 9, paint);')
s = s.replace('text(label, x + 10, yy + 14, 6f, MUTED, true);', 'text(label, x + 10, yy + 13, 5.8f, MUTED, true);')
s = s.replace('text(v, x + 10, yy + 31, 8.8f, DARK, true);', 'text(v, x + 10, yy + 28, 8.2f, DARK, true);')
p.write_text(s)

# Version bump.
p = Path('app/build.gradle')
s = p.read_text()
s = re.sub(r'versionCode\s+\d+', 'versionCode 9', s)
s = re.sub(r"versionName\s+'[^']+'", "versionName '0.8.1-pdf-spec-fix-dev'", s)
p.write_text(s)
PY
