package com.example.studyflow;

import android.content.Context;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Permanently deletes the logged in user's account (Settings > Delete account).
 *
 * Order of work:
 *   1. re-authenticate with the password (Firebase requires a recent login to delete an account)
 *   2. delete the data in Firestore while the user is still signed in
 *        - the user's spots, with their photos and likes
 *        - likes and saves the user gave to other spots (their counters go down by one)
 *        - follow links in both directions
 *        - the profile (users/{uid}) and the username claim (usernames/{name}), last
 *   3. delete the Firebase Authentication user
 *
 * Every step can be run again, so if something fails half way the user can simply try again.
 * Steps marked "best effort" touch other people's documents; if the Firestore rules do not
 * allow them they are skipped and the deletion still goes on.
 */
public final class AccountDeleter {

    private static final String TAG = "AccountDeleter";
    private static final int BATCH_LIMIT = 400; // a Firestore batch holds 500 writes at most

    private AccountDeleter() {}

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    public static void deleteCurrentAccount(Context context, String password,
                                            AuthRepository.Callback callback) {
        Context app = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("You are not logged in.");
            return;
        }
        if (!AuthRepository.isOnline(app)) {
            callback.onError(AuthRepository.NO_INTERNET);
            return;
        }

        String uid = user.getUid();
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);

        user.reauthenticate(credential)
                .addOnSuccessListener(unused -> deleteAllData(uid)
                        .addOnSuccessListener(done -> user.delete()
                                .addOnSuccessListener(deleted -> {
                                    FirebaseAuth.getInstance().signOut();
                                    UserSession.clear(app);
                                    callback.onSuccess();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Could not delete the Firebase user", e);
                                    callback.onError(describeAuthDeleteFailure(e));
                                }))
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Could not delete the user's data", e);
                            callback.onError(describeDataFailure(e));
                        }))
                .addOnFailureListener(e -> callback.onError(describeReauthFailure(e)));
    }

    // ------------------------------------------------------------- the data

    private static Task<Void> deleteAllData(String uid) {
        return deleteMySpots(uid)
                .onSuccessTask(unused -> removeLikesIGave(uid))
                .onSuccessTask(unused -> removeSavesIGave(uid))
                .onSuccessTask(unused -> removeFollowLinks(uid))
                .onSuccessTask(unused -> deleteProfile(uid));
    }

    /** spots where creatorId == uid, each with its photos and likes. */
    private static Task<Void> deleteMySpots(String uid) {
        return db().collection("spots").whereEqualTo("creatorId", uid).get()
                .<Void>onSuccessTask(snapshot -> {
                    List<Task<Void>> jobs = new ArrayList<>();
                    for (DocumentSnapshot spot : snapshot.getDocuments()) {
                        jobs.add(deleteSpotWithChildren(spot.getReference()));
                    }
                    return Tasks.whenAll(jobs);
                });
    }

    private static Task<Void> deleteSpotWithChildren(DocumentReference spotRef) {
        return deleteQuery(spotRef.collection("photos"))
                .onSuccessTask(unused ->
                        bestEffort(deleteQuery(spotRef.collection("likes")), "likes of a deleted spot"))
                .onSuccessTask(unused -> spotRef.delete());
    }

    /** Best effort: likes the user gave to other people's spots (needs a collection group index). */
    private static Task<Void> removeLikesIGave(String uid) {
        Task<Void> task = db().collectionGroup("likes").whereEqualTo("uid", uid).get()
                .<Void>onSuccessTask(snapshot -> {
                    List<Task<Void>> jobs = new ArrayList<>();
                    for (DocumentSnapshot like : snapshot.getDocuments()) {
                        DocumentReference spotRef = like.getReference().getParent().getParent();
                        Task<Void> job = like.getReference().delete();
                        if (spotRef != null) {
                            job = job.onSuccessTask(unused ->
                                    spotRef.update("likes", FieldValue.increment(-1)));
                        }
                        jobs.add(bestEffort(job, "one like"));
                    }
                    return Tasks.whenAll(jobs);
                });
        return bestEffort(task, "likes the user gave");
    }

    /** users/{uid}/saved: lowers the "saves" counter of each spot (best effort), then deletes the list. */
    private static Task<Void> removeSavesIGave(String uid) {
        CollectionReference saved = db().collection("users").document(uid).collection("saved");
        return saved.get().<Void>onSuccessTask(snapshot -> {
            List<DocumentSnapshot> docs = snapshot.getDocuments();
            List<Task<Void>> counters = new ArrayList<>();
            for (DocumentSnapshot doc : docs) {
                String spotId = doc.getString("spotId") != null ? doc.getString("spotId") : doc.getId();
                counters.add(bestEffort(
                        db().collection("spots").document(spotId).update("saves", FieldValue.increment(-1)),
                        "one save counter"));
            }
            return Tasks.whenAll(counters).onSuccessTask(unused -> deleteDocs(docs));
        });
    }

    /** Removes the user from other people's follower / following lists (best effort) and clears their own. */
    private static Task<Void> removeFollowLinks(String uid) {
        DocumentReference me = db().collection("users").document(uid);
        return unlinkFromOthers(me.collection("following"), "followers", uid)
                .onSuccessTask(unused -> unlinkFromOthers(me.collection("followers"), "following", uid));
    }

    /**
     * For every person listed in {@code mine}, removes our entry from their {@code theirList}
     * (best effort), then deletes {@code mine}.
     */
    private static Task<Void> unlinkFromOthers(CollectionReference mine, String theirList, String uid) {
        return mine.get().<Void>onSuccessTask(snapshot -> {
            List<DocumentSnapshot> docs = snapshot.getDocuments();
            List<Task<Void>> reverse = new ArrayList<>();
            for (DocumentSnapshot doc : docs) { // the document id is the other person's uid
                reverse.add(bestEffort(
                        db().collection("users").document(doc.getId())
                                .collection(theirList).document(uid).delete(),
                        "link in another user's list"));
            }
            return Tasks.whenAll(reverse).onSuccessTask(unused -> deleteDocs(docs));
        });
    }

    /** The profile and the username claim, deleted together. Done last so a failed run can be repeated. */
    private static Task<Void> deleteProfile(String uid) {
        DocumentReference userRef = db().collection("users").document(uid);
        return userRef.get().<Void>onSuccessTask(profile -> {
            String usernameLower = profile.getString("usernameLower");
            if (usernameLower == null || usernameLower.isEmpty()) {
                return userRef.delete(); // old profile without a username claim
            }
            DocumentReference nameRef = db().collection("usernames").document(usernameLower);
            return nameRef.get().onSuccessTask(claim -> {
                WriteBatch batch = db().batch();
                if (claim.exists() && uid.equals(claim.getString("uid"))) {
                    batch.delete(nameRef); // frees the username for somebody else
                }
                batch.delete(userRef);
                return batch.commit();
            });
        });
    }

    // -------------------------------------------------------------- helpers

    private static Task<Void> deleteQuery(Query query) {
        return query.get().<Void>onSuccessTask(snapshot -> deleteDocs(snapshot.getDocuments()));
    }

    private static Task<Void> deleteDocs(List<DocumentSnapshot> docs) {
        List<Task<Void>> commits = new ArrayList<>();
        for (int start = 0; start < docs.size(); start += BATCH_LIMIT) {
            WriteBatch batch = db().batch();
            int end = Math.min(start + BATCH_LIMIT, docs.size());
            for (DocumentSnapshot doc : docs.subList(start, end)) {
                batch.delete(doc.getReference());
            }
            commits.add(batch.commit());
        }
        return Tasks.whenAll(commits);
    }

    /** Wraps a task so that a failure is only logged and the chain carries on. */
    private static Task<Void> bestEffort(Task<Void> task, String what) {
        return task.continueWith(done -> {
            if (!done.isSuccessful()) {
                Log.w(TAG, "Skipped (best effort): " + what, done.getException());
            }
            return null;
        });
    }

    // ---------------------------------------------------------- error text

    private static String describeReauthFailure(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException
                || e instanceof FirebaseAuthInvalidUserException) {
            return "That password is not correct.";
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes and try again.";
        }
        if (AuthRepository.isNetworkError(e)) {
            return AuthRepository.NO_INTERNET;
        }
        return "Could not check your password. Please try again.";
    }

    private static String describeDataFailure(Exception e) {
        if (AuthRepository.isNetworkError(e)) {
            return AuthRepository.NO_INTERNET;
        }
        return "Could not delete all of your data. Please check your connection and try again.";
    }

    private static String describeAuthDeleteFailure(Exception e) {
        if (e instanceof FirebaseAuthRecentLoginRequiredException) {
            return "For your security, please log out, log back in and try again.";
        }
        if (AuthRepository.isNetworkError(e)) {
            return AuthRepository.NO_INTERNET;
        }
        return "Your places and profile were removed, but the account itself could not be deleted. "
                + "Please try again.";
    }
}