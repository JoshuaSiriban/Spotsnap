package com.example.studyflow;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reviews and star ratings.
 *
 *   spots/{spotId}/reviews/{uid}   one review per person (the document id is their uid)
 *
 * The spot keeps two counters (spots/{spotId}.ratingSum and .ratingCount). They change in the
 * same transaction as the review, so the average always matches the real reviews.
 */
public final class ReviewRepository {

    private static final String TAG = "ReviewRepository";
    private static final String SPOTS = "spots";
    private static final String REVIEWS = "reviews";

    private ReviewRepository() {}

    /** Called when a review was saved or deleted. The changes tell the screen how the spot's counters moved. */
    public interface OnChanged {
        void onDone(long ratingSumChange, int ratingCountChange);

        void onError(String message);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    /** All reviews of a spot, newest first. */
    public static void loadReviews(String spotId, Consumer<List<Review>> onResult,
                                   Consumer<String> onError) {
        db().collection(SPOTS).document(spotId).collection(REVIEWS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Review> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Long rating = doc.getLong("rating");
                        if (rating == null) continue;
                        String username = doc.getString("username");
                        String text = doc.getString("text");
                        Timestamp created = doc.getTimestamp("createdAt");
                        list.add(new Review(
                                doc.getId(),
                                username == null || username.isEmpty() ? "unknown" : username,
                                rating.intValue(),
                                text == null ? "" : text,
                                created == null ? 0 : created.toDate().getTime()));
                    }
                    onResult.accept(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not load the reviews", e);
                    onError.accept("Could not load the reviews.");
                });
    }

    /**
     * Adds the logged in user's review, or replaces it if they already wrote one.
     * {@code rating} must be 1 to 5. {@code text} may be empty.
     */
    public static void saveReview(Spot spot, String username, int rating, String text,
                                  OnChanged callback) {
        if (!UserSession.isLoggedIn() || spot == null || spot.getId() == null) {
            callback.onError("You need to be logged in to review places.");
            return;
        }
        if (rating < 1 || rating > 5) {
            callback.onError("Please choose 1 to 5 stars.");
            return;
        }
        String uid = UserSession.getUid();
        DocumentReference spotRef = db().collection(SPOTS).document(spot.getId());
        DocumentReference reviewRef = spotRef.collection(REVIEWS).document(uid);

        db().runTransaction(transaction -> {
                    DocumentSnapshot old = transaction.get(reviewRef);

                    Map<String, Object> data = new HashMap<>();
                    data.put("uid", uid);
                    data.put("username", username == null ? "" : username);
                    data.put("rating", rating);
                    data.put("text", text == null ? "" : text);

                    long sumChange;
                    long countChange;
                    if (old.exists()) {
                        Long oldRating = old.getLong("rating");
                        sumChange = rating - (oldRating == null ? 0 : oldRating);
                        countChange = oldRating == null ? 1 : 0;
                        data.put("updatedAt", FieldValue.serverTimestamp());
                        transaction.update(reviewRef, data);
                    } else {
                        sumChange = rating;
                        countChange = 1;
                        data.put("createdAt", FieldValue.serverTimestamp());
                        transaction.set(reviewRef, data);
                    }
                    transaction.update(spotRef,
                            "ratingSum", FieldValue.increment(sumChange),
                            "ratingCount", FieldValue.increment(countChange));
                    return new long[]{sumChange, countChange};
                }).addOnSuccessListener(change -> callback.onDone(change[0], (int) change[1]))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not save the review", e);
                    callback.onError("Could not save your review. Check your internet connection.");
                });
    }

    /** Deletes the logged in user's review of this spot. */
    public static void deleteMyReview(Spot spot, OnChanged callback) {
        if (!UserSession.isLoggedIn() || spot == null || spot.getId() == null) {
            callback.onError("You need to be logged in.");
            return;
        }
        DocumentReference spotRef = db().collection(SPOTS).document(spot.getId());
        DocumentReference reviewRef = spotRef.collection(REVIEWS).document(UserSession.getUid());

        db().runTransaction(transaction -> {
                    DocumentSnapshot old = transaction.get(reviewRef);
                    if (!old.exists()) return new long[]{0, 0};

                    Long oldRating = old.getLong("rating");
                    long sumChange = oldRating == null ? 0 : -oldRating;
                    long countChange = -1;
                    transaction.delete(reviewRef);
                    transaction.update(spotRef,
                            "ratingSum", FieldValue.increment(sumChange),
                            "ratingCount", FieldValue.increment(countChange));
                    return new long[]{sumChange, countChange};
                }).addOnSuccessListener(change -> callback.onDone(change[0], (int) change[1]))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not delete the review", e);
                    callback.onError("Could not delete your review. Check your internet connection.");
                });
    }
}