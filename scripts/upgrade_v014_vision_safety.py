from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
SMART = ROOT / 'app/src/main/java/com/aracdefteri/app/SmartDocumentAnalyzer.java'
OCR = ROOT / 'app/src/main/java/com/aracdefteri/app/ReceiptOcr.java'
ACT = ROOT / 'app/src/main/java/com/aracdefteri/app/NextMainActivity.java'
GRADLE = ROOT / 'app/build.gradle'


def replace_method(src: str, signature: str, replacement: str) -> str:
    start = src.find(signature)
    if start < 0:
        raise SystemExit(f'method signature not found: {signature}')
    brace = src.find('{', start)
    if brace < 0:
        raise SystemExit(f'opening brace not found: {signature}')
    depth = 0
    i = brace
    in_str = False
    esc = False
    in_char = False
    while i < len(src):
        c = src[i]
        if in_str:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == '"':
                in_str = False
        elif in_char:
            if esc:
                esc = False
            elif c == '\\':
                esc = True
            elif c == "'":
                in_char = False
        else:
            if c == '"':
                in_str = True
            elif c == "'":
                in_char = True
            elif c == '{':
                depth += 1
            elif c == '}':
                depth -= 1
                if depth == 0:
                    return src[:start] + replacement.rstrip() + '\n' + src[i+1:]
        i += 1
    raise SystemExit(f'closing brace not found: {signature}')


smart = SMART.read_text(encoding='utf-8')

smart = replace_method(smart, '    public static void enrich(String raw, RecordParser.Parsed p)', r'''    public static void enrich(String raw, RecordParser.Parsed p) {
        if (raw == null || p == null) return;
        String text = raw.trim();
        if (text.isEmpty()) return;

        String n = normalize(text);
        Scores scores = scoreDocument(n);
        String kind = scores.bestKind();

        // Görsel analiz katmanının açık işaretleri OCR sınıflandırmasından daha güvenilirdir.
        if (n.contains("belge_turu: ekspertiz")) kind = "EXPERTISE";
        else if (n.contains("belge_turu: odometer")) kind = "ODOMETER";

        // Yakıt fişinde KM bulunması fişi kilometre kaydına çevirmemeli.
        if ((kind == null || kind.isEmpty() || "ODOMETER".equals(kind)) && hasFuelEvidence(text, n, p)) {
            kind = "FUEL";
        }

        p.documentKind = kind == null ? "" : kind;
        p.confidence = scores.confidence();
        if (n.contains("gorsel_bulgular:")) p.confidence = Math.max(p.confidence, 94);
        else if (n.contains("belge_turu: odometer")) p.confidence = Math.max(p.confidence, 90);

        String plate = extractPlate(text);
        if (!plate.isEmpty()) p.plate = plate;

        // Belge fotoğraflarında etiketsiz 5-6 haneli her sayı KM değildir.
        // ODO görselinde daha serbest aday aranır; diğer belgelerde yalnız açık KM/ODO etiketi kabul edilir.
        if ("ODOMETER".equals(kind)) {
            int km = extractBestKm(text);
            p.km = km > 0 ? km : extractExplicitKm(text);
        } else {
            p.km = extractExplicitKm(text);
        }
        if ("EXPERTISE".equals(kind) && n.contains("gorsel_sema: kaporta")) p.km = 0;

        if ("FUEL".equals(kind)) enrichFuel(text, n, p);
        else if ("INSURANCE".equals(kind)) enrichInsurance(text, n, p);
        else if ("INSPECTION".equals(kind)) enrichInspection(text, n, p);
        else if ("EXPERTISE".equals(kind)) enrichExpertise(text, n, p);
        else if ("TAX".equals(kind)) enrichTax(text, n, p);
        else if ("MAINTENANCE".equals(kind)) enrichMaintenance(text, n, p);
        else if ("DAMAGE".equals(kind)) enrichDamage(text, n, p);
        else if ("ODOMETER".equals(kind)) {
            p.type = "";
            p.date = "";
            p.amount = 0;
            p.quantity = 0;
            p.unitPrice = 0;
            p.vendor = "";
            p.detailSummary = "";
            if (p.km <= 0) p.warnings.add("Kilometre değeri güvenle ayırt edilemedi.");
        }

        if ("FUEL".equals(kind)) p.type = "Yakıt";
        else if ("INSURANCE".equals(kind)) p.type = "Sigorta/Kasko";
        else if ("INSPECTION".equals(kind)) p.type = "Muayene";
        else if ("EXPERTISE".equals(kind)) p.type = "Ekspertiz";
        else if ("TAX".equals(kind)) p.type = "Vergi";
        else if ("MAINTENANCE".equals(kind) && (p.type == null || p.type.isEmpty())) p.type = "Bakım";
        else if ("DAMAGE".equals(kind) && (p.type == null || p.type.isEmpty())) p.type = "Hasar";
    }''')

