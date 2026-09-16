from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/aracdefteri/app'

visual = r'''package com.aracdefteri.app;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Görsel OCR sonrası güvenlik ve görsel şema analizi. */
public final class VisualDocumentAnalyzer {
    private static final Locale TR = new Locale("tr", "TR");
    private VisualDocumentAnalyzer() { }

    public static void sanitizeTextOcr(String raw, RecordParser.Parsed p) {
        if (p == null) return;
        String text = raw == null ? "" : raw;
        String n = norm(text);

        if (!plausibleVendor(p.vendor)) p.vendor = "";

        boolean expertise = "EXPERTISE".equals(p.documentKind) || looksLikeExpertiseText(n);
        boolean insurance = "INSURANCE".equals(p.documentKind) || "Sigorta/Kasko".equals(p.type);
        boolean fuel = "FUEL".equals(p.documentKind) || "Yakıt".equals(p.type);

        if (expertise) {
            p.type = "Ekspertiz";
            p.documentKind = "EXPERTISE";
            p.expertiseSubtype = "Kaporta/boya";
            int explicit = explicitKm(text);
            p.km = explicit > 0 ? explicit : 0;
            if (!plausibleSummary(p.detailSummary)) p.detailSummary = "";
            p.confidence = Math.max(p.confidence, 82);
        } else if (insurance) {
            int explicit = explicitKm(text);
            p.km = explicit > 0 ? explicit : 0;
            p.date = nonEmpty(p.policyStartDate) ? p.policyStartDate : "";
            p.nextDate = nonEmpty(p.policyEndDate) ? p.policyEndDate : p.nextDate;
            p.detailSummary = join(" • ",
                    nonEmpty(p.vendor) ? p.vendor : "",
                    nonEmpty(p.policyNo) ? "Poliçe: " + p.policyNo : "",
                    nonEmpty(p.policyStartDate) ? "Başlangıç: " + p.policyStartDate : "",
                    nonEmpty(p.policyEndDate) ? "Bitiş: " + p.policyEndDate : "",
                    nonEmpty(p.plate) ? "Plaka: " + p.plate : "");
        } else if (fuel) {
            int explicit = explicitKm(text);
            p.km = explicit > 0 ? explicit : 0;
        } else if ("INSPECTION".equals(p.documentKind) || "Muayene".equals(p.type)) {
            int explicit = explicitKm(text);
            p.km = explicit > 0 ? explicit : 0;
        }

        if ((p.type == null || p.type.isEmpty()) && (p.documentKind == null || p.documentKind.isEmpty())) {
            recoverFocusedOdometer(text, p);
        }
    }

    public static void applyImage(Bitmap bitmap, String raw, RecordParser.Parsed p) {
        sanitizeTextOcr(raw, p);
        if (bitmap == null || p == null) return;
        String n = norm(raw == null ? "" : raw);
        if (looksLikeExpertiseText(n) && hasExpertiseColors(bitmap)) {
            analyzeExpertiseDiagram(bitmap, p);
        } else if ((p.type == null || p.type.isEmpty()) && (p.documentKind == null || p.documentKind.isEmpty())) {
            recoverFocusedOdometer(raw, p);
        }
    }

    private static void recoverFocusedOdometer(String raw, RecordParser.Parsed p) {
        if (raw == null || !raw.contains("ODOMETRE_BOLGESI")) return;
        String[] parts = raw.split("ODOMETRE_BOLGESI", 2);
        if (parts.length < 2) return;
        String zone = parts[1];
        int cut = zone.indexOf("---");
        if (cut > 0) zone = zone.substring(0, cut);
        Matcher m = Pattern.compile("(?<!\\d)([0-9][0-9 .]{3,10})(?!\\d)").matcher(zone);
        int best = 0;
        while (m.find()) {
            String digits = m.group(1).replaceAll("[^0-9]", "");
            if (digits.length() < 4 || digits.length() > 7) continue;
            try {
                int v = Integer.parseInt(digits);
                if (v >= 1000 && v < 2_000_000 && !gauge(v) && v > best) best = v;
            } catch (Exception ignored) { }
        }
        if (best > 0) {
            p.km = best;
            p.documentKind = "ODOMETER";
            p.type = "";
            p.confidence = Math.max(p.confidence, 90);
        }
    }

    private static int explicitKm(String raw) {
        if (raw == null) return 0;
        Pattern[] ps = new Pattern[]{
                Pattern.compile("(?is)(?:\\bODO\\b|ODOMETRE|ODOMETER|KILOMETRE|TOPLAM\\s*KM|\\bKM\\b)\\s*[:=.-]?\\s*([0-9][0-9 .]{3,10})"),
                Pattern.compile("(?is)([0-9][0-9 .]{3,10})\\s*(?:KM|KILOMETRE)\\b")
        };
        for (Pattern p : ps) {
            Matcher m = p.matcher(raw.toUpperCase(TR));
            while (m.find()) {
                String d = m.group(1).replaceAll("[^0-9]", "");
                try {
                    int v = Integer.parseInt(d);
                    if (v >= 1000 && v < 2_000_000 && !gauge(v)) return v;
                } catch (Exception ignored) { }
            }
        }
        return 0;
    }

    private static boolean gauge(int v) {
        int[] bad = {100120,120140,140160,160180,180200,200220,2040,4060,6080,80100};
        for (int x : bad) if (v == x) return true;
        return false;
    }

    private static boolean looksLikeExpertiseText(String n) {
        int hits = 0;
        if (n.contains("kaporta")) hits++;
        if (n.contains("ekspertiz")) hits++;
        if (n.contains("orijinal")) hits++;
        if (n.contains("boyali")) hits++;
        if (n.contains("degisen")) hits++;
        if (n.contains("lokal") || n.contains("l. boyali")) hits++;
        return hits >= 3;
    }

    private static boolean hasExpertiseColors(Bitmap b) {
        int step = Math.max(2, Math.min(b.getWidth(), b.getHeight()) / 350);
        int colored = 0, total = 0;
        for (int y = (int)(b.getHeight()*0.15f); y < (int)(b.getHeight()*0.86f); y += step) {
            for (int x = (int)(b.getWidth()*0.10f); x < (int)(b.getWidth()*0.90f); x += step) {
                total++;
                if (colorState(b.getPixel(x,y)) != 0) colored++;
            }
        }
        return total > 0 && colored > total * 0.025f;
    }

    private static void analyzeExpertiseDiagram(Bitmap b, RecordParser.Parsed p) {
        p.type = "Ekspertiz";
        p.documentKind = "EXPERTISE";
        p.expertiseSubtype = "Kaporta/boya";
        p.km = 0;
        p.confidence = Math.max(p.confidence, 92);

        List<String> out = new ArrayList<>();
        addPanel(out, "Kaput", panelState(b, .37f,.20f,.63f,.40f));
        addPanel(out, "Sol ön çamurluk", panelState(b, .16f,.23f,.31f,.42f));
        addPanel(out, "Sağ ön çamurluk", panelState(b, .69f,.23f,.84f,.42f));
        addPanel(out, "Sol ön kapı", panelState(b, .17f,.40f,.37f,.56f));
        addPanel(out, "Sağ ön kapı", panelState(b, .63f,.40f,.83f,.56f));
        addPanel(out, "Sol arka kapı", panelState(b, .17f,.55f,.37f,.70f));
        addPanel(out, "Sağ arka kapı", panelState(b, .63f,.55f,.83f,.70f));
        addPanel(out, "Sol arka çamurluk", panelState(b, .16f,.68f,.31f,.84f));
        addPanel(out, "Sağ arka çamurluk", panelState(b, .69f,.68f,.84f,.84f));
        addPanel(out, "Tavan", panelState(b, .39f,.42f,.61f,.65f));
        addPanel(out, "Bagaj kapağı", panelState(b, .38f,.69f,.62f,.83f));

        int changed = countState(out, "Değişen");
        int painted = countState(out, "Boyalı");
        int local = countState(out, "Lokal boyalı");
        String summary = join(" • ", out.toArray(new String[0]));
        if (!summary.isEmpty()) {
            p.detailSummary = summary + "\nÖzet: " + changed + " değişen, " + painted + " boyalı, " + local + " lokal boyalı panel.";
        }
    }

    private static int countState(List<String> list, String state) {
        int n = 0;
        for (String s : list) if (s.endsWith(state)) n++;
        return n;
    }

    private static void addPanel(List<String> out, String name, String state) {
        if (state == null || state.isEmpty()) return;
        out.add(name + ": " + state);
    }

    private static String panelState(Bitmap b, float l, float t, float r, float bot) {
        int x1 = Math.max(0, Math.min(b.getWidth()-1, (int)(b.getWidth()*l)));
        int y1 = Math.max(0, Math.min(b.getHeight()-1, (int)(b.getHeight()*t)));
        int x2 = Math.max(x1+1, Math.min(b.getWidth(), (int)(b.getWidth()*r)));
        int y2 = Math.max(y1+1, Math.min(b.getHeight(), (int)(b.getHeight()*bot)));
        int[] c = new int[5];
        int step = Math.max(2, Math.min(x2-x1, y2-y1) / 45);
        int total = 0;
        for (int y=y1; y<y2; y+=step) for (int x=x1; x<x2; x+=step) {
            total++;
            int s = colorState(b.getPixel(x,y));
            if (s >= 0 && s < c.length) c[s]++;
        }
        if (total == 0) return "";
        int best = 0;
        for (int i=1;i<c.length;i++) if (c[i] > c[best]) best = i;
        float ratio = c[best] / (float) total;
        if (best == 1 && ratio > .10f) return "Değişen";
        if (best == 2 && ratio > .10f) return "Lokal boyalı";
        if (best == 3 && ratio > .10f) return "Boyalı";
        if (best == 4 && ratio > .12f) return "Sök-tak";
        return "Orijinal";
    }

    // 0 diğer/beyaz, 1 kırmızı, 2 sarı, 3 mavi/mor, 4 gri
    private static int colorState(int color) {
        int r=Color.red(color), g=Color.green(color), b=Color.blue(color);
        if (r > 165 && g < 125 && b < 125 && r > g*1.35f) return 1;
        if (r > 165 && g > 145 && b < 120 && r+g > b*3) return 2;
        if (b > 115 && b > r*1.08f && b > g*1.08f && r < 190) return 3;
        int max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b));
        int avg=(r+g+b)/3;
        if (max-min < 24 && avg > 75 && avg < 205) return 4;
        return 0;
    }

    private static boolean plausibleVendor(String s) {
        if (!nonEmpty(s)) return false;
        String v=s.trim();
        if (v.length()<2 || v.length()>48) return false;
        int letters=0,digits=0,bad=0,words=0,shortWords=0;
        for(char ch:v.toCharArray()) {
            if(Character.isLetter(ch)) letters++;
            else if(Character.isDigit(ch)) digits++;
            else if(!Character.isWhitespace(ch) && "&.-/'".indexOf(ch)<0) bad++;
        }
        for(String w:v.split("\\s+")){ if(!w.isEmpty()){words++; if(w.length()==1) shortWords++;}}
        if (letters < 3 || bad > 2 || digits > letters) return false;
        if (words >= 4 && shortWords >= 2) return false;
        String n=norm(v);
        if (n.contains("bolic") || n.contains("taoa") || n.contains("vkale") || n.contains("orli")) return false;
        return true;
    }

    private static boolean plausibleSummary(String s) {
        if (!nonEmpty(s)) return false;
        String n=norm(s);
        return n.contains("boy") || n.contains("degis") || n.contains("orij") || n.contains("motor") || n.contains("fren") || n.contains("obd") || n.contains("airbag") || n.contains("sasi") || n.contains("podye");
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(TR).replace('ı','i').replace('ş','s').replace('ğ','g').replace('ü','u').replace('ö','o').replace('ç','c');
    }
    private static boolean nonEmpty(String s) { return s != null && !s.trim().isEmpty(); }
    private static String join(String sep, String... items) {
        StringBuilder b=new StringBuilder();
        for(String s:items){ if(!nonEmpty(s)) continue; if(b.length()>0)b.append(sep); b.append(s.trim()); }
        return b.toString();
    }
}
'''
(JAVA / 'VisualDocumentAnalyzer.java').write_text(visual, encoding='utf-8')

