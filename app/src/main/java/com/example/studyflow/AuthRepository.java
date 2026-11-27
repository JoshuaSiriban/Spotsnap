package com.example.studyflow;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.util.Log;
import android.util.Patterns;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
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
 * Sign up, log in, password reset and log out with Firebase Authentication.
 * (Deleting an account lives in AccountDeleter.)
 *
 * Firestore layout used here:
 *   users/{uid}                -> profile (username, usernameLower, fullName, email, createdAt)
 *   usernames/{usernameLower}  -> { uid }  (exists only to keep usernames unique)
 */
public final class AuthRepository {

    private static final String TAG = "AuthRepository";

    public static final int MIN_PASSWORD_LENGTH = 6; // Firebase Authentication's minimum
    public static final String NO_INTERNET = "No internet connection. Check your connection and try again.";

    private AuthRepository() {}

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    /** Which input an error belongs to, so a screen can show it right under that input. */
    public enum Field { NONE, USERNAME, EMAIL, PASSWORD }

    /** Like Callback, but says which field the error is about (Login and Sign up use this). */
    public interface FormCallback {
        void onSuccess();
        void onError(Field field, String message);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ---------------------------------------------------------- connectivity

    /** True when the phone has a network that can reach the internet. */
    public static boolean isOnline(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true; // cannot tell, so let Firebase try
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // ------------------------------------------------------------ validation

    /** Returns an error message, or null when the username is fine. */
    public static String validateUsername(String username) {
        if (username.isEmpty()) {
            return "Choose a username";
        }
        if (username.length() < 3 || username.length() > 20) {
            return "Username must be 3 to 20 characters";
        }
        if (!username.matches("[A-Za-z0-9_.]+")) {
            return "Use only letters, numbers, \"_\" and \".\"";
        }
        return null;
    }

    /** Returns an error message, or null when the email looks fine. */
    public static String validateEmail(String email) {
        if (email.isEmpty()) {
            return "Enter your email";
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            return "Enter a valid email, like you@example.com";
        }
        return null;
    }

    /** Returns an error message, or null when the password is acceptable for a new account. */
    public static String validateNewPassword(String password) {
        if (password.isEmpty()) {
            return "Choose a password";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Use at least " + MIN_PASSWORD_LENGTH + " characters";
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
                              String email, String password, FormCallback callback) {
        Context appContext = context.getApplicationContext();

        if (!isOnline(appContext)) {
            callback.onError(Field.NONE, NO_INTERNET);
            return;
        }

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError(Field.NONE, "Could not create the account. Please try again.");
                        return;
                    }
                    claimUsernameAndSaveProfile(appContext, user, username, fullName, email, callback);
                })
                .addOnFailureListener(e -> failSignUp(e, callback));
    }

