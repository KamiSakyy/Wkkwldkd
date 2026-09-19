package com.lumi.chat.chars;

import com.lumi.chat.Models;
import com.lumi.chat.net.Http;
import com.lumi.chat.net.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Расширенный поиск персонажей: имя + аниме.
 * Основной источник — AniList GraphQL (стабильный). Резерв — Kitsu.
 */
public final class CharacterFinder {
    private CharacterFinder() {}

    public interface Done {
        void ok(List<Models.CharacterInfo> list);
        void fail(String msg);
    }

    private static final String ANILIST = "https://graphql.anilist.co";

    public static void search(String name, String anime, Done done) {
        Http.POOL.execute(() -> {
            List<Models.CharacterInfo> out = new ArrayList<>();
            String n = name == null ? "" : name.trim();
            String a = anime == null ? "" : anime.trim();
            if (!a.isEmpty()) {
                out = fromMediaByAnime(a, n);
            }
            if (out.isEmpty() && !n.isEmpty()) {
                out = fromCharactersSearch(n);
            }
            if (out.isEmpty() && !n.isEmpty()) {
                out = fromKitsu(n);
            }
            if (out.isEmpty()) {
                done.fail("Никого не нашла 🤔 Попробуй уточнить имя (по-английски ищется лучше) или название аниме.");
            } else {
                done.ok(out);
            }
        });
    }

    /** Персонажи аниме (опц. фильтр по имени). */
    private static List<Models.CharacterInfo> fromMediaByAnime(String anime, String nameFilter) {
        List<Models.CharacterInfo> out = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            body.put("query", "query($s:String){Page(perPage:2){media(search:$s,type:ANIME,sort:SEARCH_MATCH)"
                    + "{title{romaji russian} coverImage{large} siteUrl characters(perPage:20,sort:ROLE)"
                    + "{nodes{name{full native} image{large} description}}}}}");
            body.put("variables", new JSONObject().put("s", anime));
            String r = Http.postJson(ANILIST, body.toString(), null, 25000);
            JSONObject data = Json.obj(r).optJSONObject("data");
            if (data == null) return out;
            JSONObject page = data.optJSONObject("Page");
            if (page == null) return out;
            JSONArray media = page.optJSONArray("media");
            if (media == null) return out;
            for (int m = 0; m < media.length(); m++) {
                JSONObject md = media.getJSONObject(m);
                String title = Json.str(md, "title.romaji");
                if (Json.str(md, "title.russian").isEmpty()) title = Json.str(md, "title.romaji");
                else title = Json.str(md, "title.russian") + " · " + Json.str(md, "title.romaji");
                JSONArray nodes = md.optJSONObject("characters") == null ? null : md.optJSONObject("characters").optJSONArray("nodes");
                if (nodes == null) continue;
                for (int i = 0; i < nodes.size(); i++) {
                    JSONObject c = nodes.getJSONObject(i);
                    String full = Json.str(c, "name.full");
                    if (full.isEmpty()) continue;
                    if (!nameFilter.isEmpty() && !full.toLowerCase().replace('ё', 'е').contains(nameFilter.toLowerCase().replace('ё', 'е'))) continue;
                    Models.CharacterInfo ci = new Models.CharacterInfo();
                    ci.name = full;
                    ci.nativeName = Json.str(c, "name.native");
                    ci.image = Json.str(c, "image.large");
                    ci.description = cut(Json.stripHtml(c.optString("description", "")), 900);
                    ci.animeTitle = title;
                    ci.animeCover = Json.str(md, "coverImage.large");
                    ci.url = Json.str(md, "siteUrl");
                    out.add(ci);
                }
                if (!out.isEmpty()) break; // хватило первого подходящего аниме
            }
        } catch (Exception ignore) {}
        return out;
    }

    /** Поиск по имени персонажа. */
    private static List<Models.CharacterInfo> fromCharactersSearch(String name) {
        List<Models.CharacterInfo> out = new ArrayList<>();
        try {
            JSONObject body = new JSONObject();
            body.put("query", "query($s:String){Page(perPage:12){characters(search:$s,sort:SEARCH_MATCH)"
                    + "{name{full native} image{large} description media(perPage:3){nodes{title{romaji russian}}}}}}");
            body.put("variables", new JSONObject().put("s", name));
            String r = Http.postJson(ANILIST, body.toString(), null, 25000);
            JSONObject data = Json.obj(r).optJSONObject("data");
            if (data == null) return out;
            JSONArray chars = data.optJSONObject("Page") == null ? null : data.optJSONObject("Page").optJSONArray("characters");
            if (chars == null) return out;
            for (int i = 0; i < chars.length(); i++) {
                JSONObject c = chars.getJSONObject(i);
                String full = Json.str(c, "name.full");
                if (full.isEmpty()) continue;
                Models.CharacterInfo ci = new Models.CharacterInfo();
                ci.name = full;
                ci.nativeName = Json.str(c, "name.native");
                ci.image = Json.str(c, "image.large");
                ci.description = cut(Json.stripHtml(c.optString("description", "")), 900);
                StringBuilder animes = new StringBuilder();
                try {
                    JSONArray nodes = c.getJSONObject("media").getJSONArray("nodes");
                    for (int k = 0; k < nodes.length(); k++) {
                        JSONObject n = nodes.getJSONObject(k);
                        String ru = Json.str(n, "title.russian");
                        String ro = Json.str(n, "title.romaji");
                        if (animes.length() > 0) animes.append(", ");
                        animes.append(ru.isEmpty() ? ro : ru);
                    }
                } catch (Exception ignore) {}
                ci.animeTitle = animes.toString();
                ci.url = "https://anilist.co/search/characters?search=" + Json.urlEnc(name);
                out.add(ci);
            }
        } catch (Exception ignore) {}
        return out;
    }

    /** Резерв: Kitsu. */
    private static List<Models.CharacterInfo> fromKitsu(String name) {
        List<Models.CharacterInfo> out = new ArrayList<>();
        try {
            String r = Http.get("https://kitsu.io/api/edge/characters?filter[name]=" + Json.urlEnc(name) + "&page[limit]=10", 20000);
            JSONArray data = Json.obj(r).optJSONArray("data");
            if (data == null) return out;
            for (int i = 0; i < data.length(); i++) {
                JSONObject c = data.getJSONObject(i);
                JSONObject attrs = c.optJSONObject("attributes");
                if (attrs == null) continue;
                Models.CharacterInfo ci = new Models.CharacterInfo();
                ci.name = attrs.optString("canonicalName", attrs.optString("name", ""));
                if (ci.name.isEmpty()) continue;
                ci.nativeName = Json.str(attrs, "names.en_jp");
                JSONObject img = attrs.optJSONObject("image");
                ci.image = img == null ? "" : img.optString("large", img.optString("original", ""));
                ci.description = cut(Json.stripHtml(attrs.optString("description", "")), 900);
                ci.animeTitle = "";
                out.add(ci);
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static String cut(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max).replaceAll("\\s\\S*$", "…");
    }
}
