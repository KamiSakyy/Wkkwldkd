package com.lumi.chat.net;

import org.json.JSONObject;

/** Хелперы для JSON. */
public final class Json {
    private Json() {}

    public static JSONObject obj(String s) {
        try { return new JSONObject(s); } catch (Exception e) { return new JSONObject(); }
    }

    /** Достаёт строку по пути "a.b.c" без исключений. */
    public static String str(JSONObject o, String path) {
        try {
            String[] parts = path.split("\\.");
            JSONObject cur = o;
            for (int i = 0; i < parts.length - 1; i++) {
                cur = cur.optJSONObject(parts[i]);
                if (cur == null) return "";
            }
            return cur.optString(parts[parts.length - 1], "");
        } catch (Exception e) { return ""; }
    }

    public static long lng(JSONObject o, String path) {
        String s = str(o, path);
        try { return Long.parseLong(s); } catch (Exception e) { return 0L; }
    }

    public static int intOf(JSONObject o, String path) {
        String s = str(o, path);
        try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
    }

    public static String stripHtml(String s) {
        if (s == null || s.isEmpty()) return "";
        return s.replaceAll("(?s)<[^>]*>", "")
                .replace("&quot;", "\"").replace("&amp;", "&")
                .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&mdash;", "—").replace("&hellip;", "…")
                .replaceAll("\\[written by MAL_Rewrite]", "")
                .trim();
    }

    public static String urlEnc(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }
}
