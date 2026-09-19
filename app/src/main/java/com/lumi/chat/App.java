package com.lumi.chat;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.lumi.chat.anime.AiringChecker;
import com.lumi.chat.db.Db;
import com.lumi.chat.img.ImageLoader;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;

/** Точка входа: инициализация БД, кэша картинок, каналов уведомлений + журнал сбоев. */
public class App extends Application {
    public static final String CH_EPISODES = "episodes";
    public static final String CH_GENERAL = "general";

    private static App instance;

    public static App get() { return instance; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Db.init(this);
        ImageLoader.init(this);

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CH_EPISODES,
                "Новые серии аниме", NotificationManager.IMPORTANCE_HIGH));
        nm.createNotificationChannel(new NotificationChannel(CH_GENERAL,
                "Lumi", NotificationManager.IMPORTANCE_DEFAULT));

        if (!Db.get().subIds().isEmpty()) {
            AiringChecker.scheduleNext(this);
        }

        // Журнал сбоев: любое падение пишем в файл, чтобы видеть причину (показывается в Настройках)
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                FileWriter fw = new FileWriter(new File(getFilesDir(), "lumi-crash.log"), true);
                fw.append("=== ").append(new Date().toString()).append(" ===\n")
                  .append(android.util.Log.getStackTraceString(e)).append("\n\n");
                fw.close();
            } catch (Exception ignore) {}
            android.os.Process.killProcess(android.os.Process.myPid());
        });
    }

    /** Хвост журнала сбоев (для показа в настройках). */
    public static String crashTail() {
        try {
            File f = new File(instance.getFilesDir(), "lumi-crash.log");
            if (!f.exists() || f.length() == 0) return "";
            java.io.RandomAccessFile raf = new java.io.RandomAccessFile(f, "r");
            long skip = Math.max(0, f.length() - 600);
            raf.seek(skip);
            byte[] buf = new byte[600];
            int n = raf.read(buf);
            raf.close();
            String s = new String(buf, 0, Math.max(0, n)).replaceAll("\\s+", " ");
            return s.length() > 400 ? s.substring(s.length() - 400) : s;
        } catch (Exception e) { return ""; }
    }
}