smart = replace_method(smart, '    private static boolean hasFuelEvidence(String raw, String n, RecordParser.Parsed p)', r'''    private static boolean hasFuelEvidence(String raw, String n, RecordParser.Parsed p) {
        int score = 0;
        if (p.quantity > 0 && p.quantity < 1000) score += 5;
        if (p.unitPrice > 0 && p.unitPrice < 100000) score += 4;
        if (p.amount > 0 && p.amount < 10_000_000) score += 2;
        if (p.fuelType != null && !p.fuelType.isEmpty()) score += 4;
        if (!knownVendor(n).isEmpty() && isFuelVendor(n)) score += 5;
        if (isFuelVendor(n)) score += 4;
        if (containsAny(n, "akaryakit", "motorin", "dizel", "benzin", "kursunsuz", "otogaz", "lpg", "pompa", "tabanca", "litre", "tl/lt", "tl/l", "yakit")) score += 4;
        if (containsAny(n, "mali fis", "yazar kasa", "fis no", "toplam", "kdv") && containsAny(n, "lt", "litre", "motorin", "benzin", "lpg")) score += 3;
        if (Pattern.compile("(?i)[0-9]{1,4}(?:[.,][0-9]{1,3})?\\s*(?:lt|l|litre)\\s*[x×*]\\s*[0-9]{1,5}(?:[.,][0-9]{1,3})?").matcher(raw).find()) score += 7;
        return score >= 6;
    }''')

