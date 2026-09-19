package com.lumi.chat.img;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.lumi.chat.R;
import com.lumi.chat.net.Http;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Асинхронный загрузчик картинок с кэшем в памяти и на диске. Без внешних библиотек. */
public final class ImageLoader {
    private static ImageLoader inst;

    public static synchronized void init(Context ctx) {
        if (inst == null) inst = new ImageLoader(ctx.getApplicationContext());
    }

    public static ImageLoader get() {
        if (inst == null) throw new IllegalStateException("ImageLoader.init не вызван");
        return inst;
    }

    private final LruCache<String, Bitmap> mem;
    private final File diskDir;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Long> failed = new ConcurrentHashMap<>();

    private ImageLoader(Context ctx) {
        int max = (int) Math.max(8 * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8);
        mem = new LruCache<String, Bitmap>(max) {
            @Override protected int sizeOf(String k, Bitmap b) { return b.getByteCount(); }
        };
        diskDir = new File(ctx.getCacheDir(), "imgs");
        diskDir.mkdirs();
    }

    public void load(String url, ImageView iv) {
        load(url, iv, true);
    }

    public void load(String url, ImageView iv, boolean usePlaceholder) {
        if (url == null || url.isEmpty()) {
            iv.setImageResource(R.drawable.ic_image);
            return;
        }
        iv.setTag(url);
        Bitmap m = mem.get(url);
        if (m != null && !m.isRecycled()) {
            iv.setImageBitmap(m);
            return;
        }
        if (usePlaceholder) iv.setImageDrawable(new ColorDrawable(0xFF23233A));
        Long f = failed.get(url);
        if (f != null && System.currentTimeMillis() - f < 60_000L) {
            iv.setImageResource(R.drawable.ic_image);
            return;
        }
        Http.POOL.execute(() -> {
            Bitmap b = mem.get(url);
            if (b == null || b.isRecycled()) {
                b = readDisk(md5(url));
                if (b == null) {
                    b = download(url);
                    if (b != null) writeDisk(md5(url), b);
                }
            }
            Bitmap res = b;
            if (res == null) failed.put(url, System.currentTimeMillis());
            main.post(() -> {
                String tag = iv.getTag() instanceof String ? (String) iv.getTag() : null;
                if (!url.equals(tag)) return; // view уже переиспользована под другой URL
                if (res != null && !res.isRecycled()) iv.setImageBitmap(res);
                else iv.setImageResource(R.drawable.ic_image);
            });
        });
    }

    /** Скачивает и декодирует bitmap (для сохранения/обоев). */
    public Bitmap fetchBitmap(String url) throws Exception {
        Bitmap b = mem.get(url);
        if (b != null && !b.isRecycled()) return b;
        b = readDisk(md5(url));
        if (b == null) {
            b = download(url);
            if (b != null) writeDisk(md5(url), b);
        }
        if (b == null) throw new Exception("не удалось загрузить изображение");
        return b;
    }

    private Bitmap download(String url) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", Http.UA);
            int code = c.getResponseCode();
            if (code >= 400) return null;
            InputStream in = c.getInputStream();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            byte[] bytes = bos.toByteArray();
            BitmapFactory.Options bo = new BitmapFactory.Options();
            bo.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bo);
            int sample = 1;
            int maxSide = Math.max(bo.outWidth, bo.outHeight);
            while (maxSide / (sample * 2) >= 1400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o2);
            c.disconnect();
            return b;
        } catch (Exception e) {
            try { if (c != null) c.disconnect(); } catch (Exception ignore) {}
            return null;
        }
    }

    private Bitmap readDisk(String key) {
        try {
            File f = new File(diskDir, key + ".jpg");
            if (!f.exists()) return null;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) f.delete();
            return b;
        } catch (Exception e) { return null; }
    }

    private void writeDisk(String key, Bitmap b) {
        try {
            File f = new File(diskDir, key + ".jpg");
            FileOutputStream fo = new FileOutputStream(f);
            b.compress(Bitmap.CompressFormat.JPEG, 85, fo);
            fo.close();
        } catch (Exception ignore) {}
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(s.hashCode());
        }
    }
}
