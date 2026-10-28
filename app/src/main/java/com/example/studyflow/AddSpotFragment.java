package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

public class AddSpotFragment extends Fragment {

    public AddSpotFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_spot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());
        view.findViewById(R.id.photo_frame).setOnClickListener(v -> toast("Photo picker (UI only)"));
        view.findViewById(R.id.btn_camera).setOnClickListener(v -> toast("Camera (UI only)"));
        view.findViewById(R.id.btn_gallery).setOnClickListener(v -> toast("Gallery (UI only)"));
        view.findViewById(R.id.btn_adjust_pin).setOnClickListener(v -> toast("Adjust pin on map (UI only)"));

        TextInputLayout nameLayout = view.findViewById(R.id.layout_name);
        TextInputEditText nameInput = view.findViewById(R.id.input_name);
        TextInputEditText reviewInput = view.findViewById(R.id.input_review);
        ChipGroup chips = view.findViewById(R.id.chip_group);

        view.findViewById(R.id.btn_pin_it).setOnClickListener(v -> {
            String name = String.valueOf(nameInput.getText()).trim();
            if (name.isEmpty()) {
                nameLayout.setError("Give this spot a name");
                return;
            }
            nameLayout.setError(null);
            Chip chip = view.findViewById(chips.getCheckedChipId());
            String category = chip == null ? "Other" : chip.getText().toString();
            String review = String.valueOf(reviewInput.getText()).trim();
            ArrayList<Photo> photos = new ArrayList<>();
            photos.add(new Photo(R.drawable.dlsu, name));
            Spot spot = new Spot(name, "Jerimaya", review, category, 14.5995, 120.9842, photos, 0, 0);

            PlaceDetailsFragment details = PlaceDetailsFragment.newInstance(name, category, review);
            details.setSpot(spot);
            DrawerMenu.open(requireActivity(), details);
        });
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}