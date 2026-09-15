package com.aracdefteri.app;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        PendingResult pending=goAsync();new Thread(()->{try(AppDatabase db=new AppDatabase(c)){ReminderScheduler.rescheduleAll(c,db);}finally{pending.finish();}},"restore-reminders").start();
    }
}
