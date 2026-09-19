package com.lumi.chat.art;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
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
        if (!(act instanceof Host)) return;
        ViewerDialog vd = new ViewerDialog();
        vd.item = a;
        vd.show(act.getSupportFragmentManager(), "viewer");
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
        big.setOnLongClickListener(x -> {
            boolean fav = Db.get().isFav(item.url);
            if (fav) Db.get().delFav(item.url); else Db.get().addFav("art", item.title, item.url, item.source);
            Toast.makeText(requireContext(), fav ? "Убрала из избранного" : "В избранном ⭐", Toast.LENGTH_SHORT).show();
            return true;
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
}
