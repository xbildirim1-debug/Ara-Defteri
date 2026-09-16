package com.aracdefteri.app;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Şablona bağlı olmayan araç-belgesi zenginleştiricisi.
 * OCR/konuşma metninde önce belge türünü puanlar, sonra belgeye özel alanları çıkarır.
 * Kişisel/özel kimlik alanlarını (TC, VKN, VIN/şasi vb.) özet metnine taşımaz.
 */
public final class SmartDocumentAnalyzer {
    private static final Locale TR = new Locale("tr", "TR");

    private SmartDocumentAnalyzer() { }

    public static void enrich(String raw, RecordParser.Parsed p) {
        if (raw == null || p == null) return;
        String text = raw.trim();
        if (text.isEmpty()) return;

        String n = normalize(text);
        Scores scores = scoreDocument(n);
        String kind = scores.bestKind();
        p.documentKind = kind;
        p.confidence = scores.confidence();

        String plate = extractPlate(text);
        if (!plate.isEmpty()) p.plate = plate;

        int betterKm = extractBestKm(text);
        if (betterKm > 0) p.km = betterKm;

        // Fişlerde kilometre de bulunabilir. Litre/tutar/birim fiyat gibi güçlü yakıt
        // işaretleri varsa ODO sonucu yakıt kaydını ezmemeli.
        if ((kind == null || kind.isEmpty() || "ODOMETER".equals(kind)) && hasFuelEvidence(text, n, p)) {
            kind = "FUEL";
            p.documentKind = "FUEL";
            p.confidence = Math.max(p.confidence, 82);
        }

        if ("FUEL".equals(kind)) enrichFuel(text, n, p);
        else if ("INSURANCE".equals(kind)) enrichInsurance(text, n, p);
        else if ("INSPECTION".equals(kind)) enrichInspection(text, n, p);
        else if ("EXPERTISE".equals(kind)) enrichExpertise(text, n, p);
        else if ("TAX".equals(kind)) enrichTax(text, n, p);
        else if ("MAINTENANCE".equals(kind)) enrichMaintenance(text, n, p);
        else if ("DAMAGE".equals(kind)) enrichDamage(text, n, p);
        else if ("ODOMETER".equals(kind)) {
            if (p.km <= 0) p.warnings.add("Kilometre değeri güvenle ayırt edilemedi.");
        }

        if ("FUEL".equals(kind)) p.type = "Yakıt";
        else if ("INSURANCE".equals(kind)) p.type = "Sigorta/Kasko";
        else if ("INSPECTION".equals(kind)) p.type = "Muayene";
        else if ("EXPERTISE".equals(kind)) p.type = "Ekspertiz";
        else if ("TAX".equals(kind)) p.type = "Vergi";
        else if ("MAINTENANCE".equals(kind) && (p.type == null || p.type.isEmpty())) p.type = "Bakım";
        else if ("DAMAGE".equals(kind) && (p.type == null || p.type.isEmpty())) p.type = "Hasar";
    }

    private static boolean hasFuelEvidence(String raw, String n, RecordParser.Parsed p) {
        int score = 0;
        if (p.quantity > 0 && p.quantity < 1000) score += 5;
        if (p.unitPrice > 0 && p.unitPrice < 100000) score += 4;
        if (p.amount > 0 && p.amount < 10_000_000) score += 2;
        if (p.fuelType != null && !p.fuelType.isEmpty()) score += 3;
        if (!knownVendor(n).isEmpty()) score += 2;
        if (containsAny(n, "akaryakit", "motorin", "dizel", "benzin", "kursunsuz", "otogaz", "lpg", "pompa", "tabanca", "litre", "tl/lt", "tl/l")) score += 3;
        if (Pattern.compile("(?i)[0-9]{1,4}(?:[.,][0-9]{1,3})?\\s*(?:lt|l|litre)\\s*[x×*]\\s*[0-9]{1,5}(?:[.,][0-9]{1,3})?").matcher(raw).find()) score += 6;
        return score >= 6;
    }