# ReceiptOcr: add binary + focused odometer OCR and visual post-processing.
p = JAVA / 'ReceiptOcr.java'
s = p.read_text(encoding='utf-8')
s = s.replace(
'''                        recycle(finalEnhanced, finalScaled, finalSource);\n                        finishImageRecognition(recognizer, originalText, enhancedText, callback);''',
'''                        recycle(finalEnhanced, finalScaled, finalSource);\n                        processFocusedImage(context, imageUri, recognizer, originalText, enhancedText, callback);''')
s = s.replace(
'''                        recycle(finalEnhanced, finalScaled, finalSource);\n                        finishImageRecognition(recognizer, originalText, "", callback);''',
'''                        recycle(finalEnhanced, finalScaled, finalSource);\n                        processFocusedImage(context, imageUri, recognizer, originalText, "", callback);''')
s = s.replace(
'''                finishImageRecognition(recognizer, originalText, "", callback);''',
'''                processFocusedImage(context, imageUri, recognizer, originalText, "", callback);''')

start = s.index('    private static void finishImageRecognition(')
end = s.index('    private static void recycle(', start)
new_block = r'''    private static void processFocusedImage(Context context, Uri imageUri, TextRecognizer recognizer,
                                            String original, String enhanced, Callback callback) {
        Bitmap source = null;
        try {
            try (InputStream in = context.getContentResolver().openInputStream(imageUri)) {
                source = BitmapFactory.decodeStream(in);
            }
            if (source == null) {
                finishImageRecognition(context, imageUri, recognizer, original, enhanced, "", "", callback);
                return;
            }
            int maxWidth = 1800;
            Bitmap work = source;
            if (source.getWidth() > maxWidth) {
                int h = Math.max(1, Math.round(source.getHeight() * (maxWidth / (float) source.getWidth())));
                work = Bitmap.createScaledBitmap(source, maxWidth, h, true);
            }
            final Bitmap finalSource = source;
            final Bitmap finalWork = work;
            final Bitmap binary = binaryBitmap(work);
            recognizer.process(InputImage.fromBitmap(binary, 0))
                    .addOnSuccessListener(t -> {
                        String binaryText = t == null ? "" : t.getText().trim();
                        int x = Math.max(0, (int)(finalWork.getWidth() * .22f));
                        int y = Math.max(0, (int)(finalWork.getHeight() * .48f));
                        int w = Math.max(1, Math.min(finalWork.getWidth() - x, (int)(finalWork.getWidth() * .56f)));
                        int h = Math.max(1, Math.min(finalWork.getHeight() - y, (int)(finalWork.getHeight() * .30f)));
                        Bitmap crop = Bitmap.createBitmap(finalWork, x, y, w, h);
                        Bitmap crop2 = Bitmap.createScaledBitmap(crop, Math.min(1800, crop.getWidth()*2), Math.min(900, crop.getHeight()*2), true);
                        crop.recycle();
                        recognizer.process(InputImage.fromBitmap(crop2, 0))
                                .addOnSuccessListener(ct -> {
                                    String focus = ct == null ? "" : ct.getText().trim();
                                    crop2.recycle(); binary.recycle();
                                    if (finalWork != finalSource && !finalWork.isRecycled()) finalWork.recycle();
                                    if (!finalSource.isRecycled()) finalSource.recycle();
                                    finishImageRecognition(context, imageUri, recognizer, original, enhanced, binaryText, focus, callback);
                                })
                                .addOnFailureListener(e -> {
                                    crop2.recycle(); binary.recycle();
                                    if (finalWork != finalSource && !finalWork.isRecycled()) finalWork.recycle();
                                    if (!finalSource.isRecycled()) finalSource.recycle();
                                    finishImageRecognition(context, imageUri, recognizer, original, enhanced, binaryText, "", callback);
                                });
                    })
                    .addOnFailureListener(e -> {
                        binary.recycle();
                        if (finalWork != finalSource && !finalWork.isRecycled()) finalWork.recycle();
                        if (!finalSource.isRecycled()) finalSource.recycle();
                        finishImageRecognition(context, imageUri, recognizer, original, enhanced, "", "", callback);
                    });
        } catch (Exception e) {
            if (source != null && !source.isRecycled()) source.recycle();
            finishImageRecognition(context, imageUri, recognizer, original, enhanced, "", "", callback);
        }
    }

    private static Bitmap binaryBitmap(Bitmap src) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        int w=src.getWidth(), h=src.getHeight();
        int stepSample=Math.max(1, Math.min(w,h)/500);
        long sum=0; long count=0;
        for(int y=0;y<h;y+=stepSample) for(int x=0;x<w;x+=stepSample){
            int c=src.getPixel(x,y); sum += (android.graphics.Color.red(c)*30 + android.graphics.Color.green(c)*59 + android.graphics.Color.blue(c)*11)/100; count++; }
        int threshold = count==0 ? 155 : (int)(sum/count);
        threshold = Math.max(115, Math.min(195, threshold));
        int[] row=new int[w];
        for(int y=0;y<h;y++){
            for(int x=0;x<w;x++){
                int c=src.getPixel(x,y); int lum=(android.graphics.Color.red(c)*30 + android.graphics.Color.green(c)*59 + android.graphics.Color.blue(c)*11)/100;
                row[x]= lum < threshold ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
            }
            out.setPixels(row,0,w,0,y,w,1);
        }
        return out;
    }

    private static void finishImageRecognition(Context context, Uri imageUri, TextRecognizer recognizer,
                                               String original, String enhanced, String binary, String focus,
                                               Callback callback) {
        try {
            StringBuilder all = new StringBuilder();
            if (original != null && !original.trim().isEmpty()) all.append(original.trim());
            if (enhanced != null && !enhanced.trim().isEmpty() && !enhanced.trim().equals(original == null ? "" : original.trim())) {
                if (all.length()>0) all.append("\n--- IKINCI_OKUMA ---\n"); all.append(enhanced.trim());
            }
            if (binary != null && !binary.trim().isEmpty()) {
                if (all.length()>0) all.append("\n--- YUKSEK_KONTRAST ---\n"); all.append(binary.trim());
            }
            if (focus != null && !focus.trim().isEmpty()) {
                if (all.length()>0) all.append("\n--- ODOMETRE_BOLGESI ---\n"); all.append(focus.trim());
            }
            String combined = all.toString().trim();
            if (combined.isEmpty()) { callback.onError(new IOException("Belgede okunabilir metin bulunamadı")); return; }
            RecordParser.Parsed parsed = RecordParser.fromText(combined);
            Bitmap visual = null;
            try {
                try (InputStream in = context.getContentResolver().openInputStream(imageUri)) { visual = BitmapFactory.decodeStream(in); }
                if (visual != null && visual.getWidth() > 1800) {
                    int hh=Math.max(1,Math.round(visual.getHeight()*(1800f/visual.getWidth())));
                    Bitmap scaled=Bitmap.createScaledBitmap(visual,1800,hh,true); visual.recycle(); visual=scaled;
                }
                VisualDocumentAnalyzer.applyImage(visual, combined, parsed);
            } catch (Exception ignored) {
                VisualDocumentAnalyzer.sanitizeTextOcr(combined, parsed);
            } finally {
                if (visual != null && !visual.isRecycled()) visual.recycle();
            }
            callback.onResult(parsed);
        } finally {
            recognizer.close();
        }
    }

'''
s = s[:start] + new_block + s[end:]

