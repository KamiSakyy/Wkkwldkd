package com.lumi.chat.chat;

import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.img.ImageLoader;
import com.lumi.chat.util.Md;
import com.lumi.chat.util.Saver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Список сообщений: мои / Люми-текст / Люми с картинками. */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Cb {
        void onTts(String text);
        void onImage(Models.ArtItem a);
        void onRetry();
    }

    public final List<Models.Msg> items = new ArrayList<>();
    private final Cb cb;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public ChatAdapter(Cb cb) { this.cb = cb; }

    private static final int T_ME = 0, T_LUMI = 1;

    @Override
    public int getItemViewType(int pos) {
        return "user".equals(items.get(pos).role) ? T_ME : T_LUMI;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == T_ME) {
            return new MeVh(inf.inflate(R.layout.item_msg_me, parent, false));
        }
        return new LumiVh(inf.inflate(R.layout.item_msg_lumi, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
        Models.Msg m = items.get(pos);
        if (h instanceof MeVh) {
            MeVh vh = (MeVh) h;
            vh.text.setText(Md.toSpan(m.content));
            vh.text.setOnLongClickListener(v -> {
                Saver.copy(v.getContext(), m.content);
                return true;
            });
            vh.time.setText(m.pending ? "" : timeFmt.format(new Date(m.ts)));
        } else {
            LumiVh vh = (LumiVh) h;
            String display = "images".equals(m.kind) ? m.intro() : m.content;
            if (m.pending && (display == null || display.isEmpty())) display = "";
            final String shown = display == null ? "" : display;
            vh.text.setText(Md.toSpan(shown));
            vh.text.setMovementMethod(LinkMovementMethod.getInstance());
            vh.text.setOnLongClickListener(v -> {
                Saver.copy(v.getContext(), shown);
                return true;
            });

            boolean waiting = m.pending && shown.isEmpty();
            vh.dots.setVisibility(waiting ? View.VISIBLE : View.GONE);
            vh.text.setVisibility(m.pending && shown.isEmpty() ? View.GONE : View.VISIBLE);
            vh.dots.clearAnimation();
            if (waiting) {
                AlphaAnimation blink = new AlphaAnimation(0.25f, 1f);
                blink.setDuration(900);
                blink.setRepeatMode(Animation.REVERSE);
                blink.setRepeatCount(Animation.INFINITE);
                vh.dots.startAnimation(blink);
            }

            // сетка картинок
            vh.grid.removeAllViews();
            ArrayList<Models.ArtItem> imgs = "images".equals(m.kind) ? m.images() : new ArrayList<>();
            if (!imgs.isEmpty()) {
                vh.grid.setVisibility(View.VISIBLE);
                int cols = 2;
                for (int row = 0; row < (imgs.size() + cols - 1) / cols; row++) {
                    LinearLayout lr = new LinearLayout(vh.grid.getContext());
                    lr.setOrientation(LinearLayout.HORIZONTAL);
                    for (int c = 0; c < cols; c++) {
                        int idx = row * cols + c;
                        if (idx >= imgs.size()) break;
                        Models.ArtItem a = imgs.get(idx);
                        ImageView iv = new ImageView(vh.grid.getContext());
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, 300, 1f);
                        lp.setMargins(c == 0 ? 0 : 6, 6, c == 0 ? 6 : 0, 0);
                        iv.setLayoutParams(lp);
                        iv.setBackgroundResource(R.drawable.bg_tile_rounded);
                        iv.setClipToOutline(true);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        lr.addView(iv);
                        ImageLoader.get().load(a.url, iv);
                        iv.setOnClickListener(v -> cb.onImage(a));
                    }
                    vh.grid.addView(lr);
                }
            } else {
                vh.grid.setVisibility(View.GONE);
            }

            vh.time.setText(m.pending ? "" : timeFmt.format(new Date(m.ts)));
            vh.tts.setVisibility(m.pending ? View.GONE : View.VISIBLE);
            vh.tts.setOnClickListener(v -> cb.onTts(Md.plain(shown)));
            boolean failed = !m.pending && shown.startsWith("Не получилось");
            vh.retry.setVisibility(failed ? View.VISIBLE : View.GONE);
            vh.retry.setOnClickListener(v -> cb.onRetry());
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void add(Models.Msg m) {
        items.add(m);
        notifyItemInserted(items.size() - 1);
    }

    public void updateLast(String content, String kind, boolean pending) {
        if (items.isEmpty()) return;
        int idx = items.size() - 1;
        Models.Msg m = items.get(idx);
        m.content = content;
        if (kind != null) m.kind = kind;
        m.pending = pending;
        notifyItemChanged(idx);
    }

    public void removeLast() {
        if (items.isEmpty()) return;
        int idx = items.size() - 1;
        items.remove(idx);
        notifyItemRemoved(idx);
    }

    public Models.Msg last() { return items.isEmpty() ? null : items.get(items.size() - 1); }

    static class MeVh extends RecyclerView.ViewHolder {
        final TextView text, time;
        MeVh(View v) {
            super(v);
            text = v.findViewById(R.id.text);
            time = v.findViewById(R.id.time);
        }
    }

    static class LumiVh extends RecyclerView.ViewHolder {
        final TextView text, tts, time, retry, dots;
        final LinearLayout grid;
        LumiVh(View v) {
            super(v);
            text = v.findViewById(R.id.text);
            grid = v.findViewById(R.id.imgGrid);
            tts = v.findViewById(R.id.btnTts);
            time = v.findViewById(R.id.time);
            retry = v.findViewById(R.id.btnRetry);
            dots = v.findViewById(R.id.dots);
        }
    }
}
