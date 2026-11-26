package com.example.studyflow;

import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

/** The small pop-ups for changing your name and your password (Profile and Settings). */
public final class AccountDialogs {

    private AccountDialogs() {}

    /** Asks for a new full name and saves it. {@code afterSave} runs once it worked. */
    public static void editName(FragmentActivity activity, Runnable afterSave) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_edit_name, null);
        TextInputEditText input = content.findViewById(R.id.input_full_name);
        input.setText(UserSession.getDisplayName(activity));
        if (input.getText() != null) input.setSelection(input.getText().length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Edit profile")
                .setView(content)
                .setPositiveButton("Save", null) // set below so the dialog stays open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            if (name.isEmpty()) {
                input.setError("Please enter your name");
                return;
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            AuthRepository.updateFullName(activity, name, new AuthRepository.Callback() {
                @Override
                public void onSuccess() {
                    dialog.dismiss();
                    refreshDrawer(activity);
                    Toast.makeText(activity, "Profile updated", Toast.LENGTH_SHORT).show();
                    if (afterSave != null) afterSave.run();
                }

                @Override
                public void onError(String message) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    /** Asks for the current and the new password, then changes it. */
    public static void changePassword(FragmentActivity activity) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_change_password, null);
        TextInputEditText current = content.findViewById(R.id.input_current_password);
        TextInputEditText newPass = content.findViewById(R.id.input_new_password);
        TextInputEditText repeat = content.findViewById(R.id.input_repeat_password);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Change password")
                .setView(content)
                .setPositiveButton("Change", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldPassword = text(current);
            String newPassword = text(newPass);

            if (oldPassword.isEmpty()) {
                current.setError("Enter your current password");
                return;
            }
            if (newPassword.length() < 6) {
                newPass.setError("Use at least 6 characters");
                return;
            }
            if (!newPassword.equals(text(repeat))) {
                repeat.setError("The passwords do not match");
                return;
            }
            if (newPassword.equals(oldPassword)) {
                newPass.setError("Choose a different password");
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            AuthRepository.changePassword(oldPassword, newPassword, new AuthRepository.Callback() {
                @Override
                public void onSuccess() {
                    dialog.dismiss();
                    Toast.makeText(activity, "Password changed", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String message) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                }
            });
        }));
        dialog.show();
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static void refreshDrawer(FragmentActivity activity) {
        DrawerLayout drawer = activity.findViewById(R.id.drawer_layout);
        if (drawer != null) DrawerMenu.bindUser(drawer);
    }
}