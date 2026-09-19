package com.lumi.chat.chat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.art.ArtSources;
import com.lumi.chat.art.ViewerDialog;
import com.lumi.chat.chars.CharacterFinder;
import com.lumi.chat.db.Db;
import com.lumi.chat.anime.AnimeApi;
import com.lumi.chat.util.Md;
import com.lumi.chat.util.Saver;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Чат с Люми: текст + арты + персонажи + расписание + память + голос. */
public class ChatFragment extends Fragment implements ChatAdapter.Cb {

    private ChatAdapter adapter;
    private EditText input;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;

    private String lastType = IntentRouter.T_CHAT;
    private String lastQuery = "";
    private String pendingDiscuss = null; // тема из уведомления о новой серии
    private boolean busy = false;         // не плодим параллельные запросы

    /** Уведомление о новой серии → сразу обсудить с Люми. */
    public void discuss(String text) {
        pendingDiscuss = text;
        if (getView() != null && input != null) {
            input.setText(text);
            send();
            pendingDiscuss = null;
        }
    }

    private final ActivityResultLauncher<Intent> voiceLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                    ArrayList<String> words = res.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (words != null && !words.isEmpty() && input != null) {
                        input.setText(words.get(0));
                        input.setSelection(input.getText().length());
                    }
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_chat, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        RecyclerView list = v.findViewById(R.id.chatList);
        LinearLayoutManager lm = new LinearLayoutManager(getContext());
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        adapter = new ChatAdapter(this);
        list.setAdapter(adapter);

        input = v.findViewById(R.id.input);
        ImageButton send = v.findViewById(R.id.btnSend);
        ImageButton mic = v.findViewById(R.id.btnMic);

        send.setOnClickListener(x -> send());
        mic.setOnClickListener(x -> startVoice());

        loadHistory();
        buildChips();

        tts = new TextToSpeech(getContext(), st -> {
            if (st == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru"));
            ttsReady = st == TextToSpeech.SUCCESS;
        });

        if (pendingDiscuss != null) {
            String t = pendingDiscuss;
            pendingDiscuss = null;
            input.setText(t);
            send();
        }
    }

    private void loadHistory() {
        List<Models.Msg> hist = Db.get().lastMsgs(200);
        if (hist.isEmpty()) {
            Models.Msg hi = new Models.Msg("assistant", "text",
                    "Привет! Я **Люми** ✨ мне 18, и я твоя ИИ-подруга 💜\n\n" +
                    "Умею:\n• искать **арты** — просто скажи «дай арты Rem» или «обои аниме»\n" +
                    "• рассказывать о **персонажах** — «кто такая Rem»\n" +
                    "• узнавать, **когда выйдет серия** — «когда новая серия Джоджо?»\n" +
                    "• **запоминать** — «меня зовут …», «запомни, я люблю…»\n\n" +
                    "Ну и просто поболтать, конечно 😊");
            hi.id = Db.get().addMsg(hi);
            adapter.add(hi);
        } else {
            for (Models.Msg m : hist) adapter.add(m);
        }
    }

    /** Быстрые подсказки под полем ввода (видны, пока переписка почти пустая). */
    private void buildChips() {
        try {
            HorizontalScrollView scroll = requireView().findViewById(R.id.chipsScroll);
            LinearLayout row = requireView().findViewById(R.id.chipsRow);
            row.removeAllViews();
            String[] chips = {"🎨 Дай арты Rem", "😈 Кто такая Макима?", "📺 Когда серия Фрирен?", "😮 Расскажи о себе"};
            for (String label : chips) {
                TextView t = new TextView(getContext());
                t.setText(label);
                t.setBackgroundResource(R.drawable.bg_chip);
                t.setTextColor(0xFFC4B5FD);
                t.setTextSize(13f);
                int pad = (int) (10 * getResources().getDisplayMetrics().density);
                t.setPadding(pad + 4, pad - 4, pad + 4, pad - 4);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(0, 0, pad, 0);
                t.setLayoutParams(lp);
                t.setOnClickListener(x -> {
                    input.setText(label);
                    send();
                });
                row.addView(t);
            }
            scroll.setVisibility(adapter.getItemCount() <= 1 ? View.VISIBLE : View.GONE);
        } catch (Exception ignore) {}
    }

    private void startVoice() {
        try {
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори, я слушаю…");
            voiceLauncher.launch(i);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Голосовой ввод недоступен 🎙", Toast.LENGTH_SHORT).show();
        }
    }