smart = replace_method(smart, '    private static void enrichFuel(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichFuel(String raw, String n, RecordParser.Parsed p) {
        p.type = "Yakıt";
        String vendor = knownVendor(n);
        if (vendor.isEmpty()) vendor = safeLabeledVendor(raw, new String[]{"istasyon", "firma", "unvan", "ünvan", "satici", "satıcı"}, "fuel");
        p.vendor = vendor;

        if (containsAny(n, "kursunsuz", "95 oktan", "95 ron", "benzin")) p.fuelType = "Benzin";
        else if (containsAny(n, "motorin", "mazot", "diesel", "dizel", "dieseltech", "pro diesel", "pro-diesel")) p.fuelType = "Dizel";
        else if (containsAny(n, "otogaz", "lpg")) p.fuelType = "LPG";
        else if (containsAny(n, "kwh", "elektrik", "sarj")) p.fuelType = "Elektrik";
        else if (containsAny(n, "cng", "dogalgaz")) p.fuelType = "CNG";

        double q = extractFuelQuantity(raw);
        if (q > 0) p.quantity = q;
        double unit = extractFuelUnitPrice(raw);
        if (unit > 0) p.unitPrice = unit;
        double total = labelledMoney(raw, new String[]{"genel toplam", "odenecek", "ödenecek", "toplam tutar", "satis tutari", "satış tutarı", "toplam", "total", "k.karti", "kredi karti", "nakit"});
        if (total > 0) p.amount = total;

        // İki alan güvenilir ise üçüncüyü matematiksel olarak tamamla.
        if (p.quantity > 0 && p.amount > 0 && p.unitPrice <= 0) {
            double u = p.amount / p.quantity;
            if (u > 0.5 && u < 5000) p.unitPrice = round2(u);
        }
        if (p.quantity > 0 && p.unitPrice > 0 && p.amount <= 0) {
            double a = p.quantity * p.unitPrice;
            if (a > 1 && a < 10_000_000) p.amount = round2(a);
        }
        if (p.amount > 0 && p.unitPrice > 0 && p.quantity <= 0) {
            double qq = p.amount / p.unitPrice;
            if (qq > 0.05 && qq < 2000) p.quantity = round3(qq);
        }

        String date = labelledDate(raw, new String[]{"islem tarihi", "işlem tarihi", "tarih", "date"});
        if (date.isEmpty()) date = firstDate(raw);
        p.date = date;
        p.detailSummary = compactFuelSummary(p);
        if (p.quantity <= 0) p.warnings.add("Yakıt miktarı okunamadı.");
        if (p.amount <= 0) p.warnings.add("Toplam tutar okunamadı.");
    }''')

smart = replace_method(smart, '    private static void enrichInsurance(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichInsurance(String raw, String n, RecordParser.Parsed p) {
        p.type = "Sigorta/Kasko";
        p.vendor = "";
        p.date = "";
        p.nextDate = "";
        p.amount = 0;
        p.policyNo = "";
        p.policyStartDate = "";
        p.policyEndDate = "";

        if (containsAny(n, "kasko", "genisletilmis kasko", "kasko sigorta")) p.insuranceSubtype = "Kasko";
        else if (containsAny(n, "zorunlu mali sorumluluk", "zorunlu trafik", "trafik sigort")) p.insuranceSubtype = "Zorunlu trafik sigortası";

        String vendor = knownVendor(n);
        if (vendor.isEmpty()) vendor = safeLabeledVendor(raw, new String[]{"sigorta sirketi", "sigorta şirketi", "sigortaci", "sigortacı", "sigortaci unvani", "şirket", "sirket"}, "insurance");
        p.vendor = vendor;

        p.policyNo = extractLabelValue(raw, new String[]{"police/yenileme no", "poliçe/yenileme no", "police no", "poliçe no", "police numarasi", "poliçe numarası"}, "[A-Z0-9][A-Z0-9./_-]{3,40}");
        p.policyStartDate = labelledDate(raw, new String[]{"police baslama tarihi", "poliçe başlama tarihi", "baslama tarihi", "başlama tarihi", "sigorta baslangici", "sigorta başlangıcı"});
        p.policyEndDate = labelledDate(raw, new String[]{"police bitis tarihi", "poliçe bitiş tarihi", "bitis tarihi", "bitiş tarihi", "sigorta sonu", "sigorta bitis"});
        if (!p.policyStartDate.isEmpty()) p.date = p.policyStartDate;
        if (!p.policyEndDate.isEmpty()) p.nextDate = p.policyEndDate;
        double premium = labelledMoney(raw, new String[]{"odenecek tutar", "ödenecek tutar", "odenecek yekun", "brut prim", "brüt prim", "toplam prim", "net prim", "prim"});
        if (premium > 0) p.amount = premium;
        p.detailSummary = joinNonEmpty(" • ", p.vendor, p.policyNo.isEmpty() ? "" : "Poliçe: " + p.policyNo, p.policyStartDate.isEmpty() ? "" : "Başlangıç: " + p.policyStartDate, p.policyEndDate.isEmpty() ? "" : "Bitiş: " + p.policyEndDate, p.plate.isEmpty() ? "" : "Plaka: " + p.plate);
    }''')

