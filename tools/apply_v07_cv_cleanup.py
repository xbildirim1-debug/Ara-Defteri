from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
activity_path = root / 'app/src/main/java/com/aracdefteri/app/NextMainActivity.java'
pdf_path = root / 'app/src/main/java/com/aracdefteri/app/VehicleCvPdf.java'
gradle_path = root / 'app/build.gradle'

activity = activity_path.read_text(encoding='utf-8')
pdf = pdf_path.read_text(encoding='utf-8')
gradle = gradle_path.read_text(encoding='utf-8')

# ---- CV screen: simplify summary, use full catalog variant, rename image section ----
start = activity.index('private void renderCv(LinearLayout content) {')
marker = '        LinearLayout photoCard = card();'
photo_card_pos = activity.index(marker, start)
head = activity[start:photo_card_pos]
new_head = r'''private void renderCv(LinearLayout content) {
        AppDatabase.Vehicle v = db.getVehicle();
        String cvVariant = prefs.getString("vehicle_catalog_variant", "").trim();
        if (cvVariant.isEmpty()) {
            cvVariant = (prefs.getString("vehicle_engine", "") + " " + prefs.getString("vehicle_trim", "")).trim();
        }
        String fullCvName = (v.year + " " + v.brand + " " + v.model + (cvVariant.isEmpty() ? "" : " " + cvVariant)).replaceAll("\\s+", " ").trim();

        TextView title = tv("Araç CV", text, 22, true);
        content.addView(title);
        TextView subtitle = tv(fullCvName, muted, 10, false);
        subtitle.setPadding(0,dp(2),0,dp(9));
        content.addView(subtitle);

        LinearLayout summary = card();
        summary.setPadding(dp(13),dp(11),dp(13),dp(11));
        summary.addView(tv(fullCvName, text, 16, true));
        String meta = (prefs.getString("vehicle_body", "") + "  •  " + v.fuelType + "  •  " + prefs.getString("vehicle_transmission", "")).trim();
        meta = meta.replaceFirst("^\\s*•\\s*", "").replaceFirst("\\s*•\\s*$", "");
        if (!meta.trim().isEmpty()) summary.addView(tv(meta, muted, 9, false));
        content.addView(summary);
        gap(content,10);

        sectionTitle(content, "Araç resimleri", "Ana araç fotoğrafına ek olarak en fazla 10 fotoğraf");
'''
activity = activity[:start] + new_head + activity[photo_card_pos:]

activity = activity.replace('cvPhotoCountView = tv(cvPhotoUris.size() + " / " + VehicleCvPdf.MAX_EXTRA_PHOTOS + " fotoğraf seçildi", text, 12, true);',
                            'cvPhotoCountView = tv(cvPhotoUris.size() + " / " + VehicleCvPdf.MAX_EXTRA_PHOTOS + " araç resmi seçildi", text, 12, true);')
activity = activity.replace('photoCard.addView(tv("Bu fotoğraflar yalnız oluşturulan PDF içinde kullanılır.", muted, 9, false));',
                            'photoCard.addView(tv("Seçilen araç resimleri yalnız oluşturulan PDF içinde kullanılır.", muted, 9, false));')
activity = activity.replace('toast("CV için en fazla 10 fotoğraf ekleyebilirsin");', 'toast("En fazla 10 araç resmi ekleyebilirsin");')
activity = activity.replace('if (added > 0) toast(added + " fotoğraf CV\'ye eklendi");', 'if (added > 0) toast(added + " araç resmi eklendi");')

# ---- PDF: full vehicle name helper ----
pdf = pdf.replace('w.headerTitle = vehicle.year + " " + vehicle.brand + " " + vehicle.model;',
                  'w.headerTitle = fullVehicleName(vehicle, prefs);')

# remove unused about call; description is user note only and is drawn with identity
pdf = pdf.replace('            drawAbout(w);\n', '')

