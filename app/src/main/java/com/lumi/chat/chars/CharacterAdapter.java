package com.lumi.chat.chars;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.db.Db;
import com.lumi.chat.img.ImageLoader;
import com.lumi.chat.util.Saver;

import java.util.ArrayList;
import java.util.List;

/** Список персонажей. */
public class CharacterAdapter extends RecyclerView.Adapter<CharacterAdapter.Vh> {

    public interface Cb {
        void onWantArts(String name);
    }

    public final List<Models.CharacterInfo> items = new ArrayList<>();
    private final Cb cb;

    public CharacterAdapter(Cb cb) { this.cb = cb; }

    @NonNull
    @Override
    public Vh onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Vh(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_character, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Vh h, int pos) {
        Models.CharacterInfo c = items.get(pos);
        ImageLoader.get().load(c.image, h.img);
        h.name.setText(c.name == null ? "" : c.name);
        h.nativeName.setText(c.nativeName == null ? "" : c.nativeName);
        h.anime.setText(c.animeTitle == null ? "" : "📺 " + c.animeTitle);
        h.desc.setText(c.description == null || c.description.isEmpty()
                ? "Описание не нашлось, но выглядит интересно ✨" : c.description);
        h.desc.setMaxLines(h.expanded ? 40 : 5);
        h.desc.setOnClickListener(v -> {
            h.expanded = !h.expanded;
            h.desc.setMaxLines(h.expanded ? 40 : 5);
        });

        boolean fav = Db.get().isFav(c.image);
        h.btnFav.setImageResource(fav ? R.drawable.ic_star : R.drawable.ic_heart);
        h.btnFav.setColorFilter(fav ? 0xFFFFD54F : 0xFF9A9AB0);
        h.btnFav.setOnClickListener(v -> {
            boolean f = Db.get().isFav(c.image);
            if (f) {
                Db.get().delFav(c.image);
                h.btnFav.setImageResource(R.drawable.ic_heart);
                h.btnFav.setColorFilter(0xFF9A9AB0);
            } else {
                Db.get().addFav("character", c.name, c.image, c.animeTitle == null ? "" : c.animeTitle);
                h.btnFav.setImageResource(R.drawable.ic_star);
                h.btnFav.setColorFilter(0xFFFFD54F);
            }
        });

        h.btnArts.setOnClickListener(v -> cb.onWantArts(c.name));
        h.btnShare.setOnClickListener(v -> {
            String s = c.name + (c.animeTitle == null || c.animeTitle.isEmpty() ? "" : " · " + c.animeTitle)
                    + "\n\n" + (c.description == null ? "" : c.description);
            Saver.shareText(v.getContext(), s);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    public void set(List<Models.CharacterInfo> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    static class Vh extends RecyclerView.ViewHolder {
        final ImageView img;
        final TextView name, nativeName, anime, desc;
        final ImageButton btnFav, btnArts, btnShare;
        boolean expanded = false;
        Vh(View v) {
            super(v);
            img = v.findViewById(R.id.img);
            name = v.findViewById(R.id.name);
            nativeName = v.findViewById(R.id.nativeName);
            anime = v.findViewById(R.id.anime);
            desc = v.findViewById(R.id.desc);
            btnFav = v.findViewById(R.id.btnFav);
            btnArts = v.findViewById(R.id.btnArts);
            btnShare = v.findViewById(R.id.btnShare);
        }
    }
}
