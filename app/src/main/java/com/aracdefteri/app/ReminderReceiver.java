package com.aracdefteri.app;

import android.app.*;
import android.content.*;
import android.database.Cursor;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL_ID="vehicle_reminders_v2";
    @Override public void onReceive(Context c,Intent intent){
        long id=intent.getLongExtra("record_id",-1);if(id<0)return;
        try(AppDatabase db=new AppDatabase(c);Cursor row=db.getReadableDatabase().rawQuery("SELECT r.title,r.type,r.status,r.completed,r.next_date,v.archived,v.brand,v.model FROM records r JOIN vehicle v ON v.id=r.vehicle_id WHERE r.id=?",new String[]{""+id})){
            if(!row.moveToFirst()||row.getInt(3)!=0||row.getInt(5)!=0)return;
            AppDatabase.Record r=new AppDatabase.Record();r.type=row.getString(1);r.status=row.getString(2);if(!RecordRules.pending(r))return;
            long days=RecordRules.days(row.getString(4));if(days<0||days>7)return;
            show(c,(int)id,row.getString(6)+" "+row.getString(7)+" • "+row.getString(0),days==0?"Bugün zamanı geldi":days+" gün kaldı",id);
        }
    }
    static boolean show(Context c,int id,String title,String message,long recordId){
        ReminderScheduler.ensureChannel(c);NotificationManager nm=c.getSystemService(NotificationManager.class);if(!nm.areNotificationsEnabled())return false;
        Intent open=new Intent(c,NextMainActivity.class).putExtra("record_id",recordId).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending=PendingIntent.getActivity(c,id,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try{nm.notify(id,new Notification.Builder(c,CHANNEL_ID).setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message)).setContentIntent(pending).setAutoCancel(true).build());return true;}catch(SecurityException e){return false;}
    }
}
