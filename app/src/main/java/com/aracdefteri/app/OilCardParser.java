package com.aracdefteri.app;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Printed checklist names do not prove that maintenance was performed. */
public final class OilCardParser {
    private OilCardParser(){}
    private static final String[][] PARTS={{"motor yagi","Motor yağı"},{"san. yagi","Şanzıman yağı"},{"sanziman yagi","Şanzıman yağı"},{"def. yagi","Diferansiyel yağı"},{"diferansiyel yagi","Diferansiyel yağı"},{"yag filtresi","Yağ filtresi"},{"mazot filtresi","Yakıt filtresi"},{"yakit filtresi","Yakıt filtresi"},{"hava filtresi","Hava filtresi"},{"polen filtresi","Polen filtresi"}};
    public static boolean isCard(String raw){String n=DocumentLayout.norm(raw);return (n.contains("degistigi")&&n.contains("degisecegi"))||n.contains("yag degisim kart")||n.contains("bakim kart");}
    public static void apply(String raw,RecordParser.Parsed p){
        if(!isCard(raw))return;
        p.type="Bakım";p.documentKind="MAINTENANCE";p.maintenanceSubtype="Yağ bakımı";p.km=0;p.nextKm=0;p.amount=0;p.maintenanceParts.clear();p.vendor="";
        List<String> summary=new ArrayList<>();int common=0;boolean different=false;
        boolean nextFirst=DocumentLayout.norm(raw).indexOf("degisecegi")<DocumentLayout.norm(raw).indexOf("degistigi");
        // Printed part labels frequently span two lines inside the same table cell.
        raw=raw.replaceAll("(?iu)(motor|şan\\.|san\\.|şanzıman|def\\.|diferansiyel|yağ|mazot|yakıt|hava|polen)[ \\t]*\\r?\\n[ \\t]*(yağı|yagi|filtresi)", "$1 $2");
        for(String line:raw.split("\\r?\\n")){
            String n=DocumentLayout.norm(line);
            if(n.contains("yag cinsi")){Matcher oil=Pattern.compile("(?i)\\b(?:0|5|10|15|20)\\s*W\\s*[-/]?\\s*(?:20|30|40|50|60)\\b").matcher(line);if(oil.find())summary.add("Yağ cinsi: "+oil.group());}
            for(String[] part:PARTS){
                if(!n.contains(part[0]))continue;
                Matcher nums=Pattern.compile("(?<![0-9])(?:[0-9]{1,3}(?:[.,][0-9]{3})+|[0-9]{3,7})(?![0-9])").matcher(line);
                List<Integer> km=new ArrayList<>();while(nums.find()){int v=DocumentLayout.wholeKm(nums.group());if(v>0)km.add(v);}
                if(km.size()!=2)continue;
                int done=km.get(nextFirst?1:0),next=km.get(nextFirst?0:1);
                if(next<=done){p.warnings.add(part[1]+": sonraki km değişim km'sinden büyük olmalı.");continue;}
                p.maintenanceParts.add(part[1]);summary.add(part[1]+": değiştiği km "+done+", değişeceği km "+next);
                if(common==0)common=done;else if(common!=done)different=true;p.nextKm=p.nextKm==0?next:Math.min(next,p.nextKm);break;
            }
        }
        p.km=different?0:common;p.detailSummary=String.join("\n",summary);
        if(p.maintenanceParts.isEmpty())p.warnings.add("Kartın el yazısı/değişim değerleri okunamadı. Boş satırlar bakım sayılmadı.");
        if(different)p.warnings.add("Parçaların değişim kilometreleri farklı; kayıt kilometresini kontrol et.");
    }
}
