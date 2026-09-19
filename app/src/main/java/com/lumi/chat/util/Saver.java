package com.lumi.chat.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.lumi.chat.img.ImageLoader;
import com.lumi.chat.net.Http;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/** Скачивание картинок, обои, шеринг — без ключей, только системные средства Android. */
public final class Saver {
    private Saver() {}

    public interface Res { void done(String pathOrNull, String error); }

    public static void download(Context ctx, String url) {
        Toast.makeText(ctx, "Скачиваю…", Toast.LENGTH_SHORT).show();
        Http.POOL.execute(() -> {
            try {
                Bitmap bm = ImageLoader.get().fetchBitmap(url);
                String name = "lumi_" + System.currentTimeMillis() + ".jpg";
                String saved;
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                    cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lumi");
                    Uri uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) throw new Exception("не удалось создать файл");
                    OutputStream os = ctx.getContentResolver().openOutputStream(uri);
                    bm.compress(Bitmap.CompressFormat.JPEG, 92, os);
                    os.close();
                    saved = "Pictures/Lumi/" + name;
                } else {
                    File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    File f = new File(dir, name);
                    FileOutputStream fo = new FileOutputStream(f);
                    bm.compress(Bitmap.CompressFormat.JPEG, 92, fo);
                    fo.close();
                    saved = f.getAbsolutePath();
                }
                mainToast(ctx, "Сохранила: " + saved + " 💜");
            } catch (Exception e) {
                mainToast(ctx, "Не получилось скачать 😢 " + e.getMessage());
            }
        });
    }

    public static void setWallpaper(Context ctx, String url) {
        Toast.makeText(ctx, "Ставлю обои…", Toast.LENGTH_SHORT).show();
        Http.POOL.execute(() -> {
            try {
                Bitmap bm = ImageLoader.get().fetchBitmap(url);
                android.app.WallpaperManager wm = android.app.WallpaperManager.getInstance(ctx);
                wm.setBitmap(bm);
                mainToast(ctx, "Обои установлены! ✨");
            } catch (Exception e) {
                mainToast(ctx, "Не получилось 😢 " + e.getMessage());
            }
        });
    }

    public static void shareImage(Context ctx, String url) {
        Toast.makeText(ctx, "Готовлю к отправке…", Toast.LENGTH_SHORT).show();
        Http.POOL.execute(() -> {
            try {
                Bitmap bm = ImageLoader.get().fetchBitmap(url);
                File dir = new File(ctx.getCacheDir(), "share");
                dir.mkdirs();
                File f = new File(dir, "lumi_" + System.currentTimeMillis() + ".jpg");
                FileOutputStream fo = new FileOutputStream(f);
                bm.compress(Bitmap.CompressFormat.JPEG, 92, fo);
                fo.close();
                Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", f);
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("image/jpeg");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mainStart(ctx, Intent.createChooser(i, "Поделиться артом"));
            } catch (Exception e) {
                mainToast(ctx, "Не получилось 😢 " + e.getMessage());
            }
        });
    }

    public static void shareText(Context ctx, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        ctx.startActivity(Intent.createChooser(i, "Поделиться"));
    }

    public static void openBrowser(Context ctx, String url) {
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(ctx, "Браузер не найден", Toast.LENGTH_SHORT).show();
        }
    }

    public static void copy(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("lumi", text));
        Toast.makeText(ctx, "Скопировано 💜", Toast.LENGTH_SHORT).show();
    }

    private static void mainToast(Context ctx, String msg) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(() -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
    }

    private static void mainStart(Context ctx, Intent intent) {
        android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
        h.post(() -> {
            try { ctx.startActivity(intent); } catch (Exception ignore) {}
        });
    }
}
