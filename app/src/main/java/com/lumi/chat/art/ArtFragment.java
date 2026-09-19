package com.lumi.chat.art;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.lumi.chat.Models;
import com.lumi.chat.R;
import com.lumi.chat.db.Db;
import com.lumi.chat.util.Saver;

import java.util.ArrayList;
import java.util.List;

/** Галерея артов: категории, поиск, избранное. */
public class ArtFragment extends Fragment implements ArtAdapter.Cb {

    private ArtAdapter adapter;
    private String category = "Все";
    private String query = "";
    private final List<TextView> chipViews = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup c, @Nullable Bundle b) {
        return inf.inflate(R.layout.fragment_art, c, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle b) {
        RecyclerView grid = v.findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(getContext(), 2));
        adapter = new ArtAdapter(this);
        grid.setAdapter(adapter);

        EditText search = v.findViewById(R.id.searchArt);
        ImageButton btn = v.findViewById(R.id.btnSearchArt);
        btn.setOnClickListener(x -> {
            query = search.getText().toString().trim();
            if (query.isEmpty()) { loadCategory(); return; }
            category = "";
            markChips();
            reload();
        });

        SwipeRefresh swipe = v.findViewById(R.id.swipe);
        swipe.setOnRefreshListener(this::reload);

        LinearLayout chips = v.findViewById(R.id.chips);
        buildChips(chips);
        markChips();
        loadCategory();
    }

    private void buildChips(LinearLayout holder) {
        holder.removeAllViews();
        chipViews.clear();
        for (String cat : ArtSources.CATEGORIES) {
            TextView t = new TextView(getContext());
            t.setText(cat);
            t.setBackgroundResource(R.drawable.bg_input);
            int pad = (int) (10 * getResources().getDisplayMetrics().density);
            t.setPadding(pad + 6, pad - 4, pad + 6, pad - 4);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, pad, 0);
            t.setLayoutParams(lp);
            t.setTextColor(0xFFA78BFA);
            t.setTextSize(13f);
            t.setOnClickListener(x -> {
                category = cat;
                query = "";
                markChips();
                if ("⭐ Избранное".equals(cat)) {
                    List<Models.ArtItem> favs = Db.get().favs();
                    adapter.set(favs);
                    if (favs.isEmpty()) Toast.makeText(getContext(), "Избранное пусто — зажми арт, чтобы добавить ⭐", Toast.LENGTH_SHORT).show();
                } else {
                    loadCategory();
                }
            });
            holder.addView(t);
            chipViews.add(t);
        }
    }

    private void markChips() {
        for (int i = 0; i < chipViews.size(); i++) {
            TextView t = chipViews.get(i);
            boolean sel = ArtSources.CATEGORIES[i].equals(category);
            t.setAlpha(sel ? 1f : 0.45f);
        }
    }

    public void applySearch(String q) {
        query = q;
        category = "";
        if (getView() == null) return; // фрагмент ещё не создан — загрузит onViewCreated
        markChips();
        reload();
        RecyclerView rv = getView().findViewById(R.id.grid);
        if (rv != null) rv.scrollToPosition(0);
    }

    private void loadCategory() {
        SwipeRefresh swipe = requireView().findViewById(R.id.swipe);
        swipe.setRefreshing(true);
        ArtSources.byCategory(category.isEmpty() ? "Все" : category, 12, new ArtSources.Done() {
            @Override public void ok(List<Models.ArtItem> items) {
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

    private void reload() {
        if (!query.isEmpty()) {
            SwipeRefresh swipe = requireView().findViewById(R.id.swipe);
            swipe.setRefreshing(true);
            ArtSources.fetch(query, 12, false, new ArtSources.Done() {
                @Override public void ok(List<Models.ArtItem> items) {
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
        } else {
            loadCategory();
        }
    }

    @Override public void onClick(Models.ArtItem a) { ViewerDialog.show(requireActivity(), a); }

    @Override
    public void onLongClick(Models.ArtItem a) {
        boolean fav = Db.get().isFav(a.url);
        if (fav) { Db.get().delFav(a.url); Toast.makeText(getContext(), "Убрала из избранного", Toast.LENGTH_SHORT).show(); }
        else { Db.get().addFav("art", a.title, a.url, a.source); Toast.makeText(getContext(), "В избранном ⭐", Toast.LENGTH_SHORT).show(); }
    }
}