# compact cover and use full model name
cover_start = pdf.index('    private static void drawCover(')
identity_start = pdf.index('    private static void drawVehicleIdentity(', cover_start)
new_cover = r'''    private static void drawCover(Activity activity, Writer w, AppDatabase.Vehicle v,
                                  SharedPreferences prefs, String documentNo, boolean showPlate) {
        w.newPage(false);
        w.paint.setColor(Writer.ACCENT_DARK);
        w.canvas.drawRect(0, 0, Writer.PAGE_W, 96, w.paint);

        Bitmap icon = w.appIconBitmap(22);
        if (icon != null) {
            w.canvas.drawBitmap(icon, 38, 20, w.paint);
            icon.recycle();
        }
        w.text("ARAÇ DEFTERİ", 68, 35, 9.5f, Color.WHITE, true);
        w.text("Aracının dijital hafızası", 68, 49, 7, 0xFFD8F5EB, false);
        w.text(fullVehicleName(v, prefs), 38, 77, 15.5f, Color.WHITE, true);

        int y = 108;
        String mainPhoto = pref(prefs, "vehicle_photo_uri");
        Bitmap photo = mainPhoto.isEmpty() ? null : loadBitmap(activity, Uri.parse(mainPhoto), 1800);
        if (photo != null) {
            w.drawImageCover(photo, 38, y, 519, 236);
            photo.recycle();
            y += 252;
        } else {
            w.paint.setColor(0xFFF0F5F3);
            w.canvas.drawRoundRect(new RectF(38, y, 557, y + 118), 12, 12, w.paint);
            w.text("ARAÇ FOTOĞRAFI EKLENMEMİŞ", 58, y + 52, 10, Writer.DARK, true);
            w.text("Ana araç fotoğrafı burada görünür.", 58, y + 72, 7.5f, Writer.MUTED, false);
            y += 134;
        }

        String phone = pref(prefs, "cv_phone");
        if (!phone.isEmpty()) {
            w.text("İletişim: " + phone, 38, y, 8.5f, Writer.ACCENT_DARK, true);
            y += 17;
        }

        String body = pref(prefs, "vehicle_body");
        String trans = pref(prefs, "vehicle_transmission");
        boolean showPrice = prefs.getBoolean("cv_show_price", false);
        String price = pref(prefs, "cv_sale_price");
        w.coverChip(38, y + 5, 250, "GÜNCEL KM", formatInt(v.km) + " km");
        w.coverChip(307, y + 5, 250, "YAKIT", blankFallback(v.fuelType, "Belirtilmedi"));
        w.coverChip(38, y + 55, 250, "KASA / VİTES", joinNonEmpty(" • ", body, trans));
        if (showPrice && !price.isEmpty())
            w.coverChip(307, y + 55, 250, "İSTENEN SATIŞ FİYATI", price + " ₺");
        else
            w.coverChip(307, y + 55, 250, "MOTOR / GÜÇ", joinNonEmpty(" • ", pref(prefs, "vehicle_engine"), pref(prefs, "vehicle_power")));

        String note = pref(prefs, "cv_note");
        if (!note.isEmpty()) {
            int noteY = y + 118;
            w.text("AÇIKLAMA", 38, noteY, 7, Writer.ACCENT_DARK, true);
            w.drawWrapped(note, 38, noteY + 15, 8, Writer.DARK, false, Writer.CONTENT_W, 11);
        }

        w.text("Bu belge Araç Defteri uygulaması ile kullanıcı kayıtlarından oluşturulmuştur; resmî doğrulama belgesi değildir.", 38, 786, 6.8f, Writer.MUTED, false);
    }

'''
pdf = pdf[:cover_start] + new_cover + pdf[identity_start:]

# replace identity method: no record summary, include user description only if it did not fit/for clarity
identity_start = pdf.index('    private static void drawVehicleIdentity(')
history_start = pdf.index('    private static void drawHistory(', identity_start)
new_identity = r'''    private static void drawVehicleIdentity(Writer w, AppDatabase.Vehicle v, SharedPreferences prefs,
                                            String phone, String note, boolean showPlate,
                                            AppDatabase db, boolean showCosts) {
        w.newPage(true);
        w.section("Araç bilgileri", "Teknik ve kayıtlı araç bilgileri");

        w.keyValue("Araç türü", prefOr(prefs, "vehicle_type", "Otomobil"));
        w.keyValue("Marka / Model", v.brand + " / " + v.model);
        w.keyValue("Model yılı", String.valueOf(v.year));
        w.keyValue("Motor / Paket / Versiyon", pref(prefs, "vehicle_catalog_variant"));
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

        if (note != null && !note.trim().isEmpty()) {
            w.ensure(72);
            w.y += 4;
            w.section("Açıklama", null);
            w.paragraph(note.trim(), 8.2f, Writer.DARK);
            w.y += 3;
        }
    }

'''
pdf = pdf[:identity_start] + new_identity + pdf[history_start:]

