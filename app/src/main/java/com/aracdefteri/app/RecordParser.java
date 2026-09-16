package com.aracdefteri.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ortak akıllı giriş ayrıştırıcısı.
 * OCR, ses veya ileride serbest metin girişinden gelen metni aynı kurallarla işler.
 * Ham metni veritabanına kaydetmez; yalnızca gerekli alanları üretir.
 */
public final class RecordParser {
    private static final Locale TR = new Locale("tr", "TR");

    private RecordParser() { }

    public static final class Parsed {
        public String type = "";
        public String date = "";
        public String vendor = "";
        public String fuelType = "";
        public String maintenanceSubtype = "";
        public String insuranceSubtype = "";
        public double amount = 0;
        public double quantity = 0;
        public double unitPrice = 0;
        public int km = 0;

        // Belge türünden bağımsız ortak alanlar.
        public String documentKind = "";
        public String plate = "";
        public String policyNo = "";
        public String policyStartDate = "";
        public String policyEndDate = "";
        public String inspectionResult = "";
        public String nextDate = "";
        public String detailSummary = "";
        public String expertiseSubtype = "";
        public String taxSubtype = "";
        public String paymentPeriod = "";
        public int confidence = 0;
        public final List<String> warnings = new ArrayList<>();

        public final Set<String> maintenanceParts = new LinkedHashSet<>();
    }

    public static Parsed fromText(String text) {
        Parsed p = new Parsed();
        if (text == null) return p;
        String raw = text.trim();
        if (raw.isEmpty()) return p;
        String lower = normalize(raw);

        p.type = detectType(lower);
        p.date = extractDate(raw, lower);
        p.km = extractKm(raw, lower);
        p.amount = extractAmount(raw, lower);
        p.quantity = extractQuantity(raw, lower);
        p.unitPrice = extractUnitPrice(raw, lower);
        p.fuelType = detectFuelType(lower);
        p.vendor = detectVendor(raw, lower);
        p.maintenanceSubtype = detectMaintenanceSubtype(lower);
        p.insuranceSubtype = detectInsuranceSubtype(lower);
        detectMaintenanceParts(lower, p.maintenanceParts);

        // Farklı fiş/poliçe/muayene/ekspertiz şablonları için belgeye özel ikinci katman.
        SmartDocumentAnalyzer.enrich(raw, p);

        // Matematiksel tutarlılık: toplam yok ama miktar ve birim fiyat güvenilir görünüyorsa öneri üret.
        if (p.amount <= 0 && p.quantity > 0 && p.unitPrice > 0) {
            double calculated = p.quantity * p.unitPrice;
            if (calculated > 1 && calculated < 1_000_000) p.amount = round2(calculated);
        }
        return p;
    }

    private static String normalize(String s) {
        return s.toLowerCase(TR)
                .replace('ı', 'i')
                .replace('ş', 's')
                .replace('ğ', 'g')
                .replace('ü', 'u')
                .replace('ö', 'o')
                .replace('ç', 'c')
                .replace('’', '\'');
    }

    private static String detectType(String t) {
        if (containsAny(t, "benzin", "motorin", "mazot", "dizel", "lpg", "otogaz", "akaryakit", "yakit", "kwh", "sarj")) return "Yakıt";
        if (containsAny(t, "bakim", "motor yagi", "yag degis", "filtre", "servis bakimi", "periyodik")) return "Bakım";
        if (containsAny(t, "kasko", "trafik sigort", "police", "sigorta")) return "Sigorta/Kasko";
        if (containsAny(t, "mtv", "motorlu tasitlar vergisi", "vergi odeme")) return "Vergi";
        if (containsAny(t, "muayene", "tuvturk", "kusurlu")) return "Muayene";
        if (containsAny(t, "hasar", "kaza", "gocuk", "kaporta onar", "cam hasar")) return "Hasar";
        if (containsAny(t, "ekspertiz", "expertiz", "oto ekspertiz")) return "Ekspertiz";
        return "";
    }

    private static String detectFuelType(String t) {
        if (containsAny(t, "motorin", "mazot", "dizel")) return "Dizel";
        if (containsAny(t, "lpg", "otogaz")) return "LPG";
        if (containsAny(t, "kwh", "elektrik", "sarj")) return "Elektrik";
        if (containsAny(t, "cng", "dogalgaz")) return "CNG";
        if (t.contains("benzin")) return "Benzin";
        return "";
    }