    private void send() {
        try {
            if (input == null) return;
            String text = input.getText().toString().trim();
            if (text.isEmpty()) return;
            if (busy) {
                Toast.makeText(getContext(), "Секунду, я ещё отвечаю ✍️", Toast.LENGTH_SHORT).show();
                return;
            }
            input.setText("");

            try {
                HorizontalScrollView sc = requireView().findViewById(R.id.chipsScroll);
                sc.setVisibility(View.GONE);
            } catch (Exception ignore) {}

            Models.Msg um = new Models.Msg("user", "text", text);
            um.id = Db.get().addMsg(um);
            adapter.add(um);
            scroll();

            rememberFrom(text);

            IntentRouter.Intent it = IntentRouter.route(text, lastType, lastQuery);
            lastType = it.type;
            lastQuery = it.query;

            switch (it.type) {
                case IntentRouter.T_ART: doArt(it.query); break;
                case IntentRouter.T_CHAR: doChar(it.query); break;
                case IntentRouter.T_SCHEDULE: doSchedule(it.query); break;
                default: doChat();
            }
        } catch (Exception e) {
            // НИКОГДА не даём приложению упасть из-за сообщения
            try {
                Toast.makeText(getContext(), "Ой, что-то сломалось: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } catch (Exception ignore) {}
        }
    }

    private void scroll() {
        try {
            RecyclerView rv = requireView().findViewById(R.id.chatList);
            rv.scrollToPosition(adapter.getItemCount() - 1);
        } catch (Exception ignore) {}
    }

    private boolean uiOk() { return isAdded() && getActivity() != null && !getActivity().isFinishing(); }

    private void runUi(Runnable r) {
        if (!uiOk()) return;
        requireActivity().runOnUiThread(r);
    }

    // ---------- память ----------
    private void rememberFrom(String text) {
        try {
            String low = text.toLowerCase().replace('ё', 'е');
            java.util.regex.Matcher m1 = java.util.regex.Pattern
                    .compile("(?:меня зовут|я)\\s+([A-Za-zА-Яа-яЁё\\-]{2,20})").matcher(low);
            if (low.contains("меня зовут") && m1.find()) {
                String name = text.substring(m1.start(1), m1.end(1));
                Db.get().kvSet("mem.имя", name);
            }
            java.util.regex.Matcher m2 = java.util.regex.Pattern
                    .compile("запомни[те]?[:,]?\\s+(.+)").matcher(text);
            if (m2.find()) {
                String fact = m2.group(1).trim();
                int cnt;
                try { cnt = Integer.parseInt(Db.get().kvGet("memCount", "0")); } catch (Exception e) { cnt = 0; }
                Db.get().kvSet("mem.заметка " + cnt, fact);
                Db.get().kvSet("memCount", String.valueOf(cnt + 1));
            }
            java.util.regex.Matcher m3 = java.util.regex.Pattern
                    .compile("я люблю\\s+(.+)").matcher(text);
            if (m3.find()) {
                Db.get().kvSet("mem.любит", m3.group(1).trim());
            }
        } catch (Exception ignore) {}
    }

    private Models.Msg startPending(String label) {
        adapter.add(new Models.Msg("assistant", "text", ""));
        adapter.updateLast(label, "text", true);
        scroll();
        return adapter.last();
    }

    private void finishPendingText(String text) {
        Models.Msg m = new Models.Msg("assistant", "text", text);
        m.id = Db.get().addMsg(m);
        adapter.updateLast(text, "text", false);
        scroll();
    }

    // ---------- арты ----------
    private void doArt(String query) {
        busy = true;
        startPending("Ищу арты" + (query.isEmpty() ? "" : " по «" + query + "»") + "… 🎨");
        ArtSources.fetch(query, 6, false, new ArtSources.Done() {
            @Override public void ok(List<Models.ArtItem> items) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    ArrayList<Models.ArtItem> arr = new ArrayList<>(items);
                    String intro = LumiService.randomFrom(new String[]{
                            "Держи! 💜", "Смотри, что нашла ✨", "Лови! Топчики?", "Вот они, красотки 😍",
                            "Намыла артов! По «" + (query.isEmpty() ? "рандом" : query) + "» 🎨"});
                    String json = Models.Msg.imagesJson(intro, arr);
                    Models.Msg m = new Models.Msg("assistant", "images", json);
                    m.id = Db.get().addMsg(m);
                    adapter.updateLast(json, "images", false);
                    scroll();
                });
            }
            @Override public void fail(String msg) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    finishPendingText(msg);
                });
            }
        });
    }

    // ---------- персонаж ----------
    private void doChar(String query) {
        busy = true;
        startPending("Сейчас посмотрю, кто это… 🔍");
        CharacterFinder.search(query, "", new CharacterFinder.Done() {
            @Override public void ok(List<Models.CharacterInfo> list) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    Models.CharacterInfo c = list.get(0);
                    StringBuilder sb = new StringBuilder();
                    sb.append("**").append(c.name).append("**");
                    if (c.nativeName != null && !c.nativeName.isEmpty()) sb.append(" (").append(c.nativeName).append(")");
                    sb.append("\n");
                    if (c.animeTitle != null && !c.animeTitle.isEmpty()) sb.append("📺 ").append(c.animeTitle).append("\n");
                    sb.append("\n").append(c.description == null || c.description.isEmpty()
                            ? "Описание пока не нашла, но она точно классная ✨" : c.description);
                    String text = sb.toString();
                    ArrayList<Models.ArtItem> arr = new ArrayList<>();
                    if (c.image != null && !c.image.isEmpty()) {
                        Models.ArtItem a = new Models.ArtItem();
                        a.url = c.image;
                        a.title = c.name;
                        a.source = c.url;
                        arr.add(a);
                    }
                    String json = Models.Msg.imagesJson(text, arr);
                    Models.Msg m = new Models.Msg("assistant", "images", json);
                    m.id = Db.get().addMsg(m);
                    adapter.updateLast(json, "images", false);
                    scroll();
                });
            }
            @Override public void fail(String msg) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    finishPendingText(msg);
                });
            }
        });
    }

    // ---------- расписание ----------
    private void doSchedule(String query) {
        busy = true;
        startPending("Проверяю расписание эфиров… 📺");
        final String q = query.isEmpty() ? lastQuery : query;
        AnimeApi.search(q.isEmpty() ? "popular" : q, new AnimeApi.Done() {
            @Override public void ok(List<Models.AiringItem> list) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    String text;
                    Models.AiringItem hit = null;
                    for (Models.AiringItem a : list) { if (a.airing) { hit = a; break; } }
                    if (hit != null) {
                        text = "**" + hit.title + "** — серия " + hit.episode
                                + " выйдет через " + AnimeApi.humanize(hit.timeUntil) + " ⏰\n"
                                + "Хочешь — подпишись во вкладке «Аниме» 🔔, и я пришлю уведомление, как только она выйдет!";
                    } else if (!list.isEmpty()) {
                        text = "**" + list.get(0).title + "** — похоже, сезон уже завершён 😌\nМогу поискать арты или другой тайтл!";
                    } else {
                        text = "Не нашла в расписании 😢 Попробуй название на латинице.";
                    }
                    finishPendingText(text);
                });
            }
            @Override public void fail(String msg) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    finishPendingText(msg);
                });
            }
        });
    }

    // ---------- обычный чат (LumiService сам уходит в фоновый поток!) ----------
    private void doChat() {
        busy = true;
        startPending("");
        final List<Models.Msg> history = Db.get().lastMsgs(18);
        final StringBuilder acc = new StringBuilder();
        LumiService.chat(history, Db.get().dossier(), new LumiService.StreamCb() {
            @Override public void onDelta(String d) {
                acc.append(d);
                final String snapshot = LumiService.clean(acc.toString());
                runUi(() -> adapter.updateLast(snapshot, "text", true));
            }
            @Override public void onDone(String full) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    Models.Msg m = new Models.Msg("assistant", "text", full);
                    m.id = Db.get().addMsg(m);
                    adapter.updateLast(full, "text", false);
                    scroll();
                    maybeSpeak(full);
                });
            }
            @Override public void onError(String err) {
                runUi(() -> {
                    busy = false;
                    if (!isAdded()) return;
                    finishPendingText(err);
                });
            }
        });
    }

    // ---------- TTS ----------
    private void maybeSpeak(String text) {
        if (!Db.get().kvGet("tts", "0").equals("1")) return;
        speakNow(text);
    }

    private void speakNow(String text) {
        try {
            if (!ttsReady || tts == null) return;
            tts.speak(Md.plain(text), TextToSpeech.QUEUE_FLUSH, null, "lumi-" + System.currentTimeMillis());
        } catch (Exception ignore) {}
    }

    @Override public void onTts(String text) { speakNow(text); }

    @Override
    public void onImage(Models.ArtItem a) {
        Activity act = getActivity();
        if (act instanceof ViewerDialog.Host) {
            ((ViewerDialog.Host) act).showViewer(a);
        }
    }

    @Override
    public void onRetry() {
        busy = false;
        adapter.removeLast();
        doChat();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignore) {} }
    }
}
