package com.aracdefteri.app;

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
