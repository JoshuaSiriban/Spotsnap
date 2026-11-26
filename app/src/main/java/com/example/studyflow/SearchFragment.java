package com.example.studyflow;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Search: type to find places by name, review, category or username, and/or tap a category chip.
 * It searches every public place plus the user's own private ones.
 */
public class SearchFragment extends Fragment {

    private static final int MAX_RESULTS = 50;

    private final ArrayList<Spot> allSpots = new ArrayList<>();
    private boolean loaded;
    private SpotListAdapter adapter;

    public SearchFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText input = view.findViewById(R.id.search_edit_text);
        View clear = view.findViewById(R.id.search_clear);
        ChipGroup chips = view.findViewById(R.id.search_chip_group);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new SpotListAdapter(new SpotListAdapter.Listener() {
            @Override
            public void onOpen(Spot spot) {
                hideKeyboard();
                PlaceDetailsFragment.open(requireActivity(), spot);
            }

            @Override
            public void onShowOnMap(Spot spot) {
                hideKeyboard();
                ((MainActivity) requireActivity()).focusOnSpot(spot);
            }
        });
        RecyclerView list = view.findViewById(R.id.search_results);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                clear.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                runSearch();
            }
        });
        clear.setOnClickListener(v -> input.setText(""));
        chips.setOnCheckedStateChangeListener((group, ids) -> runSearch());
        input.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            return true;
        });

        // Show the keyboard right away
        input.requestFocus();
        input.postDelayed(() -> {
            if (getView() == null) return;
            InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);

        SpotRepository.loadVisibleSpots(requireContext(), spots -> {
            if (getView() == null) return;
            allSpots.clear();
            allSpots.addAll(spots);
            loaded = true;
            runSearch();
        }, message -> {
            if (getView() == null) return;
            setStatus(message + " Check your internet connection.");
        });
    }

    private void runSearch() {
        View root = getView();
        if (root == null || !loaded) return;

        String query = ((EditText) root.findViewById(R.id.search_edit_text)).getText().toString();
        String[] words = simplify(query).trim().isEmpty()
                ? new String[0] : simplify(query).trim().split("\\s+");
        String category = selectedCategory(root);

        if (words.length == 0 && category == null) {
            // nothing typed and no chip: show the newest places as a starting point
            List<Spot> newest = new ArrayList<>(allSpots.subList(0, Math.min(10, allSpots.size())));
            adapter.setSpots(newest);
            setStatus(allSpots.isEmpty() ? "NO PLACES YET" : "NEWEST PLACES");
            return;
        }

        List<Spot> matches = new ArrayList<>();
        final java.util.Map<Spot, Integer> scores = new java.util.IdentityHashMap<>();
        for (Spot spot : allSpots) {
            if (category != null && !Categories.normalize(spot.getCategory()).equals(category)) continue;
            int score = scoreOf(spot, words);
            if (score < 0) continue;
            scores.put(spot, score);
            matches.add(spot);
        }
        // best matches first, then the newest
        Collections.sort(matches, (a, b) -> {
            int sa = scores.get(a);
            int sb = scores.get(b);
            if (sa != sb) return Integer.compare(sb, sa);
            return Long.compare(b.getCreatedAt(), a.getCreatedAt());
        });
        if (matches.size() > MAX_RESULTS) matches = new ArrayList<>(matches.subList(0, MAX_RESULTS));

        adapter.setSpots(matches);
        setStatus(matches.isEmpty() ? "NO PLACES FOUND"
                : matches.size() + (matches.size() == 1 ? " PLACE FOUND" : " PLACES FOUND"));
    }

    /** -1 when some word is not found anywhere; otherwise higher = better match. */
    private int scoreOf(Spot spot, String[] words) {
        String name = simplify(spot.getName());
        String category = simplify(Categories.keywords(spot.getCategory()) + " "
                + Categories.normalize(spot.getCategory()));
        String creator = simplify(spot.getCreator());
        String review = simplify(spot.getReview());

        int score = 0;
        for (String word : words) {
            if (name.startsWith(word) || name.contains(" " + word)) score += 5;
            else if (name.contains(word)) score += 3;
            else if (category.contains(word)) score += 3;
            else if (creator.contains(word)) score += 2;
            else if (review.contains(word)) score += 1;
            else return -1;
        }
        return score;
    }

    /** Lower case and without accents, so "cafe" finds "Café". */
    private static String simplify(String text) {
        if (text == null) return "";
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    @Nullable
    private String selectedCategory(View root) {
        ChipGroup group = root.findViewById(R.id.search_chip_group);
        int id = group.getCheckedChipId();
        if (id == View.NO_ID) return null;
        Chip chip = group.findViewById(id);
        return chip == null ? null : Categories.normalize(chip.getText().toString());
    }

    private void setStatus(String text) {
        View root = getView();
        if (root != null) ((TextView) root.findViewById(R.id.search_status)).setText(text);
    }

    private void hideKeyboard() {
        View root = getView();
        if (root == null) return;
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
    }

    @Override
    public void onDestroyView() {
        hideKeyboard();
        super.onDestroyView();
    }
}