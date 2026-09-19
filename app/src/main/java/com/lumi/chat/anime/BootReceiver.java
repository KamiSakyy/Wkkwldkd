package com.lumi.chat.anime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** После перезагрузки телефона заново ставит будильник проверки серий. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!com.lumi.chat.db.Db.get().subIds().isEmpty()) {
            AiringChecker.scheduleNext(context);
        }
    }
}
