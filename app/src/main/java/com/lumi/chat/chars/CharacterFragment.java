package com.lumi.chat.chars;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.lumi.chat.R;
import com.lumi.chat.db.Db;

import java.util.List;

/** Расширенный поиск персонажей: имя + аниме. */
public class CharacterFragment extends Fragment implements CharacterAdapter.Cb {

    private CharacterAdapter adapter;
    private boolean showingFavs = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_characters, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CharacterAdapter(this);
        list.setAdapter(adapter);

        EditText name = v.findViewById(R.id.charName);
        EditText anime = v.findViewById(R.id.charAnime);
        ImageButton btn = v.findViewById(R.id.btnFindChar);
        btn.setOnClickListener(x -> find(name.getText().toString(), anime.getText().toString()));

        TextView favToggle = v.findViewById(R.id.favToggle);
        favToggle.setOnClickListener(x -> {
            showingFavs = !showingFavs;
            favToggle.setText(showingFavs ? "🔍 Обычный поиск" : "⭐ Избранные персонажи");
            if (showingFavs) adapter.set(Db.get().favChars());
            else adapter.set(java.util.Collections.emptyList());
        });

        SwipeRefresh swipe = v.findViewById(R.id.swipe);
        swipe.setEnabled(false);
    }

    private void find(String name, String anime) {
        if (name.trim().isEmpty() && anime.trim().isEmpty()) {
            Toast.makeText(getContext(), "Введи имя персонажа или аниме 🙃", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(getContext(), "Ищу… 🔍", Toast.LENGTH_SHORT).show();
        CharacterFinder.search(name, anime, new CharacterFinder.Done() {
            @Override public void ok(List<com.lumi.chat.Models.CharacterInfo> list) {
                if (!isAdded()) return;
                adapter.set(list);
            }
            @Override public void fail(String msg) {
                if (!isAdded()) return;
                adapter.set(java.util.Collections.emptyList());
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onWantArts(String charName) {
        if (getActivity() instanceof com.lumi.chat.MainActivity) {
            ((com.lumi.chat.MainActivity) getActivity()).openArtsFor(charName);
        }
    }
}
