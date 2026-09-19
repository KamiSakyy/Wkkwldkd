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
import android.widget.ImageButton;
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
import com.lumi.chat.util.Saver;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Чат с Люми: текст + арты + персонажи + расписание + память + голос. */
public class ChatFragment extends Fragment implements ChatAdapter.Cb {

    private ChatAdapter adapter;
    private EditText input;
    private TextToSpeech tts;
    private boolean ttsReady = false;

    private String lastType = IntentRouter.T_CHAT;
    private String lastQuery = "";

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

        tts = new TextToSpeech(getContext(), st -> {
            if (st == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru"));
            ttsReady = st == TextToSpeech.SUCCESS;
        });
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
        if (input == null) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");

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
    }

    private void scroll() {
        RecyclerView rv = requireView().findViewById(R.id.chatList);
        rv.scrollToPosition(adapter.getItemCount() - 1);
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
                Db.get().kvSet("mem.заметка " + (new Date().getTime() % 10000), fact);
            }
            java.util.regex.Matcher m3 = java.util.regex.Pattern
                    .compile("я люблю\\s+(.+)").matcher(text);
            if (m3.find()) {
                Db.get().kvSet("mem.любит", m3.group(1).trim());
            }
        } catch (Exception ignore) {}
    }

    // ---------- арты ----------
    private void doArt(String query) {
        adapter.add(new Models.Msg("assistant", "text", ""));
        adapter.updateLast("Ищу арты" + (query.isEmpty() ? "" : " по «" + query + "»") + "… 🎨", "text", true);
        scroll();
        ArtSources.fetch(query, 6, false, new ArtSources.Done() {
            @Override public void ok(List<Models.ArtItem> items) {
                if (!isAdded()) return;
                ArrayList<Models.ArtItem> arr = new ArrayList<>(items);
                String intro = LumiService.randomFrom(new String[]{
                        "Держи! 💜", "Смотри, что нашла ✨", "Лови! Топчики?", "Вот они, красотки 😍",
                        "Намыла артов! По «" + (query.isEmpty() ? "рандом" : query) + "» 🎨"});
                Models.Msg m = new Models.Msg("assistant", "images", Models.Msg.imagesJson(intro, arr));
                m.id = Db.get().addMsg(m);
                adapter.updateLast(Models.Msg.imagesJson(intro, arr), "images", false);
                scroll();
            }
            @Override public void fail(String msg) {
                if (!isAdded()) return;
                Models.Msg m = new Models.Msg("assistant", "text", msg);
                m.id = Db.get().addMsg(m);
                adapter.updateLast(msg, "text", false);
                scroll();
            }
        });
    }

    // ---------- персонаж ----------
    private void doChar(String query) {
        adapter.add(new Models.Msg("assistant", "text", ""));
        adapter.updateLast("Сейчас посмотрю, кто это… 🔍", "text", true);
        scroll();
        CharacterFinder.search(query, "", new CharacterFinder.Done() {
            @Override public void ok(List<Models.CharacterInfo> list) {
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
                Models.Msg m = new Models.Msg("assistant", "images",
                        Models.Msg.imagesJson(text, arr));
                m.id = Db.get().addMsg(m);
                adapter.updateLast(Models.Msg.imagesJson(text, arr), "images", false);
                scroll();
            }
            @Override public void fail(String msg) {
                if (!isAdded()) return;
                Models.Msg m = new Models.Msg("assistant", "text", msg);
                m.id = Db.get().addMsg(m);
                adapter.updateLast(msg, "text", false);
                scroll();
            }
        });
    }

    // ---------- расписание ----------
    private void doSchedule(String query) {
        adapter.add(new Models.Msg("assistant", "text", ""));
        adapter.updateLast("Проверяю расписание эфиров… 📺", "text", true);
        scroll();
        final String q = query.isEmpty() ? lastQuery : query;
        AnimeApi.search(q.isEmpty() ? "popular" : q, new AnimeApi.Done() {
            @Override public void ok(List<Models.AiringItem> list) {
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
                Models.Msg m = new Models.Msg("assistant", "text", text);
                m.id = Db.get().addMsg(m);
                adapter.updateLast(text, "text", false);
                scroll();
            }
            @Override public void fail(String msg) {
                if (!isAdded()) return;
                Models.Msg m = new Models.Msg("assistant", "text", msg);
                m.id = Db.get().addMsg(m);
                adapter.updateLast(msg, "text", false);
                scroll();
            }
        });
    }

    // ---------- обычный чат ----------
    private void doChat() {
        adapter.add(new Models.Msg("assistant", "text", ""));
        adapter.updateLast("Печатает…", "text", true);
        scroll();

        final List<Models.Msg> history = Db.get().lastMsgs(18);
        final StringBuilder acc = new StringBuilder();
        LumiService.chat(history, Db.get().dossier(), new LumiService.StreamCb() {
            @Override public void onDelta(String d) {
                if (!isAdded()) return;
                acc.append(d);
                requireActivity().runOnUiThread(() -> {
                    adapter.updateLast(LumiService.clean(acc.toString()), "text", true);
                });
            }
            @Override public void onDone(String full) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Models.Msg m = new Models.Msg("assistant", "text", full);
                    m.id = Db.get().addMsg(m);
                    adapter.updateLast(full, "text", false);
                    scroll();
                    maybeSpeak(full);
                });
            }
            @Override public void onError(String err) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    Models.Msg m = new Models.Msg("assistant", "text", err);
                    m.id = Db.get().addMsg(m);
                    adapter.updateLast(err, "text", false);
                    scroll();
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
        if (!ttsReady || tts == null) return;
        tts.speak(Md.plain(text), TextToSpeech.QUEUE_FLUSH, null, "lumi-" + System.currentTimeMillis());
    }

    @Override public void onTts(String text) { speakNow(text); }

    @Override public void onImage(Models.ArtItem a) {
        Activity act = getActivity();
        if (act instanceof ViewerDialog.Host) {
            ((ViewerDialog.Host) act).showViewer(a);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignore) {} }
    }
}
