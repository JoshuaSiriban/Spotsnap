package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

/** UI only: shows dummy data unless values are passed in from Add Spot. */
public class PlaceDetailsFragment extends Fragment {

    public PlaceDetailsFragment() {
        // Required empty public constructor
    }

    public static PlaceDetailsFragment newInstance(@Nullable String name, @Nullable String category,
                                                   @Nullable String review) {
        PlaceDetailsFragment f = new PlaceDetailsFragment();
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("category", category);
        b.putString("review", review);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_place_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        if (args != null) {
            String name = args.getString("name");
            String category = args.getString("category");
            String review = args.getString("review");
            if (name != null) ((TextView) view.findViewById(R.id.txt_name)).setText(name);
            if (category != null) ((TextView) view.findViewById(R.id.txt_category)).setText(category.toUpperCase());
            if (review != null && !review.isEmpty()) ((TextView) view.findViewById(R.id.txt_review)).setText(review);
        }

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        RecyclerView rv = view.findViewById(R.id.rv_photos);
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        rv.setAdapter(new PolaroidAdapter());

        // Like and Save toggle between two looks (see res/color/icon_toggle.xml)
        view.findViewById(R.id.btn_like).setOnClickListener(v -> v.setSelected(!v.isSelected()));
        view.findViewById(R.id.btn_save).setOnClickListener(v -> {
            v.setSelected(!v.isSelected());
            Toast.makeText(requireContext(), v.isSelected() ? "Saved to your places" : "Removed from saved",
                    Toast.LENGTH_SHORT).show();
        });

        MaterialButton follow = view.findViewById(R.id.btn_follow);
        follow.setOnClickListener(v ->
                follow.setText(follow.getText().toString().equals("Follow") ? "Following" : "Follow"));

        view.findViewById(R.id.btn_go).setOnClickListener(v ->
                Toast.makeText(requireContext(), "Directions (UI only)", Toast.LENGTH_SHORT).show());
    }
}