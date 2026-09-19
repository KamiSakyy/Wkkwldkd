package com.lumi.chat.anime;

import com.lumi.chat.Models;
import com.lumi.chat.net.Http;
import com.lumi.chat.net.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** AniList GraphQL: поиск аниме, расписание выхода серий. Стабильный бесплатный API. */
public final class AnimeApi {
    private AnimeApi() {}

    private static final String ANILIST = "https://graphql.anilist.co";

    public interface Done {
        void ok(List<Models.AiringItem> list);
        void fail(String msg);
    }

    private static String gql(String query, JSONObject vars) throws Exception {
        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("variables", vars);
        return Http.postJson(ANILIST, body.toString(), null, 25000);
    }

    /** Поиск аниме по названию. */
    public static void search(String q, Done done) {
        Http.POOL.execute(() -> {
            List<Models.AiringItem> out = new ArrayList<>();
            try {
                String r = gql("query($s:String){Page(perPage:14){media(search:$s,type:ANIME,sort:SEARCH_MATCH)"
                        + "{id title{romaji russian english} coverImage{large} siteUrl episodes genres"
                        + " nextAiringEpisode{episode timeUntilAiring airingAt}}}}",
                        new JSONObject().put("s", q));
                JSONArray media = Json.obj(r).optJSONObject("data") == null ? null
                        : Json.obj(r).optJSONObject("data").optJSONObject("Page") == null ? null
                        : Json.obj(r).optJSONObject("data").optJSONObject("Page").optJSONArray("media");
                if (media != null) {
                    for (int i = 0; i < media.length(); i++) out.add(parseMedia(media.getJSONObject(i)));
                }
            } catch (Exception ignore) {}
            if (out.isEmpty()) done.fail("Не нашла такое аниме 😔 Попробуй латиницу или оригинальное название.");
            else done.ok(out);
        });
    }

    /** Аниме по id (для уведомлений). */
    public static Models.AiringItem byId(long id) {
        try {
            String r = gql("query($id:Int){Media(id:$id,type:ANIME){id title{romaji russian} coverImage{large} siteUrl"
                    + " nextAiringEpisode{episode timeUntilAiring airingAt} episodes}}",
                    new JSONObject().put("id", (int) id));
            JSONObject media = Json.obj(r).optJSONObject("data") == null ? null
                    : Json.obj(r).optJSONObject("data").optJSONObject("Media");
            if (media != null) return parseMedia(media);
        } catch (Exception ignore) {}
        return null;
    }

    /** Расписание эфиров в окне [from, to] (unix-секунды). */
    public static void airing(long from, long to, Done done) {
        Http.POOL.execute(() -> {
            List<Models.AiringItem> out = new ArrayList<>();
            Set<Long> uniq = new HashSet<>();
            try {
                String r = gql("query($f:Int,$t:Int){Page(perPage:50){airingSchedules(airingAt_greater:$f,airingAt_lesser:$t)"
                        + "{episode airingAt media{id title{romaji russian} coverImage{large} siteUrl}}}}",
                        new JSONObject().put("f", (int) (from)).put("t", (int) (to)));
                JSONArray arr = Json.obj(r).optJSONObject("data") == null ? null
                        : Json.obj(r).optJSONObject("data").optJSONObject("Page") == null ? null
                        : Json.obj(r).optJSONObject("data").optJSONObject("Page").optJSONArray("airingSchedules");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject s = arr.getJSONObject(i);
                        Models.AiringItem it = parseMedia(s.optJSONObject("media"));
                        if (it == null) continue;
                        it.episode = s.optInt("episode", 0);
                        it.airingAt = s.optLong("airingAt", 0);
                        if (uniq.contains(it.mediaId)) continue;
                        uniq.add(it.mediaId);
                        out.add(it);
                    }
                }
            } catch (Exception ignore) {}
            if (out.isEmpty()) done.fail("Расписание не загрузилось 🔄 Потяни вниз, чтобы обновить.");
            else done.ok(out);
        });
    }

    private static Models.AiringItem parseMedia(JSONObject md) {
        if (md == null) return null;
        Models.AiringItem a = new Models.AiringItem();
        a.mediaId = md.optLong("id", 0);
        if (a.mediaId == 0) return null;
        String ru = Json.str(md, "title.russian");
        String ro = Json.str(md, "title.romaji");
        String en = Json.str(md, "title.english");
        a.title = ro.isEmpty() ? (en.isEmpty() ? ru : en) : ro;
        a.titleRu = ru;
        a.cover = Json.str(md, "coverImage.large");
        a.siteUrl = Json.str(md, "siteUrl");
        JSONObject nae = md.optJSONObject("nextAiringEpisode");
        if (nae != null) {
            a.airing = true;
            a.episode = nae.optInt("episode", 0);
            a.timeUntil = nae.optLong("timeUntilAiring", 0);
            a.airingAt = nae.optLong("airingAt", 0);
        } else {
            a.airing = false;
            a.episode = md.optInt("episodes", 0);
        }
        return a;
    }

    /** «через 1д 3ч 15м» из секунд. */
    public static String humanize(long seconds) {
        if (seconds <= 0) return "сейчас";
        long d = seconds / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("д ");
        if (h > 0) sb.append(h).append("ч ");
        if (d == 0 && m > 0) sb.append(m).append("м");
        return sb.toString().trim();
    }
}
