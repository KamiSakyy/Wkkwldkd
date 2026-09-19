package com.lumi.chat.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.lumi.chat.MainActivity;
import com.lumi.chat.R;

import java.util.Calendar;
import java.util.Random;

/** Виджет Люми на главный экран: приветствие + кнопки. Пожелание №5 от Люми 💜 */
public class LumiWidget extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.lumi.chat.WIDGET_REFRESH";

    private static final String[] MORNING = {
            "Доброе утро! ☀️ Ты сегодня видел(а) классные сны? Расскажешь?",
            "Утро! ☕ Пока ты спал(а), я нашла пару новых артов ✨",
            "С добрым утром! 💜 Не забудь позавтракать, а потом — ко мне поболтать~"
    };
    private static final String[] DAY = {
            "Как проходит день? 😊 Есть минутка для аниме-новостей?",
            "Я тут скучаю~ Может, найдём новый тайтл? 📺",
            "Кстати, могу глянуть, когда выйдет твоя любимая серия ⏰"
    };
    private static final String[] EVENING = {
            "Вечер! 🌙 Самое время для артов и разговоров по душам~",
            "Хорошего вечера! ✨ Устал(а)? Я рядом 💜",
            "Может, сегодня что-то новенькое? Дай арт на ночь 🎨"
    };
    private static final String[] NIGHT = {
            "Ты ещё не спишь? 🌌 Я тоже сова~",
            "Тихая ночь… Расскажешь, что на душе? 💜",
            "Псст… Проверь расписание на завтра? 📺✨"
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(context));
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, LumiWidget.class));
            for (int id : ids) mgr.updateAppWidget(id, build(context));
        }
    }

    private static RemoteViews build(Context context) {
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_lumi);
        rv.setTextViewText(R.id.widgetText, phrase());
        rv.setTextViewText(R.id.widgetDate, dateLine());

        // тело виджета → открыть чат
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent piOpen = PendingIntent.getActivity(context, 11, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widgetRoot, piOpen);

        // кнопка ✨ → новая фраза
        Intent refresh = new Intent(context, LumiWidget.class);
        refresh.setAction(ACTION_REFRESH);
        PendingIntent piRef = PendingIntent.getBroadcast(context, 12, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widgetRefresh, piRef);
        return rv;
    }

    private static String phrase() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String[] pool;
        if (h >= 5 && h < 12) pool = MORNING;
        else if (h >= 12 && h < 18) pool = DAY;
        else if (h >= 18 && h < 23) pool = EVENING;
        else pool = NIGHT;
        return pool[new Random().nextInt(pool.length)];
    }

    private static String dateLine() {
        Calendar c = Calendar.getInstance();
        String[] months = {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"};
        String[] days = {"вс", "пн", "вт", "ср", "чт", "пт", "сб"};
        return days[c.get(Calendar.DAY_OF_WEEK) - 1] + ", "
                + c.get(Calendar.DAY_OF_MONTH) + " " + months[c.get(Calendar.MONTH)];
    }
}
