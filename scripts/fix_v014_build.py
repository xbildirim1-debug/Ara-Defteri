from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/aracdefteri/app'

# Fix one leftover old ReceiptOcr call signature.
p = JAVA / 'ReceiptOcr.java'
s = p.read_text(encoding='utf-8')
s = s.replace('finishImageRecognition(recognizer, originalText, "", callback);',
              'processFocusedImage(context, imageUri, recognizer, originalText, "", callback);')
p.write_text(s, encoding='utf-8')

# Color-state priority: thin gray outlines must not be interpreted as Sök-tak.
p = JAVA / 'VisualDocumentAnalyzer.java'
s = p.read_text(encoding='utf-8')
old = '''        int best = 0;\n        for (int i=1;i<c.length;i++) if (c[i] > c[best]) best = i;\n        float ratio = c[best] / (float) total;\n        if (best == 1 && ratio > .10f) return "Değişen";\n        if (best == 2 && ratio > .10f) return "Lokal boyalı";\n        if (best == 3 && ratio > .10f) return "Boyalı";\n        if (best == 4 && ratio > .12f) return "Sök-tak";\n        return "Orijinal";'''
new = '''        float red = c[1] / (float) total;\n        float yellow = c[2] / (float) total;\n        float blue = c[3] / (float) total;\n        float gray = c[4] / (float) total;\n        if (red > .10f) return "Değişen";\n        if (yellow > .10f) return "Lokal boyalı";\n        if (blue > .10f) return "Boyalı";\n        if (gray > .35f) return "Sök-tak";\n        return "Orijinal";'''
if old not in s:
    raise SystemExit('panelState block not found')
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')

print('v0.14 build hotfix applied')
