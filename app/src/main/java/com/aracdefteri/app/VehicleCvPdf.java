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
        w.headerTitle = vehicle.year + " " + vehicle.brand + " " + vehicle.model;
        try {
            drawCover(activity, w, vehicle, prefs, documentNo, showPlate);
            drawVehicleIdentity(w, vehicle, prefs, phone, note, showPlate, db, showCosts);
            drawHistory(w, db, showCosts);
            drawPhotos(activity, w, selectedPhotos);
            drawAbout(w);
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

    private static void drawVehicleIdentity(Writer w, AppDatabase.Vehicle v, SharedPreferences prefs,
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

    private static void drawHistory(Writer w, AppDatabase db, boolean showCosts) {
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

        int index = 0;
        while (index < photos.size()) {
            w.newPage(true);
            w.section("CV Fotoğrafları", "Kullanıcının bu PDF için seçtiği fotoğraflar");
            for (int slot = 0; slot < 2 && index < photos.size(); slot++, index++) {
                Bitmap bitmap = loadBitmap(activity, photos.get(index), 1800);
                if (bitmap == null) {
                    w.simpleCard("Fotoğraf " + (index + 1), "Fotoğraf okunamadı", "");
                    continue;
                }
                w.ensure(325);
                w.label("FOTOĞRAF " + (index + 1) + " / " + photos.size());
                w.drawImageFit(bitmap, Writer.LEFT, w.y + 8, Writer.CONTENT_W, 270);
                w.y += 292;
                bitmap.recycle();
            }
        }
    }

    private static void drawAbout(Writer w) {
        w.ensure(170);
        w.y += 10;
        w.section("Açıklama", null);
        w.paragraph("Bu PDF Araç Defteri uygulaması ile kullanıcı tarafından girilen araç kayıtlarından oluşturulmuştur.", 8.5f, Writer.DARK);
        w.y += 5;
        w.paragraph("Belgedeki satış fiyatı varsa kullanıcı tarafından girilen istenen fiyattır; bağımsız değerleme değildir.", 8.5f, Writer.DARK);
        w.y += 5;
        w.paragraph("Araç geçmişi, hasar, kilometre ve diğer bilgiler resmî doğrulama niteliği taşımaz. Şase/VIN bilgisi tutulmaz.", 8.5f, Writer.DARK);
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
            y = 38;
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

        void label(String value) {
            text(value, LEFT, y, 7.5f, MUTED, true);
            y += 13;
        }

        void keyValue(String key, String value) {
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
            canvas.drawRoundRect(new RectF(x, yy, x + w, yy + 46), 10, 10, paint);
            text(label, x + 11, yy + 15, 6.2f, MUTED, true);
            String v = value == null || value.trim().isEmpty() ? "Belirtilmedi" : value;
            if (v.length() > 31) v = v.substring(0, 30) + "…";
            text(v, x + 11, yy + 34, 9.2f, DARK, true);
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
            if (y + required > 790) newPage(true);
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
