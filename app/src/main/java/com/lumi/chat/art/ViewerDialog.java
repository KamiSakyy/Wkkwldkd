package com.lumi.chat.art;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.db.Db;
import com.lumi.chat.img.ImageLoader;
import com.lumi.chat.util.Saver;

/** Полноэкранный просмотр арта: скачать / обои / поделиться / источник / избранное (долгий тап). */
public class ViewerDialog extends BottomSheetDialogFragment {

    public interface Host {
        void showViewer(Models.ArtItem a);
    }

    private Models.ArtItem item;

    public static void show(FragmentActivity act, Models.ArtItem a) {
        if (!(act instanceof Host) || act.isFinishing()) return;
        try {
            ViewerDialog vd = new ViewerDialog();
            vd.item = a;
            vd.show(act.getSupportFragmentManager(), "viewer");
        } catch (Exception e) {
            // окно не открылось (например, активити в фоне) — не падаем
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog d = new BottomSheetDialog(requireContext());
        View v = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_viewer, null, false);
        d.setContentView(v);
        if (item == null) item = new Models.ArtItem();

        ImageView big = v.findViewById(R.id.bigImg);
        ImageLoader.get().load(item.url, big, false);

        ImageButton fav = v.findViewById(R.id.btnFav);
        paintFav(fav, Db.get().isFav(item.url));
        fav.setOnClickListener(x -> {
            boolean f = Db.get().isFav(item.url);
            if (f) Db.get().delFav(item.url); else Db.get().addFav("art", item.title, item.url, item.source);
            paintFav(fav, !f);
            Toast.makeText(requireContext(), f ? "Убрала сердечко 💔" : "Мне тоже нравится! В избранном ❤️", Toast.LENGTH_SHORT).show();
        });

        v.findViewById(R.id.btnDownload).setOnClickListener(x -> Saver.download(requireContext(), item.url));
        v.findViewById(R.id.btnWallpaper).setOnClickListener(x -> Saver.setWallpaper(requireContext(), item.url));
        v.findViewById(R.id.btnShare).setOnClickListener(x -> Saver.shareImage(requireContext(), item.url));
        v.findViewById(R.id.btnSource).setOnClickListener(x -> {
            if (item.source != null && !item.source.isEmpty()) Saver.openBrowser(requireContext(), item.source);
            else Toast.makeText(requireContext(), "Источник неизвестен", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.btnClose).setOnClickListener(x -> dismiss());
        return d;
    }

    private static void paintFav(ImageButton b, boolean fav) {
        b.setImageResource(fav ? R.drawable.ic_heart : R.drawable.ic_heart);
        b.setColorFilter(fav ? 0xFFFF6B9D : 0xFF9A9AB0);
    }
}
