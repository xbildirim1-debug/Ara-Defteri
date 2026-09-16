from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
APP = ROOT / "app/src/main/java/com/aracdefteri/app"

visual = r'''package com.aracdefteri.app;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OCR sonucunu güvenli hale getirir. Temel kural: belgede açıkça bulunmayan alan
 * asla başka bir sayıdan tahmin edilmez. Görsel şemalar için ImageDocumentAnalyzer
 * kullanılır.
 */
public final class VisualDocumentAnalyzer {
    private static final Locale TR = new Locale("tr", "TR");

    private VisualDocumentAnalyzer() { }

    public static void sanitizeTextOcr(String raw, RecordParser.Parsed p) {
        if (p == null) return;
        String text = raw == null ? "" : raw;
        String n = norm(text);

        if (looksLikeFuelReceipt(text, n, p)) {
            p.type = "Yakıt";
            p.documentKind = "FUEL";
            p.confidence = Math.max(p.confidence, 92);
            // Fişte KM etiketi yoksa KM yoktur. MERSİS, fiş no, lisans no vb. KM değildir.
            p.km = strictExplicitKm(text);
            fillFuel(text, n, p);
            return;
        }

        boolean insurance = "INSURANCE".equals(p.documentKind) || "Sigorta/Kasko".equals(p.type)
                || containsAny(n, "sigorta policesi", "police no", "police numarasi", "sigorta baslangic");
        if (insurance) {
            if (!looksLikeVehiclePolicy(n, p)) {
                clearNonVehiclePolicy(p);
                p.warnings.add("Bu belge araç sigortası/kasko poliçesi gibi görünmüyor; araç kaydı oluşturulmadı.");
                return;
            }
            p.type = "Sigorta/Kasko";
            p.documentKind = "INSURANCE";
            p.km = strictExplicitKm(text);
            if (!plausibleVendor(p.vendor)) p.vendor = "";
            p.detailSummary = join(" • ",
                    nonEmpty(p.vendor) ? p.vendor : "",
                    nonEmpty(p.policyNo) ? "Poliçe: " + p.policyNo : "",
                    nonEmpty(p.policyStartDate) ? "Başlangıç: " + p.policyStartDate : "",
                    nonEmpty(p.policyEndDate) ? "Bitiş: " + p.policyEndDate : "",
                    nonEmpty(p.plate) ? "Plaka: " + p.plate : "");
            return;
        }

        boolean expertise = "EXPERTISE".equals(p.documentKind) || "Ekspertiz".equals(p.type)
                || containsAny(n, "kaporta detay ekspertiz", "oto ekspertiz", "ekspertiz raporu");
        if (expertise) {
            p.type = "Ekspertiz";
            p.documentKind = "EXPERTISE";
            p.expertiseSubtype = nonEmpty(p.expertiseSubtype) ? p.expertiseSubtype : "Kaporta/boya";
            p.km = strictExplicitKm(text);
            if (!plausibleVendor(p.vendor)) p.vendor = "";
            if (!containsPartFinding(p.detailSummary)) p.detailSummary = "";
            p.confidence = Math.max(p.confidence, 82);
            return;
        }

        boolean inspection = "INSPECTION".equals(p.documentKind) || "Muayene".equals(p.type);
        if (inspection) {
            p.km = strictExplicitKm(text);
            if (!plausibleVendor(p.vendor) && !"TÜVTÜRK".equals(p.vendor)) p.vendor = "";
            return;
        }

        // OCR belgelerinde yalnız açık etiketli kilometre kabul edilir. Odometer görüntüsü
        // applyImage içinde ayrıca görsel olarak doğrulanır.
        if (!"ODOMETER".equals(p.documentKind)) {
            int explicit = strictExplicitKm(text);
            p.km = explicit > 0 ? explicit : 0;
        }
        if (!plausibleVendor(p.vendor)) p.vendor = "";
    }

    public static void applyImage(Bitmap bitmap, String raw, RecordParser.Parsed p) {
        if (p == null) return;
        sanitizeTextOcr(raw, p);
        if (bitmap == null || bitmap.isRecycled()) return;

        String hints = ImageDocumentAnalyzer.buildVisualHints(bitmap, raw == null ? "" : raw);
        if (hints.isEmpty()) return;

        if (hints.contains("BELGE_TURU: EKSPERTIZ")) {
            p.type = "Ekspertiz";
            p.documentKind = "EXPERTISE";
            p.expertiseSubtype = "Kaporta/boya";
            p.km = strictExplicitKm(raw);
            String findings = hintValue(hints, "GORSEL_BULGULAR:");
            if (!findings.isEmpty()) p.detailSummary = findings;
            else p.detailSummary = "";
            p.confidence = Math.max(p.confidence, 96);
            return;
        }

        if (hints.contains("BELGE_TURU: ODOMETER")) {
            p.type = "";
            p.documentKind = "ODOMETER";
            int km = hintInt(hints, "ODO");
            // Hız kadranı (örn. 140 km/h) hiçbir zaman kilometre sayacı değildir.
            p.km = km >= 1000 && km < 2_000_000 ? km : 0;
            p.confidence = p.km > 0 ? 97 : 72;
            if (p.km <= 0) p.warnings.add("ODO/toplam kilometre güvenle okunamadı; 140 km/h gibi hız değerleri kullanılmadı.");
        }
    }

    private static boolean looksLikeFuelReceipt(String raw, String n, RecordParser.Parsed p) {
        if ("FUEL".equals(p.documentKind) || "Yakıt".equals(p.type)) return true;
        boolean line = Pattern.compile("(?i)[0-9]{1,4}(?:[.,][0-9]{1,3})?\\s*(?:LT|LİTRE|LITRE|L)\\s*[X×*]\\s*[0-9]{1,5}(?:[.,][0-9]{1,3})?").matcher(raw).find();
        if (line) return true;
        int score = 0;
        if (containsAny(n, "benzin", "motorin", "dizel", "mazot", "lpg", "otogaz", "akaryakit", "eurodi", "euro diesel", "excelium")) score += 4;
        if (containsAny(n, " petrol", "petrol ", "istasyon", "pompa", "litre", " lt ")) score += 2;
        if (containsAny(n, "toplam", "topkdv", "k.karti", "b.karti", "fis no")) score += 2;
        if (p.quantity > 0) score += 3;
        return score >= 5;
    }

    private static void fillFuel(String raw, String n, RecordParser.Parsed p) {
        Matcher line = Pattern.compile("(?i)([0-9]{1,4}(?:[.,][0-9]{1,3})?)\\s*(?:LT|LİTRE|LITRE|L)\\s*[X×*]\\s*([0-9]{1,5}(?:[.,][0-9]{1,3})?)").matcher(raw);
        if (line.find()) {
            double q = decimal(line.group(1));
            double u = decimal(line.group(2));
            if (q > 0 && q < 1000) p.quantity = q;
            if (u > 0 && u < 10000) p.unitPrice = u;
        }

        double total = 0;
        for (String l : raw.split("\\r?\\n")) {
            String x = norm(l);
            if (!x.contains("toplam") || x.contains("topkdv") || x.contains("kdv")) continue;
            double v = lastMoney(l);
            if (v > total) total = v;
        }
        if (total > 0) p.amount = total;
        if (p.amount <= 0 && p.quantity > 0 && p.unitPrice > 0) p.amount = round2(p.quantity * p.unitPrice);

        String fuel = "";
        if (containsAny(n, "motorin", "dizel", "mazot", "eurodi", "euro diesel", "eurodiesel", "excelium")) fuel = "Dizel";
        else if (containsAny(n, "benzin", "kursunsuz", "95 oktan", "95 ron")) fuel = "Benzin";
        else if (containsAny(n, "lpg", "otogaz")) fuel = "LPG";
        else if (containsAny(n, "kwh", "elektrik", "sarj")) fuel = "Elektrik";
        if (!fuel.isEmpty()) p.fuelType = fuel;

        String vendor = findFuelVendor(raw, n);
        if (!vendor.isEmpty()) p.vendor = vendor;

        p.detailSummary = join(" • ",
                nonEmpty(p.vendor) ? p.vendor : "",
                nonEmpty(p.fuelType) ? p.fuelType : "",
                p.quantity > 0 ? trim(p.quantity) + ("Elektrik".equals(p.fuelType) ? " kWh" : " L") : "",
                p.unitPrice > 0 ? trim(p.unitPrice) + " TL/birim" : "",
                p.amount > 0 ? trim(p.amount) + " TL" : "");
    }

    private static String findFuelVendor(String raw, String n) {
        String[][] known = {
                {"shell", "Shell"}, {"opet", "Opet"}, {"petrol ofisi", "Petrol Ofisi"},
                {"totalenergies", "TotalEnergies"}, {"total energies", "TotalEnergies"}, {"bp", "BP"},
                {"aytemiz", "Aytemiz"}, {"alpet", "Alpet"}, {"turkiye petrolleri", "Türkiye Petrolleri"},
                {"kadoil", "Kadoil"}, {"sunpet", "Sunpet"}, {"socar", "SOCAR"}, {"lukoil", "Lukoil"}
        };
        for (String[] k : known) if (n.contains(k[0])) return k[1];
        int count = 0;
        for (String line : raw.split("\\r?\\n")) {
            if (++count > 10) break;
            String clean = line.replaceAll("\\s{2,}", " ").trim().replaceFirst("^\\d{1,3}\\s+", "");
            String q = norm(clean);
            if (clean.length() < 4 || clean.length() > 55) continue;
            if (containsAny(q, "petrol", "akaryakit", "istasyon") && !containsAny(q, "lisans", "adres", "cad", "sok", "mersis")) return clean;
        }
        return "";
    }

    private static boolean looksLikeVehiclePolicy(String n, RecordParser.Parsed p) {
        if (nonEmpty(p.plate)) return true;
        boolean vehicle = containsAny(n,
                "zorunlu trafik", "trafik sigort", "zorunlu mali sorumluluk", "kasko",
                "arac bilg", "arac mark", "arac model", "plaka", "tescil", "ruhsat",
                "sasi", "şasi", "vin", "motor no", "motor numarasi");
        if (vehicle) return true;
        boolean property = containsAny(n,
                "sari panjur", "deprem bina", "esya teminat", "dask", "bina brut", "risk adresi",
                "yapi tarzi", "deprem risk", "konut", "isyeri", "bina ", " esya ");
        return !property && false;
    }

    private static void clearNonVehiclePolicy(RecordParser.Parsed p) {
        p.type = "";
        p.documentKind = "NON_VEHICLE_INSURANCE";
        p.vendor = "";
        p.insuranceSubtype = "";
        p.policyNo = "";
        p.policyStartDate = "";
        p.policyEndDate = "";
        p.date = "";
        p.nextDate = "";
        p.amount = 0;
        p.km = 0;
        p.detailSummary = "";
        p.confidence = 0;
    }

    private static int strictExplicitKm(String raw) {
        if (raw == null || raw.trim().isEmpty()) return 0;
        String upper = raw.toUpperCase(TR);
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?is)(?:\\bODO\\b|ODOMETER|ODOMETRE|TOPLAM\\s*KM|KİLOMETRE|KILOMETRE|ARAÇ\\s*KM|ARAC\\s*KM)\\s*(?:DE|DA)?\\s*[:=.-]?\\s*([0-9][0-9 .]{2,10})"),
                Pattern.compile("(?is)\\bKM\\b\\s*(?:DE|DA)?\\s*[:=.-]?\\s*([0-9][0-9 .]{2,10})"),
                Pattern.compile("(?is)([0-9][0-9 .]{2,10})\\s*(?:KİLOMETRE|KILOMETRE|KM)\\b(?!\\s*/\\s*H)")
        };
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(upper);
            while (m.find()) {
                String whole = m.group();
                if (whole.matches("(?is).*KM\\s*/\\s*H.*")) continue;
                int v = digits(m.group(1));
                if (v > 0 && v < 2_000_000 && !gauge(v)) return v;
            }
        }
        return 0;
    }

    private static boolean containsPartFinding(String s) {
        if (!nonEmpty(s)) return false;
        String n = norm(s);
        return containsAny(n, "kaput", "kapi", "camurluk", "tavan", "bagaj", "tampon", "sasi", "podye", "direk", "motor", "fren", "obd", "airbag", "suspansiyon");
    }

    private static String hintValue(String hints, String prefix) {
        for (String line : hints.split("\\r?\\n")) {
            String s = line.trim();
            if (s.startsWith(prefix)) return s.substring(prefix.length()).trim();
        }
        return "";
    }

    private static int hintInt(String hints, String prefix) {
        Pattern p = Pattern.compile("(?m)^" + Pattern.quote(prefix) + "\\s+([0-9]{4,7})\\s*$");
        Matcher m = p.matcher(hints);
        if (!m.find()) return 0;
        try { return Integer.parseInt(m.group(1)); } catch (Exception e) { return 0; }
    }

    private static int digits(String s) {
        try {
            String d = s == null ? "" : s.replaceAll("[^0-9]", "");
            if (d.isEmpty() || d.length() > 7) return 0;
            return Integer.parseInt(d);
        } catch (Exception e) { return 0; }
    }

    private static boolean gauge(int v) {
        int[] bad = {20,40,60,80,100,120,140,160,180,200,220,100120,120140,140160,160180,180200,200220,2040,4060,6080,80100};
        for (int x : bad) if (v == x) return true;
        return false;
    }

    private static double lastMoney(String line) {
        Matcher m = Pattern.compile("(?<!\\d)([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,3})|[0-9]+(?:[.,][0-9]{1,3})?)(?!\\d)").matcher(line);
        double last = 0;
        while (m.find()) {
            double v = money(m.group(1));
            if (v > 0) last = v;
        }
        return last;
    }

    private static double money(String token) {
        if (token == null) return 0;
        String s = token.trim().replace(" ", "");
        int comma = s.lastIndexOf(','), dot = s.lastIndexOf('.');
        try {
            if (comma >= 0 && dot >= 0) {
                if (comma > dot) s = s.replace(".", "").replace(',', '.');
                else s = s.replace(",", "");
            } else if (comma >= 0) s = s.replace('.', ' ').replace(" ", "").replace(',', '.');
            else if (dot >= 0 && s.length() - dot - 1 == 3 && s.indexOf('.') == dot) {
                // Yakıt birim fiyatı 41.900 gibi gelebileceği için 3 hanede ondalığı koru.
            }
            return Double.parseDouble(s);
        } catch (Exception e) { return 0; }
    }

    private static double decimal(String s) {
        try { return Double.parseDouble(s.trim().replace(',', '.')); }
        catch (Exception e) { return 0; }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private static String trim(double v) { if (Math.abs(v - Math.rint(v)) < 0.000001) return String.valueOf((long)Math.rint(v)); return String.format(Locale.US, "%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", ""); }

    private static boolean plausibleVendor(String s) {
        if (!nonEmpty(s)) return false;
        String v = s.trim();
        if (v.length() < 2 || v.length() > 55) return false;
        int letters = 0, digits = 0, weird = 0;
        for (char ch : v.toCharArray()) {
            if (Character.isLetter(ch)) letters++;
            else if (Character.isDigit(ch)) digits++;
            else if (!Character.isWhitespace(ch) && "&.-/'".indexOf(ch) < 0) weird++;
        }
        if (letters < 3 || weird > 2 || digits > letters) return false;
        String n = norm(v);
        if (containsAny(n, "bolic", "taoa", "vkale", "orli", "orijinal", "boyali", "degisen", "sok tak", "plastik")) return false;
        return true;
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(TR).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c');
    }

    private static boolean containsAny(String s, String... keys) {
        for (String k : keys) if (s.contains(k)) return true;
        return false;
    }
    private static boolean nonEmpty(String s) { return s != null && !s.trim().isEmpty(); }
    private static String join(String sep, String... items) {
        List<String> out = new ArrayList<>();
        for (String s : items) if (nonEmpty(s)) out.add(s.trim());
        StringBuilder b = new StringBuilder();
        for (String s : out) { if (b.length() > 0) b.append(sep); b.append(s); }
        return b.toString();
    }
}
'''