smart = replace_method(smart, '    private static void enrichInspection(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichInspection(String raw, String n, RecordParser.Parsed p) {
        p.type = "Muayene";
        p.vendor = containsAny(n, "tuvturk", "tuv turk") ? "TÜVTÜRK" : "";
        p.date = "";
        p.nextDate = "";
        p.amount = 0;
        String result = "";
        if (containsAny(n, "hafif kusurlu", "hafif kusur")) result = "Hafif kusurlu";
        if (containsAny(n, "agir kusurlu", "agir kusur")) result = "Ağır kusurlu";
        if (containsAny(n, "emniyetsiz")) result = "Emniyetsiz";
        if (containsAny(n, "kusursuz")) result = "Kusursuz";
        if (result.isEmpty() && containsAny(n, "muayene onaylandi", "muayene sonucu onaylandi")) result = "Kusursuz";
        p.inspectionResult = result;
        String inspectionDate = labelledDate(raw, new String[]{"muayene tarihi ve saati", "muayene tarihi", "islem tarihi", "işlem tarihi"});
        if (!inspectionDate.isEmpty()) p.date = inspectionDate;
        String validity = labelledDate(raw, new String[]{"muayene gecerlilik tarihi", "muayene geçerlilik tarihi", "gecerlilik tarihi", "geçerlilik tarihi", "muayene gecerlilik"});
        if (!validity.isEmpty()) p.nextDate = validity;
        double fee = labelledMoney(raw, new String[]{"muayene ucreti", "muayene ücreti", "ucret", "ücret", "odenecek bedel", "ödenecek bedel"});
        if (fee > 0) p.amount = fee;
        List<String> faults = collectRelevantLines(raw, new String[]{"kusur", "fren", "far", "lastik", "cam", "plaka", "emisyon", "silecek", "ayna", "korna", "direksiyon", "suspansiyon", "aks"}, 7);
        String faultText = faults.isEmpty() ? "" : join(faults, " | ");
        p.detailSummary = joinNonEmpty(" • ", p.inspectionResult.isEmpty() ? "" : "Sonuç: " + p.inspectionResult, p.nextDate.isEmpty() ? "" : "Geçerlilik: " + p.nextDate, p.km > 0 ? "KM: " + p.km : "", faultText);
    }''')

smart = replace_method(smart, '    private static void enrichExpertise(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichExpertise(String raw, String n, RecordParser.Parsed p) {
        p.type = "Ekspertiz";
        p.vendor = "";
        p.date = "";
        p.amount = 0;
        String vendor = expertiseVendor(n);
        if (!vendor.isEmpty()) p.vendor = vendor;

        boolean body = containsAny(n, "kaporta", "boya", "mikron", "degisen", "orijinal", "orj", "podye", "sasi", "camurluk", "direk", "gorsel_sema: kaporta");
        boolean mech = containsAny(n, "motor", "mekanik", "dyno", "fren", "suspansiyon", "obd", "airbag", "yuruyen");
        if (body && !mech) p.expertiseSubtype = "Kaporta/boya";
        else if (mech && !body) p.expertiseSubtype = "Mekanik";
        else p.expertiseSubtype = "Genel ekspertiz";

        String reportDate = labelledDate(raw, new String[]{"rapor tarihi", "islem tarihi", "işlem tarihi", "ekspertiz tarihi", "giris tarihi", "giriş tarihi"});
        if (!reportDate.isEmpty()) p.date = reportDate;

        String visual = extractVisualFindings(raw);
        if (!visual.isEmpty()) {
            p.expertiseSubtype = "Kaporta/boya";
            p.detailSummary = visual;
            p.confidence = Math.max(p.confidence, 94);
        } else {
            List<String> lines = collectExpertiseFindings(raw, 14);
            p.detailSummary = lines.isEmpty() ? "" : join(lines, " | ");
        }

        double reportFee = labelledMoney(raw, new String[]{"ekspertiz ucreti", "ekspertiz ücreti", "paket ucreti", "paket ücreti", "odenen", "ödenen", "toplam"});
        if (reportFee > 0 && reportFee < 200000) p.amount = reportFee;
        if (n.contains("gorsel_sema: kaporta")) p.km = 0;
    }''')

smart = replace_method(smart, '    private static void enrichTax(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichTax(String raw, String n, RecordParser.Parsed p) {
        p.type = "Vergi";
        p.vendor = "";
        p.amount = 0;
        p.date = "";
        p.nextDate = "";
        if (containsAny(n, "ek mtv", "ek motorlu tasitlar")) p.taxSubtype = "Ek MTV";
        else if (containsAny(n, "mtv", "motorlu tasitlar vergisi")) p.taxSubtype = "MTV";
        else p.taxSubtype = "Diğer resmî ödeme";
        double amount = labelledMoney(raw, new String[]{"odenen tutar", "ödenen tutar", "odenecek tutar", "ödenecek tutar", "toplam", "tutar", "tahakkuk"});
        if (amount > 0) p.amount = amount;
        String paid = labelledDate(raw, new String[]{"odeme tarihi", "ödeme tarihi", "tahsil tarihi", "tarih"});
        if (!paid.isEmpty()) p.date = paid;
        String due = labelledDate(raw, new String[]{"son odeme tarihi", "son ödeme tarihi", "vade tarihi"});
        if (!due.isEmpty()) p.nextDate = due;
        if (containsAny(n, "1. taksit", "1.taksit", "birinci taksit")) p.paymentPeriod = "1. taksit";
        else if (containsAny(n, "2. taksit", "2.taksit", "ikinci taksit")) p.paymentPeriod = "2. taksit";
        p.detailSummary = joinNonEmpty(" • ", p.taxSubtype, p.paymentPeriod, p.nextDate.isEmpty() ? "" : "Son ödeme: " + p.nextDate);
    }''')

smart = replace_method(smart, '    private static void enrichMaintenance(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichMaintenance(String raw, String n, RecordParser.Parsed p) {
        if (p.type == null || p.type.isEmpty()) p.type = "Bakım";
        p.vendor = "";
        p.date = "";
        p.amount = 0;
        String vendor = serviceVendor(raw, n);
        if (!vendor.isEmpty() && plausibleCompanyText(vendor, "service")) p.vendor = vendor;
        List<String> ops = collectRelevantLines(raw, new String[]{"motor yagi", "yag filtresi", "hava filtresi", "polen filtresi", "yakit filtresi", "fren balata", "fren disk", "buji", "aku", "triger", "lastik", "antifriz", "sanziman", "servis", "bakim"}, 10);
        p.detailSummary = ops.isEmpty() ? "" : join(ops, " | ");
        String date = labelledDate(raw, new String[]{"servis tarihi", "islem tarihi", "işlem tarihi", "tarih"});
        if (!date.isEmpty()) p.date = date;
        double total = labelledMoney(raw, new String[]{"genel toplam", "odenecek", "ödenecek", "toplam tutar", "toplam"});
        if (total > 0) p.amount = total;
    }''')

