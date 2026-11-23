package com.example.studyflow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Sign up, log in and log out with Firebase Authentication.
 *
 * Firestore layout used here:
 *   users/{uid}                -> profile (username, usernameLower, fullName, email, createdAt)
 *   usernames/{usernameLower}  -> { uid }  (exists only to keep usernames unique)
 */
public final class AuthRepository {

    private static final String TAG = "AuthRepository";

    private AuthRepository() {}

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ------------------------------------------------------------ validation

    /** Returns an error message, or null when the username is fine. */
    public static String validateUsername(String username) {
        if (username.length() < 3 || username.length() > 20) {
            return "Username must be 3 to 20 characters";
        }
        if (!username.matches("[A-Za-z0-9_.]+")) {
            return "Use only letters, numbers, \"_\" and \".\"";
        }
        return null;
    }

    // --------------------------------------------------------------- sign up

    /**
     * Creates the Firebase account, then claims the username and saves the profile in one
     * transaction. If the username is already taken (or saving fails) the new account is
     * deleted again, so nobody ends up with an account that has no profile.
     */
    public static void signUp(Context context, String username, String fullName,
                              String email, String password, Callback callback) {
        Context appContext = context.getApplicationContext();

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError("Could not create the account. Please try again.");
                        return;
                    }
                    claimUsernameAndSaveProfile(appContext, user, username, fullName, email, callback);
                })
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    private static void claimUsernameAndSaveProfile(Context appContext, FirebaseUser user,
                                                    String username, String fullName,
                                                    String email, Callback callback) {
        String usernameLower = username.toLowerCase(Locale.ROOT);
        DocumentReference nameRef = db().collection("usernames").document(usernameLower);
        DocumentReference userRef = db().collection("users").document(user.getUid());

        Map<String, Object> claim = new HashMap<>();
        claim.put("uid", user.getUid());

        Map<String, Object> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("usernameLower", usernameLower);
        profile.put("fullName", fullName);
        profile.put("email", email);
        profile.put("createdAt", FieldValue.serverTimestamp());

        db().runTransaction(transaction -> {
            if (transaction.get(nameRef).exists()) {
                throw new FirebaseFirestoreException("Username taken",
                        FirebaseFirestoreException.Code.ALREADY_EXISTS);
            }
            transaction.set(nameRef, claim);
            transaction.set(userRef, profile);
            return null;
        }).addOnSuccessListener(unused -> {
            UserSession.save(appContext, user.getUid(), username, fullName);
            callback.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Could not save profile, removing the new account", e);
            // Undo the account so the person can try again with another username.
            user.delete().addOnCompleteListener(task -> {
                FirebaseAuth.getInstance().signOut();
                UserSession.clear(appContext);
                callback.onError(describe(e));
            });
        });
    }

    // ---------------------------------------------------------------- log in

    public static void logIn(Context context, String email, String password, Callback callback) {
        Context appContext = context.getApplicationContext();

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result ->
                        // Even if the profile cannot be loaded (e.g. offline), the login still
                        // works; MainActivity retries loading the profile later.
                        loadProfile(appContext, callback::onSuccess))
                .addOnFailureListener(e -> callback.onError(describe(e)));
    }

    /**
     * Reads users/{uid} into the local session. {@code afterLoad} always runs, whether or not
     * the profile could be read, so the caller can simply refresh its screen.
     */
    public static void loadProfile(Context context, Runnable afterLoad) {
        Context appContext = context.getApplicationContext();
        String uid = UserSession.getUid();
        if (uid.isEmpty()) {
            afterLoad.run();
            return;
        }

        db().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String username = doc.getString("username");
                    if (username != null && !username.isEmpty()) {
                        String fullName = doc.getString("fullName");
                        UserSession.save(appContext, uid, username, fullName == null ? "" : fullName);
                    }
                    afterLoad.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not load profile", e);
                    afterLoad.run();
                });
    }

    // ---------------------------------------------------------------- log out

    /** Logs out and sends the user back to the login screen, with no way to go "back". */
    public static void logOut(Activity activity) {
        FirebaseAuth.getInstance().signOut();
        UserSession.clear(activity);

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    // ----------------------------------------------------------- error text

    /** Turns Firebase exceptions into messages a normal person can understand. */
    private static String describe(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return "No internet connection. Check your connection and try again.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "That email is already registered. Try logging in instead.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Use at least 6 characters.";
        }
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            if (code == FirebaseFirestoreException.Code.ALREADY_EXISTS) {
                return "That username is already taken. Please pick another one.";
            }
            if (code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                return "No internet connection. Check your connection and try again.";
            }
        }
        return "Something went wrong. Please try again.";
    }
}