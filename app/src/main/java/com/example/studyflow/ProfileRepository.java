package com.example.studyflow;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/** Saves the bio and the profile picture in users/{uid}. (The name is saved by AuthRepository.) */
public final class ProfileRepository {

    private static final String TAG = "ProfileRepository";

    private ProfileRepository() {}

    /**
     * @param newPhoto    a new picture, or null to keep the current one
     * @param removePhoto true to delete the current picture (ignored when {@code newPhoto} is given)
     */
    public static void save(Context context, String bio, @Nullable byte[] newPhoto,
                            boolean removePhoto, AuthRepository.Callback callback) {
        if (!UserSession.isLoggedIn()) {
            callback.onError("You are not logged in.");
            return;
        }
        if (!AuthRepository.isOnline(context)) {
            callback.onError(AuthRepository.NO_INTERNET);
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("bio", bio == null ? "" : bio);
        if (newPhoto != null) {
            data.put("photo", Blob.fromBytes(newPhoto));
        } else if (removePhoto) {
            data.put("photo", FieldValue.delete());
        }

        FirebaseFirestore.getInstance().collection("users").document(UserSession.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not save the profile", e);
                    callback.onError(AuthRepository.describe(e));
                });
    }
}