# history: remove expertise and expenses, tighten spaces
history_start = pdf.index('    private static void drawHistory(')
photos_start = pdf.index('    private static void drawPhotos(', history_start)
new_history = r'''    private static void drawHistory(Writer w, AppDatabase db, boolean showCosts) {
        List<AppDatabase.Record> all = db.getRecords(500);
        String[] types = {"Bakım", "Hasar", "Muayene", "Sigorta/Kasko", "Vergi", "Yakıt"};
        String[] titles = {"Bakım & Onarım", "Hasar Geçmişi", "Muayene", "Sigorta & Kasko", "Vergi / Resmî Ödemeler", "Yakıt / Enerji"};
        for (int i = 0; i < types.length; i++) {
            List<AppDatabase.Record> group = recordsOfType(all, types[i]);
            if (group.isEmpty()) continue;
            w.ensure(70);
            w.y += 3;
            w.section(titles[i], null);
            for (AppDatabase.Record r : group) w.recordCard(r, showCosts);
        }
    }

'''
pdf = pdf[:history_start] + new_history + pdf[photos_start:]

# photos: 3 per page, smaller images, correct title
photos_start = pdf.index('    private static void drawPhotos(')
about_start = pdf.index('    private static void drawAbout(', photos_start)
new_photos = r'''    private static void drawPhotos(Activity activity, Writer w, List<Uri> selectedPhotos) {
        ArrayList<Uri> photos = new ArrayList<>();
        if (selectedPhotos != null) {
            for (Uri uri : selectedPhotos) {
                if (uri == null) continue;
                photos.add(uri);
                if (photos.size() >= MAX_EXTRA_PHOTOS) break;
            }
        }
        if (photos.isEmpty()) return;

        int index = 0;
        while (index < photos.size()) {
            w.newPage(true);
            w.section("Araç Resimleri", "CV için seçilen araç fotoğrafları");
            for (int slot = 0; slot < 3 && index < photos.size(); slot++, index++) {
                Bitmap bitmap = loadBitmap(activity, photos.get(index), 1600);
                if (bitmap == null) {
                    w.simpleCard("Araç resmi " + (index + 1), "Fotoğraf okunamadı", "");
                    continue;
                }
                w.ensure(215);
                w.label("ARAÇ RESMİ " + (index + 1) + " / " + photos.size());
                w.drawImageFit(bitmap, Writer.LEFT, w.y + 5, Writer.CONTENT_W, 184);
                w.y += 202;
                bitmap.recycle();
            }
        }
    }

'''
pdf = pdf[:photos_start] + new_photos + pdf[about_start:]

# remove default About/Description boilerplate entirely; user description is handled above.
about_start = pdf.index('    private static void drawAbout(')
write_start = pdf.index('    private static Uri writeDocument(', about_start)
pdf = pdf[:about_start] + pdf[write_start:]

# add fullVehicleName helper before formatMoney
helper_marker = '    private static String formatMoney(double value) {'
helper = r'''    private static String fullVehicleName(AppDatabase.Vehicle v, SharedPreferences prefs) {
        String variant = pref(prefs, "vehicle_catalog_variant");
        if (variant.isEmpty()) {
            variant = joinNonEmpty(" ", pref(prefs, "vehicle_engine"), pref(prefs, "vehicle_trim"), pref(prefs, "vehicle_transmission"));
        }
        return joinNonEmpty(" ", String.valueOf(v.year), v.brand, v.model, variant);
    }

'''
pdf = pdf.replace(helper_marker, helper + helper_marker, 1)

# compact Writer spacing and cards
pdf = pdf.replace('            y = 38;\n            if (header) drawHeader();', '            y = 32;\n            if (header) drawHeader();')
pdf = pdf.replace('Bitmap icon = appIconBitmap(19);', 'Bitmap icon = appIconBitmap(16);')
pdf = pdf.replace('canvas.drawBitmap(icon, LEFT, 27, paint);', 'canvas.drawBitmap(icon, LEFT, 22, paint);')
pdf = pdf.replace('text("ARAÇ DEFTERİ", LEFT + 27, 41, 8.5f, ACCENT, true);', 'text("ARAÇ DEFTERİ", LEFT + 23, 34, 7.8f, ACCENT, true);')
pdf = pdf.replace('if (h.length() > 34) h = h.substring(0, 33) + "…";', 'if (h.length() > 48) h = h.substring(0, 47) + "…";')
pdf = pdf.replace('text(h, RIGHT - 190, 41, 7.5f, DARK, true);', 'text(h, LEFT + 168, 34, 7.2f, DARK, true);')
pdf = pdf.replace('canvas.drawRect(LEFT, 56, RIGHT, 57, paint);\n            y = 76;', 'canvas.drawRect(LEFT, 48, RIGHT, 49, paint);\n            y = 62;')

