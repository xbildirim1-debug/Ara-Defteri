package com.aracdefteri.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/** Modern, markalı ve güvenli Araç Defteri CV PDF üreticisi. */
public final class VehicleCvPdf {
    private VehicleCvPdf() {}

    public static final int MAX_EXTRA_PHOTOS = 10;

    public static final class Result {
        public final Uri uri;
        public final String documentNo;
        Result(Uri uri, String documentNo) {
            this.uri = uri;
            this.documentNo = documentNo;
        }
    }

    public static Result create(Activity activity,
                                AppDatabase db,
                                SharedPreferences prefs,
                                List<Uri> selectedPhotos) throws Exception {
        AppDatabase.Vehicle vehicle = db.getVehicle();
        String documentNo = nextDocumentNo(prefs);
        boolean showCosts = prefs.getBoolean("cv_show_costs", false);
        boolean showPlate = false;
        String phone = pref(prefs, "cv_phone");
        String note = pref(prefs, "cv_note");

        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        Writer w = new Writer(activity, document, documentNo);
        w.headerTitle = fullVehicleName(vehicle, prefs);
        try {
            drawCover(activity, w, vehicle, prefs, documentNo, showPlate);
            drawHistory(w, db, showCosts);
            drawPhotos(activity, w, selectedPhotos);
            w.finish();

            String fileName = "Arac-Defteri-CV-" + documentNo.replace("-", "") + ".pdf";
            Uri uri = writeDocument(activity, document, fileName);
            prefs.edit().putString("cv_last_document_no", documentNo).apply();
            return new Result(uri, documentNo);
        } finally {
            document.close();
        }
    }

    private static void drawCover(Activity activity, Writer w, AppDatabase.Vehicle v,
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
        String color = pref(prefs, "vehicle_color");
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
        boolean showPrice = prefs.getBoolean("cv_show_price", false);
        String price = pref(prefs, "cv_sale_price");
        boolean hasPrice = showPrice && !price.isEmpty();
        if (!phone.isEmpty() && hasPrice) {
            w.coverChip(38, y, 250, "İLETİŞİM", phone);
            w.coverChip(307, y, 250, "İSTENEN SATIŞ FİYATI", price + " ₺");
            y += 46;
        } else if (!phone.isEmpty()) {
            w.coverChip(38, y, 519, "İLETİŞİM", phone);
            y += 46;
        } else if (hasPrice) {
            w.coverChip(38, y, 519, "İSTENEN SATIŞ FİYATI", price + " ₺");
            y += 46;
        }

        w.coverChip(38, y + 4, 250, "GÜNCEL KM", formatInt(v.km) + " km");
        w.coverChip(307, y + 4, 250, "YAKIT", blankFallback(fuel, "Belirtilmedi"));
        w.coverChip(38, y + 50, 250, "MOTOR / GÜÇ", joinNonEmpty(" • ", engine, power));
        w.coverChip(307, y + 50, 250, "ŞANZIMAN", blankFallback(trans, "Belirtilmedi"));
        w.coverChip(38, y + 96, 250, "KASA / RENK", joinNonEmpty(" • ", body, color));
        w.coverChip(307, y + 96, 250, "ÇEKİŞ", blankFallback(drivetrain, "Belirtilmedi"));
        w.coverChip(38, y + 142, 250, "PAKET / VERSİYON", blankFallback(trim, "Belirtilmedi"));
        w.coverChip(307, y + 142, 250, "NESİL / SERİ", blankFallback(generation, "Belirtilmedi"));
        y += 189;

        String note = pref(prefs, "cv_note");
        if (!note.isEmpty()) {
            w.text("AÇIKLAMA", 38, y + 10, 7.2f, Writer.ACCENT_DARK, true);
            y = w.drawWrapped(note, 38, y + 25, 7.6f, Writer.DARK, false, Writer.CONTENT_W, 10) + 4;
        }

        w.text("Bu belge kullanıcı tarafından girilen kayıtlardan oluşturulmuştur; resmî doğrulama belgesi değildir.", 38, y + 8, 6.3f, Writer.MUTED, false);
        w.y = y + 24;
    }

