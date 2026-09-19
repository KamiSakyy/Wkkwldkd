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

/** Работа с LLM. Вся цепочка — в фоновом потоке, много фолбэков + кастомный API из настроек. */
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
    private static final String HACKCLUB = "https://ai.hackclub.com/chat/completions";
    private static final Random RND = new Random();

    public static String model() {
        String m = Db.get().kvGet("model", "");
        return m.isEmpty() ? DEFAULT_MODEL : m;
    }

    /**
     * Полный цикл. САМ уходит в фоновый поток (важно: из UI-потока вызывать безопасно).
     * Порядок попыток:
     *  1) llm7.io стриминг (GLM-5.3-Flash, бесплатный)
     *  2) llm7.io без стрима
     *  3) llm7.io codestral-latest
     *  4) кастомный OpenAI-совместимый API из настроек (если вписан)
     *  5) ai.hackclub.com (бесплатный, без ключа)
     *  6) Pollinations (резерв; в некоторых регионах заблокирован)
     */
    public static void chat(List<Models.Msg> history, String dossier, StreamCb cb) {
        Http.POOL.execute(() -> {
            JSONArray msgs = buildMessages(history, dossier);
            String model = model();
            String token7 = Db.get().kvGet("token7", "");
            String llm7Auth = token7.isEmpty() ? "public-anonymous" : token7;

            boolean[] delivered = {false};

            // 1) llm7 стриминг (блокирует поток до завершения, дельты летят в UI)
            Http.postSse(LLM7, body(msgs, model, true, null), llm7Auth, new Http.SseCallback() {
                @Override public void onDelta(String d) { if (!delivered[0]) cb.onDelta(d); }
                @Override public void onDone(String full) { delivered[0] = true; cb.onDone(clean(full)); }
                @Override public void onError(String e) { /* пробуем следующие */ }
            });
            if (delivered[0]) return;

            // 2) llm7 без стрима
            attempt(msgs, LLM7, model, llm7Auth, delivered, cb);
            if (delivered[0]) return;

            // 3) llm7 другая модель
            attempt(msgs, LLM7, "codestral-latest", llm7Auth, delivered, cb);
            if (delivered[0]) return;

            // 4) кастомный API пользователя (гарантированный вариант при блокировках)
            String cu = Db.get().kvGet("customUrl", "").trim();
            String ck = Db.get().kvGet("customKey", "").trim();
            String cm = Db.get().kvGet("customModel", "").trim();
            if (!cu.isEmpty()) {
                attempt(msgs, cu, cm.isEmpty() ? model : cm, ck, delivered, cb);
                if (delivered[0]) return;
            }

            // 5) hackclub (без ключа)
            attempt(msgs, HACKCLUB, "unnamed", "", delivered, cb);
            if (delivered[0]) return;

            // 6) pollinations
            attempt(msgs, POLLINATIONS, "openai", "", delivered, cb);
            if (delivered[0]) return;

            cb.onError("Не получилось связаться с моим «мозгом» 😔\n\n" +
                    "Проверь интернет. Если твои бесплатные ИИ блокирует регион (например, Pollinations в РФ) — " +
                    "открой Настройки и впиши любой OpenAI-совместимый API (URL, ключ, модель): " +
                    "тогда я буду работать через него, 100% надёжно 💜");
        });
    }

    /** Одна блокирующая попытка; при успехе доставляет текст и ставит delivered=true. */
    private static void attempt(JSONArray msgs, String url, String model, String bearer, boolean[] delivered, StreamCb cb) {
        try {
            String ref = url.contains("pollinations") ? "lumi-chat" : null;
            String r = Http.postJson(url, body(msgs, model, false, ref), bearer, 45000);
            String c = parseContent(r);
            if (c != null && !c.trim().isEmpty()) {
                delivered[0] = true;
                String f = clean(c);
                cb.onDelta(f);
                cb.onDone(f);
            }
        } catch (Exception ignore) {}
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
                    StringBuilder sb = new StringBuilder("Отправила пользователю арты: ");
                    ArrayList<Models.ArtItem> imgs = m.images();
                    for (int k = 0; k < imgs.size(); k++) {
                        if (k > 0) sb.append("; ");
                        String t = imgs.get(k).title;
                        sb.append(t == null || t.isEmpty() ? "арт" : t);
                    }
                    String intro = m.intro();
                    if (!intro.isEmpty()) sb.append(". Я написала: ").append(intro);
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
