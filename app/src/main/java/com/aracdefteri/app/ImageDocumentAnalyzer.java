package com.aracdefteri.app;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR metninin tek başına yetmediği görüntüler için hafif, cihaz-içi görsel analiz.
 * - Gösterge panelinde ODO/toplam kilometre adayını güçlendirir.
 * - Kaporta ekspertiz şemalarında renk + konum ilişkisini parça bulgularına çevirir.
 * Ham görüntü veya OCR metni kalıcı olarak saklanmaz.
 */
public final class ImageDocumentAnalyzer {
    private static final Locale TR = new Locale("tr", "TR");

    private ImageDocumentAnalyzer() { }

    public static String buildVisualHints(Bitmap bitmap, String ocrText) {
        if (bitmap == null || bitmap.isRecycled()) return "";
        String raw = ocrText == null ? "" : ocrText;
        String n = normalize(raw);
        StringBuilder hints = new StringBuilder();

        boolean expertise = looksLikeExpertiseDiagram(n, bitmap);
        if (expertise) {
            String findings = analyzeBodyDiagram(bitmap);
            hints.append("BELGE_TURU: EKSPERTIZ\n");
            hints.append("EKSPERTIZ_TURU: Kaporta/boya\n");
            if (!findings.isEmpty()) hints.append("GORSEL_BULGULAR: ").append(findings).append('\n');
            // Şema sayfasındaki rastgele sayılar kilometre değildir; yalnız açık KM etiketi kabul edilir.
            hints.append("GORSEL_SEMA: KAPORTA\n");
            return hints.toString().trim();
        }

        if (looksLikeDashboard(n, bitmap)) {
            hints.append("BELGE_TURU: ODOMETER\n");
            int km = extractOdometerCandidate(raw);
            if (km > 0) hints.append("ODO ").append(km).append('\n');
        }

        if (looksLikeReceipt(n, bitmap)) hints.append("GORSEL_TIP: FIS\n");
        return hints.toString().trim();
    }

    private static boolean looksLikeExpertiseDiagram(String n, Bitmap bitmap) {
        int textScore = 0;
        if (containsAny(n, "kaporta", "ekspertiz", "expertiz")) textScore += 3;
        if (containsAny(n, "orijinal", "orjinal", "orj")) textScore += 2;
        if (containsAny(n, "boyali", "boya")) textScore += 2;
        if (containsAny(n, "degisen", "sok tak", "lokal", "plastik", "folyo")) textScore += 2;
        if (textScore >= 5) return true;

        // OCR başlığı kaçırsa bile tipik kaporta şemasında beyaz zemin üzerinde
        // kırmızı/sarı/mavi-mor olmak üzere birden fazla durum rengi bulunur.
        double[] ratios = globalStrongColorRatios(bitmap);
        return ratios[0] > 0.025 && ratios[1] > 0.012 && ratios[2] > 0.012 && whiteRatio(bitmap) > 0.45;
    }

    private static boolean looksLikeDashboard(String n, Bitmap bitmap) {
        if (containsAny(n, "km/h", "kmh", "r/min", "x1000", "x 1000", "rpm", "odometer", "odometre", " odo ")) return true;
        // Gece/karanlık gösterge fotoğraflarında OCR bazen yalnız birkaç rakam okur.
        // Koyu piksel oranı yüksekse ve görüntüde 5-7 haneli bir aday varsa bunu gösterge adayı say.
        return darkRatio(bitmap) > 0.42 && extractOdometerCandidate(n) > 0;
    }

    private static boolean looksLikeReceipt(String n, Bitmap bitmap) {
        boolean receiptText = containsAny(n, "fis", "fatura", "kdv", "toplam", "pos", "terminal", "mali", "odeme", "tarih");
        boolean fuelText = containsAny(n, "akaryakit", "benzin", "motorin", "dizel", "lpg", "otogaz", "litre", " lt ", "tl/lt", "pompa");
        float aspect = bitmap.getHeight() / (float) Math.max(1, bitmap.getWidth());
        return (receiptText || fuelText) && whiteRatio(bitmap) > 0.50 && aspect > 0.9f;
    }

