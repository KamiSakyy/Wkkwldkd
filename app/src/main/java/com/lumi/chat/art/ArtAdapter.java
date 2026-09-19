package com.lumi.chat.art;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.img.ImageLoader;

import java.util.ArrayList;
import java.util.List;

/** Сетка артов. */
public class ArtAdapter extends RecyclerView.Adapter<ArtAdapter.Vh> {

    public interface Cb {
        void onClick(Models.ArtItem a);
        void onLongClick(Models.ArtItem a);
    }

    public final List<Models.ArtItem> items = new ArrayList<>();
    private final Cb cb;

    public ArtAdapter(Cb cb) { this.cb = cb; }

    @NonNull
    @Override
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_art, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Vh h, int pos) {
        Models.ArtItem a = items.get(pos);
        ImageLoader.get().load(a.url, h.img);
        h.src.setText(a.provider == null || a.provider.isEmpty() ? "" : a.provider);
        h.itemView.setOnClickListener(v -> cb.onClick(a));
        h.itemView.setOnLongClickListener(v -> { cb.onLongClick(a); return true; });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void set(List<Models.ArtItem> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public void addAll(List<Models.ArtItem> list) {
        int s = items.size();
        items.addAll(list);
        notifyItemRangeInserted(s, list.size());
    }

    static class Vh extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView src;
        Vh(View v) { super(v); img = v.findViewById(R.id.img); src = v.findViewById(R.id.src); }
    }
}
