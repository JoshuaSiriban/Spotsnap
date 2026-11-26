package com.example.studyflow;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The places a user saved.
 *
 *   users/{uid}/saved/{spotId}   { spotId, savedAt }
 *
 * Only the id is stored, so a saved place always shows the latest name, category and photos.
 * Saving or unsaving also adds or removes 1 from the "saves" counter of the spot, in the same
 * transaction, so the number on Place Details stays honest.
 */
public final class SavedRepository {

    private static final String TAG = "SavedRepository";

    private SavedRepository() {}

    public interface OnSavedSpots {
        void onLoaded(ArrayList<Spot> spots);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    private static DocumentReference savedRef(String spotId) {
        return db().collection("users").document(UserSession.getUid())
                .collection("saved").document(spotId);
    }

    /** Calls back with true when the logged in user saved this spot. */
    public static void isSaved(String spotId, Consumer<Boolean> callback) {
        if (!UserSession.isLoggedIn() || spotId == null) {
            callback.accept(false);
            return;
        }
        savedRef(spotId).get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists()))
                .addOnFailureListener(e -> callback.accept(false));
    }

    /** Saves or unsaves a spot. Calls back with an error message, or null when it worked. */
    public static void setSaved(Spot spot, boolean save, Consumer<String> callback) {
        if (!UserSession.isLoggedIn() || spot == null || spot.getId() == null) {
            callback.accept("You need to be logged in to save places.");
            return;
        }
        DocumentReference ref = savedRef(spot.getId());
        DocumentReference spotRef = db().collection("spots").document(spot.getId());

        db().runTransaction(transaction -> {
                    boolean exists = transaction.get(ref).exists();
                    if (save && !exists) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("spotId", spot.getId());
                        data.put("savedAt", FieldValue.serverTimestamp());
                        transaction.set(ref, data);
                        transaction.update(spotRef, "saves", FieldValue.increment(1));
                    } else if (!save && exists) {
                        transaction.delete(ref);
                        transaction.update(spotRef, "saves", FieldValue.increment(-1));
                    }
                    return null;
                }).addOnSuccessListener(unused -> callback.accept(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not change the saved state", e);
                    callback.accept("Could not update your saved places. Check your internet connection.");
                });
    }

    /**
     * Loads the saved places, most recently saved first. Places that were deleted are removed
     * from the list (and cleaned up); places that became private are simply left out.
     */
    public static void loadSaved(Context context, OnSavedSpots callback, @Nullable Consumer<String> onError) {
        if (!UserSession.isLoggedIn()) {
            callback.onLoaded(new ArrayList<>());
            return;
        }
        db().collection("users").document(UserSession.getUid()).collection("saved").get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = new ArrayList<>(snapshot.getDocuments());
                    // newest first (client side, so no Firestore index is needed)
                    Collections.sort(docs, (a, b) -> Long.compare(millis(b), millis(a)));

                    int total = docs.size();
                    if (total == 0) {
                        callback.onLoaded(new ArrayList<>());
                        return;
                    }
                    Spot[] found = new Spot[total];
                    int[] remaining = {total};

                    for (int i = 0; i < total; i++) {
                        final int index = i;
                        DocumentSnapshot saved = docs.get(i);
                        String spotId = saved.getString("spotId") != null ? saved.getString("spotId") : saved.getId();
                        SpotRepository.loadSpot(context, spotId, spot -> {
                            found[index] = spot;
                            if (spot == null) {
                                // could not be read: deleted or private now. Clean up only if gone.
                                db().collection("spots").document(spotId).get()
                                        .addOnSuccessListener(d -> {
                                            if (!d.exists()) saved.getReference().delete();
                                        });
                            }
                            if (--remaining[0] == 0) {
                                ArrayList<Spot> result = new ArrayList<>();
                                for (Spot s : found) if (s != null) result.add(s);
                                callback.onLoaded(result);
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not load saved places", e);
                    if (onError != null) onError.accept("Could not load your saved places.");
                });
    }

    private static long millis(DocumentSnapshot doc) {
        Timestamp t = doc.getTimestamp("savedAt");
        return t == null ? Long.MAX_VALUE : t.toDate().getTime(); // pending write = newest
    }
}