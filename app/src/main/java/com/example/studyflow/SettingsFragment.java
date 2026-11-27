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

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Settings: change password, privacy (default for new posts, make all posts private / public),
 * distance units and delete account (log out is in the side menu). The options are saved on the phone in AppSettings.
 */
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

        // ---- Account
        TextView nameValue = view.findViewById(R.id.row_name_value);
        nameValue.setText(UserSession.getDisplayName(requireContext()));
        view.findViewById(R.id.row_edit_profile).setOnClickListener(v ->
                AccountDialogs.editName(requireActivity(), () -> {
                    if (getView() != null) nameValue.setText(UserSession.getDisplayName(requireContext()));
                }));
        view.findViewById(R.id.row_password).setOnClickListener(v ->
                AccountDialogs.changePassword(requireActivity()));
        ((TextView) view.findViewById(R.id.row_email_value)).setText(UserSession.getEmail());

        // ---- Privacy
        CheckBox defaultPublic = view.findViewById(R.id.chk_public_posts);
        defaultPublic.setChecked(AppSettings.defaultPublic(requireContext()));
        view.findViewById(R.id.row_public_posts).setOnClickListener(v -> defaultPublic.toggle());
        defaultPublic.setOnCheckedChangeListener((box, checked) ->
                AppSettings.setDefaultPublic(requireContext(), checked));

        view.findViewById(R.id.row_all_private).setOnClickListener(v -> confirmVisibility(false));
        view.findViewById(R.id.row_all_public).setOnClickListener(v -> confirmVisibility(true));

        // ---- Preferences
        TextView units = view.findViewById(R.id.row_units_value);
        units.setText(AppSettings.unitName(requireContext()));
        view.findViewById(R.id.row_units).setOnClickListener(v -> {
            AppSettings.setUseMiles(requireContext(), !AppSettings.useMiles(requireContext()));
            units.setText(AppSettings.unitName(requireContext()));
        });

        // ---- Delete account (asks for the password, then removes the account and its data)
        view.findViewById(R.id.btn_delete_account).setOnClickListener(v ->
                AccountDialogs.deleteAccount(requireActivity()));
    }

    private void confirmVisibility(boolean makePublic) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(makePublic ? "Make all posts public?" : "Make all posts private?")
                .setMessage(makePublic
                        ? "Everyone will be able to see all of your places."
                        : "Only you will be able to see your places.")
                .setPositiveButton(makePublic ? "Make public" : "Make private", (d, w) ->
                        SpotRepository.setAllMySpotsPublic(makePublic, new SpotRepository.OnDone() {
                            @Override
                            public void onDone() {
                                toast(makePublic ? "All your posts are public now" : "All your posts are private now");
                            }

                            @Override
                            public void onError(String message) {
                                toast(message);
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void toast(String message) {
        if (getContext() != null) Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
    }
}