package com.lumi.chat.chat;

import com.lumi.chat.Models;
import com.lumi.chat.db.Db;
import com.lumi.chat.net.Http;
import com.lumi.chat.net.Json;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Работа с LLM: llm7.io (основной, бесплатный) + Pollinations (резерв). Стриминг + фолбэки. */
public final class LumiService {
    private LumiService() {}

    public interface StreamCb {
        void onDelta(String d);
        void onDone(String full);
        void onError(String message);
    }

    public static final String DEFAULT_MODEL = "GLM-5.3-Flash";
    private static final String LLM7 = "https://api.llm7.io/v1/chat/completions";
    private static final String POLLINATIONS = "https://text.pollinations.ai/openai";
    private static final Random RND = new Random();

    public static String model() {
        String m = Db.get().kvGet("model", "");
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    /** Полный цикл: строит контекст и стримит ответ, при ошибке пробует следующий провайдера. */
    public static void chat(List<Models.Msg> history, String dossier, StreamCb cb) {
        JSONArray msgs = buildMessages(history, dossier);
        String model = model();
        String token7 = Db.get().kvGet("token7", "");
        // 1) llm7 стриминг
        Http.postSse(LLM7, body(msgs, model, true, null), token7.isEmpty() ? "public-anonymous" : token7, new Http.SseCallback() {
            @Override public void onDelta(String d) { cb.onDelta(d); }
            @Override public void onDone(String full) { cb.onDone(clean(full)); }
            @Override public void onError(String e) {
                // 2) llm7 без стрима
                try {
                    String r = Http.postJson(LLM7, body(msgs, model, false, null), token7.isEmpty() ? "public-anonymous" : token7, 60000);
                    String content = parseContent(r);
                    if (!content.isEmpty()) { cb.onDone(clean(content)); return; }
                    throw new Exception("пустой ответ");
                } catch (Exception ex) {
                    // 3) другой модельный резерв: codestral на llm7
                    try {
                        String r2 = Http.postJson(LLM7, body(msgs, "codestral-latest", false, null), token7.isEmpty() ? "public-anonymous" : token7, 60000);
                        String c2 = parseContent(r2);
                        if (!c2.isEmpty()) { cb.onDone(clean(c2)); return; }
                        throw new Exception("пустой ответ");
                    } catch (Exception ex2) {
                        // 4) Pollinations резерв
                        try {
                            String r3 = Http.postJson(POLLINATIONS, body(msgs, "openai", false, "lumi-chat"), "", 60000);
                            String c3 = parseContent(r3);
                            if (!c3.isEmpty()) { cb.onDone(clean(c3)); return; }
                            throw new Exception("пустой ответ");
                        } catch (Exception ex3) {
                            cb.onError("Не получилось связаться с моим «мозгом» 😔 Проверь интернет или поменяй модель в настройках.");
                        }
                    }
                }
            }
        });
    }

    private static String parseContent(String resp) {
        try {
            JSONObject o = new JSONObject(resp);
            JSONArray ch = o.optJSONArray("choices");
            if (ch == null || ch.length() == 0) return "";
            JSONObject m = ch.getJSONObject(0).optJSONObject("message");
            return m == null ? "" : m.optString("content", "");
        } catch (Exception e) { return ""; }
    }

    private static JSONArray buildMessages(List<Models.Msg> history, String dossier) {
        JSONArray msgs = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", Persona.system(dossier));
            msgs.put(sys);
            int from = Math.max(0, history.size() - 16);
            for (int i = from; i < history.size(); i++) {
                Models.Msg m = history.get(i);
                if (m.pending) continue;
                JSONObject o = new JSONObject();
                o.put("role", "user".equals(m.role) ? "user" : "assistant");
                String c = m.content;
                if ("images".equals(m.kind)) {
                    if ("user".equals(m.role)) continue;
                    StringBuilder sb = new StringBuilder("Отправила арты: ");
                    try {
                        JSONArray arr = new JSONArray(c);
                        for (int k = 0; k < arr.length(); k++) {
                            if (k > 0) sb.append("; ");
                            sb.append(arr.getJSONObject(k).optString("title", "арт"));
                        }
                    } catch (Exception ignore) {}
                    c = sb.toString();
                }
                if (c == null || c.trim().isEmpty()) continue;
                o.put("content", c.length() > 4000 ? c.substring(0, 4000) : c);
                msgs.put(o);
            }
        } catch (Exception ignore) {}
        return msgs;
    }

    private static String body(JSONArray messages, String model, boolean stream, String referrer) {
        try {
            JSONObject o = new JSONObject();
            o.put("model", model);
            o.put("messages", messages);
            o.put("stream", stream);
            if (referrer != null) o.put("referrer", referrer);
            return o.toString();
        } catch (Exception e) { return "{\"model\":\"" + model + "\",\"stream\":" + stream + "}"; }
    }

    /** Убирает служебные блоки размышлений и лишние обёртки. */
    public static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("(?s)<think>.*?</think>", "")
                .replaceAll("(?s)<\\|[^>]*\\|>", "")
                .trim();
    }

    public static String randomFrom(String[] arr) {
        return arr[RND.nextInt(arr.length)];
    }
}