(APP / "VisualDocumentAnalyzer.java").write_text(visual, encoding="utf-8")

# RecordParser: hız göstergesindeki "140 km/h" gibi değerleri kilometre sayacı sanma;
# konuşmada "kilometre de 235 bin 487" biçimini de destekle.
record = APP / "RecordParser.java"
text = record.read_text(encoding="utf-8")
start = text.index("    private static int extractKm(String raw, String lower) {")
end = text.index("    private static double extractAmount(String raw, String lower) {", start)
new_km = r'''    private static int extractKm(String raw, String lower) {
        String n = lower;

        Matcher mixedPrefix = Pattern.compile("(?:kilometre(?:m)?|odometre|odo|\\bkm\\b)\\s*(?:de|da)?\\s*[:=-]?\\s*(\\d{1,3})\\s*bin(?:\\s*(\\d{1,3}))?").matcher(n);
        if (mixedPrefix.find()) {
            long value = safeLong(mixedPrefix.group(1)) * 1000L + (mixedPrefix.group(2) == null ? 0 : safeLong(mixedPrefix.group(2)));
            if (value > 0 && value < 2_000_000) return (int) value;
        }
        Matcher mixedSuffix = Pattern.compile("(\\d{1,3})\\s*bin(?:\\s*(\\d{1,3}))?\\s*(?:km|kilometre)").matcher(n);
        if (mixedSuffix.find()) {
            long value = safeLong(mixedSuffix.group(1)) * 1000L + (mixedSuffix.group(2) == null ? 0 : safeLong(mixedSuffix.group(2)));
            if (value > 0 && value < 2_000_000) return (int) value;
        }

        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?:kilometre(?:m)?|odometre|odo|toplam\\s*km|\\bkm\\b)\\s*(?:de|da)?\\s*[:=-]?\\s*([0-9][0-9 .]{2,12})"),
                Pattern.compile("([0-9][0-9 .]{2,12})\\s*(?:km|kilometre)\\b(?!\\s*/\\s*h)")
        };
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(n);
            while (m.find()) {
                if (m.group().matches("(?is).*km\\s*/\\s*h.*")) continue;
                long value = parseWholeNumber(m.group(1));
                if (value > 0 && value < 2_000_000) return (int) value;
            }
        }

        Matcher words = Pattern.compile("([a-zçğıöşü ]{2,60})\\s+(?:km|kilometre)\\b(?!\\s*/\\s*h)").matcher(raw.toLowerCase(TR));
        while (words.find()) {
            long value = parseTurkishNumber(words.group(1));
            if (value > 0 && value < 2_000_000) return (int) value;
        }
        return 0;
    }

'''
text = text[:start] + new_km + text[end:]
record.write_text(text, encoding="utf-8")

