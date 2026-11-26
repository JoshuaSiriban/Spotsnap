package com.example.studyflow;

import android.util.Log;

import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Likes and follows.
 *
 *   spots/{spotId}/likes/{uid}          one document per person who liked the spot
 *   users/{uid}/following/{otherUid}    the people {uid} follows
 *   users/{uid}/followers/{otherUid}    the people who follow {uid}
 *
 * The number of likes is also kept on the spot (spots/{spotId}.likes). It changes in the same
 * transaction as the like document, so the number always matches the real likes.
 */
public final class SocialRepository {

    private static final String TAG = "SocialRepository";

    private SocialRepository() {}

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ------------------------------------------------------------------ likes

    private static DocumentReference likeRef(String spotId) {
        return db().collection("spots").document(spotId)
                .collection("likes").document(UserSession.getUid());
    }

    /** Calls back with true when the logged in user liked this spot. */
    public static void isLiked(String spotId, Consumer<Boolean> callback) {
        if (!UserSession.isLoggedIn() || spotId == null) {
            callback.accept(false);
            return;
        }
        likeRef(spotId).get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists()))
                .addOnFailureListener(e -> callback.accept(false));
    }

    /** Likes or unlikes a spot. Calls back with an error message, or null when it worked. */
    public static void setLiked(Spot spot, boolean like, Consumer<String> callback) {
        if (!UserSession.isLoggedIn() || spot == null || spot.getId() == null) {
            callback.accept("You need to be logged in to like places.");
            return;
        }
        DocumentReference ref = likeRef(spot.getId());
        DocumentReference spotRef = db().collection("spots").document(spot.getId());

        db().runTransaction(transaction -> {
                    boolean exists = transaction.get(ref).exists();
                    if (like && !exists) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("uid", UserSession.getUid());
                        data.put("likedAt", FieldValue.serverTimestamp());
                        transaction.set(ref, data);
                        transaction.update(spotRef, "likes", FieldValue.increment(1));
                    } else if (!like && exists) {
                        transaction.delete(ref);
                        transaction.update(spotRef, "likes", FieldValue.increment(-1));
                    }
                    return null;
                }).addOnSuccessListener(unused -> callback.accept(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not change the like", e);
                    callback.accept("Could not update your like. Check your internet connection.");
                });
    }

    // ---------------------------------------------------------------- follows

    /** Calls back with true when the logged in user follows {@code targetUid}. */
    public static void isFollowing(String targetUid, Consumer<Boolean> callback) {
        if (!UserSession.isLoggedIn() || targetUid == null) {
            callback.accept(false);
            return;
        }
        db().collection("users").document(UserSession.getUid())
                .collection("following").document(targetUid).get()
                .addOnSuccessListener(doc -> callback.accept(doc.exists()))
                .addOnFailureListener(e -> callback.accept(false));
    }

    /** Follows or unfollows a user. Calls back with an error message, or null when it worked. */
    public static void setFollowing(String myUsername, String targetUid, String targetUsername,
                                    boolean follow, Consumer<String> callback) {
        if (!UserSession.isLoggedIn() || targetUid == null) {
            callback.accept("You need to be logged in to follow people.");
            return;
        }
        String myUid = UserSession.getUid();
        if (myUid.equals(targetUid)) {
            callback.accept("You cannot follow yourself.");
            return;
        }
        DocumentReference followingRef = db().collection("users").document(myUid)
                .collection("following").document(targetUid);
        DocumentReference followerRef = db().collection("users").document(targetUid)
                .collection("followers").document(myUid);

        WriteBatch batch = db().batch();
        if (follow) {
            Map<String, Object> following = new HashMap<>();
            following.put("uid", targetUid);
            following.put("username", targetUsername == null ? "" : targetUsername);
            following.put("followedAt", FieldValue.serverTimestamp());
            batch.set(followingRef, following);

            Map<String, Object> follower = new HashMap<>();
            follower.put("uid", myUid);
            follower.put("username", myUsername == null ? "" : myUsername);
            follower.put("followedAt", FieldValue.serverTimestamp());
            batch.set(followerRef, follower);
        } else {
            batch.delete(followingRef);
            batch.delete(followerRef);
        }
        batch.commit()
                .addOnSuccessListener(unused -> callback.accept(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not change the follow", e);
                    callback.accept("Could not update. Check your internet connection.");
                });
    }

    /** The uids of everyone the logged in user follows, or null when it could not be loaded. */
    public static void loadFollowingIds(Consumer<Set<String>> callback) {
        if (!UserSession.isLoggedIn()) {
            callback.accept(new HashSet<>());
            return;
        }
        db().collection("users").document(UserSession.getUid()).collection("following").get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> ids = new HashSet<>();
                    snapshot.getDocuments().forEach(d -> ids.add(d.getId()));
                    callback.accept(ids);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not load following", e);
                    callback.accept(null);
                });
    }

    /** Number of followers of a user, or -1 when it could not be counted. */
    public static void countFollowers(String uid, Consumer<Long> callback) {
        count("followers", uid, callback);
    }

    /** Number of people a user follows, or -1 when it could not be counted. */
    public static void countFollowing(String uid, Consumer<Long> callback) {
        count("following", uid, callback);
    }

    private static void count(String list, String uid, Consumer<Long> callback) {
        db().collection("users").document(uid).collection(list)
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener(result -> callback.accept(result.getCount()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not count " + list, e);
                    callback.accept(-1L);
                });
    }
}