smart = replace_method(smart, '    private static void enrichDamage(String raw, String n, RecordParser.Parsed p)', r'''    private static void enrichDamage(String raw, String n, RecordParser.Parsed p) {
        if (p.type == null || p.type.isEmpty()) p.type = "Hasar";
        p.vendor = "";
        p.date = "";
        p.amount = 0;
        List<String> lines = collectRelevantLines(raw, new String[]{"hasar", "kaza", "onarim", "kaporta", "boya", "cam", "tampon", "camurluk", "kapi", "degisen", "parca", "iscilik"}, 10);
        p.detailSummary = lines.isEmpty() ? "" : join(lines, " | ");
        String date = labelledDate(raw, new String[]{"hasar tarihi", "kaza tarihi", "onarim tarihi", "onarım tarihi", "tarih"});
        if (!date.isEmpty()) p.date = date;
        double total = labelledMoney(raw, new String[]{"onarim tutari", "onarım tutarı", "hasar tutari", "hasar tutarı", "genel toplam", "toplam"});
        if (total > 0) p.amount = total;
    }''')

smart = replace_method(smart, '    private static Scores scoreDocument(String n)', r'''    private static Scores scoreDocument(String n) {
        Scores s = new Scores();
        s.fuel += hits(n, 3, "akaryakit", "kursunsuz", "motorin", "otogaz", "epdk", "pompa", "lt x", "lt *", "litre", "yakit");
        s.fuel += hits(n, 2, "shell", "opet", "petrol ofisi", "totalenergies", "aytemiz", "bp", "socar");
        if (n.contains("gorsel_tip: fis") && containsAny(n, "lt", "litre", "motorin", "benzin", "lpg", "akaryakit")) s.fuel += 5;
        s.insurance += hits(n, 4, "police no", "police/yenileme", "sigorta baslang", "sigorta bitis", "zorunlu mali sorumluluk");
        s.insurance += hits(n, 3, "kasko", "teminat", "sigorta ettiren", "sigortali");
        s.inspection += hits(n, 5, "arac muayene raporu", "muayene gecerlilik", "tuvturk");
        s.inspection += hits(n, 4, "hafif kusurlu", "agir kusurlu", "emniyetsiz", "muayene sonucu");
        s.expertise += hits(n, 5, "belge_turu: ekspertiz", "gorsel_bulgular", "kaporta detay ekspertizi");
        s.expertise += hits(n, 4, "oto ekspertiz", "ekspertiz raporu", "kaporta boya", "dyno", "obd", "podye");
        s.expertise += hits(n, 2, "mikron", "orijinal", "orj", "degisen", "suspansiyon", "airbag", "motor testi");
        s.tax += hits(n, 5, "motorlu tasitlar vergisi", "mtv");
        s.tax += hits(n, 2, "gib", "tahakkuk", "vergi dairesi");
        s.maintenance += hits(n, 3, "periyodik bakim", "servis bakimi", "motor yagi", "yag filtresi", "fren balata", "triger");
        s.maintenance += hits(n, 1, "servis", "bakim", "filtre");
        s.damage += hits(n, 3, "hasar dosya", "kaza tutanagi", "onarim", "kaporta onarim", "cam hasar");
        s.odometer += hits(n, 7, "belge_turu: odometer");
        s.odometer += hits(n, 5, "odometre", "odometer", "odo", "toplam km");
        s.odometer += hits(n, 1, "trip", "km/h", "r/min");
        return s;
    }''')