# Kaporta şemasında renkleri daha dar panel bölgeleriyle eşleştir; beyaz paneli de
# güvenli olduğunda Orijinal olarak raporla.
image = APP / "ImageDocumentAnalyzer.java"
it = image.read_text(encoding="utf-8")
old_zones = '''        Zone[] zones = new Zone[]{
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
        };'''
new_zones = '''        Zone[] zones = new Zone[]{
                new Zone("Sol ön çamurluk", 0.18f, 0.31f, 0.23f, 0.41f),
                new Zone("Kaput", 0.38f, 0.62f, 0.22f, 0.40f),
                new Zone("Sağ ön çamurluk", 0.69f, 0.82f, 0.23f, 0.41f),
                new Zone("Sol ön kapı", 0.21f, 0.36f, 0.40f, 0.55f),
                new Zone("Sağ ön kapı", 0.64f, 0.79f, 0.40f, 0.55f),
                new Zone("Sol arka kapı", 0.21f, 0.36f, 0.55f, 0.69f),
                new Zone("Sağ arka kapı", 0.64f, 0.79f, 0.55f, 0.69f),
                new Zone("Sol arka çamurluk", 0.18f, 0.33f, 0.68f, 0.84f),
                new Zone("Sağ arka çamurluk", 0.67f, 0.82f, 0.68f, 0.84f),
                new Zone("Tavan", 0.40f, 0.60f, 0.42f, 0.65f),
                new Zone("Bagaj / arka kapak", 0.39f, 0.61f, 0.69f, 0.82f)
        };'''
