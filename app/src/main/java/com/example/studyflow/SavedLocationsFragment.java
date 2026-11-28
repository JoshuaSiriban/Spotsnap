package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The places the user saved with the bookmark button on Place Details (read from Firestore).
 * The chips filter them: All, Want to go, or Visited.
 */
public class SavedLocationsFragment extends Fragment {

    private final ArrayList<Spot> saved = new ArrayList<>();                // every saved place
    private final Map<String, String> statuses = new HashMap<>();           // spot id -> want / visited
    private boolean spotsLoaded;
    private boolean statusesLoaded;
    private String error;                                                   // null when all loaded fine
    private final List<Spot> shown = new ArrayList<>();                     // after the filter
    private SpotListAdapter adapter;

    public SavedLocationsFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved_locations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        adapter = new SpotListAdapter(new SpotListAdapter.Listener() {
            @Override
            public void onOpen(Spot spot) {
                PlaceDetailsFragment.open(requireActivity(), spot);
            }

            @Override
            public void onShowOnMap(Spot spot) {
                ((MainActivity) requireActivity()).focusOnSpot(spot);
            }
        });
        RecyclerView list = view.findViewById(R.id.saved_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);

        ((ChipGroup) view.findViewById(R.id.saved_chip_group))
                .setOnCheckedStateChangeListener((group, ids) -> render());

        view.findViewById(R.id.btn_show_all_map).setOnClickListener(v ->
                ((MainActivity) requireActivity()).showSpotsOnMap(
                        new ArrayList<>(shown), mapLabel()));

        // Loaded again every time this screen is shown, so changes made in Place Details show up here
        SavedRepository.loadSaved(requireContext(), spots -> {
            if (getView() == null) return;
            saved.clear();
            saved.addAll(spots);
            spotsLoaded = true;
            render();
        }, message -> {
            if (getView() == null) return;
            error = message;
            render();
        });

        SavedRepository.loadStatuses(map -> {
            if (getView() == null) return;
            statuses.clear();
            statuses.putAll(map);
            statusesLoaded = true;
            render();
        }, message -> {
            if (getView() == null) return;
            error = message;
            render();
        });
    }

    /** Which filter is selected: null = All, otherwise a SavedRepository.STATUS_ value. */
    @Nullable
    private String selectedStatus() {
        View root = getView();
        if (root == null) return null;
        int id = ((ChipGroup) root.findViewById(R.id.saved_chip_group)).getCheckedChipId();
        if (id == R.id.saved_chip_want) return SavedRepository.STATUS_WANT;
        if (id == R.id.saved_chip_visited) return SavedRepository.STATUS_VISITED;
        return null;
    }

    private String mapLabel() {
        String filter = selectedStatus();
        if (SavedRepository.STATUS_WANT.equals(filter)) return "Want to go";
        if (SavedRepository.STATUS_VISITED.equals(filter)) return "Visited";
        return "Your saved places";
    }

    private void render() {
        View root = getView();
        if (root == null) return;
        TextView status = root.findViewById(R.id.saved_status);
        View showAll = root.findViewById(R.id.btn_show_all_map);

        if (error != null) {
            adapter.setSpots(new ArrayList<>());
            shown.clear();
            status.setText(error + " Check your internet connection and try again.");
            showAll.setVisibility(View.GONE);
            return;
        }
        if (!spotsLoaded || !statusesLoaded) {
            status.setText("Loading...");
            return;
        }

        String filter = selectedStatus();
        shown.clear();
        for (Spot spot : saved) {
            if (filter == null || filter.equals(SavedRepository.statusOf(statuses.get(spot.getId())))) {
                shown.add(spot);
            }
        }
        adapter.setStatuses(statuses);
        adapter.setSpots(shown);

        if (shown.isEmpty()) {
            if (filter == null) status.setText("Nothing saved yet. Tap the bookmark on a place to save it.");
            else if (filter.equals(SavedRepository.STATUS_WANT)) status.setText("No places marked Want to go.");
            else status.setText("No places marked Visited yet. Open a saved place and tap Visited.");
            showAll.setVisibility(View.GONE);
        } else {
            int n = shown.size();
            String what = filter == null ? (n == 1 ? " saved place" : " saved places")
                    : filter.equals(SavedRepository.STATUS_WANT) ? " want to go"
                      : " visited";
            status.setText(n + what);
            showAll.setVisibility(View.VISIBLE);
        }
    }
}