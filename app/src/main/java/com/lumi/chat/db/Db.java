package com.lumi.chat.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.lumi.chat.Models;

import java.util.ArrayList;
import java.util.List;

/** Локальное хранилище: история чата, досье, избранное, подписки, настройки. */
public final class Db extends SQLiteOpenHelper {
    private static Db inst;

    public static synchronized void init(Context ctx) {
        if (inst == null) inst = new Db(ctx.getApplicationContext());
    }

    public static Db get() {
        if (inst == null) throw new IllegalStateException("Db.init не вызван");
        return inst;
    }

    private Db(Context c) { super(c, "lumi.db", null, 1); }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE messages(id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT, kind TEXT, content TEXT, ts INTEGER)");
        db.execSQL("CREATE TABLE kv(k TEXT PRIMARY KEY, v TEXT)");
        db.execSQL("CREATE TABLE favorites(id INTEGER PRIMARY KEY AUTOINCREMENT, type TEXT, title TEXT, url TEXT, source TEXT, ts INTEGER)");
        db.execSQL("CREATE TABLE subs(mediaId INTEGER PRIMARY KEY, title TEXT, cover TEXT, lastEp INTEGER DEFAULT 0, ts INTEGER)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int o, int n) {}

    // ---------- сообщения ----------
    public long addMsg(Models.Msg m) {
        ContentValues cv = new ContentValues();
        cv.put("role", m.role);
        cv.put("kind", m.kind);
        cv.put("content", m.content);
        cv.put("ts", m.ts);
        return get().getWritableDatabase().insert("messages", null, cv);
    }

    public List<Models.Msg> lastMsgs(int n) {
        List<Models.Msg> out = new ArrayList<>();
        Cursor c = get().getReadableDatabase().rawQuery("SELECT id, role, kind, content, ts FROM messages ORDER BY id DESC LIMIT ?", new String[]{String.valueOf(n)});
        while (c.moveToNext()) {
            Models.Msg m = new Models.Msg();
            m.id = c.getLong(0);
            m.role = c.getString(1);
            m.kind = c.getString(2);
            m.content = c.getString(3);
            m.ts = c.getLong(4);
            out.add(m);
        }
        c.close();
        java.util.Collections.reverse(out);
        return out;
    }

    public void clearMsgs() { get().getWritableDatabase().delete("messages", null, null); }

    // ---------- ключ-значение (настройки, досье) ----------
    public String kvGet(String k, String def) {
        Cursor c = get().getReadableDatabase().rawQuery("SELECT v FROM kv WHERE k=?", new String[]{k});
        String r = def;
        if (c.moveToFirst()) r = c.getString(0);
        c.close();
        return r == null ? def : r;
    }

    public void kvSet(String k, String v) {
        ContentValues cv = new ContentValues();
        cv.put("k", k);
        cv.put("v", v);
        get().getWritableDatabase().insertWithOnConflict("kv", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void kvDel(String k) { get().getWritableDatabase().delete("kv", "k=?", new String[]{k}); }

    /** Всё «досье» — ключи с префиксом mem. */
    public String dossier() {
        StringBuilder sb = new StringBuilder();
        Cursor c = get().getReadableDatabase().rawQuery("SELECT k, v FROM kv WHERE k LIKE 'mem.%' ORDER BY k", null);
        while (c.moveToNext()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append("- ").append(c.getString(0).substring(4)).append(": ").append(c.getString(1));
        }
        c.close();
        return sb.toString();
    }

    // ---------- избранное ----------
    public void addFav(String type, String title, String url, String source) {
        if (isFav(url)) return;
        ContentValues cv = new ContentValues();
        cv.put("type", type);
        cv.put("title", title);
        cv.put("url", url);
        cv.put("source", source);
        cv.put("ts", System.currentTimeMillis());
        get().getWritableDatabase().insert("favorites", null, cv);
    }

    public boolean isFav(String url) {
        Cursor c = get().getReadableDatabase().rawQuery("SELECT 1 FROM favorites WHERE url=?", new String[]{url});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    public void delFav(String url) { get().getWritableDatabase().delete("favorites", "url=?", new String[]{url}); }

    public List<Models.ArtItem> favs() {
        List<Models.ArtItem> out = new ArrayList<>();
        Cursor c = get().getReadableDatabase().rawQuery("SELECT title, url, source FROM favorites WHERE type='art' ORDER BY ts DESC", null);
        while (c.moveToNext()) {
            Models.ArtItem a = new Models.ArtItem();
            a.title = c.getString(0);
            a.url = c.getString(1);
            a.source = c.getString(2);
            a.provider = "избранное";
            out.add(a);
        }
        c.close();
        return out;
    }

    public List<Models.CharacterInfo> favChars() {
        List<Models.CharacterInfo> out = new ArrayList<>();
        Cursor c = get().getReadableDatabase().rawQuery("SELECT title, url, source FROM favorites WHERE type='character' ORDER BY ts DESC", null);
        while (c.moveToNext()) {
            Models.CharacterInfo ci = new Models.CharacterInfo();
            ci.name = c.getString(0);
            ci.image = c.getString(1);
            ci.animeTitle = c.getString(2);
            out.add(ci);
        }
        c.close();
        return out;
    }

    // ---------- подписки на аниме ----------
    public void addSub(long mediaId, String title, String cover) {
        ContentValues cv = new ContentValues();
        cv.put("mediaId", mediaId);
        cv.put("title", title);
        cv.put("cover", cover);
        cv.put("lastEp", 0);
        cv.put("ts", System.currentTimeMillis());
        get().getWritableDatabase().insertWithOnConflict("subs", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void delSub(long mediaId) { get().getWritableDatabase().delete("subs", "mediaId=?", new String[]{String.valueOf(mediaId)}); }

    public boolean isSub(long mediaId) {
        Cursor c = get().getReadableDatabase().rawQuery("SELECT 1 FROM subs WHERE mediaId=?", new String[]{String.valueOf(mediaId)});
        boolean r = c.moveToFirst();
        c.close();
        return r;
    }

    public List<long[]> subIds() {
        List<long[]> out = new ArrayList<>();
        Cursor c = get().getReadableDatabase().rawQuery("SELECT mediaId, lastEp FROM subs", null);
        while (c.moveToNext()) out.add(new long[]{c.getLong(0), c.getLong(1)});
        c.close();
        return out;
    }

    public void setSubLastEp(long mediaId, int ep) {
        get().getWritableDatabase().execSQL("UPDATE subs SET lastEp=? WHERE mediaId=?", new Object[]{ep, mediaId});
    }

    public String subTitle(long mediaId) {
        Cursor c = get().getReadableDatabase().rawQuery("SELECT title FROM subs WHERE mediaId=?", new String[]{String.valueOf(mediaId)});
        String r = null;
        if (c.moveToFirst()) r = c.getString(0);
        c.close();
        return r;
    }
}