# Insert strict extraction / sanitization helpers once.
marker = '    private static String extractPlate(String raw) {'
if '    private static int extractExplicitKm(String raw)' not in smart:
    helpers = r'''
    private static int extractExplicitKm(String raw) {
        if (raw == null) return 0;
        Pattern[] patterns = new Pattern[]{
                Pattern.compile("(?is)(?:\\bODO\\b|ODOMETER|ODOMETRE|TOPLAM\\s*KM|KILOMETRE(?:M)?|ARAC\\s*KM|ARAÇ\\s*KM)\\s*[:=.-]?\\s*([0-9][0-9 .]{2,10})"),
                Pattern.compile("(?is)(?<![0-9])([0-9][0-9 .]{2,10})\\s*(?:KM|KILOMETRE)(?!\\s*/\\s*H)")
        };
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(raw);
            while (m.find()) {
                String digits = m.group(1).replaceAll("[^0-9]", "");
                try {
                    int value = Integer.parseInt(digits);
                    if (value >= 0 && value < 2_000_000 && !looksLikeGaugeScale(value)) return value;
                } catch (Exception ignored) { }
            }
        }
        return 0;
    }

    private static double extractFuelQuantity(String raw) {
        Matcher direct = Pattern.compile("(?i)([0-9]{1,4}(?:[.,][0-9]{1,3})?)\\s*(?:LT\\.?|LITRE|L\\b|KWH)").matcher(raw);
        while (direct.find()) {
            double v = dec(direct.group(1));
            if (v > 0.01 && v < 2000) return v;
        }
        double labeled = labelledDecimal(raw, new String[]{"miktar", "hacim", "volume", "quantity"});
        return labeled > 0.01 && labeled < 2000 ? labeled : 0;
    }

    private static double extractFuelUnitPrice(String raw) {
        Matcher mult = Pattern.compile("(?i)[0-9]{1,4}(?:[.,][0-9]{1,3})?\\s*(?:LT\\.?|LITRE|L\\b|KWH)\\s*[x×*]\\s*([0-9]{1,5}(?:[.,][0-9]{1,3})?)").matcher(raw);
        if (mult.find()) {
            double v = dec(mult.group(1));
            if (v > 0.01 && v < 5000) return v;
        }
        double labeled = labelledMoney(raw, new String[]{"birim fiyat", "litre fiyat", "lt fiyat", "fiyat/lt", "tl/lt", "tl/l", "tl/kwh", "unit price"});
        return labeled > 0.01 && labeled < 5000 ? labeled : 0;
    }

    private static String safeLabeledVendor(String raw, String[] labels, String context) {
        String[] lines = raw.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String normalized = normalize(lines[i]);
            boolean labelHit = false;
            for (String label : labels) if (normalized.contains(normalize(label))) { labelHit = true; break; }
            if (!labelHit) continue;
            String candidate = lines[i].replaceFirst("(?i)^.*?[:=-]", "").trim();
            if (!plausibleCompanyText(candidate, context) && i + 1 < lines.length) candidate = lines[i + 1].trim();
            if (plausibleCompanyText(candidate, context)) return candidate.replaceAll("\\s{2,}", " ").trim();
        }
        return "";
    }

    private static boolean plausibleCompanyText(String value, String context) {
        if (value == null) return false;
        String v = value.trim();
        if (v.length() < 2 || v.length() > 72) return false;
        String n = normalize(v);
        if (containsAny(n, "tarih", "saat", "toplam", "tutar", "kdv", "police no", "plaka", "telefon", "adres", "musteri", "sase", "vin")) return false;
        int letters = 0, digits = 0, weird = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else if (!Character.isWhitespace(c) && "&.-/'()".indexOf(c) < 0) weird++;
        }
        if (letters < 3 || letters < digits * 2 || weird > 2) return false;
        if ("insurance".equals(context)) return containsAny(n, "sigorta", "insurance", "anonim", "a.s", "aş", "as ") || !knownVendor(n).isEmpty();
        if ("fuel".equals(context)) return containsAny(n, "petrol", "akaryakit", "enerji", "istasyon", "oil", "fuel") || isFuelVendor(n);
        if ("service".equals(context)) return containsAny(n, "servis", "otomotiv", "oto", "motor", "bakim") || letters >= 7;
        return true;
    }

    private static String extractVisualFindings(String raw) {
        Matcher m = Pattern.compile("(?im)^\\s*GORSEL_BULGULAR\\s*:\\s*(.+)$").matcher(raw);
        return m.find() ? m.group(1).trim() : "";
    }

    private static List<String> collectExpertiseFindings(String raw, int max) {
        List<String> all = collectRelevantLines(raw, new String[]{"orijinal", "orj", "boyali", "boya", "degisen", "degisim", "lokal", "duzelt", "gocuk", "hasarli", "ezik", "sasi", "podye", "direk", "kaput", "camurluk", "kapi", "tavan", "bagaj", "dyno", "motor guc", "motor performans", "obd", "ariza", "fren", "suspansiyon", "airbag", "hava yastigi", "yag kac", "su kac", "lastik", "amortisor", "aks"}, max * 2);
        List<String> out = new ArrayList<>();
        for (String line : all) {
            String n = normalize(line);
            int legend = 0;
            for (String k : new String[]{"orijinal", "l. boyali", "boyali", "degisen", "sok tak", "plastik", "folyo", "yok"}) if (n.contains(k)) legend++;
            boolean hasPart = containsAny(n, "kaput", "camurluk", "kapi", "tavan", "bagaj", "sasi", "podye", "direk", "tampon", "marspiyel", "motor", "fren", "obd", "airbag", "lastik", "suspansiyon");
            if (legend >= 3 && !hasPart) continue;
            out.add(line);
            if (out.size() >= max) break;
        }
        return out;
    }

    private static double round3(double value) { return Math.round(value * 1000.0) / 1000.0; }

'''
    if marker not in smart:
        raise SystemExit('helper insertion marker not found')
    smart = smart.replace(marker, helpers + marker, 1)

