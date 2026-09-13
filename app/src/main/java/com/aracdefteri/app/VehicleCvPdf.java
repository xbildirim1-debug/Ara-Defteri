package com.aracdefteri.app;

import android.app.Activity;
import android.content.ContentValues;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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

/** Araç Defteri markalı, ayrıntılı Araç CV PDF üreticisi. */
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
        boolean showPlate = prefs.getBoolean("cv_show_plate", true);
        String phone = prefs.getString("cv_phone", "").trim();
        String note = prefs.getString("cv_note", "").trim();

        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        PdfWriter w = new PdfWriter(activity, document, documentNo);

        try {
            w.newPage(false);
            w.drawBrandHeader(true);
            w.y += 16;
            w.title("DİJİTAL ARAÇ GEÇMİŞİ", 25);
            w.subtle("Araç CV • Kullanıcı kayıtlarından derlenmiştir", 11);
            w.y += 10;
            w.documentBadge(documentNo);
            w.y += 18;

            String mainPhoto = prefs.getString("vehicle_photo_uri", "");
            if (!mainPhoto.isEmpty()) {
                Bitmap b = loadBitmap(activity, Uri.parse(mainPhoto));
                if (b != null) {
                    w.drawImageFit(b, 42, w.y, 511, 245);
                    w.y += 260;
                    b.recycle();
                }
            }

            w.title(vehicle.year + " " + vehicle.brand + " " + vehicle.model, 22);
            String trim = pref(prefs, "vehicle_trim");
            String engine = pref(prefs, "vehicle_engine");
            String line = joinNonEmpty(" • ", trim, engine);
            if (!line.isEmpty()) w.subtle(line, 11);
            w.subtle(formatInt(vehicle.km) + " km • " + vehicle.fuelType, 11);
            w.y += 12;

            w.newPage(true);
            w.section("Araç kimliği ve teknik bilgiler");
            w.field("Araç türü", prefOr(prefs, "vehicle_type", "Otomobil"));
            w.field("Marka / Model", vehicle.brand + " / " + vehicle.model);
            w.field("Model yılı", String.valueOf(vehicle.year));
            w.field("Nesil / Seri", pref(prefs, "vehicle_generation"));
            w.field("Paket / Versiyon", trim);
            w.field("Kasa tipi", pref(prefs, "vehicle_body"));
            w.field("Yakıt", vehicle.fuelType);
            w.field("Şanzıman", pref(prefs, "vehicle_transmission"));
            w.field("Motor", engine);
            w.field("Motor gücü", pref(prefs, "vehicle_power"));
            w.field("Çekiş", pref(prefs, "vehicle_drivetrain"));
            w.field("Renk", pref(prefs, "vehicle_color"));
            w.field("Güncel kilometre", formatInt(vehicle.km) + " km");
            if (showPlate && !vehicle.plate.isEmpty()) w.field("Plaka", vehicle.plate);
            if (!phone.isEmpty()) w.field("İletişim", phone);
            if (!note.isEmpty()) {
                w.y += 6;
                w.label("Özel not");
                w.paragraph(note, 10, Color.DKGRAY);
            }

            w.y += 12;
            w.section("Özet");
            w.statLine("Bakım / Onarım", db.countRecordsByType("Bakım") + " kayıt");
            w.statLine("Hasar", db.countRecordsByType("Hasar") + " kayıt");
            w.statLine("Ekspertiz", db.countRecordsByType("Ekspertiz") + " kayıt");
            w.statLine("Muayene", db.countRecordsByType("Muayene") + " kayıt");
            w.statLine("Sigorta / Kasko", db.countRecordsByType("Sigorta/Kasko") + " kayıt");
            w.statLine("Vergi", db.countRecordsByType("Vergi") + " kayıt");
            w.statLine("Yakıt", db.countRecordsByType("Yakıt") + " kayıt");
            w.statLine("Toplam kayıt", db.countAllRecords() + " kayıt");
            if (showCosts) w.statLine("Kayıtlı toplam gider", formatMoney(db.getExpenseTotal()));

            List<AppDatabase.Record> records = db.getRecords(500);
            String[] sections = {"Bakım", "Hasar", "Ekspertiz", "Muayene", "Sigorta/Kasko", "Vergi", "Yakıt"};
            String[] titles = {"Bakım & Onarım Geçmişi", "Hasar Geçmişi", "Ekspertiz Geçmişi", "Muayene Geçmişi", "Sigorta & Kasko", "Vergi / Resmî Ödemeler", "Yakıt / Enerji Kayıtları"};
            for (int i = 0; i < sections.length; i++) {
                List<AppDatabase.Record> group = recordsOfType(records, sections[i]);
                if (group.isEmpty()) continue;
                w.newPage(true);
                w.section(titles[i]);
                for (AppDatabase.Record r : group) w.record(r, showCosts);
            }

            List<AppDatabase.Expense> expenses = db.getExpenses();
            if (!expenses.isEmpty()) {
                w.newPage(true);
                w.section("Diğer Giderler");
                int shown = 0;
                for (AppDatabase.Expense e : expenses) {
                    if (shown++ >= 40) break;
                    w.ensure(48);
                    w.bold(e.category + (showCosts ? " • " + formatMoney(e.amount) : ""), 11);
                    w.subtle(e.date + (e.note.isEmpty() ? "" : " • " + e.note), 9);
                    w.y += 9;
                }
            }

            ArrayList<Uri> photos = new ArrayList<>();
            if (selectedPhotos != null) {
                for (Uri u : selectedPhotos) {
                    if (u == null) continue;
                    photos.add(u);
                    if (photos.size() >= MAX_EXTRA_PHOTOS) break;
                }
            }
            if (!photos.isEmpty()) {
                int index = 0;
                while (index < photos.size()) {
                    w.newPage(true);
                    w.section("CV Fotoğrafları");
                    for (int slot = 0; slot < 2 && index < photos.size(); slot++, index++) {
                        Bitmap b = loadBitmap(activity, photos.get(index));
                        if (b == null) continue;
                        w.label("Fotoğraf " + (index + 1) + " / " + photos.size());
                        w.drawImageFit(b, 42, w.y, 511, 285);
                        w.y += 300;
                        b.recycle();
                    }
                }
            }

            w.ensure(130);
            w.y += 12;
            w.section("Belge hakkında");
            w.paragraph("Bu PDF Araç Defteri uygulaması ile oluşturulmuştur. Belge numarası bu kurulum tarafından üretilen belge serisini ayırt etmek için kullanılır.", 9, Color.DKGRAY);
            w.paragraph("Bilgiler araç sahibi tarafından oluşturulan kayıtlardan derlenmiştir; resmî ekspertiz, resmî hasar sorgusu veya kilometre doğrulama belgesi değildir.", 9, Color.DKGRAY);
            w.paragraph("Şase/VIN bilgisi Araç Defteri tarafından tutulmaz ve bu belgeye eklenmez.", 9, Color.DKGRAY);
            w.finish();

            String fileName = "Arac-Defteri-CV-" + documentNo.replace("-", "") + ".pdf";
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AracDefteri");
                uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new Exception("PDF dosyası oluşturulamadı");
                try (OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                    if (out == null) throw new Exception("PDF dosyası açılamadı");
                    document.writeTo(out);
                }
            } else {
                File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
                if (dir == null) throw new Exception("Belge klasörü bulunamadı");
                File file = new File(dir, fileName);
                try (OutputStream out = new FileOutputStream(file)) {
                    document.writeTo(out);
                }
                uri = Uri.fromFile(file);
            }
            prefs.edit().putString("cv_last_document_no", documentNo).apply();
            return new Result(uri, documentNo);
        } finally {
            document.close();
        }
    }

    private static List<AppDatabase.Record> recordsOfType(List<AppDatabase.Record> all, String type) {
        ArrayList<AppDatabase.Record> out = new ArrayList<>();
        for (AppDatabase.Record r : all) if (type.equals(r.type)) out.add(r);
        return out;
    }

    private static Bitmap loadBitmap(Activity activity, Uri uri) {
        try (InputStream in = activity.getContentResolver().openInputStream(uri)) {
            return BitmapFactory.decodeStream(in);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String nextDocumentNo(SharedPreferences prefs) {
        String issuer = prefs.getString("cv_issuer_code", "");
        if (issuer == null || issuer.length() != 6) {
            issuer = String.format(Locale.US, "%06d", 100000 + new Random().nextInt(900000));
        }
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

    private static String joinNonEmpty(String separator, String... values) {
        StringBuilder b = new StringBuilder();
        for (String v : values) {
            if (v == null || v.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(separator);
            b.append(v.trim());
        }
        return b.toString();
    }

    private static String formatMoney(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(2);
        return nf.format(v) + " ₺";
    }

    private static String formatInt(int v) {
        return NumberFormat.getIntegerInstance(new Locale("tr", "TR")).format(v);
    }

    private static final class PdfWriter {
        private final Activity activity;
        private final android.graphics.pdf.PdfDocument document;
        private final String documentNo;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private android.graphics.pdf.PdfDocument.Page page;
        private Canvas canvas;
        private int pageNo = 0;
        private int y = 52;
        private static final int PAGE_W = 595;
        private static final int PAGE_H = 842;
        private static final int LEFT = 42;
        private static final int RIGHT = 553;
        private static final int ACCENT = 0xFF08A376;

        PdfWriter(Activity activity, android.graphics.pdf.PdfDocument document, String documentNo) {
            this.activity = activity;
            this.document = document;
            this.documentNo = documentNo;
        }

        void newPage(boolean smallHeader) {
            if (page != null) closeCurrentPage();
            pageNo++;
            page = document.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create());
            canvas = page.getCanvas();
            canvas.drawColor(Color.WHITE);
            y = 48;
            if (smallHeader) drawBrandHeader(false);
        }

        void finish() {
            if (page != null) {
                closeCurrentPage();
                page = null;
            }
        }

        private void closeCurrentPage() {
            drawFooter();
            document.finishPage(page);
        }

        void drawBrandHeader(boolean large) {
            Bitmap icon = appIconBitmap(large ? 66 : 32);
            if (icon != null) {
                canvas.drawBitmap(icon, LEFT, y - 7, paint);
                icon.recycle();
            }
            int x = LEFT + (large ? 80 : 44);
            paint.setColor(ACCENT);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(large ? 22 : 13);
            canvas.drawText("ARAÇ DEFTERİ", x, y + (large ? 30 : 16), paint);
            if (large) {
                paint.setColor(Color.DKGRAY);
                paint.setTypeface(Typeface.DEFAULT);
                paint.setTextSize(9);
                canvas.drawText("Aracının dijital hafızası", x, y + 47, paint);
                y += 72;
            } else {
                paint.setColor(Color.LTGRAY);
                canvas.drawLine(LEFT, y + 32, RIGHT, y + 32, paint);
                y += 48;
            }
        }

        private Bitmap appIconBitmap(int size) {
            try {
                Drawable drawable = activity.getDrawable(R.drawable.ic_launcher);
                if (drawable == null) return null;
                Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bitmap);
                drawable.setBounds(0, 0, size, size);
                drawable.draw(c);
                return bitmap;
            } catch (Exception ignored) {
                return null;
            }
        }

        void documentBadge(String no) {
            paint.setColor(0xFFE9F7F2);
            canvas.drawRoundRect(LEFT, y, RIGHT, y + 54, 12, 12, paint);
            paint.setColor(ACCENT);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(9);
            canvas.drawText("ARAÇ DEFTERİ BELGE NO", LEFT + 14, y + 19, paint);
            paint.setColor(Color.rgb(18,31,28));
            paint.setTextSize(18);
            canvas.drawText(no, LEFT + 14, y + 42, paint);
            y += 54;
        }

        void section(String title) {
            ensure(42);
            paint.setColor(ACCENT);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(15);
            canvas.drawText(title, LEFT, y, paint);
            paint.setColor(0xFFDCECE6);
            canvas.drawLine(LEFT, y + 9, RIGHT, y + 9, paint);
            y += 28;
        }

        void field(String label, String value) {
            if (value == null || value.trim().isEmpty()) return;
            ensure(34);
            label(label);
            paint.setColor(Color.rgb(20, 30, 28));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10.5f);
            canvas.drawText(value.trim(), LEFT + 145, y - 1, paint);
            y += 20;
        }

        void statLine(String label, String value) {
            ensure(28);
            paint.setColor(Color.DKGRAY);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(10);
            canvas.drawText(label, LEFT, y, paint);
            paint.setColor(Color.BLACK);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            canvas.drawText(value, 360, y, paint);
            y += 20;
        }

        void record(AppDatabase.Record r, boolean showCosts) {
            ensure(86);
            paint.setColor(0xFFF6F9F8);
            canvas.drawRoundRect(LEFT, y - 13, RIGHT, y + 62, 10, 10, paint);
            int startY = y;
            paint.setColor(Color.rgb(18,31,28));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(11);
            canvas.drawText(ellipsize(r.title, 62), LEFT + 12, y + 5, paint);
            y += 19;
            String meta = r.date + " • " + formatInt(r.km) + " km";
            if (!r.subtype.isEmpty()) meta += " • " + r.subtype;
            if (showCosts && r.cost > 0) meta += " • " + formatMoney(r.cost);
            subtleAt(meta, LEFT + 12, y, 8.5f);
            y += 15;
            if ("Yakıt".equals(r.type) && r.quantity > 0) {
                String fuel = trimDouble(r.quantity) + " " + r.unit;
                if (r.unitPrice > 0 && showCosts) fuel += " • " + formatMoney(r.unitPrice) + "/" + r.unit;
                subtleAt(fuel, LEFT + 12, y, 8.5f);
                y += 14;
            }
            if ("Bakım".equals(r.type) && !r.extra.isEmpty()) {
                subtleAt("Değişen: " + ellipsize(r.extra.replace("|", ", "), 72), LEFT + 12, y, 8.5f);
                y += 14;
            }
            if (!r.detail.isEmpty()) {
                subtleAt(ellipsize(r.detail.replace('\n', ' '), 78), LEFT + 12, y, 8.5f);
                y += 14;
            }
            String next = "";
            if (!r.nextDate.isEmpty()) next = "Sonraki: " + r.nextDate;
            if (r.nextKm > 0) next += (next.isEmpty() ? "Sonraki: " : " • ") + formatInt(r.nextKm) + " km";
            if (!next.isEmpty()) {
                paint.setColor(0xFFB27616);
                paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
                paint.setTextSize(8.5f);
                canvas.drawText(ellipsize(next, 74), LEFT + 12, y, paint);
                y += 14;
            }
            y = Math.max(y + 14, startY + 77);
        }

        void title(String value, float size) {
            ensure((int)size + 14);
            paint.setColor(Color.rgb(18,31,28));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(size);
            canvas.drawText(ellipsize(value, size > 20 ? 40 : 65), LEFT, y, paint);
            y += (int)size + 8;
        }

        void bold(String value, float size) {
            ensure((int)size + 8);
            paint.setColor(Color.rgb(18,31,28));
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(size);
            canvas.drawText(ellipsize(value, 78), LEFT, y, paint);
            y += (int)size + 6;
        }

        void subtle(String value, float size) {
            ensure((int)size + 8);
            subtleAt(ellipsize(value, 86), LEFT, y, size);
            y += (int)size + 7;
        }

        void label(String value) {
            paint.setColor(Color.GRAY);
            paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            paint.setTextSize(8.5f);
            canvas.drawText(value.toUpperCase(new Locale("tr", "TR")), LEFT, y, paint);
        }

        void paragraph(String text, float size, int color) {
            if (text == null || text.trim().isEmpty()) return;
            paint.setColor(color);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(size);
            int maxChars = size <= 9 ? 92 : 80;
            List<String> lines = wrap(text, maxChars);
            for (String line : lines) {
                ensure((int)size + 8);
                canvas.drawText(line, LEFT, y, paint);
                y += (int)size + 5;
            }
            y += 5;
        }

        void drawImageFit(Bitmap bitmap, float left, float top, float maxW, float maxH) {
            if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
            float scale = Math.min(maxW / bitmap.getWidth(), maxH / bitmap.getHeight());
            float w = bitmap.getWidth() * scale;
            float h = bitmap.getHeight() * scale;
            float x = left + (maxW - w) / 2f;
            float yy = top + (maxH - h) / 2f;
            paint.setColor(0xFFF3F6F5);
            canvas.drawRoundRect(left, top, left + maxW, top + maxH, 12, 12, paint);
            canvas.drawBitmap(bitmap, null, new android.graphics.RectF(x, yy, x + w, yy + h), paint);
        }

        void ensure(int needed) {
            if (y + needed <= 770) return;
            newPage(true);
        }

        private void subtleAt(String value, float x, float yy, float size) {
            paint.setColor(Color.DKGRAY);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(size);
            canvas.drawText(value, x, yy, paint);
        }

        private void drawFooter() {
            paint.setColor(Color.LTGRAY);
            canvas.drawLine(LEFT, 798, RIGHT, 798, paint);
            paint.setTextSize(7.5f);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setColor(Color.GRAY);
            canvas.drawText("Araç Defteri ile oluşturuldu • Belge No: " + documentNo, LEFT, 815, paint);
            canvas.drawText("Sayfa " + pageNo, 515, 815, paint);
        }
    }

    private static List<String> wrap(String text, int maxChars) {
        ArrayList<String> out = new ArrayList<>();
        String clean = text.replace('\n', ' ').trim();
        if (clean.isEmpty()) return out;
        String[] words = clean.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() > 0 && line.length() + 1 + word.length() > maxChars) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    private static String ellipsize(String s, int max) {
        if (s == null) return "";
        String value = s.trim();
        return value.length() <= max ? value : value.substring(0, Math.max(1, max - 1)) + "…";
    }

    private static String trimDouble(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.00001) return String.valueOf((long)Math.rint(v));
        return String.format(Locale.getDefault(), "%.2f", v).replaceAll("0+$", "").replaceAll("[.,]$", "");
    }
}