    private static String detectMaintenanceSubtype(String t) {
        if (containsAny(t, "motor yagi", "yag filtresi", "yag degis")) return "Yağ bakımı";
        if (containsAny(t, "fren balata", "fren disk", "fren")) return "Fren";
        if (containsAny(t, "lastik")) return "Lastik";
        if (containsAny(t, "aku")) return "Akü";
        if (containsAny(t, "klima")) return "Klima";
        if (containsAny(t, "sanziman")) return "Şanzıman";
        if (containsAny(t, "elektrik", "alternator", "mars")) return "Elektrik";
        if (containsAny(t, "kaporta", "boya")) return "Kaporta/Boya";
        if (containsAny(t, "periyodik")) return "Periyodik bakım";
        if (containsAny(t, "motor")) return "Motor";
        if (containsAny(t, "bakim", "servis")) return "Genel bakım";
        return "";
    }

    private static String detectInsuranceSubtype(String t) {
        if (t.contains("kasko")) return "Kasko";
        if (containsAny(t, "zorunlu trafik", "trafik sigort")) return "Zorunlu trafik sigortası";
        return "";
    }

    private static void detectMaintenanceParts(String t, Set<String> out) {
        addIf(t, out, "Motor yağı", "motor yagi", "motor yag");
        addIf(t, out, "Yağ filtresi", "yag filtresi");
        addIf(t, out, "Hava filtresi", "hava filtresi");
        addIf(t, out, "Polen filtresi", "polen filtresi", "kabin filtresi");
        addIf(t, out, "Yakıt filtresi", "yakit filtresi", "mazot filtresi");
        addIf(t, out, "Fren balatası", "fren balata", "balata");
        addIf(t, out, "Fren diski", "fren disk");
        addIf(t, out, "Buji", "buji");
        addIf(t, out, "Akü", "aku");
        addIf(t, out, "Triger", "triger", "zamanlama kayisi");
        addIf(t, out, "Lastik", "lastik");
        addIf(t, out, "Antifriz", "antifriz", "sogutma sivisi");
        addIf(t, out, "Şanzıman yağı", "sanziman yagi", "sanziman yag");
    }

    private static void addIf(String t, Set<String> out, String label, String... keys) {
        for (String k : keys) if (t.contains(k)) { out.add(label); return; }
    }

