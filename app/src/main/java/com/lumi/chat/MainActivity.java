package com.lumi.chat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.lumi.chat.anime.AnimeFragment;
import com.lumi.chat.art.ArtFragment;
import com.lumi.chat.art.ViewerDialog;
import com.lumi.chat.chat.ChatFragment;
import com.lumi.chat.chars.CharacterFragment;
import com.lumi.chat.chat.LumiService;
import com.lumi.chat.db.Db;

/** Главная: 4 вкладки + настройки. */
public class MainActivity extends FragmentActivity implements ViewerDialog.Host {

    private ChatFragment chatFrag;
    private ArtFragment artFrag;
    private CharacterFragment charsFrag;
    private AnimeFragment animeFrag;

    private BottomNavigationView nav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatFrag = new ChatFragment();
        artFrag = new ArtFragment();
        charsFrag = new CharacterFragment();
        animeFrag = new AnimeFragment();

        nav = findViewById(R.id.bottomNav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chat) show(chatFrag, "Lumi ✨", "твоя ИИ-подруга · 18 лет");
            else if (id == R.id.nav_art) show(artFrag, "Арты 🎨", "nekos.best · waifu.im · Kitsu · AniList");
            else if (id == R.id.nav_chars) show(charsFrag, "Персонажи 🔍", "имя + аниме · AniList · Kitsu");
            else if (id == R.id.nav_anime) show(animeFrag, "Аниме 🔔", "расписание серий · AniList");
            return true;
        });

        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettings());
        show(chatFrag, "Lumi ✨", "твоя ИИ-подруга · 18 лет");

        askNotifications();
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String open = intent.getStringExtra("open");
        if ("anime".equals(open)) {
            nav.setSelectedItemId(R.id.nav_anime);
        } else if ("chat".equals(open)) {
            nav.setSelectedItemId(R.id.nav_chat);
            String discuss = intent.getStringExtra("discuss");
            if (discuss != null && !discuss.isEmpty()) chatFrag.discuss(discuss);
        }
    }

    private void show(Fragment f, String title, String sub) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .commitAllowingStateLoss();
        ((TextView) findViewById(R.id.title)).setText(title);
        ((TextView) findViewById(R.id.subtitle)).setText(sub);
    }

    public void openArtsFor(String query) {
        nav.setSelectedItemId(R.id.nav_art);
        artFrag.applySearch(query);
    }

    @Override
    public void showViewer(Models.ArtItem a) {
        ViewerDialog.show(this, a);
    }

    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 5);
        }
    }

    // ---------- настройки ----------
    private void showSettings() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null, false);
        android.widget.EditText etModel = v.findViewById(R.id.etModel);
        android.widget.EditText etToken7 = v.findViewById(R.id.etToken7);
        android.widget.EditText etPoll = v.findViewById(R.id.etPoll);
        android.widget.CheckBox cbTts = v.findViewById(R.id.cbTts);
        TextView dossier = v.findViewById(R.id.tvDossier);

        String model = Db.get().kvGet("model", "");
        etModel.setText(model.isEmpty() ? LumiService.DEFAULT_MODEL : model);
        etToken7.setText(Db.get().kvGet("token7", ""));
        etPoll.setText(Db.get().kvGet("polltoken", ""));
        cbTts.setChecked(Db.get().kvGet("tts", "0").equals("1"));
        String d = Db.get().dossier();
        dossier.setText(d.isEmpty() ? "Досье пусто — расскажи Люми о себе в чате 💜" : "Что Люми запомнила:\n" + d.replace("\n-", "\n• ").replaceFirst("^\n•", "•"));

        new AlertDialog.Builder(this)
                .setView(v)
                .show();

        v.findViewById(R.id.btnSave).setOnClickListener(x -> {
            Db.get().kvSet("model", etModel.getText().toString().trim());
            Db.get().kvSet("token7", etToken7.getText().toString().trim());
            Db.get().kvSet("polltoken", etPoll.getText().toString().trim());
            Db.get().kvSet("tts", cbTts.isChecked() ? "1" : "0");
            Toast.makeText(this, "Сохранила! ✨", Toast.LENGTH_SHORT).show();
        });
        v.findViewById(R.id.btnClearChat).setOnClickListener(x -> {
            Db.get().clearMsgs();
            Toast.makeText(this, "Чат очищен. Начнём заново! 🌸", Toast.LENGTH_SHORT).show();
            show(chatFrag, "Lumi ✨", "твоя ИИ-подруга · 18 лет");
        });
        v.findViewById(R.id.btnClearMem).setOnClickListener(x -> {
            Db.get().kvDel("mem.имя");
            for (String k : new String[]{"mem.любит", "memCount"}) Db.get().kvDel(k);
            for (int i = 0; i < 200; i++) Db.get().kvDel("mem.заметка " + i);
            Toast.makeText(this, "Забыла всё обо тебе 🧹", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 5 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Уведомления о сериях включены 🔔", Toast.LENGTH_SHORT).show();
        }
    }
}
