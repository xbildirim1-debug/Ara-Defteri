package com.aracdefteri.app;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Pure date and accounting rules, shared by the home screen and reminders. */
public final class RecordRules {
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);
    public static LocalDate date(String value) { try{return LocalDate.parse(value,DATE);}catch(Exception e){return null;} }
    public static boolean pending(AppDatabase.Record r) {
        return !r.completed && !("Vergi".equals(r.type)&&"Ödendi".equals(r.status))
                && !("Sigorta/Kasko".equals(r.type)&&"İptal".equals(r.status));
    }
    public static long days(String value) { LocalDate d=date(value);return d==null?Long.MAX_VALUE:ChronoUnit.DAYS.between(LocalDate.now(),d); }
    public static long priority(AppDatabase.Record r,int km) {
        if(!pending(r))return Long.MAX_VALUE;
        if(r.nextKm>0&&r.nextKm<=km)return Long.MIN_VALUE+1;
        return days(r.nextDate);
    }
    public static String dueLabel(AppDatabase.Record r,int km) {
        List<String> parts=new ArrayList<>();
        long days=days(r.nextDate);
        if(days!=Long.MAX_VALUE)parts.add(days<0?(-days)+" gün gecikti":days==0?"Bugün":days==1?"Yarın":days+" gün kaldı");
        if(r.nextKm>0){int left=r.nextKm-km;parts.add(left<0?(-left)+" km aşıldı":left==0?"Kilometre zamanı geldi":left+" km kaldı");}
        return String.join(" • ",parts);
    }
    public static final class Consumption {
        public String fuel,unit; public double quantity,cost; public int distance,intervals;
        public double per100(){return distance>0?quantity*100/distance:0;}
        public double perKm(){return distance>0?cost/distance:0;}
    }
    public static List<Consumption> consumption(List<AppDatabase.Record> records) {
        Map<String,List<AppDatabase.Record>> groups=new LinkedHashMap<>();
        for(AppDatabase.Record r:records)if("Yakıt".equals(r.type))groups.computeIfAbsent(r.subtype,k->new ArrayList<>()).add(r);
        List<Consumption> out=new ArrayList<>();
        for(Map.Entry<String,List<AppDatabase.Record>> entry:groups.entrySet()) {
            List<AppDatabase.Record> rows=entry.getValue();rows.sort(Comparator.comparingInt((AppDatabase.Record r)->r.km).thenComparingLong(r->r.id));
            Consumption c=new Consumption();c.fuel=entry.getKey();c.unit="Elektrik".equals(c.fuel)?"kWh":"L";
            // Battery percentage and charging losses are unknown; do not claim EV consumption.
            if("Elektrik".equals(c.fuel))continue;
            AppDatabase.Record anchor=null;double q=0,cost=0;int lastKm=-1;
            for(AppDatabase.Record r:rows){
                if(r.km==lastKm){anchor=null;q=cost=0;continue;}lastKm=r.km;
                if(anchor==null){if("full".equals(r.status))anchor=r;continue;}
                if(r.quantity<=0||r.km<=anchor.km){anchor=null;q=cost=0;continue;}
                q+=r.quantity;cost+=r.cost;
                if("full".equals(r.status)){c.quantity+=q;c.cost+=cost;c.distance+=r.km-anchor.km;c.intervals++;anchor=r;q=cost=0;}
            }
            if(c.intervals>0)out.add(c);
        }return out;
    }
}
