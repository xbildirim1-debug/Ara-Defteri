from pathlib import Path

# SmartDocumentAnalyzer: kilometre bulunan akaryakıt fişini sadece ODO sanma.
p = Path('app/src/main/java/com/aracdefteri/app/SmartDocumentAnalyzer.java')
s = p.read_text(encoding='utf-8')
old = '''        int betterKm = extractBestKm(text);\n        if (betterKm > 0) p.km = betterKm;\n\n        if (\"FUEL\".equals(kind)) enrichFuel(text, n, p);'''
new = '''        int betterKm = extractBestKm(text);\n        if (betterKm > 0) p.km = betterKm;\n\n        // Akaryakıt fişlerinde kilometre de bulunabilir. Litre/tutar/birim fiyat gibi\n        // güçlü yakıt işaretleri varsa ODO sınıflandırması yakıt kaydını ezmemeli.\n        if ((kind == null || kind.isEmpty() || \"ODOMETER\".equals(kind)) && hasFuelEvidence(text, n, p)) {\n            kind = \"FUEL\";\n            p.documentKind = \"FUEL\";\n            p.confidence = Math.max(p.confidence, 82);\n        }\n\n        if (\"FUEL\".equals(kind)) enrichFuel(text, n, p);'''
if old not in s:
    raise SystemExit('SmartDocumentAnalyzer insertion point not found')
s = s.replace(old, new, 1)
marker = '    private static void enrichFuel(String raw, String n, RecordParser.Parsed p) {'
helper = '''    private static boolean hasFuelEvidence(String raw, String n, RecordParser.Parsed p) {\n        int score = 0;\n        if (p.quantity > 0 && p.quantity < 1000) score += 5;\n        if (p.unitPrice > 0 && p.unitPrice < 100000) score += 4;\n        if (p.amount > 0 && p.amount < 10_000_000) score += 2;\n        if (p.fuelType != null && !p.fuelType.isEmpty()) score += 3;\n        if (!knownVendor(n).isEmpty()) score += 2;\n        if (containsAny(n, \"akaryakit\", \"motorin\", \"dizel\", \"benzin\", \"kursunsuz\", \"otogaz\", \"lpg\", \"pompa\", \"tabanca\", \"litre\", \"tl/lt\", \"tl/l\")) score += 3;\n        if (Pattern.compile(\"(?i)[0-9]{1,4}(?:[.,][0-9]{1,3})?\\\\s*(?:lt|l|litre)\\\\s*[x×*]\\\\s*[0-9]{1,5}(?:[.,][0-9]{1,3})?\").matcher(raw).find()) score += 6;\n        return score >= 6;\n    }\n\n'''
if marker not in s:
    raise SystemExit('enrichFuel marker not found')
s = s.replace(marker, helper + marker, 1)
p.write_text(s, encoding='utf-8')

# Asistan: fişte km varsa bile yakıt verileri varsa ODO yoluna girme.
p = Path('app/src/main/java/com/aracdefteri/app/NextMainActivity.java')
s = p.read_text(encoding='utf-8')
old = '''        boolean odometerOnly = \"ODOMETER\".equals(parsed.documentKind) ||\n                ((parsed.type == null || parsed.type.trim().isEmpty()) && parsed.km > 0);\n        if (odometerOnly) {'''
new = '''        boolean fuelLike = \"FUEL\".equals(parsed.documentKind)\n                || (parsed.quantity > 0 && (parsed.amount > 0 || parsed.unitPrice > 0))\n                || ((parsed.fuelType != null && !parsed.fuelType.trim().isEmpty())\n                    && (parsed.quantity > 0 || parsed.amount > 0 || parsed.unitPrice > 0));\n        if (fuelLike) {\n            parsed.type = \"Yakıt\";\n            parsed.documentKind = \"FUEL\";\n        }\n\n        boolean odometerOnly = !fuelLike && (\"ODOMETER\".equals(parsed.documentKind) ||\n                ((parsed.type == null || parsed.type.trim().isEmpty()) && parsed.km > 0));\n        if (odometerOnly) {'''
if old not in s:
    raise SystemExit('assistant odometer block not found')
s = s.replace(old, new, 1)
p.write_text(s, encoding='utf-8')

# Sürüm.
p = Path('app/build.gradle')
s = p.read_text(encoding='utf-8')
import re
s = re.sub(r'versionCode\s+\d+', 'versionCode 16', s, count=1)
s = re.sub(r"versionName\s+'[^']+'", "versionName '0.13.0-fuel-receipt-dev'", s, count=1)
p.write_text(s, encoding='utf-8')
