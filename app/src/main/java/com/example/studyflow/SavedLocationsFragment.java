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

import java.util.ArrayList;

/** The places the user saved with the bookmark button on Place Details (read from Firestore). */
public class SavedLocationsFragment extends Fragment {

    private final ArrayList<Spot> saved = new ArrayList<>();

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

        TextView status = view.findViewById(R.id.saved_status);
        View showAll = view.findViewById(R.id.btn_show_all_map);

        SpotListAdapter adapter = new SpotListAdapter(new SpotListAdapter.Listener() {
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

        showAll.setOnClickListener(v ->
                ((MainActivity) requireActivity()).showSpotsOnMap(new ArrayList<>(saved), "Your saved places"));

        // Loaded again every time this screen is shown, so unsaving in Place Details shows up here
        SavedRepository.loadSaved(requireContext(), spots -> {
            if (getView() == null) return;
            saved.clear();
            saved.addAll(spots);
            adapter.setSpots(spots);
            if (spots.isEmpty()) {
                status.setText("Nothing saved yet. Tap the bookmark on a place to save it.");
                showAll.setVisibility(View.GONE);
            } else {
                status.setText(spots.size() + (spots.size() == 1 ? " saved place" : " saved places"));
                showAll.setVisibility(View.VISIBLE);
            }
        }, message -> {
            if (getView() == null) return;
            status.setText(message + " Check your internet connection and try again.");
        });
    }
}