    private static int extractOdometerCandidate(String raw) {
        if (raw == null) return 0;
        Matcher labeled = Pattern.compile("(?is)(?:\\bODO\\b|ODOMETER|ODOMETRE|TOPLAM\\s*KM|KILOMETRE(?:M)?)\\s*[:=.-]?\\s*([0-9][0-9 .]{3,10})").matcher(raw);
        while (labeled.find()) {
            int value = digitsValue(labeled.group(1));
            if (plausibleOdo(value)) return value;
        }

        int best = 0;
        Matcher m = Pattern.compile("(?<!\\d)([0-9](?:[ .]?[0-9]){4,6})(?!\\d)").matcher(raw);
        while (m.find()) {
            int value = digitsValue(m.group(1));
            if (!plausibleOdo(value)) continue;
            if (isGaugeScale(value)) continue;
            if (value > best) best = value;
        }
        return best;
    }

    private static int digitsValue(String token) {
        try {
            String digits = token == null ? "" : token.replaceAll("[^0-9]", "");
            if (digits.isEmpty() || digits.length() > 7) return 0;
            return Integer.parseInt(digits);
        } catch (Exception ignored) { return 0; }
    }

    private static boolean plausibleOdo(int value) {
        return value >= 1000 && value < 2_000_000 && !isGaugeScale(value);
    }

    private static boolean isGaugeScale(int value) {
        int[] suspicious = {100120,120140,140160,160180,180200,200220,2040,4060,6080,80100,100120140,120140160};
        for (int v : suspicious) if (value == v) return true;
        return false;
    }

    /**
     * Yaygın üstten görünüş kaporta şemalarında güçlü durum renklerini parça bölgeleriyle eşler.
     * Yalnız renk güveni yüksek değişiklikleri raporlar; beyaz alanları otomatik "orijinal" ilan etmez.
     */
    private static String analyzeBodyDiagram(Bitmap bitmap) {
        List<String> findings = new ArrayList<>();
        Zone[] zones = new Zone[]{
                new Zone("Sol ön çamurluk", 0.18f, 0.34f, 0.23f, 0.42f),
                new Zone("Kaput", 0.36f, 0.63f, 0.22f, 0.41f),
                new Zone("Sağ ön çamurluk", 0.66f, 0.82f, 0.23f, 0.42f),
                new Zone("Sol ön kapı", 0.19f, 0.40f, 0.39f, 0.56f),
                new Zone("Sağ ön kapı", 0.60f, 0.82f, 0.39f, 0.56f),
                new Zone("Sol arka kapı", 0.19f, 0.40f, 0.54f, 0.70f),
                new Zone("Sağ arka kapı", 0.60f, 0.82f, 0.54f, 0.70f),
                new Zone("Sol arka çamurluk", 0.18f, 0.35f, 0.63f, 0.84f),
                new Zone("Sağ arka çamurluk", 0.65f, 0.82f, 0.63f, 0.84f),
                new Zone("Tavan", 0.38f, 0.62f, 0.40f, 0.67f),
                new Zone("Bagaj / arka kapak", 0.36f, 0.63f, 0.67f, 0.84f)
        };
        for (Zone zone : zones) {
            String state = classifyRegion(bitmap, zone);
            if (!state.isEmpty()) findings.add(zone.name + ": " + state);
        }
        return join(findings, " | ");
    }