if old_zones not in it:
    raise SystemExit("ImageDocumentAnalyzer zone block not found")
it = it.replace(old_zones, new_zones)
it = it.replace("int total = 0, red = 0, yellow = 0, blue = 0, gray = 0;", "int total = 0, red = 0, yellow = 0, blue = 0, gray = 0, white = 0;")
it = it.replace("else if (sat < 0.12f && val > 0.28f && val < 0.82f) gray++;", "else if (sat < 0.12f && val > 0.28f && val < 0.82f) gray++;\n                else if (sat < 0.15f && val >= 0.82f) white++;")
it = it.replace("double gr = gray / (double) total;\n\n        double best", "double gr = gray / (double) total;\n        double wr = white / (double) total;\n\n        double best")
it = it.replace("if (best >= 0.075) {", "if (best >= 0.09) {")
it = it.replace('''        if (gr >= 0.34) return "Sök-tak";
        return "";''', '''        if (gr >= 0.40) return "Sök-tak";
        if (wr >= 0.55 && best < 0.08 && gr < 0.28) return "Orijinal";
        return "";''')
image.write_text(it, encoding="utf-8")

# Ses oturumu kısa sessizlikte kapanmasın; sağlayıcı hata döndürse bile son partial
# cümleyi kaybetmeden yeni segment dinlemeye devam etsin.
voice = APP / "VoiceCaptureActivity.java"
vt = voice.read_text(encoding="utf-8")
vt = vt.replace("EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6500L", "EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 8500L")
vt = vt.replace("EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4500L", "EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6500L")
old_error = '''        // NO_MATCH ve SPEECH_TIMEOUT normal duraksama gibi ele alınır.
        // RECOGNIZER_BUSY/CLIENT için biraz daha uzun bekleyip yeniden başlarız.
        long delay = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) ? 900L : RESTART_DELAY_MS;
        statusView.setText("Dinlemeye devam ediyorum…");
        scheduleRestart(delay);'''
