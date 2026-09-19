package com.lumi.chat.anime;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
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
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.lumi.chat.R;

/** Аниме: что выходит в ближайшие 7 дней + поиск + подписки. */
public class AnimeFragment extends Fragment {

    private AnimeAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_anime, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        RecyclerView list = v.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AnimeAdapter();
        list.setAdapter(adapter);

        EditText search = v.findViewById(R.id.searchAnime);
        ImageButton btn = v.findViewById(R.id.btnSearchAnime);
        btn.setOnClickListener(x -> {
            String q = search.getText().toString().trim();
            if (q.isEmpty()) { loadSchedule(); return; }
            ((TextView) requireView().findViewById(R.id.sectionTitle)).setText("🔍 Результаты: " + q);
            searchAnime(q);
        });

        SwipeRefreshLayout swipe = v.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(() -> {
            search.setText("");
            ((TextView) requireView().findViewById(R.id.sectionTitle)).setText("🔥 Скоро в эфире (7 дней)");
            loadSchedule();
        });

        loadSchedule();
    }

    private void loadSchedule() {
        SwipeRefreshLayout swipe = requireView().findViewById(R.id.swipe);
        swipe.setRefreshing(true);
        long now = System.currentTimeMillis() / 1000L;
        AnimeApi.airing(now - 3600, now + 7L * 24 * 3600, new AnimeApi.Done() {
            @Override public void ok(java.util.List<com.lumi.chat.Models.AiringItem> items) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                adapter.set(items);
            }
            @Override public void fail(String msg) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void searchAnime(String q) {
        SwipeRefreshLayout swipe = requireView().findViewById(R.id.swipe);
        swipe.setRefreshing(true);
        AnimeApi.search(q, new AnimeApi.Done() {
            @Override public void ok(java.util.List<com.lumi.chat.Models.AiringItem> items) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                adapter.set(items);
            }
            @Override public void fail(String msg) {
                if (!isAdded()) return;
                swipe.setRefreshing(false);
                Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= 33 &&
                ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }
}