    private static void drawVehicleIdentity(Writer w, AppDatabase.Vehicle v, SharedPreferences prefs,
                                            String phone, String note, boolean showPlate,
                                            AppDatabase db, boolean showCosts) {
        w.newPage(true);
        w.section("Araç bilgileri", "Teknik ve kayıtlı araç bilgileri");
        TurkeyVehicleSpecs.Spec spec = resolvedSpec(v, prefs);

        w.keyValue("Araç türü", prefOr(prefs, "vehicle_type", "Otomobil"));
        w.keyValue("Marka / Model", v.brand + " / " + v.model);
        w.keyValue("Model yılı", String.valueOf(v.year));
        w.keyValue("Motor / Paket / Versiyon", resolvedValue(prefs, "vehicle_catalog_variant", spec == null ? "" : spec.variant));
        w.keyValue("Nesil / Seri", resolvedValue(prefs, "vehicle_generation", spec == null ? "" : spec.generation));
        w.keyValue("Paket / Versiyon", resolvedValue(prefs, "vehicle_trim", spec == null ? "" : spec.trim));
        w.keyValue("Kasa tipi", resolvedValue(prefs, "vehicle_body", spec == null ? "" : spec.body));
        w.keyValue("Yakıt", blankFallback(v.fuelType, spec == null ? "" : spec.fuel));
        w.keyValue("Şanzıman", resolvedValue(prefs, "vehicle_transmission", spec == null ? "" : spec.transmission));
        w.keyValue("Motor", resolvedValue(prefs, "vehicle_engine", spec == null ? "" : spec.engine));
        w.keyValue("Motor gücü", resolvedValue(prefs, "vehicle_power", spec == null ? "" : spec.power));
        w.keyValue("Çekiş", resolvedValue(prefs, "vehicle_drivetrain", spec == null ? "" : spec.drivetrain));
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

    private static void drawHistory(Writer w, AppDatabase db, boolean showCosts) {
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

    private static void drawPhotos(Activity activity, Writer w, List<Uri> selectedPhotos) {
        ArrayList<Uri> photos = new ArrayList<>();
        if (selectedPhotos != null) {
            for (Uri uri : selectedPhotos) {
                if (uri == null) continue;
                photos.add(uri);
                if (photos.size() >= MAX_EXTRA_PHOTOS) break;
            }
        }
        if (photos.isEmpty()) return;

        // Geçmiş kayıtlarından sonra sayfada yer varsa fotoğrafları aynı sayfada sürdür.
        // Böylece yarım dolu sayfa bırakıp gereksiz yeni sayfa açılmaz.
        if (w.y + 195 > 790) w.newPage(true);
        w.y += 4;
        w.section("Araç Resimleri", null);

        for (int index = 0; index < photos.size(); index++) {
            if (w.y + 174 > 790) {
                w.newPage(true);
                w.section("Araç Resimleri", null);
            }
            Bitmap bitmap = loadBitmap(activity, photos.get(index), 1600);
            if (bitmap == null) {
                w.simpleCard("Araç resmi " + (index + 1), "Fotoğraf okunamadı", "");
                continue;
            }
            w.label("ARAÇ RESMİ " + (index + 1) + " / " + photos.size());
            w.drawImageFit(bitmap, Writer.LEFT, w.y + 3, Writer.CONTENT_W, 150);
            w.y += 163;
            bitmap.recycle();
        }
    }

    private static Uri writeDocument(Activity activity, android.graphics.pdf.PdfDocument document, String fileName) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AracDefteri");
            Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("PDF dosyası oluşturulamadı");
            try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new Exception("PDF dosyası açılamadı");
                document.writeTo(out);
            }
            return uri;
        }
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (dir == null) throw new Exception("Belge klasörü bulunamadı");
        File file = new File(dir, fileName);
        try (OutputStream out = new FileOutputStream(file)) { document.writeTo(out); }
        return Uri.fromFile(file);
    }

    private static List<AppDatabase.Record> recordsOfType(List<AppDatabase.Record> all, String type) {
        ArrayList<AppDatabase.Record> out = new ArrayList<>();
        for (AppDatabase.Record r : all) if (type.equals(r.type)) out.add(r);
        return out;
    }

    private static Bitmap loadBitmap(Activity activity, Uri uri, int maxDimension) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            while (largest > maxDimension * sample * 2) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sample);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception ignored) { return null; }
    }

    private static String nextDocumentNo(SharedPreferences prefs) {
        String issuer = prefs.getString("cv_issuer_code", "");
        if (issuer == null || issuer.length() != 6) issuer = String.format(Locale.US, "%06d", 100000 + new Random().nextInt(900000));
        int serial = prefs.getInt("cv_serial_counter", 0) + 1;
        if (serial > 999999) serial = 1;
        prefs.edit().putString("cv_issuer_code", issuer).putInt("cv_serial_counter", serial).apply();
        String date = new SimpleDateFormat("yyMMdd", Locale.US).format(new Date());
        return issuer + "-" + date + "-" + String.format(Locale.US, "%06d", serial);
    }

    private static String pref(SharedPreferences prefs, String key) {
        String value = prefs.getString(key, "");
        return value == null ? "" : value.trim();
    }

    private static String prefOr(SharedPreferences prefs, String key, String fallback) {
        String value = pref(prefs, key);
        return value.isEmpty() ? fallback : value;
    }

    private static String blankFallback(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static String joinNonEmpty(String separator, String... values) {
        StringBuilder b = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(separator);
            b.append(value.trim());
        }
        return b.toString();
    }

    private static TurkeyVehicleSpecs.Spec resolvedSpec(AppDatabase.Vehicle v, SharedPreferences prefs) {
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

    private static String formatMoney(double value) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(2);
        return nf.format(value) + " ₺";
    }

    private static String formatInt(int value) {
        return NumberFormat.getIntegerInstance(new Locale("tr", "TR")).format(value);
    }

    private static final class Writer {
        static final int PAGE_W = 595, PAGE_H = 842, LEFT = 38, RIGHT = 557, CONTENT_W = RIGHT - LEFT;
        static final int ACCENT = 0xFF0AA878, ACCENT_DARK = 0xFF08785C, DARK = 0xFF17211F, MUTED = 0xFF65736F, LIGHT = 0xFFF2F7F5;

        final Activity activity;
        final android.graphics.pdf.PdfDocument document;
        final String documentNo;
        String headerTitle = "";
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        android.graphics.pdf.PdfDocument.Page page;
        Canvas canvas;
        int pageNo = 0;
        int y = 48;

        Writer(Activity activity, android.graphics.pdf.PdfDocument document, String documentNo) {
            this.activity = activity;
            this.document = document;
            this.documentNo = documentNo;
        }

        void newPage(boolean header) {
            if (page != null) closePage();
            pageNo++;
            page = document.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
            canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            y = 32;
            if (header) drawHeader();
        }

        void finish() {
            if (page != null) {
                closePage();
                page = null;
            }
        }

        void closePage() {
            drawFooter();
            document.finishPage(page);
        }

        void drawHeader() {
            Bitmap icon = appIconBitmap(16);
            if (icon != null) {
                canvas.drawBitmap(icon, LEFT, 22, paint);
                icon.recycle();
            }
            text("ARAÇ DEFTERİ", LEFT + 23, 34, 7.8f, ACCENT, true);
            String h = headerTitle == null ? "" : headerTitle;
            if (h.length() > 48) h = h.substring(0, 47) + "…";
            text(h, LEFT + 168, 34, 7.2f, DARK, true);
            paint.setColor(0xFFE6ECEA);
            canvas.drawRect(LEFT, 48, RIGHT, 49, paint);
            y = 62;
        }

        Bitmap appIconBitmap(int size) {
            try {
                Drawable drawable = activity.getDrawable(R.drawable.ic_launcher);
                if (drawable == null) return null;
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bitmap);
                drawable.setBounds(0, 0, size, size);
                drawable.draw(c);
                return bitmap;
            } catch (Exception ignored) { return null; }
        }

        void drawFooter() {
            paint.setColor(0xFFE6ECEA);
            canvas.drawRect(LEFT, 801, RIGHT, 802, paint);
            text("Araç Defteri • Belge No: " + documentNo, LEFT, 820, 6.5f, MUTED, false);
            text("Sayfa " + pageNo, RIGHT - 34, 820, 6.5f, MUTED, false);
        }

        void section(String title, String subtitle) {
            ensure(38);
            text(title, LEFT, y, 12.5f, ACCENT, true);
            y += 13;
            if (subtitle != null && !subtitle.isEmpty()) {
                text(subtitle, LEFT, y, 7f, MUTED, false);
                y += 10;
            } else y += 2;
            paint.setColor(0xFFE6ECEA);
            canvas.drawRect(LEFT, y, RIGHT, y + 1, paint);
            y += 7;
        }

        void label(String value) {
            text(value, LEFT, y, 7f, MUTED, true);
            y += 11;
        }

        void keyValue(String key, String value) {
            if (value == null || value.trim().isEmpty()) return;
            int h = Math.max(20, measuredWrappedHeight(value, 8.2f, 325) + 6);
            ensure(h + 2);
            if ((y / 24) % 2 == 0) {
                paint.setColor(0xFFF8FAF9);
                canvas.drawRoundRect(new RectF(LEFT, y - 6, RIGHT, y + h - 5), 6, 6, paint);
            }
            text(key.toUpperCase(new Locale("tr", "TR")), LEFT + 7, y + 3, 6.3f, MUTED, true);
            drawWrapped(value, LEFT + 155, y + 3, 8.2f, DARK, true, 350, 10);
            y += h;
        }

        void summaryGrid(String[][] items) {
            int boxW = 251, boxH = 43, gap = 17;
            for (int i = 0; i < items.length; i += 2) {
                ensure(boxH + 8);
                summaryBox(LEFT, y, boxW, boxH, items[i][0], items[i][1]);
                if (i + 1 < items.length) summaryBox(LEFT + boxW + gap, y, boxW, boxH, items[i + 1][0], items[i + 1][1]);
                y += boxH + 8;
            }
        }

        void summaryBox(int x, int yy, int w, int h, String title, String value) {
            paint.setColor(LIGHT);
            canvas.drawRoundRect(new RectF(x, yy, x + w, yy + h), 12, 12, paint);
            text(title.toUpperCase(new Locale("tr", "TR")), x + 11, yy + 15, 6.5f, MUTED, true);
            text(value, x + 11, yy + 33, 10.5f, DARK, true);
        }

        void recordCard(AppDatabase.Record r, boolean showCosts) {
            String meta = r.date + " • " + formatInt(r.km) + " km";
            if (!r.subtype.isEmpty()) meta += " • " + r.subtype;
            if (showCosts && r.cost > 0) meta += " • " + formatMoney(r.cost);
            String detail = r.detail == null ? "" : r.detail.trim();
            String next = "";
            if (!r.nextDate.isEmpty() || r.nextKm > 0) next = "Sonraki: " + joinNonEmpty(" • ", r.nextDate, r.nextKm > 0 ? formatInt(r.nextKm) + " km" : "");
            int detailH = detail.isEmpty() ? 0 : measuredWrappedHeight(detail, 7.3f, CONTENT_W - 24) + 3;
            int h = 43 + detailH + (next.isEmpty() ? 0 : 11);
            ensure(h + 5);
            paint.setColor(0xFFF7FAF9);
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + h), 8, 8, paint);
            text(r.title, LEFT + 12, y + 15, 9f, DARK, true);
            text(meta, LEFT + 12, y + 28, 6.6f, MUTED, false);
            int yy = y + 38;
            if (!detail.isEmpty()) yy = drawWrapped(detail, LEFT + 12, yy, 7.3f, DARK, false, CONTENT_W - 24, 9) + 2;
            if (!next.isEmpty()) text(next, LEFT + 12, yy + 7, 6.8f, ACCENT_DARK, true);
            y += h + 5;
        }

        void simpleCard(String title, String subtitle, String right) {
            int h = 45;
            ensure(h + 6);
            paint.setColor(0xFFF7FAF9);
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + h), 8, 8, paint);
            text(title, LEFT + 12, y + 18, 9, DARK, true);
            text(subtitle, LEFT + 12, y + 33, 7, MUTED, false);
            if (right != null && !right.isEmpty()) text(right, RIGHT - 95, y + 18, 8, ACCENT_DARK, true);
            y += h + 6;
        }

        void coverChip(int x, int yy, int w, String label, String value) {
            paint.setColor(0xFFF1F6F4);
            canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 38), 9, 9, paint);
            text(label, x + 10, yy + 13, 5.8f, MUTED, true);
            String v = value == null || value.trim().isEmpty() ? "Belirtilmedi" : value;
            if (v.length() > 31) v = v.substring(0, 30) + "…";
            text(v, x + 10, yy + 28, 8.2f, DARK, true);
        }

        void infoBox(String label, String value) {
            ensure(70);
            paint.setColor(0xFFEAF7F2);
            canvas.drawRoundRect(new RectF(LEFT, y, RIGHT, y + 62), 12, 12, paint);
            text(label.toUpperCase(new Locale("tr", "TR")), LEFT + 15, y + 21, 8, ACCENT_DARK, true);
            text(value, LEFT + 15, y + 44, 13, DARK, true);
            y += 80;
        }

        void paragraph(String value, float size, int color) {
            ensure(measuredWrappedHeight(value, size, CONTENT_W) + 8);
            y = drawWrapped(value, LEFT, y, size, color, false, CONTENT_W, Math.round(size + 4));
        }

        void ensure(int required) {
            if (y + required > 798) newPage(true);
        }

        void text(String value, float x, float yy, float size, int color, boolean bold) {
            if (value == null) return;
            paint.setColor(color);
            paint.setTextSize(size);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
            canvas.drawText(value, x, yy, paint);
        }

        int drawWrapped(String value, int x, int startY, float size, int color, boolean bold, int maxWidth, int lineHeight) {
            if (value == null || value.trim().isEmpty()) return startY;
            paint.setTextSize(size);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
            paint.setColor(color);
            int yy = startY;
            for (String paragraph : value.replace('\n', ' ').trim().split("\\s+")) {
                // handled below through token buffer
            }
            String[] words = value.replace('\n', ' ').trim().split("\\s+");
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                    line.setLength(0);
                    line.append(candidate);
                } else {
                    canvas.drawText(line.toString(), x, yy, paint);
                    yy += lineHeight;
                    line.setLength(0);
                    line.append(word);
                }
            }
            if (line.length() > 0) {
                canvas.drawText(line.toString(), x, yy, paint);
                yy += lineHeight;
            }
            return yy;
        }

        int measuredWrappedHeight(String value, float size, int maxWidth) {
            if (value == null || value.trim().isEmpty()) return 0;
            paint.setTextSize(size);
            paint.setTypeface(Typeface.DEFAULT);
            String[] words = value.replace('\n', ' ').trim().split("\\s+");
            int lines = 1;
            StringBuilder line = new StringBuilder();
            for (String word : words) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (paint.measureText(candidate) <= maxWidth || line.length() == 0) {
                    line.setLength(0); line.append(candidate);
                } else {
                    lines++;
                    line.setLength(0); line.append(word);
                }
            }
            return lines * Math.round(size + 4);
        }

        void drawImageFit(Bitmap bitmap, int x, int yy, int maxW, int maxH) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
            float scale = Math.min((float) maxW / bitmap.getWidth(), (float) maxH / bitmap.getHeight());
            float width = bitmap.getWidth() * scale;
            float height = bitmap.getHeight() * scale;
            float left = x + (maxW - width) / 2f;
            float top = yy + (maxH - height) / 2f;
            paint.setColor(0xFFF2F5F4);
            canvas.drawRoundRect(new RectF(x, yy, x + maxW, yy + maxH), 12, 12, paint);
            canvas.drawBitmap(bitmap, null, new RectF(left, top, left + width, top + height), paint);
        }

        void drawImageCover(Bitmap bitmap, int x, int yy, int targetW, int targetH) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
            float scale = Math.max((float) targetW / bitmap.getWidth(), (float) targetH / bitmap.getHeight());
            float srcW = targetW / scale;
            float srcH = targetH / scale;
            float sx = (bitmap.getWidth() - srcW) / 2f;
            float sy = (bitmap.getHeight() - srcH) / 2f;
            android.graphics.Rect src = new android.graphics.Rect(Math.max(0, Math.round(sx)), Math.max(0, Math.round(sy)), Math.min(bitmap.getWidth(), Math.round(sx + srcW)), Math.min(bitmap.getHeight(), Math.round(sy + srcH)));
            canvas.drawBitmap(bitmap, src, new RectF(x, yy, x + targetW, yy + targetH), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            paint.setColor(0xFFE1E8E5);
            canvas.drawRoundRect(new RectF(x, yy, x + targetW, yy + targetH), 14, 14, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
