package com.lumi.chat.anime;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.db.Db;
import com.lumi.chat.img.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Список аниме: расписание/поиск + подписка на уведомления. */
public class AnimeAdapter extends RecyclerView.Adapter<AnimeAdapter.Vh> {

    public final List<Models.AiringItem> items = new ArrayList<>();

    @NonNull
    @Override
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_airing, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Vh h, int pos) {
        Models.AiringItem a = items.get(pos);
        ImageLoader.get().load(a.cover, h.cover);
        h.title.setText(a.title == null ? "" : a.title);
        if (a.airing) {
            h.when.setText("🔔 Серия " + a.episode + (a.timeUntil > 0 ? " через " + AnimeApi.humanize(a.timeUntil) : " скоро"));
            h.when.setTextColor(0xFF34D399);
        } else {
            String t = "Серий: " + (a.episode > 0 ? a.episode : "?") + " · завершено";
            h.when.setText(t);
            h.when.setTextColor(0xFF9A9AB0);
        }

        boolean sub = Db.get().isSub(a.mediaId);
        h.bell.setColorFilter(sub ? 0xFFA78BFA : 0xFF9A9AB0);
        h.bell.setOnClickListener(v -> {
            boolean s = Db.get().isSub(a.mediaId);
            if (s) {
                Db.get().delSub(a.mediaId);
                h.bell.setColorFilter(0xFF9A9AB0);
                Toast.makeText(v.getContext(), "Отписалась от «" + a.title + "» 🔕", Toast.LENGTH_SHORT).show();
            } else {
                Db.get().addSub(a.mediaId, a.title, a.cover);
                AiringChecker.scheduleNext(v.getContext());
                h.bell.setColorFilter(0xFFA78BFA);
                Toast.makeText(v.getContext(), "Подписалась! Пришлю уведомление о новой серии 🔔", Toast.LENGTH_SHORT).show();
            }
        });

        h.itemView.setOnClickListener(v -> {
            if (a.siteUrl != null && !a.siteUrl.isEmpty()) com.lumi.chat.util.Saver.openBrowser(v.getContext(), a.siteUrl);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void set(List<Models.AiringItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    static class Vh extends RecyclerView.ViewHolder {
        final ImageView cover;
        final TextView title, when;
        final ImageButton bell;
        Vh(View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            title = v.findViewById(R.id.title);
            when = v.findViewById(R.id.when);
            bell = v.findViewById(R.id.btnBell);
        }
    }
}