    private static String extractDate(String raw, String lower) {
        Calendar cal = Calendar.getInstance();
        if (containsWord(lower, "bugun")) return formatDate(cal.getTime());
        if (containsWord(lower, "dun")) {
            cal.add(Calendar.DAY_OF_YEAR, -1);
            return formatDate(cal.getTime());
        }

        Matcher numeric = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})(?!\\d)").matcher(raw);
        while (numeric.find()) {
            int d = safeInt(numeric.group(1));
            int m = safeInt(numeric.group(2));
            int y = safeInt(numeric.group(3));
            if (y < 100) y += 2000;
            if (validDate(d, m, y)) return String.format(Locale.US, "%02d.%02d.%04d", d, m, y);
        }

        String months = "ocak|subat|mart|nisan|mayis|haziran|temmuz|agustos|eylul|ekim|kasim|aralik";
        Matcher named = Pattern.compile("(?<!\\d)(\\d{1,2})\\s+(" + months + ")(?:\\s+(\\d{4}))?").matcher(lower);
        if (named.find()) {
            int d = safeInt(named.group(1));
            int m = monthNumber(named.group(2));
            int y = named.group(3) == null ? cal.get(Calendar.YEAR) : safeInt(named.group(3));
            if (validDate(d, m, y)) return String.format(Locale.US, "%02d.%02d.%04d", d, m, y);
        }
        return "";
    }

    private static int extractKm(String raw, String lower) {
        String n = lower;
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?:km|kilometre|odometre|odo)\\s*[:=-]?\\s*([0-9][0-9 .]{2,12})"),
                Pattern.compile("([0-9][0-9 .]{2,12})\\s*(?:km|kilometre)"),
                Pattern.compile("([0-9]{1,4})\\s*bin\\s*(?:km|kilometre)")
        };
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(n);
            while (m.find()) {
                String token = m.group(1);
                long value;
                if (m.group().contains("bin") && token.matches("\\d{1,4}")) value = safeLong(token) * 1000L;
                else value = parseWholeNumber(token);
                if (value >= 0 && value < 2_000_000) return (int) value;
            }
        }

        // Türkçe yazıyla söylenen kilometre: "yuz yirmi bes bin kilometre"
        Matcher words = Pattern.compile("([a-zçğıöşü ]{2,60})\\s+(?:km|kilometre)").matcher(raw.toLowerCase(TR));
        while (words.find()) {
            long value = parseTurkishNumber(words.group(1));
            if (value > 0 && value < 2_000_000) return (int) value;
        }
        return 0;
    }

    private static double extractAmount(String raw, String lower) {
        String[] lines = raw.split("\\r?\\n");
        double best = 0;
        for (String line : lines) {
            String l = normalize(line);
            boolean totalLine = containsAny(l, "genel toplam", "odenecek", "toplam tutar", "toplam", "tutar", "nakit", "kredi karti");
            boolean reject = containsAny(l, "kdv", "vergi orani", "indirim", "birim fiyat", "tl/lt", "tl/l", "litre fiyati");
            if (!totalLine || reject) continue;
            double candidate = lastMoneyNumber(line);
            if (candidate > best && candidate < 10_000_000) best = candidate;
        }
        if (best > 0) return best;

        Matcher currency = Pattern.compile("([0-9][0-9 .]*(?:[,][0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?)\\s*(?:tl|₺|try|lira)\\b", Pattern.CASE_INSENSITIVE).matcher(raw);
        while (currency.find()) {
            double value = parseMoney(currency.group(1));
            if (value > best && value < 10_000_000) best = value;
        }
        if (best > 0) return best;

        // Türkçe yazıyla söylenen tutar: "bes yuz lira"
        Matcher words = Pattern.compile("([a-zçğıöşü ]{2,60})\\s+(?:tl|lira)", Pattern.CASE_INSENSITIVE).matcher(raw.toLowerCase(TR));
        while (words.find()) {
            long value = parseTurkishNumber(words.group(1));
            if (value > 0 && value < 10_000_000) return value;
        }
        return 0;
    }

    private static double extractQuantity(String raw, String lower) {
        Matcher m = Pattern.compile("([0-9]+(?:[.,][0-9]{1,3})?)\\s*(?:litre|lt\\.?|l\\b|kwh)", Pattern.CASE_INSENSITIVE).matcher(lower);
        double best = 0;
        while (m.find()) {
            double v = parseDecimal(m.group(1));
            if (v > best && v < 2000) best = v;
        }
        return best;
    }

    private static double extractUnitPrice(String raw, String lower) {
        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String l = normalize(line);
            if (containsAny(l, "birim fiyat", "litre fiyati", "tl/lt", "tl/l", "tl/kwh")) {
                double v = lastMoneyNumber(line);
                if (v > 0 && v < 100_000) return v;
            }
        }
        return 0;
    }

    private static String detectVendor(String raw, String lower) {
        String[][] known = new String[][]{
                {"shell", "Shell"}, {"opet", "Opet"}, {"petrol ofisi", "Petrol Ofisi"},
                {"totalenergies", "TotalEnergies"}, {"total energies", "TotalEnergies"}, {"bp", "BP"},
                {"aytemiz", "Aytemiz"}, {"alpet", "Alpet"}, {"turkiye petrolleri", "Türkiye Petrolleri"},
                {"kadoil", "Kadoil"}, {"sunpet", "Sunpet"}, {"socar", "SOCAR"},
                {"allianz", "Allianz"}, {"anadolu sigorta", "Anadolu Sigorta"}, {"turkiye sigorta", "Türkiye Sigorta"},
                {"axa", "AXA"}, {"mapfre", "MAPFRE"}, {"hdi", "HDI"}, {"sompo", "Sompo"},
                {"quick sigorta", "Quick Sigorta"}, {"ray sigorta", "Ray Sigorta"}, {"zurich", "Zurich"},
                {"tuvturk", "TÜVTÜRK"}
        };
        for (String[] pair : known) if (lower.contains(pair[0])) return pair[1];

        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String v = line.trim();
            if (v.length() < 3 || v.length() > 48) continue;
            String n = normalize(v);
            // Bilinmeyen firmalarda yalnız şirket/istasyon çağrışımı açıkça varsa kabul et.
            if (!containsAny(n, "petrol", "akaryakit", "istasyon", "enerji", "sigorta", "servis", "ekspertiz", "otomotiv")) continue;
            if (containsAny(n, "fis", "fatura", "tarih", "saat", "toplam", "kdv", "vergi", "tutar", "pos", "terminal", "plaka", "sase", "vin", "musteri", "telefon", "tel:")) continue;
            int letters = 0, digits = 0, bad = 0;
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (Character.isLetter(c)) letters++;
                else if (Character.isDigit(c)) digits++;
                else if (!Character.isWhitespace(c) && "&.-/'".indexOf(c) < 0) bad++;
            }
            if (letters >= 5 && letters >= digits * 3 && bad <= 1) return tidyVendor(v);
        }
        return "";
    }

    private static String tidyVendor(String v) {
        String s = v.replaceAll("\\s{2,}", " ").trim();
        if (s.equals(s.toUpperCase(TR)) && s.length() > 3) {
            String lower = s.toLowerCase(TR);
            return lower.substring(0, 1).toUpperCase(TR) + lower.substring(1);
        }
        return s;
    }

    private static double lastMoneyNumber(String line) {
        Matcher m = Pattern.compile("(?<!\\d)([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,2})|[0-9]+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:[. ][0-9]{3})+)(?!\\d)").matcher(line);
        double last = 0;
        while (m.find()) {
            double v = parseMoney(m.group(1));
            if (v > 0) last = v;
        }
        return last;
    }

    private static double parseMoney(String token) {
        if (token == null) return 0;
        String s = token.trim().replace(" ", "");
        int comma = s.lastIndexOf(',');
        int dot = s.lastIndexOf('.');
        try {
            if (comma >= 0 && dot >= 0) {
                if (comma > dot) s = s.replace(".", "").replace(',', '.');
                else s = s.replace(",", "");
            } else if (comma >= 0) {
                s = s.replace('.', ' ').replace(" ", "").replace(',', '.');
            } else if (dot >= 0) {
                int after = s.length() - dot - 1;
                if (after == 3) s = s.replace(".", "");
            }
            return Double.parseDouble(s);
        } catch (Exception ignored) { return 0; }
    }

    private static double parseDecimal(String token) {
        if (token == null) return 0;
        try { return Double.parseDouble(token.replace(',', '.')); }
        catch (Exception ignored) { return 0; }
    }

    private static long parseWholeNumber(String token) {
        if (token == null) return 0;
        String digits = token.replaceAll("[^0-9]", "");
        return safeLong(digits);
    }

    private static long parseTurkishNumber(String phrase) {
        if (phrase == null) return 0;
        String normalized = normalize(phrase).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return 0;
        long total = 0, current = 0;
        boolean seen = false;
        for (String token : normalized.split(" ")) {
            if (token.matches("\\d+")) {
                current += safeLong(token);
                seen = true;
                continue;
            }
            int small = smallNumber(token);
            if (small >= 0) {
                current += small;
                seen = true;
            } else if ("yuz".equals(token)) {
                current = (current == 0 ? 1 : current) * 100;
                seen = true;
            } else if ("bin".equals(token)) {
                total += (current == 0 ? 1 : current) * 1000;
                current = 0;
                seen = true;
            } else if ("milyon".equals(token)) {
                total += (current == 0 ? 1 : current) * 1_000_000;
                current = 0;
                seen = true;
            }
        }
        return seen ? total + current : 0;
    }

    private static int smallNumber(String s) {
        switch (s) {
            case "sifir": return 0; case "bir": return 1; case "iki": return 2; case "uc": return 3;
            case "dort": return 4; case "bes": return 5; case "alti": return 6; case "yedi": return 7;
            case "sekiz": return 8; case "dokuz": return 9; case "on": return 10; case "yirmi": return 20;
            case "otuz": return 30; case "kirk": return 40; case "elli": return 50; case "altmis": return 60;
            case "yetmis": return 70; case "seksen": return 80; case "doksan": return 90; default: return -1;
        }
    }

    private static int monthNumber(String s) {
        String[] months = {"ocak", "subat", "mart", "nisan", "mayis", "haziran", "temmuz", "agustos", "eylul", "ekim", "kasim", "aralik"};
        for (int i = 0; i < months.length; i++) if (months[i].equals(s)) return i + 1;
        return 0;
    }

    private static boolean validDate(int d, int m, int y) {
        if (y < 2000 || y > 2100 || m < 1 || m > 12 || d < 1 || d > 31) return false;
        Calendar c = Calendar.getInstance();
        c.setLenient(false);
        try { c.set(y, m - 1, d); c.getTime(); return true; }
        catch (Exception ignored) { return false; }
    }

    private static String formatDate(Date d) {
        return new SimpleDateFormat("dd.MM.yyyy", Locale.US).format(d);
    }

    private static boolean containsAny(String s, String... values) {
        for (String v : values) if (s.contains(v)) return true;
        return false;
    }

    private static boolean containsWord(String s, String word) {
        return Pattern.compile("(^|\\s)" + Pattern.quote(word) + "($|\\s)").matcher(s).find();
    }

    private static int safeInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return 0; }
    }

    private static long safeLong(String s) {
        try { return Long.parseLong(s); } catch (Exception ignored) { return 0; }
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}