    private static void enrichFuel(String raw, String n, RecordParser.Parsed p) {
        p.type = "Yakıt";
        String vendor = knownVendor(n);
        if (!vendor.isEmpty()) p.vendor = vendor;
        if (containsAny(n, "kursunsuz", "95 oktan", "95 ron", "benzin")) p.fuelType = "Benzin";
        else if (containsAny(n, "motorin", "mazot", "diesel", "dizel", "dieseltech", "pro diesel", "pro-diesel")) p.fuelType = "Dizel";
        else if (containsAny(n, "otogaz", "lpg")) p.fuelType = "LPG";
        else if (containsAny(n, "kwh", "elektrik", "sarj")) p.fuelType = "Elektrik";
        else if (containsAny(n, "cng", "dogalgaz")) p.fuelType = "CNG";

        Matcher line = Pattern.compile("(?i)([0-9]{1,4}(?:[.,][0-9]{1,3})?)\\s*(?:lt|l|litre)\\s*[x×*]\\s*([0-9]{1,5}(?:[.,][0-9]{1,3})?)").matcher(n);
        if (line.find()) {
            double q = dec(line.group(1));
            double u = dec(line.group(2));
            if (q > 0 && q < 1000) p.quantity = q;
            if (u > 0 && u < 100000) p.unitPrice = u;
        }
        double labelledQ = labelledDecimal(raw, new String[]{"miktar", "litre", "lt"});
        if (p.quantity <= 0 && labelledQ > 0 && labelledQ < 1000) p.quantity = labelledQ;
        double labelledU = labelledMoney(raw, new String[]{"birim fiyat", "lt fiyat", "litre fiyat", "fiyat/lt", "tl/lt", "tl/l"});
        if (p.unitPrice <= 0 && labelledU > 0) p.unitPrice = labelledU;
        double total = labelledMoney(raw, new String[]{"genel toplam", "odenecek", "toplam tutar", "toplam", "k.karti", "kredi karti", "nakit"});
        if (total > 0) p.amount = total;
        if (p.amount <= 0 && p.quantity > 0 && p.unitPrice > 0) p.amount = round2(p.quantity * p.unitPrice);
        String date = labelledDate(raw, new String[]{"islem tarihi", "tarih"});
        if (!date.isEmpty()) p.date = date;
        p.detailSummary = compactFuelSummary(p);
    }

    private static void enrichInsurance(String raw, String n, RecordParser.Parsed p) {
        p.type = "Sigorta/Kasko";
        if (containsAny(n, "kasko", "genisletilmis kasko", "kasko sigorta")) p.insuranceSubtype = "Kasko";
        else if (containsAny(n, "zorunlu mali sorumluluk", "zorunlu trafik", "trafik sigort")) p.insuranceSubtype = "Zorunlu trafik sigortası";
        String vendor = knownVendor(n);
        if (!vendor.isEmpty()) p.vendor = vendor;
        p.policyNo = extractLabelValue(raw, new String[]{"police/yenileme no", "police no", "police numarasi"}, "[A-Z0-9][A-Z0-9./_-]{3,40}");
        p.policyStartDate = labelledDate(raw, new String[]{"police baslama tarihi", "baslama tarihi", "sigorta baslangici", "sigorta baslangic"});
        p.policyEndDate = labelledDate(raw, new String[]{"police bitis tarihi", "bitis tarihi", "sigorta sonu", "sigorta bitis"});
        if (!p.policyStartDate.isEmpty()) p.date = p.policyStartDate;
        if (!p.policyEndDate.isEmpty()) p.nextDate = p.policyEndDate;
        double premium = labelledMoney(raw, new String[]{"odenecek tutar", "odenecek yekun", "brut prim", "toplam prim", "net prim", "prim"});
        if (premium > 0) p.amount = premium;
        p.detailSummary = joinNonEmpty(" • ", p.vendor, p.policyNo.isEmpty() ? "" : "Poliçe: " + p.policyNo, p.policyStartDate.isEmpty() ? "" : "Başlangıç: " + p.policyStartDate, p.policyEndDate.isEmpty() ? "" : "Bitiş: " + p.policyEndDate, p.plate.isEmpty() ? "" : "Plaka: " + p.plate);
    }

