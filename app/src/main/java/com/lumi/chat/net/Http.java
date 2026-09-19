package com.lumi.chat.net;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Мини-HTTP клиент без внешних библиотек (только HttpURLConnection). */
public final class Http {
    private Http() {}

    public static final ExecutorService POOL = Executors.newFixedThreadPool(6);
    public static final String UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

    public interface SseCallback {
        void onDelta(String text);
        void onDone(String full);
        void onError(String message);
    }

    public static byte[] getBytes(String url, int timeoutMs) throws Exception {
        HttpURLConnection c = open(url, timeoutMs, null);
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        byte[] bytes = readAll(in);
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + " " + tail(bytes));
        return bytes;
    }

    public static String get(String url, int timeoutMs) throws Exception {
        return new String(getBytes(url, timeoutMs), StandardCharsets.UTF_8);
    }

    public static String postJson(String url, String json, String bearer, int timeoutMs) throws Exception {
        HttpURLConnection c = open(url, timeoutMs, bearer);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        c.setRequestProperty("Accept", "application/json, text/event-stream");
        OutputStream os = c.getOutputStream();
        os.write(json.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
        String body = new String(readAll(in), StandardCharsets.UTF_8);
        c.disconnect();
        if (code >= 400) throw new Exception("HTTP " + code + ": " + shortTail(body));
        return body;
    }

    /** POST c Server-Sent Events потоком (стриминг токенов от LLM). */
    public static void postSse(String url, String json, String bearer, SseCallback cb) {
        HttpURLConnection c = null;
        try {
            c = open(url, 120000, bearer);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty("Accept", "text/event-stream");
            OutputStream os = c.getOutputStream();
            os.write(json.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            int code = c.getResponseCode();
            if (code >= 400) {
                String err = new String(readAll(c.getErrorStream()), StandardCharsets.UTF_8);
                throw new Exception("HTTP " + code + ": " + shortTail(err));
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder full = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if (data.isEmpty()) continue;
                if ("[DONE]".equals(data)) break;
                try {
                    org.json.JSONObject o = new org.json.JSONObject(data);
                    org.json.JSONArray choices = o.optJSONArray("choices");
                    if (choices != null && choices.length() > 0) {
                        org.json.JSONObject first = choices.getJSONObject(0);
                        org.json.JSONObject delta = first.optJSONObject("delta");
                        String t = delta == null ? null : delta.optString("content", "");
                        if (t == null || t.isEmpty()) {
                            org.json.JSONObject msg = first.optJSONObject("message");
                            if (msg != null) t = msg.optString("content", "");
                        }
                        if (t != null && !t.isEmpty()) {
                            full.append(t);
                            cb.onDelta(t);
                        }
                    }
                } catch (Exception ignore) {}
            }
            br.close();
            c.disconnect();
            if (full.length() == 0) throw new Exception("пустой поток");
            cb.onDone(full.toString());
        } catch (Exception e) {
            try { if (c != null) c.disconnect(); } catch (Exception ignore) {}
            cb.onError(e.getMessage() == null ? "ошибка сети" : e.getMessage());
        }
    }

    private static HttpURLConnection open(String url, int timeoutMs, String bearer) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(timeoutMs);
        c.setReadTimeout(timeoutMs);
        c.setRequestProperty("User-Agent", UA);
        if (bearer != null && !bearer.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + bearer);
        return c;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (in != null) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
        }
        return bos.toByteArray();
    }

    private static String tail(byte[] b) {
        if (b == null || b.length == 0) return "";
        String s = new String(b, 0, Math.min(b.length, 400), StandardCharsets.UTF_8);
        return s.replaceAll("\\s+", " ");
    }

    private static String shortTail(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 400 ? s.substring(0, 400) : s;
    }
}