    private static void claimUsernameAndSaveProfile(Context appContext, FirebaseUser user,
                                                    String username, String fullName,
                                                    String email, FormCallback callback) {
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
                failSignUp(e, callback);
            });
        });
    }

    private static void failSignUp(Exception e, FormCallback callback) {
        if (e instanceof FirebaseAuthUserCollisionException) {
            callback.onError(Field.EMAIL, "That email is already registered. Try logging in instead.");
        } else if (e instanceof FirebaseAuthWeakPasswordException) {
            callback.onError(Field.PASSWORD, "Password is too weak. Use at least "
                    + MIN_PASSWORD_LENGTH + " characters.");
        } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
            callback.onError(Field.EMAIL, "That email address does not look right.");
        } else if (isUsernameTaken(e)) {
            callback.onError(Field.USERNAME, "That username is already taken. Please pick another one.");
        } else {
            callback.onError(Field.NONE, describe(e));
        }
    }

    // ---------------------------------------------------------------- log in

    public static void logIn(Context context, String email, String password, FormCallback callback) {
        Context appContext = context.getApplicationContext();

        if (!isOnline(appContext)) {
            callback.onError(Field.NONE, NO_INTERNET);
            return;
        }

        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result ->
                        // Even if the profile cannot be loaded (e.g. offline), the login still
                        // works; MainActivity retries loading the profile later.
                        loadProfile(appContext, callback::onSuccess))
                .addOnFailureListener(e -> failLogIn(e, callback));
    }

    private static void failLogIn(Exception e, FormCallback callback) {
        if (e instanceof FirebaseAuthInvalidUserException && isDisabled(e)) {
            callback.onError(Field.NONE, "This account has been disabled.");
        } else if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            // Firebase does not say whether the email or the password was wrong (on purpose).
            callback.onError(Field.PASSWORD, "Incorrect email or password.");
        } else {
            callback.onError(Field.NONE, describe(e));
        }
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

    // -------------------------------------------------------- forgot password

    /**
     * Asks Firebase to email a password reset link. If there is no account for the email we
     * still report success, so nobody can use this to find out who has an account.
     */
    public static void sendPasswordReset(Context context, String email, Callback callback) {
        if (!isOnline(context)) {
            callback.onError(NO_INTERNET);
            return;
        }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidUserException) {
                        callback.onSuccess();
                    } else if (e instanceof FirebaseAuthInvalidCredentialsException) {
                        callback.onError("That email address does not look right.");
                    } else {
                        Log.e(TAG, "Could not send the reset email", e);
                        callback.onError(describe(e));
                    }
                });
    }

    // --------------------------------------------------------- account changes

    /**
     * Changes the password. Firebase wants a recent login for this, so the current password is
     * checked first (re-authentication) and only then the new one is set.
     */
    public static void changePassword(String currentPassword, String newPassword, Callback callback) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            callback.onError("You are not logged in.");
            return;
        }
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(unused -> user.updatePassword(newPassword)
                        .addOnSuccessListener(done -> callback.onSuccess())
                        .addOnFailureListener(e -> callback.onError(describe(e))))
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthInvalidCredentialsException
                            || e instanceof FirebaseAuthInvalidUserException) {
                        callback.onError("Your current password is not correct.");
                    } else {
                        callback.onError(describe(e));
                    }
                });
    }

    /** Saves a new full name in users/{uid} and in the local session. Repairs an incomplete profile. */
    public static void updateFullName(Context context, String fullName, Callback callback) {
        Context appContext = context.getApplicationContext();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("You are not logged in.");
            return;
        }
        String uid = user.getUid();
        String email = user.getEmail() == null ? "" : user.getEmail();
        DocumentReference userRef = db().collection("users").document(uid);
        String[] finalUsername = {UserSession.getUsername(appContext)};

        db().runTransaction(transaction -> {
            com.google.firebase.firestore.DocumentSnapshot snap = transaction.get(userRef);
            String existing = snap.getString("username");

            if (snap.exists() && existing != null && !existing.isEmpty()) {
                // normal account: only the name changes
                transaction.update(userRef, "fullName", fullName);
                finalUsername[0] = existing;
                return null;
            }

            // Incomplete profile (an old account): create the missing fields and claim the username
            String username = UserSession.getUsername(appContext); // the part of the email before @
            DocumentReference nameRef = db().collection("usernames").document(username.toLowerCase(Locale.ROOT));
            com.google.firebase.firestore.DocumentSnapshot claim = transaction.get(nameRef);
            if (claim.exists() && !uid.equals(claim.getString("uid"))) {
                username = username + uid.substring(0, 4); // somebody else has that name
                nameRef = db().collection("usernames").document(username.toLowerCase(Locale.ROOT));
                claim = null;
            }

            Map<String, Object> profile = new HashMap<>();
            profile.put("username", username);
            profile.put("usernameLower", username.toLowerCase(Locale.ROOT));
            profile.put("fullName", fullName);
            profile.put("email", email);
            if (!snap.contains("createdAt")) profile.put("createdAt", FieldValue.serverTimestamp());
            transaction.set(userRef, profile, com.google.firebase.firestore.SetOptions.merge());

            if (claim == null || !claim.exists()) {
                Map<String, Object> claimData = new HashMap<>();
                claimData.put("uid", uid);
                transaction.set(nameRef, claimData);
            }
            finalUsername[0] = username;
            return null;
        }).addOnSuccessListener(unused -> {
            UserSession.save(appContext, uid, finalUsername[0], fullName);
            callback.onSuccess();
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Could not update the full name", e);
            callback.onError(describe(e));
        });
    }

    // ---------------------------------------------------------------- log out

    /** Logs out and sends the user back to the login screen, with no way to go "back". */
    public static void logOut(Activity activity) {
        FirebaseAuth.getInstance().signOut();
        UserSession.clear(activity);
        openLogin(activity);
    }

    /** Opens the login screen and clears every other screen from the back stack. */
    static void openLogin(Activity activity) {
        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    // ----------------------------------------------------------- error text

    static boolean isNetworkError(Exception e) {
        if (e instanceof FirebaseNetworkException) return true;
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.UNAVAILABLE;
    }

    private static boolean isUsernameTaken(Exception e) {
        return e instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.ALREADY_EXISTS;
    }

    private static boolean isDisabled(Exception e) {
        return e instanceof FirebaseAuthException
                && "ERROR_USER_DISABLED".equals(((FirebaseAuthException) e).getErrorCode());
    }

    /** Turns Firebase exceptions into messages a normal person can understand. */
    static String describe(Exception e) {
        if (isNetworkError(e)) {
            return NO_INTERNET;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a few minutes, or reset your password.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "That email is already registered. Try logging in instead.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "Password is too weak. Use at least " + MIN_PASSWORD_LENGTH + " characters.";
        }
        if (e instanceof FirebaseAuthInvalidUserException && isDisabled(e)) {
            return "This account has been disabled.";
        }
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (isUsernameTaken(e)) {
            return "That username is already taken. Please pick another one.";
        }
        return "Something went wrong. Please try again.";
    }
}