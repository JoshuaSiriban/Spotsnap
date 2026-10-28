package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    public SettingsFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        view.findViewById(R.id.row_edit_profile).setOnClickListener(v -> toast("Edit profile (UI only)"));
        view.findViewById(R.id.row_password).setOnClickListener(v -> toast("Change password (UI only)"));
        view.findViewById(R.id.row_email).setOnClickListener(v -> toast("Change email (UI only)"));

        bindCheckRow(view, R.id.row_public_posts, R.id.chk_public_posts);
        bindCheckRow(view, R.id.row_public_profile, R.id.chk_public_profile);
        bindCheckRow(view, R.id.row_notifications, R.id.chk_notifications);

        TextView units = view.findViewById(R.id.row_units_value);
        view.findViewById(R.id.row_units).setOnClickListener(v ->
                units.setText(units.getText().toString().equals("km") ? "mi" : "km"));

        view.findViewById(R.id.btn_logout).setOnClickListener(v -> toast("Logged out (UI only)"));
        view.findViewById(R.id.btn_delete).setOnClickListener(v -> toast("Delete account (UI only)"));
    }

    private void bindCheckRow(View root, int rowId, int checkId) {
        CheckBox box = root.findViewById(checkId);
        root.findViewById(rowId).setOnClickListener(v -> box.toggle());
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