    private static void enrichInspection(String raw, String n, RecordParser.Parsed p) {
        p.type = "Muayene";
        p.vendor = containsAny(n, "tuvturk", "tuv turk") ? "TÜVTÜRK" : p.vendor;
        String result = "";
        if (containsAny(n, "hafif kusurlu", "hafif kusur")) result = "Hafif kusurlu";
        if (containsAny(n, "agir kusurlu", "agir kusur")) result = "Ağır kusurlu";
        if (containsAny(n, "emniyetsiz")) result = "Emniyetsiz";
        if (containsAny(n, "kusursuz")) result = "Kusursuz";
        if (result.isEmpty() && containsAny(n, "muayene onaylandi", "muayene sonucu onaylandi")) result = "Kusursuz";
        p.inspectionResult = result;
        String inspectionDate = labelledDate(raw, new String[]{"muayene tarihi ve saati", "muayene tarihi", "islem tarihi"});
        if (!inspectionDate.isEmpty()) p.date = inspectionDate;
        String validity = labelledDate(raw, new String[]{"muayene gecerlilik tarihi", "gecerlilik tarihi", "muayene gecerlilik"});
        if (!validity.isEmpty()) p.nextDate = validity;
        double fee = labelledMoney(raw, new String[]{"muayene ucreti", "ucret", "odenecek bedel"});
        if (fee > 0) p.amount = fee;
        List<String> faults = collectRelevantLines(raw, new String[]{"kusur", "fren", "far", "lastik", "cam", "plaka", "emisyon", "silecek", "ayna", "korna", "direksiyon", "suspansiyon", "aks"}, 5);
        String faultText = faults.isEmpty() ? "" : join(faults, " | ");
        p.detailSummary = joinNonEmpty(" • ", p.inspectionResult.isEmpty() ? "" : "Sonuç: " + p.inspectionResult, p.nextDate.isEmpty() ? "" : "Geçerlilik: " + p.nextDate, p.km > 0 ? "KM: " + p.km : "", faultText);
    }

    private static void enrichExpertise(String raw, String n, RecordParser.Parsed p) {
        p.type = "Ekspertiz";
        String vendor = expertiseVendor(n);
        if (!vendor.isEmpty()) p.vendor = vendor;
        boolean body = containsAny(n, "kaporta", "boya", "mikron", "degisen", "orijinal", "orj", "podye", "sasi", "camurluk", "direk");
        boolean mech = containsAny(n, "motor", "mekanik", "dyno", "fren", "suspansiyon", "obd", "airbag", "yuruyen");
        if (body && !mech) p.expertiseSubtype = "Kaporta/boya";
        else if (mech && !body) p.expertiseSubtype = "Mekanik";
        else p.expertiseSubtype = "Genel ekspertiz";
        String reportDate = labelledDate(raw, new String[]{"rapor tarihi", "islem tarihi", "ekspertiz tarihi", "giris tarihi"});
        if (!reportDate.isEmpty()) p.date = reportDate;
        List<String> lines = collectRelevantLines(raw, new String[]{"orijinal", "orj", "boyali", "boya", "degisen", "degisim", "lokal", "duzelt", "gocuk", "hasarli", "ezik", "sasi", "podye", "direk", "dyno", "motor guc", "motor performans", "obd", "ariza", "fren", "suspansiyon", "airbag", "hava yastigi", "yag kac", "su kac", "lastik", "amortisor", "aks"}, 10);
        p.detailSummary = lines.isEmpty() ? "" : join(lines, " | ");
        double reportFee = labelledMoney(raw, new String[]{"ekspertiz ucreti", "paket ucreti", "odenen", "toplam"});
        if (reportFee > 0 && reportFee < 200000) p.amount = reportFee;
    }

    private static void enrichTax(String raw, String n, RecordParser.Parsed p) {
        p.type = "Vergi";
        if (containsAny(n, "ek mtv", "ek motorlu tasitlar")) p.taxSubtype = "Ek MTV";
        else if (containsAny(n, "mtv", "motorlu tasitlar vergisi")) p.taxSubtype = "MTV";
        else p.taxSubtype = "Diğer resmî ödeme";
        double amount = labelledMoney(raw, new String[]{"odenen tutar", "odenecek tutar", "toplam", "tutar", "tahakkuk"});
        if (amount > 0) p.amount = amount;
        String paid = labelledDate(raw, new String[]{"odeme tarihi", "tahsil tarihi", "tarih"});
        if (!paid.isEmpty()) p.date = paid;
        String due = labelledDate(raw, new String[]{"son odeme tarihi", "vade tarihi"});
        if (!due.isEmpty()) p.nextDate = due;
        if (containsAny(n, "1. taksit", "1.taksit", "birinci taksit")) p.paymentPeriod = "1. taksit";
        else if (containsAny(n, "2. taksit", "2.taksit", "ikinci taksit")) p.paymentPeriod = "2. taksit";
        p.detailSummary = joinNonEmpty(" • ", p.taxSubtype, p.paymentPeriod, p.nextDate.isEmpty() ? "" : "Son ödeme: " + p.nextDate);
    }

