package com.lumi.chat.art;

import com.lumi.chat.Models;
import com.lumi.chat.net.Http;
import com.lumi.chat.net.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Мульти-источники артов с автоматическими фолбэками:
 * 1) ИИ-генерация Pollinations (если задан токен — иначе сервис платный, пропускаем)
 * 2) Kitsu (арты конкретного персонажа)
 * 3) AniList (официальные изображения персонажей)
 * 4) nekos.best (аниме-арты, проверен)
 * 5) waifu.im (обои/арты, проверен)
 * 6) Jikan/MAL (резерв)
 */
public final class ArtSources {
    private ArtSources() {}

    public interface Done {
        void ok(List<Models.ArtItem> items);
        void fail(String message);
    }

    public static final String[] CATEGORIES = {"Все", "Неко", "Waifu", "Kitsune", "Обои", "⭐ Избранное"};

    private static final Random RND = new Random();

    public static void fetch(String query, int count, boolean aiFirst, Done done) {
        Http.POOL.execute(() -> {
            List<Models.ArtItem> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String pollToken = com.lumi.chat.db.Db.get().kvGet("polltoken", "");
            String q = query == null ? "" : query.trim();

            // ИИ-генерация — приоритет, если просили «нарисуй» и есть токен
            if (aiFirst && !pollToken.isEmpty() && !q.isEmpty()) {
                collect(aiGenerate(q, Math.min(4, count), pollToken), out, seen);
            }

            // Арты конкретного персонажа/темы
            if (!q.isEmpty() && out.size() < count) collect(kitsuChars(q, count), out, seen);
            if (!q.isEmpty() && out.size() < count) collect(anilistChars(q, count), out, seen);
            if (!q.isEmpty() && out.size() < count) collect(jikanChars(q, count), out, seen);

            // Живые аниме-арты
            if (out.size() < count) collect(nekosBest(count), out, seen);
            if (out.size() < count) collect(waifuIm(count), out, seen);

            if (out.isEmpty()) {
                done.fail("Не нашла артов 😢 Попробуй другой запрос или повтори ещё раз.");
            } else {
                done.ok(out);
            }
        });
    }

    /** Для галереи: категория из чипсов. */
    public static void byCategory(String category, int count, Done done) {
        if ("⭐ Избранное".equals(category)) {
            List<Models.ArtItem> favs = com.lumi.chat.db.Db.get().favs();
            done.ok(favs);
            return;
        }
        Http.POOL.execute(() -> {
            List<Models.ArtItem> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            switch (category) {
                case "Неко":
                    collect(nekosCat("neko", count), out, seen);
                    break;
                case "Waifu":
                    collect(nekosCat("waifu", count), out, seen);
                    if (out.size() < count) collect(waifuIm(count), out, seen);
                    break;
                case "Kitsune":
                    collect(nekosCat("kitsune", count), out, seen);
                    if (out.size() < count) collect(waifuIm(count), out, seen);
                    break;
                case "Обои":
                    collect(waifuIm(count + 4), out, seen);
                    if (out.size() < count) collect(nekosCat("waifu", count), out, seen);
                    break;
                default:
                    collect(nekosBest(count), out, seen);
                    if (out.size() < count) collect(waifuIm(count), out, seen);
            }
            if (out.isEmpty()) done.fail("Источник не ответил, потяни вниз, чтобы обновить 🔄");
            else done.ok(out);
        });
    }

    // ---------- источники ----------

