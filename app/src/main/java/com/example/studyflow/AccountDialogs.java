package com.example.studyflow;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * The small pop-ups for the account: forgot password (Login), change your name and your
 * password (Profile and Settings) and delete your account (Settings).
 */
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

    /**
     * "Forgot password?" on the Login screen: asks for the email and has Firebase send a reset
     * link. {@code prefillEmail} is whatever the person already typed on the login screen.
     */
    public static void forgotPassword(FragmentActivity activity, String prefillEmail) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_forgot_password, null);
        TextInputLayout layout = content.findViewById(R.id.layout_forgot_email);
        TextInputEditText input = content.findViewById(R.id.input_forgot_email);
        clearErrorOnEdit(input, layout);
        if (prefillEmail != null && !prefillEmail.isEmpty()) {
            input.setText(prefillEmail);
            if (input.getText() != null) input.setSelection(input.getText().length());
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Reset password")
                .setView(content)
                .setPositiveButton("Send link", null) // set below so the dialog stays open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button send = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            send.setOnClickListener(v -> {
                String email = text(input).trim();
                String error = AuthRepository.validateEmail(email);
                if (error != null) {
                    layout.setError(error);
                    return;
                }
                send.setEnabled(false);
                AuthRepository.sendPasswordReset(activity, email, new AuthRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        dialog.dismiss();
                        Toast.makeText(activity, "If an account exists for " + email
                                        + ", a reset link is on its way. Check your spam folder too.",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onError(String message) {
                        send.setEnabled(true);
                        layout.setError(message);
                    }
                });
            });
        });
        dialog.show();
    }

    /**
     * "Delete account" in Settings: explains what will be lost, asks for the password and then
     * removes the account, the user's spots and the profile (see AccountDeleter). On success the
     * person lands on the login screen.
     */
    public static void deleteAccount(FragmentActivity activity) {
        View content = activity.getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
        TextInputLayout layout = content.findViewById(R.id.layout_delete_password);
        TextInputEditText input = content.findViewById(R.id.input_delete_password);
        clearErrorOnEdit(input, layout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle("Delete account?")
                .setView(content)
                .setPositiveButton("Delete forever", null) // set below so the dialog stays open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button delete = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            delete.setTextColor(ContextCompat.getColor(activity, R.color.coral));

            delete.setOnClickListener(v -> {
                String password = text(input);
                if (password.isEmpty()) {
                    layout.setError("Enter your password to confirm");
                    return;
                }
                setDeleting(dialog, delete, cancel, input, true);
                AccountDeleter.deleteCurrentAccount(activity, password, new AuthRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        dialog.dismiss();
                        Toast.makeText(activity, "Your account was deleted", Toast.LENGTH_LONG).show();
                        AuthRepository.openLogin(activity);
                    }

                    @Override
                    public void onError(String message) {
                        setDeleting(dialog, delete, cancel, input, false);
                        layout.setError(message);
                    }
                });
            });
        });
        dialog.show();
    }

    // -------------------------------------------------------------- helpers

    /** Locks the dialog while the account is being deleted, so it cannot be dismissed half way. */
    private static void setDeleting(AlertDialog dialog, Button delete, Button cancel,
                                    TextInputEditText input, boolean deleting) {
        dialog.setCancelable(!deleting);
        dialog.setCanceledOnTouchOutside(!deleting);
        delete.setEnabled(!deleting);
        cancel.setEnabled(!deleting);
        input.setEnabled(!deleting);
        delete.setText(deleting ? "Deleting..." : "Delete forever");
    }

    /** Hides the red error text under a field as soon as the person starts typing again. */
    private static void clearErrorOnEdit(TextInputEditText input, TextInputLayout layout) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private static String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static void refreshDrawer(FragmentActivity activity) {
        DrawerLayout drawer = activity.findViewById(R.id.drawer_layout);
        if (drawer != null) DrawerMenu.bindUser(drawer);
    }
}