    private static void enrichMaintenance(String raw, String n, RecordParser.Parsed p) {
        if (p.type == null || p.type.isEmpty()) p.type = "Bakım";
        String vendor = serviceVendor(raw, n);
        if (!vendor.isEmpty()) p.vendor = vendor;
        List<String> ops = collectRelevantLines(raw, new String[]{"motor yagi", "yag filtresi", "hava filtresi", "polen filtresi", "yakit filtresi", "fren balata", "fren disk", "buji", "aku", "triger", "lastik", "antifriz", "sanziman", "servis", "bakim"}, 8);
        p.detailSummary = ops.isEmpty() ? "" : join(ops, " | ");
    }

    private static void enrichDamage(String raw, String n, RecordParser.Parsed p) {
        if (p.type == null || p.type.isEmpty()) p.type = "Hasar";
        List<String> lines = collectRelevantLines(raw, new String[]{"hasar", "kaza", "onarim", "kaporta", "boya", "cam", "tampon", "camurluk", "kapi", "degisen", "parca", "iscilik"}, 8);
        p.detailSummary = lines.isEmpty() ? "" : join(lines, " | ");
    }

    private static Scores scoreDocument(String n) {
        Scores s = new Scores();
        s.fuel += hits(n, 3, "akaryakit", "kursunsuz", "motorin", "otogaz", "epdk", "pompa", "lt x", "lt *", "litre");
        s.fuel += hits(n, 2, "shell", "opet", "petrol ofisi", "totalenergies", "aytemiz", "bp", "socar");
        s.insurance += hits(n, 4, "police no", "police/yenileme", "sigorta baslang", "sigorta bitis", "zorunlu mali sorumluluk");
        s.insurance += hits(n, 3, "kasko", "teminat", "sigorta ettiren", "sigortali");
        s.inspection += hits(n, 5, "arac muayene raporu", "muayene gecerlilik", "tuvturk");
        s.inspection += hits(n, 4, "hafif kusurlu", "agir kusurlu", "emniyetsiz", "muayene sonucu");
        s.expertise += hits(n, 4, "oto ekspertiz", "ekspertiz raporu", "kaporta boya", "dyno", "obd", "podye");
        s.expertise += hits(n, 2, "mikron", "orijinal", "orj", "degisen", "suspansiyon", "airbag", "motor testi");
        s.tax += hits(n, 5, "motorlu tasitlar vergisi", "mtv");
        s.tax += hits(n, 2, "gib", "tahakkuk", "vergi dairesi");
        s.maintenance += hits(n, 3, "periyodik bakim", "servis bakimi", "motor yagi", "yag filtresi", "fren balata", "triger");
        s.maintenance += hits(n, 1, "servis", "bakim", "filtre");
        s.damage += hits(n, 3, "hasar dosya", "kaza tutanagi", "onarim", "kaporta onarim", "cam hasar");
        s.odometer += hits(n, 5, "odometre", "odometer", "odo", "toplam km");
        s.odometer += hits(n, 1, "trip", "km");
        return s;
    }

    private static final class Scores {
        int fuel, insurance, inspection, expertise, tax, maintenance, damage, odometer;
        String bestKind() {
            int best = 0; String kind = "";
            int[][] values = {{fuel,1},{insurance,2},{inspection,3},{expertise,4},{tax,5},{maintenance,6},{damage,7},{odometer,8}};
            for (int[] v : values) if (v[0] > best) { best = v[0]; kind = switch (v[1]) { case 1 -> "FUEL"; case 2 -> "INSURANCE"; case 3 -> "INSPECTION"; case 4 -> "EXPERTISE"; case 5 -> "TAX"; case 6 -> "MAINTENANCE"; case 7 -> "DAMAGE"; case 8 -> "ODOMETER"; default -> ""; }; }
            return best < 4 ? "" : kind;
        }
        int confidence() {
            int best = Math.max(Math.max(Math.max(fuel, insurance), Math.max(inspection, expertise)), Math.max(Math.max(tax, maintenance), Math.max(damage, odometer)));
            if (best >= 14) return 95; if (best >= 10) return 88; if (best >= 7) return 78; if (best >= 4) return 65; return 0;
        }
    }