    private static List<Models.ArtItem> aiGenerate(String q, int n, String token) {
        List<Models.ArtItem> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Models.ArtItem a = new Models.ArtItem();
            a.url = "https://image.pollinations.ai/prompt/" + Json.urlEnc("anime art, " + q)
                    + "?width=832&height=1216&nologo=true&safe=true&seed=" + (100000 + RND.nextInt(900000))
                    + "&token=" + Json.urlEnc(token);
            a.title = "AI-арт: " + q;
            a.source = "";
            a.provider = "ai";
            out.add(a);
        }
        return out;
    }

    private static List<Models.ArtItem> nekosBest(int n) {
        String[] cats = {"waifu", "neko"};
        return nekosCat(cats[RND.nextInt(cats.length)], n);
    }

    private static List<Models.ArtItem> nekosCat(String cat, int n) {
        List<Models.ArtItem> out = new ArrayList<>();
        try {
            String r = Http.get("https://nekos.best/api/v2/" + cat + "?amount=" + Math.min(8, Math.max(1, n)), 20000);
            JSONObject o = Json.obj(r);
            JSONArray arr = o.optJSONArray("results");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.getJSONObject(i);
                    Models.ArtItem a = new Models.ArtItem();
                    a.url = it.optString("url");
                    a.title = (it.optString("artist_name", "").isEmpty() ? cat : "арт от " + it.optString("artist_name"));
                    a.source = it.optString("source_url", "");
                    a.provider = "nekos.best";
                    if (!a.url.isEmpty()) out.add(a);
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static List<Models.ArtItem> waifuIm(int n) {
        List<Models.ArtItem> out = new ArrayList<>();
        try {
            String r = Http.get("https://api.waifu.im/images?IncludedTags=waifu&IsNsfw=False&PageSize=" + Math.min(8, Math.max(1, n)), 20000);
            JSONObject o = Json.obj(r);
            JSONArray arr = o.optJSONArray("items");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.getJSONObject(i);
                    Models.ArtItem a = new Models.ArtItem();
                    a.url = it.optString("url");
                    a.title = "waifu.im";
                    a.source = it.optString("source", "");
                    a.provider = "waifu.im";
                    if (!a.url.isEmpty()) out.add(a);
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static List<Models.ArtItem> kitsuChars(String q, int n) {
        List<Models.ArtItem> out = new ArrayList<>();
        try {
            String url = "https://kitsu.io/api/edge/characters?filter[name]=" + Json.urlEnc(q)
                    + "&page[limit]=" + Math.min(8, Math.max(1, n)) + "&include=primaryMedia";
            String r = Http.get(url, 20000);
            JSONObject o = Json.obj(r);
            // разобрать included вручную
            java.util.Map<String, String> mediaTitles = new java.util.HashMap<>();
            org.json.JSONArray included = o.optJSONArray("included");
            if (included != null) {
                for (int i = 0; i < included.length(); i++) {
                    JSONObject m = included.getJSONObject(i);
                    if ("media".equals(m.optString("type"))) {
                        mediaTitles.put(m.optString("id"), m.optJSONObject("attributes") == null ? ""
                                : m.optJSONObject("attributes").optString("canonicalTitle", ""));
                    }
                }
            }
            JSONArray data = o.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length() && out.size() < n; i++) {
                    JSONObject c = data.getJSONObject(i);
                    JSONObject attrs = c.optJSONObject("attributes");
                    if (attrs == null) continue;
                    JSONObject img = attrs.optJSONObject("image");
                    String imgU = "";
                    if (img != null) {
                        imgU = img.optString("original", "");
                        if (imgU.isEmpty()) imgU = img.optString("large", "");
                    }
                    if (imgU.isEmpty()) continue;
                    String mediaId = "";
                    try {
                        JSONObject pm = c.optJSONObject("relationships").optJSONObject("primaryMedia").optJSONObject("data");
                        if (pm != null) mediaId = pm.optString("id");
                    } catch (Exception ignore) {}
                    Models.ArtItem a = new Models.ArtItem();
                    a.url = imgU;
                    a.title = attrs.optString("canonicalName", attrs.optString("name", q));
                    if (!mediaId.isEmpty() && mediaTitles.containsKey(mediaId)) a.title += " · " + mediaTitles.get(mediaId);
                    a.source = "https://kitsu.io/characters/" + c.optString("id");
                    a.provider = "kitsu";
                    out.add(a);
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static List<Models.ArtItem> anilistChars(String q, int n) {
        List<Models.ArtItem> out = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            body.put("query", "query($s:String){Page(perPage:8){characters(search:$s){name{full} image{large} media(perPage:1){nodes{title{romaji} siteUrl}}}}}");
            body.put("variables", new JSONObject().put("s", q));
            String r = Http.postJson("https://graphql.anilist.co", body.toString(), null, 25000);
            JSONArray chars = Json.obj(r).optJSONObject("data") == null ? null
                    : Json.obj(r).optJSONObject("data").optJSONObject("Page") == null ? null
                    : Json.obj(r).optJSONObject("data").optJSONObject("Page").optJSONArray("characters");
            if (chars != null) {
                for (int i = 0; i < chars.length() && out.size() < n; i++) {
                    JSONObject c = chars.getJSONObject(i);
                    String img = Json.str(c, "image.large");
                    if (img.isEmpty()) continue;
                    Models.ArtItem a = new Models.ArtItem();
                    a.url = img;
                    a.title = Json.str(c, "name.full");
                    String site = "";
                    try {
                        site = c.getJSONObject("media").getJSONArray("nodes").getJSONObject(0).optString("siteUrl");
                    } catch (Exception ignore) {}
                    a.source = site;
                    a.provider = "anilist";
                    out.add(a);
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static List<Models.ArtItem> jikanChars(String q, int n) {
        List<Models.ArtItem> out = new ArrayList<>();
        try {
            String r = Http.get("https://api.jikan.moe/v4/characters?q=" + Json.urlEnc(q) + "&limit=" + Math.min(8, Math.max(1, n)), 20000);
            JSONArray data = Json.obj(r).optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length() && out.size() < n; i++) {
                    JSONObject c = data.getJSONObject(i);
                    String img = Json.str(c, "images.jpg.image_url");
                    if (img.isEmpty()) continue;
                    Models.ArtItem a = new Models.ArtItem();
                    a.url = img;
                    a.title = c.optString("name", q);
                    a.source = c.optString("url", "");
                    a.provider = "MAL";
                    out.add(a);
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static void collect(List<Models.ArtItem> src, List<Models.ArtItem> out, Set<String> seen) {
        for (Models.ArtItem a : src) {
            if (a.url == null || seen.contains(a.url)) continue;
            seen.add(a.url);
            out.add(a);
        }
    }
}