SMART.write_text(smart, encoding='utf-8')

# Receipt OCR: upscale small text and feed visual hints into the parser.
ocr = OCR.read_text(encoding='utf-8')
old_scale = '''            int maxWidth = 2000;\n            if (source.getWidth() > maxWidth) {\n                int h = Math.max(1, Math.round(source.getHeight() * (maxWidth / (float) source.getWidth())));\n                scaled = Bitmap.createScaledBitmap(source, maxWidth, h, true);\n            } else {\n                scaled = source;\n            }'''
new_scale = '''            // Küçük termal fiş yazıları ve LCD/ODO rakamları için yalnız küçültmek yerine\n            // gerektiğinde kontrollü biçimde büyüt. 2600 px genişlik cihaz içi OCR için iyi bir dengedir.\n            int targetWidth = Math.max(2200, Math.min(2800, source.getWidth() < 2200 ? 2600 : source.getWidth()));\n            if (source.getWidth() != targetWidth) {\n                int h = Math.max(1, Math.round(source.getHeight() * (targetWidth / (float) source.getWidth())));\n                scaled = Bitmap.createScaledBitmap(source, targetWidth, h, true);\n            } else {\n                scaled = source;\n            }'''
if old_scale not in ocr:
    raise SystemExit('ReceiptOcr scale block not found')
ocr = ocr.replace(old_scale, new_scale, 1)

old_success = '''                    .addOnSuccessListener(text -> {\n                        String enhancedText = text == null ? "" : text.getText().trim();\n                        recycle(finalEnhanced, finalScaled, finalSource);\n                        finishImageRecognition(recognizer, originalText, enhancedText, callback);\n                    })\n                    .addOnFailureListener(error -> {\n                        recycle(finalEnhanced, finalScaled, finalSource);\n                        finishImageRecognition(recognizer, originalText, "", callback);\n                    });'''
new_success = '''                    .addOnSuccessListener(text -> {\n                        String enhancedText = text == null ? "" : text.getText().trim();\n                        String visual = ImageDocumentAnalyzer.buildVisualHints(finalSource, originalText + "\\n" + enhancedText);\n                        String enrichedOriginal = originalText;\n                        if (visual != null && !visual.trim().isEmpty()) {\n                            enrichedOriginal = (enrichedOriginal == null ? "" : enrichedOriginal.trim())\n                                    + "\\n--- GORSEL ANALIZ ---\\n" + visual.trim();\n                        }\n                        recycle(finalEnhanced, finalScaled, finalSource);\n                        finishImageRecognition(recognizer, enrichedOriginal, enhancedText, callback);\n                    })\n                    .addOnFailureListener(error -> {\n                        String visual = ImageDocumentAnalyzer.buildVisualHints(finalSource, originalText);\n                        String enrichedOriginal = originalText;\n                        if (visual != null && !visual.trim().isEmpty()) {\n                            enrichedOriginal = (enrichedOriginal == null ? "" : enrichedOriginal.trim())\n                                    + "\\n--- GORSEL ANALIZ ---\\n" + visual.trim();\n                        }\n                        recycle(finalEnhanced, finalScaled, finalSource);\n                        finishImageRecognition(recognizer, enrichedOriginal, "", callback);\n                    });'''
if old_success not in ocr:
    raise SystemExit('ReceiptOcr callback block not found')