s = s.replace('else state.callback.onResult(RecordParser.fromText(text));',
'''else {\n                    RecordParser.Parsed parsed = RecordParser.fromText(text);\n                    VisualDocumentAnalyzer.sanitizeTextOcr(text, parsed);\n                    state.callback.onResult(parsed);\n                }''')
p.write_text(s, encoding='utf-8')

# RecordParser: never use arbitrary OCR garbage as vendor/company.
p = JAVA / 'RecordParser.java'
s = p.read_text(encoding='utf-8')
old = r'''        String[] lines = raw.split("\\r?\\n");
        for (String line : lines) {
            String v = line.trim();
            if (v.length() < 3 || v.length() > 48) continue;
            String n = normalize(v);
            if (containsAny(n, "fis", "fatura", "tarih", "saat", "toplam", "kdv", "vergi", "tutar", "pos", "terminal", "plaka", "sase", "vin", "musteri", "telefon", "tel:")) continue;
            int letters = 0, digits = 0;
            for (int i = 0; i < v.length(); i++) {
                char c = v.charAt(i);
                if (Character.isLetter(c)) letters++;
                else if (Character.isDigit(c)) digits++;
            }
            if (letters >= 4 && letters >= digits * 3) return tidyVendor(v);
        }
        return "";'''
new = r'''        String[] lines = raw.split("\\r?\\n");
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
        return "";'''