new_error = '''        // Sağlayıcı sessizlikte segmenti hata ile kapatırsa partial sonucu kaybetme.
        if (!clean(lastPartial).isEmpty()) {
            commitSegment(lastPartial);
            lastPartial = "";
        }
        // NO_MATCH ve SPEECH_TIMEOUT normal duraksama gibi ele alınır.
        // RECOGNIZER_BUSY/CLIENT için biraz daha uzun bekleyip yeniden başlarız.
        long delay = (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || error == SpeechRecognizer.ERROR_CLIENT) ? 900L : RESTART_DELAY_MS;
        statusView.setText("Dinlemeye devam ediyorum… Bitir ve kullan diyene kadar kapanmayacağım.");
        scheduleRestart(delay);'''
if old_error not in vt:
    raise SystemExit("Voice error block not found")
vt = vt.replace(old_error, new_error)
voice.write_text(vt, encoding="utf-8")

# Asistan, araç dışı poliçe gibi bilinçli reddedilen içerikte gerçek nedeni göstersin.
main = APP / "NextMainActivity.java"
mt = main.read_text(encoding="utf-8")
old_empty = '''        if (detected.isEmpty()) {
            if (assistantStatus != null) assistantStatus.setText("Kayıt türü belirlenemedi. Daha net söyle veya farklı bir fotoğraf dene.");
            toast("Kayıt türü belirlenemedi");
            return;
        }'''
new_empty = '''        if (detected.isEmpty()) {
            String reason = (parsed.warnings != null && !parsed.warnings.isEmpty())
                    ? parsed.warnings.get(0)
                    : "Kayıt türü belirlenemedi. Daha net söyle veya farklı bir fotoğraf dene.";
            if (assistantStatus != null) assistantStatus.setText(reason);
            toast(reason);
            return;
        }'''
if old_empty not in mt:
    raise SystemExit("Assistant empty block not found")
mt = mt.replace(old_empty, new_empty)
main.write_text(mt, encoding="utf-8")

# Sürüm
build = ROOT / "app/build.gradle"
bt = build.read_text(encoding="utf-8")
bt = re.sub(r"versionCode\s+\d+", "versionCode 20", bt)
bt = re.sub(r"versionName\s+'[^']+'", "versionName '0.16-reliable-input'", bt)
if "v0.16 reliable input build trigger" not in bt:
    bt += "\n// v0.16 reliable input build trigger\n"
build.write_text(bt, encoding="utf-8")

print("v0.16 reliable input upgrade applied")
