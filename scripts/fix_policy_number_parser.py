from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/aracdefteri/app/SmartDocumentAnalyzer.java"
text = path.read_text(encoding="utf-8")
old = '''    private static String extractLabelValue(String raw, String[] labels, String valueRegex) {
        String[] lines = raw.split("\\\\r?\\\\n"); Pattern value = Pattern.compile(valueRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        for (int i=0;i<lines.length;i++) { String n=normalize(lines[i]); for (String label:labels) { String ln=normalize(label); if(!n.contains(ln)) continue; Matcher m=value.matcher(lines[i]); if(m.find()) return m.group(); if(i+1<lines.length){m=value.matcher(lines[i+1]); if(m.find()) return m.group();} } }
        return "";
    }
'''
new = '''    private static String extractLabelValue(String raw, String[] labels, String valueRegex) {
        String[] lines = raw.split("\\\\r?\\\\n");
        Pattern value = Pattern.compile(valueRegex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        for (int i = 0; i < lines.length; i++) {
            String normalizedLine = normalize(lines[i]);
            for (String label : labels) {
                String normalizedLabel = normalize(label);
                int at = normalizedLine.indexOf(normalizedLabel);
                if (at < 0) continue;

                // Etiketin kendisini değer sanmamak için yalnızca etiketin sağ tarafında ara.
                int tailStart = Math.min(lines[i].length(), at + label.length());
                String tail = lines[i].substring(tailStart).replaceFirst("^[\\\\s:=-]+", "");
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
'''
if new in text:
    print("Policy number parser already fixed.")
elif old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
    print("Policy number parser fixed.")
else:
    raise SystemExit("Policy number parser marker not found")