    private static String classifyRegion(Bitmap bitmap, Zone z) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int x0 = clamp(Math.round(z.x0 * w), 0, w - 1);
        int x1 = clamp(Math.round(z.x1 * w), x0 + 1, w);
        int y0 = clamp(Math.round(z.y0 * h), 0, h - 1);
        int y1 = clamp(Math.round(z.y1 * h), y0 + 1, h);
        int step = Math.max(2, Math.min(w, h) / 420);
        int total = 0, red = 0, yellow = 0, blue = 0, gray = 0;
        float[] hsv = new float[3];
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = bitmap.getPixel(x, y);
                Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv);
                float hue = hsv[0], sat = hsv[1], val = hsv[2];
                total++;
                if (sat > 0.48f && val > 0.50f && (hue < 18f || hue > 345f)) red++;
                else if (sat > 0.42f && val > 0.55f && hue >= 38f && hue <= 78f) yellow++;
                else if (sat > 0.28f && val > 0.38f && hue >= 205f && hue <= 300f) blue++;
                else if (sat < 0.12f && val > 0.28f && val < 0.82f) gray++;
            }
        }
        if (total <= 0) return "";
        double rr = red / (double) total;
        double yr = yellow / (double) total;
        double br = blue / (double) total;
        double gr = gray / (double) total;

        double best = Math.max(rr, Math.max(yr, br));
        if (best >= 0.075) {
            if (best == rr) return "Değişen";
            if (best == yr) return "Lokal boyalı";
            return "Boyalı";
        }
        // Gri/Sök-Tak rengi beyaz zemin ve gölgelerle karışabildiği için daha yüksek eşik gerekir.
        if (gr >= 0.34) return "Sök-tak";
        return "";
    }

    private static double[] globalStrongColorRatios(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int step = Math.max(3, Math.min(w, h) / 350);
        int total = 0, red = 0, yellow = 0, blue = 0;
        float[] hsv = new float[3];
        int y0 = (int) (h * 0.16f), y1 = (int) (h * 0.86f);
        int x0 = (int) (w * 0.10f), x1 = (int) (w * 0.90f);
        for (int y = y0; y < y1; y += step) {
            for (int x = x0; x < x1; x += step) {
                int c = bitmap.getPixel(x, y);
                Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv);
                total++;
                if (hsv[1] > 0.48f && hsv[2] > 0.50f && (hsv[0] < 18f || hsv[0] > 345f)) red++;
                else if (hsv[1] > 0.42f && hsv[2] > 0.55f && hsv[0] >= 38f && hsv[0] <= 78f) yellow++;
                else if (hsv[1] > 0.28f && hsv[2] > 0.38f && hsv[0] >= 205f && hsv[0] <= 300f) blue++;
            }
        }
        if (total == 0) return new double[]{0,0,0};
        return new double[]{red/(double)total, yellow/(double)total, blue/(double)total};
    }

    private static double darkRatio(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int step = Math.max(4, Math.min(w, h) / 250);
        int dark = 0, total = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = bitmap.getPixel(x,y);
                int lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000;
                if (lum < 85) dark++;
                total++;
            }
        }
        return total == 0 ? 0 : dark / (double) total;
    }

    private static double whiteRatio(Bitmap bitmap) {
        int w = bitmap.getWidth(), h = bitmap.getHeight();
        int step = Math.max(4, Math.min(w, h) / 250);
        int white = 0, total = 0;
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                int c = bitmap.getPixel(x,y);
                if (Color.red(c) > 220 && Color.green(c) > 220 && Color.blue(c) > 220) white++;
                total++;
            }
        }
        return total == 0 ? 0 : white / (double) total;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        return (" " + s.toLowerCase(TR) + " ")
                .replace('ı','i').replace('ş','s').replace('ğ','g')
                .replace('ü','u').replace('ö','o').replace('ç','c');
    }

    private static boolean containsAny(String s, String... keys) {
        for (String key : keys) if (s.contains(key)) return true;
        return false;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String join(List<String> values, String sep) {
        StringBuilder b = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (b.length() > 0) b.append(sep);
            b.append(value.trim());
        }
        return b.toString();
    }

    private static final class Zone {
        final String name;
        final float x0, x1, y0, y1;
        Zone(String name, float x0, float x1, float y0, float y1) {
            this.name = name; this.x0 = x0; this.x1 = x1; this.y0 = y0; this.y1 = y1;
        }
    }
}
