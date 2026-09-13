package com.aracdefteri.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class ReminderScheduler {
    private ReminderScheduler() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                "Araç hatırlatmaları",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Bakım, sigorta, muayene, vergi ve diğer araç hatırlatmaları");
        channel.enableVibration(true);
        manager.createNotificationChannel(channel);
    }

    public static void schedule(Context context, long recordId, String title, String dateText) {
        cancel(context, recordId);
        if (dateText == null || dateText.trim().isEmpty()) return;
        try {
            Date parsed = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateText.trim());
            if (parsed == null) return;

            Calendar due = Calendar.getInstance();
            due.setTime(parsed);
            due.set(Calendar.HOUR_OF_DAY, 9);
            due.set(Calendar.MINUTE, 0);
            due.set(Calendar.SECOND, 0);
            due.set(Calendar.MILLISECOND, 0);

            scheduleOne(context, recordId, title, due, 7);
            scheduleOne(context, recordId, title, due, 1);

            long dayDiff = calendarDayDiff(Calendar.getInstance(), due);
            if (dayDiff >= 0 && dayDiff <= 7) {
                String message;
                if (dayDiff == 0) message = "Bugün zamanı geldi. Araç Defteri kaydını kontrol et.";
                else if (dayDiff == 1) message = "Yarın zamanı geliyor. Araç Defteri kaydını kontrol et.";
                else message = dayDiff + " gün kaldı. Araç Defteri kaydını kontrol et.";
                notifyNow(context, title + " yaklaşıyor", message, (int)(recordId % Integer.MAX_VALUE));
            }
        } catch (Exception ignored) { }
    }

    private static void scheduleOne(Context context, long recordId, String title, Calendar due, int daysBefore) {
        Calendar trigger = (Calendar) due.clone();
        trigger.add(Calendar.DAY_OF_YEAR, -daysBefore);
        if (trigger.getTimeInMillis() <= System.currentTimeMillis()) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("title", title + " yaklaşıyor");
        intent.putExtra("text", daysBefore + " gün kaldı. Araç Defteri kaydını kontrol et.");
        int requestCode = requestCode(recordId, daysBefore);
        PendingIntent pending = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pending);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, trigger.getTimeInMillis(), pending);
        }
    }

    public static void cancel(Context context, long recordId) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int days : new int[]{7, 1}) {
            Intent intent = new Intent(context, ReminderReceiver.class);
            PendingIntent pending = PendingIntent.getBroadcast(
                    context,
                    requestCode(recordId, days),
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (pending != null) {
                alarm.cancel(pending);
                pending.cancel();
            }
        }
    }

    public static void test(Context context) {
        notifyNow(context, "Araç Defteri hazır", "Bildirimler çalışıyor. Yaklaşan kayıtları burada göreceksin.", 99881);
    }

    private static void notifyNow(Context context, String title, String text, int id) {
        ensureChannel(context);
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra("title", title);
        intent.putExtra("text", text);
        intent.putExtra("notification_id", id);
        context.sendBroadcast(intent);
    }

    private static int requestCode(long recordId, int days) {
        long raw = recordId * 100L + days;
        return (int)(Math.abs(raw) % Integer.MAX_VALUE);
    }

    private static long calendarDayDiff(Calendar now, Calendar due) {
        Calendar a = (Calendar) now.clone();
        Calendar b = (Calendar) due.clone();
        a.set(Calendar.HOUR_OF_DAY, 0); a.set(Calendar.MINUTE, 0); a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0);
        b.set(Calendar.HOUR_OF_DAY, 0); b.set(Calendar.MINUTE, 0); b.set(Calendar.SECOND, 0); b.set(Calendar.MILLISECOND, 0);
        return (b.getTimeInMillis() - a.getTimeInMillis()) / 86400000L;
    }
}