old_section = '''        void section(String title, String subtitle) {\n            ensure(47);\n            text(title, LEFT, y, 14, ACCENT, true);\n            y += 15;\n            if (subtitle != null && !subtitle.isEmpty()) {\n                text(subtitle, LEFT, y, 7.5f, MUTED, false);\n                y += 12;\n            } else y += 4;\n            paint.setColor(0xFFE6ECEA);\n            canvas.drawRect(LEFT, y, RIGHT, y + 1, paint);\n            y += 11;\n        }'''
new_section = '''        void section(String title, String subtitle) {\n            ensure(38);\n            text(title, LEFT, y, 12.5f, ACCENT, true);\n            y += 13;\n            if (subtitle != null && !subtitle.isEmpty()) {\n                text(subtitle, LEFT, y, 7f, MUTED, false);\n                y += 10;\n            } else y += 2;\n            paint.setColor(0xFFE6ECEA);\n            canvas.drawRect(LEFT, y, RIGHT, y + 1, paint);\n            y += 7;\n        }'''
pdf = pdf.replace(old_section, new_section)

pdf = pdf.replace('            text(value, LEFT, y, 7.5f, MUTED, true);\n            y += 13;', '            text(value, LEFT, y, 7f, MUTED, true);\n            y += 11;')
pdf = pdf.replace('int h = Math.max(24, measuredWrappedHeight(value, 8.8f, 325) + 8);', 'int h = Math.max(20, measuredWrappedHeight(value, 8.2f, 325) + 6);')
pdf = pdf.replace('text(key.toUpperCase(new Locale("tr", "TR")), LEFT + 7, y + 4, 6.8f, MUTED, true);', 'text(key.toUpperCase(new Locale("tr", "TR")), LEFT + 7, y + 3, 6.3f, MUTED, true);')
pdf = pdf.replace('drawWrapped(value, LEFT + 155, y + 4, 8.8f, DARK, true, 350, 11);', 'drawWrapped(value, LEFT + 155, y + 3, 8.2f, DARK, true, 350, 10);')

pdf = pdf.replace('int detailH = detail.isEmpty() ? 0 : measuredWrappedHeight(detail, 7.8f, CONTENT_W - 26) + 5;', 'int detailH = detail.isEmpty() ? 0 : measuredWrappedHeight(detail, 7.3f, CONTENT_W - 24) + 3;')
pdf = pdf.replace('int h = 50 + detailH + (next.isEmpty() ? 0 : 14);', 'int h = 43 + detailH + (next.isEmpty() ? 0 : 11);')
pdf = pdf.replace('ensure(h + 7);', 'ensure(h + 5);')
pdf = pdf.replace('new RectF(LEFT, y, RIGHT, y + h), 9, 9', 'new RectF(LEFT, y, RIGHT, y + h), 8, 8')
pdf = pdf.replace('text(r.title, LEFT + 13, y + 18, 9.5f, DARK, true);', 'text(r.title, LEFT + 12, y + 15, 9f, DARK, true);')
pdf = pdf.replace('text(meta, LEFT + 13, y + 33, 7, MUTED, false);', 'text(meta, LEFT + 12, y + 28, 6.6f, MUTED, false);')
pdf = pdf.replace('int yy = y + 46;', 'int yy = y + 38;')
pdf = pdf.replace('drawWrapped(detail, LEFT + 13, yy, 7.8f, DARK, false, CONTENT_W - 26, 10)', 'drawWrapped(detail, LEFT + 12, yy, 7.3f, DARK, false, CONTENT_W - 24, 9)')
pdf = pdf.replace('text(next, LEFT + 13, yy + 8, 7.2f, ACCENT_DARK, true);', 'text(next, LEFT + 12, yy + 7, 6.8f, ACCENT_DARK, true);')
pdf = pdf.replace('y += h + 7;', 'y += h + 5;')

pdf = pdf.replace('canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 46), 10, 10, paint);', 'canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 42), 9, 9, paint);')
pdf = pdf.replace('text(label, x + 11, yy + 15, 6.2f, MUTED, true);', 'text(label, x + 10, yy + 14, 6f, MUTED, true);')
pdf = pdf.replace('text(v, x + 11, yy + 34, 9.2f, DARK, true);', 'text(v, x + 10, yy + 31, 8.8f, DARK, true);')
pdf = pdf.replace('if (y + required > 790) newPage(true);', 'if (y + required > 798) newPage(true);')

activity_path.write_text(activity, encoding='utf-8')
pdf_path.write_text(pdf, encoding='utf-8')

gradle = re.sub(r'versionCode\s+\d+', 'versionCode 7', gradle)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '0.7.0-cv-cleanup-dev'", gradle)
gradle_path.write_text(gradle, encoding='utf-8')

print('Applied v0.7 compact CV cleanup')
