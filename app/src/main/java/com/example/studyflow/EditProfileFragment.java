package com.example.studyflow;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Edit your profile: picture, full name and short bio. */
public class EditProfileFragment extends Fragment {

    private byte[] currentPhoto;      // the picture saved now (null = none)
    private byte[] newPhoto;          // a picture chosen on this screen, not saved yet
    private boolean removePhoto;      // "Remove" was tapped
    private boolean saving;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ActivityResultLauncher<PickVisualMediaRequest> pickPhoto = registerForActivityResult(
            new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) prepareAvatar(uri);
            });

    public EditProfileFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // What we already know (kept on the phone)
        ((TextInputEditText) view.findViewById(R.id.edit_name)).setText(UserSession.getDisplayName(context));
        ((TextInputEditText) view.findViewById(R.id.edit_bio)).setText(UserSession.getBio(context));
        currentPhoto = UserSession.getPhoto(context);
        showAvatar();

        view.findViewById(R.id.edit_avatar).setOnClickListener(v -> choosePhoto());
        view.findViewById(R.id.btn_change_photo).setOnClickListener(v -> choosePhoto());
        view.findViewById(R.id.btn_remove_photo).setOnClickListener(v -> {
            newPhoto = null;
            removePhoto = true;
            showAvatar();
        });
        view.findViewById(R.id.btn_save_profile).setOnClickListener(v -> save());

        // The saved copy on the phone can be old (another phone, or an older version of the app)
        SocialRepository.loadUser(UserSession.getUid(), user -> {
            View root = getView();
            if (root == null || user == null) return;
            TextInputEditText bio = root.findViewById(R.id.edit_bio);
            if (bio.getText() == null || bio.getText().length() == 0) bio.setText(user.bio);
            if (currentPhoto == null && user.photo != null) {
                currentPhoto = user.photo;
                showAvatar();
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        worker.shutdown();
    }

    // ------------------------------------------------------------------ picture

    private void choosePhoto() {
        pickPhoto.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    /** Crops and shrinks the chosen picture on a background thread. */
    private void prepareAvatar(android.net.Uri uri) {
        Context appContext = requireContext().getApplicationContext();
        worker.execute(() -> {
            byte[] bytes = AvatarUtils.prepare(appContext, uri);
            mainHandler.post(() -> {
                if (getView() == null) return;
                if (bytes == null) {
                    toast("Could not read that picture");
                    return;
                }
                newPhoto = bytes;
                removePhoto = false;
                showAvatar();
            });
        });
    }

    /** The picture that will be saved: the new one, none (after Remove), or the current one. */
    private byte[] pictureToShow() {
        if (newPhoto != null) return newPhoto;
        return removePhoto ? null : currentPhoto;
    }

    private void showAvatar() {
        View root = getView();
        if (root == null) return;
        byte[] shown = pictureToShow();
        AvatarUtils.show((ImageView) root.findViewById(R.id.edit_avatar), shown, 30);
        root.findViewById(R.id.btn_remove_photo).setVisibility(shown == null ? View.GONE : View.VISIBLE);
    }

    // --------------------------------------------------------------------- save

    private void save() {
        View root = getView();
        if (root == null || saving) return;

        TextInputLayout nameLayout = root.findViewById(R.id.layout_edit_name);
        String name = String.valueOf(((TextInputEditText) root.findViewById(R.id.edit_name)).getText()).trim();
        String bio = String.valueOf(((TextInputEditText) root.findViewById(R.id.edit_bio)).getText()).trim();

        if (name.isEmpty()) {
            nameLayout.setError("Please enter your name");
            return;
        }
        nameLayout.setError(null);

        if (!AuthRepository.isOnline(requireContext())) {
            toast(AuthRepository.NO_INTERNET);
            return;
        }

        setSaving(true);
        Context app = requireContext().getApplicationContext();
        FragmentActivity activity = requireActivity();
        byte[] photoToSave = newPhoto;
        boolean remove = removePhoto;
        byte[] photoAfterSave = pictureToShow();

        // Step 1: the name. Step 2: the bio and the picture.
        AuthRepository.updateFullName(app, name, new AuthRepository.Callback() {
            @Override
            public void onSuccess() {
                ProfileRepository.save(app, bio, photoToSave, remove, new AuthRepository.Callback() {
                    @Override
                    public void onSuccess() {
                        UserSession.saveExtras(app, bio, photoAfterSave);
                        DrawerLayout drawer = activity.findViewById(R.id.drawer_layout);
                        if (drawer != null) DrawerMenu.bindUser(drawer);
                        Toast.makeText(app, "Profile updated", Toast.LENGTH_SHORT).show();
                        if (isAdded() && !activity.getSupportFragmentManager().isStateSaved()) {
                            activity.getOnBackPressedDispatcher().onBackPressed();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        fail(app, message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                fail(app, message);
            }
        });
    }

    private void fail(Context app, String message) {
        Toast.makeText(app, message, Toast.LENGTH_LONG).show();
        if (getView() != null) setSaving(false);
    }

    private void setSaving(boolean value) {
        saving = value;
        View root = getView();
        if (root == null) return;
        MaterialButton button = root.findViewById(R.id.btn_save_profile);
        button.setEnabled(!value);
        button.setText(value ? "Saving..." : "Save changes");
    }

    private void toast(String message) {
        Context context = getContext();
        if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}