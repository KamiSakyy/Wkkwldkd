package com.lumi.chat;

import java.util.ArrayList;

/** Простые модели данных приложения. */
public final class Models {
    private Models() {}

    /** Сообщение чата. kind: "text" | "images". Для images content = {"t":интро,"a":[арти]}. */
    public static class Msg {
        public long id;
        public String role;      // "user" | "assistant"
        public String kind;      // "text" | "images"
        public String content;
        public long ts;
        public boolean pending;  // временная (ещё грузится)

        public Msg() {}
        public Msg(String role, String kind, String content) {
            this.role = role; this.kind = kind; this.content = content;
            this.ts = System.currentTimeMillis();
        }

        public ArrayList<ArtItem> images() {
            ArrayList<ArtItem> out = new ArrayList<>();
            try {
                org.json.JSONArray arr = null;
                try {
                    arr = new org.json.JSONObject(content).optJSONArray("a");
                } catch (Exception notObj) {
                    arr = new org.json.JSONArray(content);
                }
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        org.json.JSONObject o = arr.getJSONObject(i);
                        ArtItem a = new ArtItem();
                        a.url = o.optString("url");
                        a.title = o.optString("title");
                        a.source = o.optString("source");
                        out.add(a);
                    }
                }
            } catch (Exception ignore) {}
            return out;
        }

        public String intro() {
            try {
                return new org.json.JSONObject(content).optString("t", "");
            } catch (Exception e) { return ""; }
        }

        public static String imagesJson(String intro, ArrayList<ArtItem> list) {
            try {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("t", intro == null ? "" : intro);
                org.json.JSONArray arr = new org.json.JSONArray();
                for (ArtItem a : list) {
                    org.json.JSONObject it = new org.json.JSONObject();
                    it.put("url", a.url);
                    it.put("title", a.title == null ? "" : a.title);
                    it.put("source", a.source == null ? "" : a.source);
                    arr.put(it);
                }
                o.put("a", arr);
                return o.toString();
            } catch (Exception e) { return "{\"t\":\"\",\"a\":[]}"; }
        }
    }

    /** Картинка/арт. */
    public static class ArtItem {
        public String url;
        public String title;
        public String source; // ссылка на страницу-источник (может быть пусто)
        public String provider;
    }

    /** Информация о персонаже. */
    public static class CharacterInfo {
        public String name;
        public String nativeName;
        public String image;
        public String description;
        public String animeTitle;
        public String animeCover;
        public String url;
    }

    /** Эпизод/аниме в эфире. */
    public static class AiringItem {
        public long mediaId;
        public String title;
        public String titleRu;
        public String cover;
        public String siteUrl;
        public int episode;
        public long airingAt;
        public long timeUntil;
        public boolean airing;
    }
}