    private static int hits(String n, int weight, String... keys) { int score = 0; for (String k : keys) if (n.contains(k)) score += weight; return score; }

    private static int extractBestKm(String raw) {
        // Gösterge panelinde ODO etiketi varsa önce hemen yanındaki/altındaki toplam kilometreyi al.
        Matcher odo = Pattern.compile("(?is)(?:\\bODO\\b|ODOMETER|ODOMETRE|TOPLAM\\s*KM|KILOMETRE)\\s*[:=.-]?\\s*([0-9][0-9 .]{3,9})").matcher(raw);
        if (odo.find()) {
            String digits = odo.group(1).replaceAll("[^0-9]", "");
            try {
                int value = Integer.parseInt(digits);
                if (value >= 1000 && value < 2_000_000 && !looksLikeGaugeScale(value)) return value;
            } catch (Exception ignored) { }
        }

        String[] lines = raw.split("\\r?\\n"); int bestValue = 0; int bestScore = Integer.MIN_VALUE;
        Pattern number = Pattern.compile("(?<!\\d)(\\d{1,7}(?:[ .]\\d{3})*|\\d{1,7})(?!\\d)");
        for (String line : lines) {
            String n = normalize(line); Matcher m = number.matcher(line);
            while (m.find()) {
                String token = m.group(1); String digits = token.replaceAll("[^0-9]", "");
                if (digits.isEmpty() || digits.length() > 7) continue;
                int value; try { value = Integer.parseInt(digits); } catch (Exception e) { continue; }
                if (value < 1 || value >= 2_000_000) continue;
                int score = 0;
                if (containsAny(n, "odometre", "odometer", "odo", "toplam km", "kilometre")) score += 8;
                if (Pattern.compile("(?i)" + Pattern.quote(token) + "\\s*km\\b").matcher(line).find()) score += 6;
                if (value >= 1000 && value <= 999999) score += 3;
                if (digits.length() >= 5) score += 2;
                if (containsAny(n, "trip", "seyahat", "menzil", "range")) score -= 9;
                if (containsAny(n, "rpm", "devir", "km/h", "hiz")) score -= 5;
                if (looksLikeGaugeScale(value)) score -= 14;
                if (line.contains(",") || line.matches(".*\\d+\\.\\d+.*")) score -= 3;
                if (score > bestScore || (score == bestScore && value > bestValue)) { bestScore = score; bestValue = value; }
            }
        }
        return bestScore >= 5 ? bestValue : 0;
    }

    private static boolean looksLikeGaugeScale(int value) {
        int[] suspicious = {100120,120140,140160,160180,180200,200220,2040,4060,6080,80100};
        for (int v : suspicious) if (value == v) return true;
        return false;
    }

    private static String extractPlate(String raw) {
        String upper = raw.toUpperCase(TR).replace('\n', ' ');
        Matcher m = Pattern.compile("(?<![A-Z0-9])([0-8][0-9])\\s*[- ]?\\s*([A-ZÇĞİÖŞÜ]{1,3})\\s*[- ]?\\s*(\\d{2,4})(?![A-Z0-9])").matcher(upper);
        while (m.find()) { int city; try { city = Integer.parseInt(m.group(1)); } catch (Exception e) { continue; } if (city >= 1 && city <= 81) return m.group(1) + " " + m.group(2) + " " + m.group(3); }
        return "";
    }

