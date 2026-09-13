from pathlib import Path
import re
root = Path(__file__).resolve().parents[1]
pdf_path = root / 'app/src/main/java/com/aracdefteri/app/VehicleCvPdf.java'
gradle_path = root / 'app/build.gradle'
pdf = pdf_path.read_text(encoding='utf-8')
block = '''        String note = pref(prefs, "cv_note");\n        if (!note.isEmpty()) {\n            int noteY = y + 118;\n            w.text("AÇIKLAMA", 38, noteY, 7, Writer.ACCENT_DARK, true);\n            w.drawWrapped(note, 38, noteY + 15, 8, Writer.DARK, false, Writer.CONTENT_W, 11);\n        }\n\n'''
pdf = pdf.replace(block, '')
pdf_path.write_text(pdf, encoding='utf-8')
gradle = gradle_path.read_text(encoding='utf-8')
gradle = re.sub(r'versionCode\s+\d+', 'versionCode 8', gradle)
gradle = re.sub(r"versionName\s+'[^']+'", "versionName '0.7.1-cv-cleanup-dev'", gradle)
gradle_path.write_text(gradle, encoding='utf-8')
print('Applied v0.7.1 description cleanup')
