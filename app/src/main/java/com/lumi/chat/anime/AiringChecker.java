package com.lumi.chat.anime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.lumi.chat.MainActivity;
import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.db.Db;

import java.util.List;

/** Фоновая проверка выхода новых серий (AlarmManager, каждые ~30 минут). */
public class AiringChecker extends BroadcastReceiver {

    public static final long INTERVAL = 30 * 60 * 1000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        com.lumi.chat.net.Http.POOL.execute(() -> tick(context));
    }

    /** Проверяет подписки и шлёт уведомления о новых сериях. */
    public static void tick(Context context) {
        try {
            List<long[]> subs = Db.get().subIds();
            if (subs.isEmpty()) return;
            long now = System.currentTimeMillis() / 1000L;
            long last = safeLast();
            long from = last > 0 ? last : now - 3600;
            for (long[] sub : subs) {
                Models.AiringItem it = AnimeApi.byId(sub[0]);
                if (it == null || !it.airing) continue;
                int ep = it.episode;
                if (ep <= 0) continue;
                // серия N выходит сейчас: timeUntilAiring — до серии N
                boolean justAired = it.timeUntil <= 900 && it.timeUntil >= -3600;
                boolean missed = from > 0 && it.airingAt > from && it.airingAt <= now;
                if (ep > sub[1] && (justAired || missed || sub[1] == 0)) {
                    notify(context, it.title == null ? "Аниме" : it.title, ep, it.siteUrl == null ? "" : it.siteUrl);
                    Db.get().setSubLastEp(sub[0], ep);
                }
            }
            Db.get().kvSet("lastCheck", String.valueOf(now));
        } catch (Exception ignore) {}
        scheduleNext(context);
    }

    private static long safeLast() {
        try { return Long.parseLong(Db.get().kvGet("lastCheck", "0")); } catch (Exception e) { return 0L; }
    }

    private static void notify(Context ctx, String title, int ep, String url) {
        try {
            // Пожелание Люми №3: уведомление открывает чат с готовой темой для обсуждения
            Intent open = new Intent(ctx, MainActivity.class);
            open.putExtra("open", "chat");
            open.putExtra("discuss", title + " — серия " + ep + " уже вышла! Обсудим? 💬");
            PendingIntent pi = PendingIntent.getActivity(ctx, (int) (System.currentTimeMillis() % 100000),
                    open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            androidx.core.app.NotificationCompat.Builder b = new androidx.core.app.NotificationCompat.Builder(ctx, com.lumi.chat.App.CH_EPISODES)
                    .setSmallIcon(R.drawable.ic_tv)
                    .setContentTitle("Новая серия! 🎉")
                    .setContentText(title + " — серия " + ep + " уже вышла")
                    .setStyle(new androidx.core.app.NotificationCompat.BigTextStyle().bigText(title + " — серия " + ep + " уже вышла. Тапни — и обсудим с Люми ✨"))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.notify(("ep" + title).hashCode(), b.build());
        } catch (Exception ignore) {}
    }

    public static void scheduleNext(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(context, AiringChecker.class);
            PendingIntent pi = PendingIntent.getBroadcast(context, 42, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + INTERVAL, INTERVAL, pi);
        } catch (Exception ignore) {}
    }
}
