package com.lumi.chat;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;

import com.lumi.chat.anime.AiringChecker;
import com.lumi.chat.db.Db;
import com.lumi.chat.img.ImageLoader;

/** Точка входа: инициализация БД, кэша картинок, каналов уведомлений. */
public class App extends Application {
    public static final String CH_EPISODES = "episodes";
    public static final String CH_GENERAL = "general";

    @Override
    public void onCreate() {
        super.onCreate();
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
    }
}