    private static String labelledDate(String raw, String[] labels) {
        String[] lines = raw.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) { String n = normalize(lines[i]); for (String label : labels) { if (!n.contains(normalize(label))) continue; for (int j = i; j <= Math.min(i + 2, lines.length - 1); j++) { String d = firstDate(lines[j]); if (!d.isEmpty()) return d; } } }
        return "";
    }

    private static String firstDate(String text) {
        if (text == null) return "";
        Matcher dmy = Pattern.compile("(?<!\\d)(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})(?!\\d)").matcher(text);
        if (dmy.find()) { int d = i(dmy.group(1)), m = i(dmy.group(2)), y = i(dmy.group(3)); if (y < 100) y += 2000; if (validDate(d,m,y)) return String.format(Locale.US, "%02d.%02d.%04d", d,m,y); }
        Matcher ymd = Pattern.compile("(?<!\\d)(20\\d{2})[-/.](\\d{1,2})[-/.](\\d{1,2})(?!\\d)").matcher(text);
        if (ymd.find()) { int y = i(ymd.group(1)), m = i(ymd.group(2)), d = i(ymd.group(3)); if (validDate(d,m,y)) return String.format(Locale.US, "%02d.%02d.%04d", d,m,y); }
        return "";
    }

    private static boolean validDate(int d, int m, int y) { if (y < 2000 || y > 2100 || m < 1 || m > 12 || d < 1) return false; int[] days = {31,(y%400==0||(y%4==0&&y%100!=0))?29:28,31,30,31,30,31,31,30,31,30,31}; return d <= days[m-1]; }

    private static String extractLabelValue(String raw, String[] labels, String valueRegex) {
        String[] lines = raw.split("\\r?\\n");
        Pattern value = Pattern.compile(valueRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        for (int i = 0; i < lines.length; i++) {
            String normalizedLine = normalize(lines[i]);
            for (String label : labels) {
                String normalizedLabel = normalize(label);
                int at = normalizedLine.indexOf(normalizedLabel);
                if (at < 0) continue;

                // Etiketin kendisini değer sanmamak için yalnızca etiketin sağ tarafında ara.
                int tailStart = Math.min(lines[i].length(), at + label.length());
                String tail = lines[i].substring(tailStart).replaceFirst("^[\\s:=-]+", "");
                Matcher m = value.matcher(tail);
                if (m.find()) return m.group();

                // Bazı PDF/OCR şablonlarında değer bir alt satırdadır.
                if (i + 1 < lines.length) {
                    m = value.matcher(lines[i + 1].trim());
                    if (m.find()) return m.group();
                }
            }
        }
        return "";
    }

    private static double labelledMoney(String raw, String[] labels) {
        String[] lines=raw.split("\\r?\\n");
        for(String label:labels){String key=normalize(label); for(int i=0;i<lines.length;i++){String n=normalize(lines[i]); if(!n.contains(key)) continue; double v=lastNumber(lines[i]); if(v>0) return v; if(i+1<lines.length){v=lastNumber(lines[i+1]); if(v>0) return v;}}}
        return 0;
    }

    private static double labelledDecimal(String raw, String[] labels) {
        String[] lines=raw.split("\\r?\\n"); Pattern num=Pattern.compile("([0-9]{1,7}(?:[.,][0-9]{1,3})?)");
        for(String line:lines){String n=normalize(line); boolean ok=false; for(String label:labels) if(n.contains(normalize(label))){ok=true;break;} if(!ok) continue; Matcher m=num.matcher(line); while(m.find()){double v=dec(m.group(1)); if(v>0) return v;}}
        return 0;
    }

    private static double lastNumber(String line) { Matcher m=Pattern.compile("(?<!\\d)([0-9]{1,3}(?:[. ][0-9]{3})*(?:,[0-9]{1,2})|[0-9]+(?:[.,][0-9]{1,2})?|[0-9]{1,3}(?:[. ][0-9]{3})+)(?!\\d)").matcher(line); double last=0; while(m.find()){double v=money(m.group(1)); if(v>0) last=v;} return last; }

    private static double money(String token) { if(token==null)return 0; String s=token.trim().replace(" ",""); int comma=s.lastIndexOf(','),dot=s.lastIndexOf('.'); try{if(comma>=0&&dot>=0){if(comma>dot)s=s.replace(".","").replace(',','.');else s=s.replace(",","");}else if(comma>=0){s=s.replace(".","").replace(',','.');}else if(dot>=0){int after=s.length()-dot-1;if(after==3)s=s.replace(".","");}return Double.parseDouble(s);}catch(Exception e){return 0;} }
    private static double dec(String token) { if(token==null)return 0; try{return Double.parseDouble(token.replace(',','.'));}catch(Exception e){return 0;} }

    private static String knownVendor(String n) {
        String[][] known={{"shell","Shell"},{"opet","Opet"},{"petrol ofisi","Petrol Ofisi"},{"totalenergies","TotalEnergies"},{"total energies","TotalEnergies"},{"bp","BP"},{"aytemiz","Aytemiz"},{"alpet","Alpet"},{"turkiye petrolleri","Türkiye Petrolleri"},{"tp petrol","Türkiye Petrolleri"},{"kadoil","Kadoil"},{"sunpet","Sunpet"},{"socar","SOCAR"},{"lukoil","Lukoil"},{"moil","MOil"},{"termo","Termopet"},{"allianz","Allianz"},{"anadolu sigorta","Anadolu Sigorta"},{"turkiye sigorta","Türkiye Sigorta"},{"axa","AXA"},{"mapfre","MAPFRE"},{"hdi","HDI Sigorta"},{"sompo","Sompo"},{"quick sigorta","Quick Sigorta"},{"ray sigorta","Ray Sigorta"},{"zurich","Zurich"},{"neova","Neova"},{"unico","Unico Sigorta"},{"tuvturk","TÜVTÜRK"}};
        for(String[] k:known) if(n.contains(k[0])) return k[1]; return "";
    }

    private static String expertiseVendor(String n) { String[][] known={{"otorapor","OTORAPOR"},{"pilot garage","Pilot Garage"},{"pilotgarage","Pilot Garage"},{"umran","Ümran Oto Ekspertiz"},{"dynomoss","Dynomoss"},{"dynoplus","Dynoplus"},{"computest","Computest"},{"auto king","Auto King"},{"ayfa ekspertiz","Ayfa Ekspertiz"}}; for(String[] k:known) if(n.contains(k[0])) return k[1]; return ""; }

    private static String serviceVendor(String raw, String n) { String known=knownVendor(n); if(!known.isEmpty())return known; for(String line:raw.split("\\r?\\n")){String x=line.trim(); if(x.length()<3||x.length()>55)continue; String q=normalize(x); if(isSensitiveLine(q))continue; if(containsAny(q,"servis","otomotiv","oto ","motor "))return cleanLine(x);} return ""; }

    private static List<String> collectRelevantLines(String raw, String[] keywords, int limit) {
        Set<String> unique=new LinkedHashSet<>();
        for(String line:raw.split("\\r?\\n")){String clean=cleanLine(line); if(clean.length()<4||clean.length()>140)continue; String n=normalize(clean); if(isSensitiveLine(n))continue; boolean match=false; for(String k:keywords) if(n.contains(normalize(k))){match=true;break;} if(!match)continue; if(containsAny(n,"www.","http","telefon","tel:","adres","mersis","vergi dairesi"))continue; unique.add(clean); if(unique.size()>=limit)break;}
        return new ArrayList<>(unique);
    }

    private static boolean isSensitiveLine(String n) { return containsAny(n,"tc kimlik","t.c. kimlik","vkn","vergi no","vergi numarasi","sase no","sasi no","vin","motor no","iban","telefon","gsm","e-posta","eposta","adres"); }

    private static String compactFuelSummary(RecordParser.Parsed p) { return joinNonEmpty(" • ",p.vendor,p.fuelType,p.quantity>0?trim(p.quantity)+("Elektrik".equals(p.fuelType)?" kWh":" L"):"",p.unitPrice>0?trim(p.unitPrice)+" birim fiyat":"",p.amount>0?trim(p.amount)+" TL":"",p.plate.isEmpty()?"":"Plaka: "+p.plate); }
    private static String joinNonEmpty(String sep,String...parts){ArrayList<String>out=new ArrayList<>();for(String x:parts)if(x!=null&&!x.trim().isEmpty())out.add(x.trim());return join(out,sep);}
    private static String join(List<String>values,String sep){StringBuilder b=new StringBuilder();for(String s:values){if(b.length()>0)b.append(sep);b.append(s);}return b.toString();}
    private static String cleanLine(String s){return s==null?"":s.replaceAll("\\s{2,}"," ").trim();}
    private static String normalize(String s){if(s==null)return "";return s.toLowerCase(TR).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c').replace('’','\'');}
    private static boolean containsAny(String s,String...keys){for(String k:keys)if(s.contains(k))return true;return false;}
    private static int i(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
    private static double round2(double v){return Math.round(v*100.0)/100.0;}
    private static String trim(double v){if(Math.abs(v-Math.rint(v))<0.000001)return String.valueOf((long)Math.rint(v));String s=String.format(Locale.US,"%.3f",v);while(s.endsWith("0"))s=s.substring(0,s.length()-1);if(s.endsWith("."))s=s.substring(0,s.length()-1);return s.replace('.',',');}
}
