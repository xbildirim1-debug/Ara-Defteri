package com.aracdefteri.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import java.time.*;
import java.util.*;

public final class ReminderScheduler {
    private ReminderScheduler(){}
    public static void ensureChannel(Context c){
        NotificationChannel ch=new NotificationChannel(ReminderReceiver.CHANNEL_ID,"Araç hatırlatmaları",NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription("Bakım, muayene, MTV ve poliçe hatırlatmaları");c.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
    public static void schedule(Context c,long id,String title,String value){
        cancel(c,id);LocalDate date=RecordRules.date(value);if(date==null)return;
        for(int before:new int[]{7,1}){
            long at=date.minusDays(before).atTime(9,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();if(at<=System.currentTimeMillis())continue;
            Intent i=new Intent(c,ReminderReceiver.class).putExtra("record_id",id).putExtra("days",before);
            PendingIntent p=PendingIntent.getBroadcast(c,code(id,before),i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            c.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,p);
        }
    }
    private static int code(long id,int days){return (int)((id*10+days)%Integer.MAX_VALUE);}
    public static void cancel(Context c,long id){
        for(int before:new int[]{7,1}){
            PendingIntent p=PendingIntent.getBroadcast(c,code(id,before),new Intent(c,ReminderReceiver.class),PendingIntent.FLAG_NO_CREATE|PendingIntent.FLAG_IMMUTABLE);
            if(p!=null){c.getSystemService(AlarmManager.class).cancel(p);p.cancel();}
        }
        c.getSystemService(NotificationManager.class).cancel((int)id);
    }
    public static void rescheduleAll(Context c,AppDatabase db){
        SharedPreferences prefs=c.getSharedPreferences("garage",0);
        for(String old:prefs.getStringSet("scheduled",new HashSet<>()))try{cancel(c,Long.parseLong(old));}catch(NumberFormatException ignored){}
        Set<String> ids=new HashSet<>();
        try(Cursor cur=db.getReadableDatabase().rawQuery("SELECT r.id,r.title,r.next_date,r.next_km,r.type,r.status,v.km,v.brand,v.model FROM records r JOIN vehicle v ON v.id=r.vehicle_id WHERE r.completed=0 AND v.archived=0",null)){
            while(cur.moveToNext()){
                AppDatabase.Record r=new AppDatabase.Record();r.id=cur.getLong(0);r.title=cur.getString(1);r.nextDate=cur.getString(2);r.nextKm=cur.getInt(3);r.type=cur.getString(4);r.status=cur.getString(5);
                if(!RecordRules.pending(r))continue;ids.add(""+r.id);schedule(c,r.id,r.title,r.nextDate);
                long days=RecordRules.days(r.nextDate);boolean kmDue=r.nextKm>0&&cur.getInt(6)>=r.nextKm;
                String signature=r.nextDate+"/"+r.nextKm+"/"+(kmDue?"km":days<=1?"1":"7");
                if((kmDue||(days>=0&&days<=7))&&!signature.equals(prefs.getString("notified_"+r.id,""))){
                    if(ReminderReceiver.show(c,(int)r.id,cur.getString(7)+" "+cur.getString(8)+" • "+r.title,RecordRules.dueLabel(r,cur.getInt(6)),r.id))prefs.edit().putString("notified_"+r.id,signature).apply();
                }
            }
        }prefs.edit().putStringSet("scheduled",ids).apply();
    }
    public static void test(Context c){ReminderReceiver.show(c,99881,"Araç Defteri hazır","Bildirimler çalışıyor.",-1);}
}