ocr = ocr.replace(old_success, new_success, 1)
OCR.write_text(ocr, encoding='utf-8')

# UI safety: low-confidence photo OCR must not silently populate common fields.
act = ACT.read_text(encoding='utf-8')
sig = '    private void applyParsedToCurrentForm(RecordParser.Parsed p, String source)'
start = act.find(sig)
if start < 0:
    raise SystemExit('applyParsedToCurrentForm not found')
brace = act.find('{', start)
depth = 0; end = None
for i in range(brace, len(act)):
    if act[i] == '{': depth += 1
    elif act[i] == '}':
        depth -= 1
        if depth == 0:
            end = i + 1
            break
if end is None: raise SystemExit('applyParsedToCurrentForm end not found')
method = act[start:end]
method = method.replace('        int found = 0;\n', '''        int found = 0;\n        boolean photoSource = source != null && source.toLowerCase(Locale.ROOT).contains("foto");\n        boolean trustedCommonFields = !photoSource || p.confidence >= 60;\n''', 1)
for old, new in [
    ('        if (p.date != null && !p.date.isEmpty() && f.date != null) {', '        if (trustedCommonFields && p.date != null && !p.date.isEmpty() && f.date != null) {'),
    ('        if (p.km > 0 && f.km != null) {', '        if (trustedCommonFields && p.km > 0 && f.km != null) {'),
    ('        if (p.amount > 0 && f.cost != null) {', '        if (trustedCommonFields && p.amount > 0 && f.cost != null) {'),
    ('        if (p.quantity > 0 && f.quantity != null) {', '        if (trustedCommonFields && p.quantity > 0 && f.quantity != null) {'),
    ('        if (p.nextDate != null && !p.nextDate.isEmpty() && f.nextDate != null) {', '        if (trustedCommonFields && p.nextDate != null && !p.nextDate.isEmpty() && f.nextDate != null) {'),
    ('        if (p.policyNo != null && !p.policyNo.isEmpty() && f.extraText != null) {', '        if (trustedCommonFields && p.policyNo != null && !p.policyNo.isEmpty() && f.extraText != null) {')
]:
    if old not in method:
        raise SystemExit(f'UI guard anchor missing: {old}')
    method = method.replace(old, new, 1)
# Never copy low-quality company OCR into titles/details.
method = method.replace('if (p.vendor != null && !p.vendor.isEmpty()) {', 'if (isSafeAssistantText(p.vendor)) {')
method = method.replace('if (p.vendor != null && !p.vendor.isEmpty() && f.title != null &&', 'if (isSafeAssistantText(p.vendor) && f.title != null &&')
act = act[:start] + method + act[end:]

helper_marker = '    private String smartResultSummary(String module, RecordParser.Parsed p, String source) {'
if '    private boolean isSafeAssistantText(String value)' not in act:
    helper = r'''    private boolean isSafeAssistantText(String value) {
        if (value == null) return false;
        String v = value.trim();
        if (v.length() < 2 || v.length() > 120) return false;
        int letters = 0, digits = 0, weird = 0;
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (Character.isLetter(c)) letters++;
            else if (Character.isDigit(c)) digits++;
            else if (!Character.isWhitespace(c) && "&.-/'():•|".indexOf(c) < 0) weird++;
        }
        return letters >= 2 && letters >= digits && weird <= 3;
    }

'''
    if helper_marker not in act: raise SystemExit('smartResultSummary marker not found')
    act = act.replace(helper_marker, helper + helper_marker, 1)
ACT.write_text(act, encoding='utf-8')

# Version bump.
gradle = GRADLE.read_text(encoding='utf-8')
gradle = re.sub(r"versionCode\s+\d+", "versionCode 18", gradle, count=1)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '0.14-vision-safe'", gradle, count=1)
GRADLE.write_text(gradle, encoding='utf-8')

print('v0.14 vision-safe upgrade applied')
