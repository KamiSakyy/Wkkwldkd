package com.lumi.chat.chat;

import android.graphics.Color;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import java.util.ArrayList;
import java.util.List;

/** Список сообщений: мои / Люми-текст / Люми с картинками. */
public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Cb {
        void onTts(String text);
        void onImage(Models.ArtItem a);
    }

    public final List<Models.Msg> items = new ArrayList<>();
    private final Cb cb;

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
            ((MeVh) h).text.setText(Md.toSpan(m.content));
            ((MeVh) h).text.setOnLongClickListener(v -> {
                Saver.copy(v.getContext(), m.content);
                return true;
            });
        } else {
            LumiVh vh = (LumiVh) h;
            String display = "images".equals(m.kind) ? m.intro() : m.content;
            if (m.pending && (display == null || display.isEmpty())) display = "Печатает…";
            vh.text.setText(Md.toSpan(display == null ? "" : display));
            vh.text.setMovementMethod(LinkMovementMethod.getInstance());
            vh.text.setOnLongClickListener(v -> {
                Saver.copy(v.getContext(), display == null ? "" : display);
                return true;
            });

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
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                        lp.setMargins(c == 0 ? 0 : 6, 6, c == 0 ? 6 : 0, 0);
                        iv.setLayoutParams(lp);
                        iv.setBackgroundResource(R.drawable.bg_card);
                        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        iv.getLayoutParams().height = 0;
                        // фиксированные квадраты:
                        lr.addView(iv);
                        ImageLoader.get().load(a.url, iv);
                        iv.setOnClickListener(v -> cb.onImage(a));
                    }
                    // задаём высоту тайлам после добавления
                    for (int c = 0; c < lr.getChildCount(); c++) {
                        View iv = lr.getChildAt(c);
                        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) iv.getLayoutParams();
                        lp.height = 300;
                        iv.setLayoutParams(lp);
                    }
                    vh.grid.addView(lr);
                }
            } else {
                vh.grid.setVisibility(View.GONE);
            }

            vh.tts.setVisibility(m.pending ? View.GONE : View.VISIBLE);
            vh.tts.setOnClickListener(v -> cb.onTts(Md.plain("images".equals(m.kind) ? m.intro() : m.content)));
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

    public Models.Msg last() { return items.isEmpty() ? null : items.get(items.size() - 1); }

    static class MeVh extends RecyclerView.ViewHolder {
        final TextView text;
        MeVh(View v) { super(v); text = v.findViewById(R.id.text); }
    }

    static class LumiVh extends RecyclerView.ViewHolder {
        final TextView text, tts;
        final LinearLayout grid;
        LumiVh(View v) {
            super(v);
            text = v.findViewById(R.id.text);
            grid = v.findViewById(R.id.imgGrid);
            tts = v.findViewById(R.id.btnTts);
        }
    }

    private static void Saver_copy() {} // заглушка
    private static void Saver_copy2() {}
}