if old not in s:
    raise SystemExit('RecordParser vendor block not found')
s = s.replace(old, new)
p.write_text(s, encoding='utf-8')

# SmartDocumentAnalyzer: recover common fuel receipt numeric columns.
p = JAVA / 'SmartDocumentAnalyzer.java'
s = p.read_text(encoding='utf-8')
needle = '''        if (p.amount <= 0 && p.quantity > 0 && p.unitPrice > 0) p.amount = round2(p.quantity * p.unitPrice);\n        String date = labelledDate(raw, new String[]{"islem tarihi", "tarih"});'''
repl = '''        recoverFuelColumns(raw, p);\n        if (p.amount <= 0 && p.quantity > 0 && p.unitPrice > 0) p.amount = round2(p.quantity * p.unitPrice);\n        String date = labelledDate(raw, new String[]{"islem tarihi", "tarih"});'''
if needle not in s:
    raise SystemExit('fuel insertion point not found')
s = s.replace(needle, repl)
anchor = '    private static void enrichInsurance(String raw, String n, RecordParser.Parsed p) {'
method = r'''    private static void recoverFuelColumns(String raw, RecordParser.Parsed p) {
        String[] lines = raw.split("\\r?\\n");
        Pattern num = Pattern.compile("(?<!\\d)([0-9]{1,5}(?:[.,][0-9]{1,3})?)(?!\\d)");
        for (String line : lines) {
            String n = normalize(line);
            if (!(containsAny(n, "lt", "litre", "motorin", "benzin", "dizel", "lpg", "kursunsuz") || line.contains("×") || line.contains("*"))) continue;
            ArrayList<Double> vals = new ArrayList<>();
            Matcher m = num.matcher(line);
            while (m.find()) { double v = dec(m.group(1)); if (v > 0) vals.add(v); }
            if (vals.size() >= 3) {
                for (int i=0;i<vals.size();i++) for (int j=0;j<vals.size();j++) for (int k=0;k<vals.size();k++) {
                    if (i==j || i==k || j==k) continue;
                    double q=vals.get(i), u=vals.get(j), total=vals.get(k);
                    if (q<=0 || q>300 || u<5 || u>5000 || total<20 || total>100000) continue;
                    double calc=q*u;
                    if (Math.abs(calc-total) <= Math.max(3.0, total*0.08)) {
                        if (p.quantity<=0) p.quantity=q;
                        if (p.unitPrice<=0) p.unitPrice=u;
                        if (p.amount<=0) p.amount=total;
                        return;
                    }
                }
            }
        }
    }

'''
if anchor not in s:
    raise SystemExit('insurance anchor not found')
s = s.replace(anchor, method + anchor)
p.write_text(s, encoding='utf-8')

# Version bump.
p = ROOT / 'app/build.gradle'
s = p.read_text(encoding='utf-8')
s = re.sub(r"versionCode\s+\d+", "versionCode 18", s)
s = re.sub(r"versionName\s+'[^']+'", "versionName '0.14-visual-ocr'", s)
p.write_text(s, encoding='utf-8')

print('v0.14 visual OCR upgrade applied')
