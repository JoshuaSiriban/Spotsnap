package com.example.studyflow;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/**
 * Who is logged in right now.
 *
 * FirebaseAuth is the source of truth for "is someone logged in" and for the uid / email.
 * The username and full name live in Firestore (users/{uid}); we keep a small local copy
 * so screens can read them instantly, even when offline.
 */
public final class UserSession {

    private static final String PREFS = "SpotSnapSession";
    private static final String KEY_UID = "uid";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_FULL_NAME = "fullName";
    private static final String KEY_BIO = "bio";
    private static final String KEY_PHOTO = "photo";   // the profile picture, as Base64 text

    private UserSession() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The Firebase user, or null when nobody is logged in. */
    public static FirebaseUser current() {
        return FirebaseAuth.getInstance().getCurrentUser();
    }

    public static boolean isLoggedIn() {
        return current() != null;
    }

    /** Firebase uid of the logged-in user, or "" when nobody is logged in. */
    public static String getUid() {
        FirebaseUser user = current();
        return user == null ? "" : user.getUid();
    }

    public static String getEmail() {
        FirebaseUser user = current();
        return user == null || user.getEmail() == null ? "" : user.getEmail();
    }

    /** True when the cached profile belongs to the user who is logged in right now. */
    public static boolean hasProfile(Context context) {
        SharedPreferences p = prefs(context);
        return isLoggedIn()
                && getUid().equals(p.getString(KEY_UID, ""))
                && !p.getString(KEY_USERNAME, "").isEmpty();
    }

    /** The unique username. Falls back to the part of the email before the "@". */
    public static String getUsername(Context context) {
        if (hasProfile(context)) return prefs(context).getString(KEY_USERNAME, "");
        String email = getEmail();
        int at = email.indexOf('@');
        if (at > 0) return email.substring(0, at);
        return "guest";
    }

    /** The name to show on screens: full name if the user gave one, otherwise the username. */
    public static String getDisplayName(Context context) {
        if (hasProfile(context)) {
            String fullName = prefs(context).getString(KEY_FULL_NAME, "");
            if (!fullName.isEmpty()) return fullName;
        }
        return getUsername(context);
    }

    /** The short bio of the logged in user ("" when there is none). */
    public static String getBio(Context context) {
        return hasProfile(context) ? prefs(context).getString(KEY_BIO, "") : "";
    }

    /** The profile picture of the logged in user (a small JPEG), or null when there is none. */
    @Nullable
    public static byte[] getPhoto(Context context) {
        if (!hasProfile(context)) return null;
        String text = prefs(context).getString(KEY_PHOTO, "");
        if (text.isEmpty()) return null;
        try {
            return Base64.decode(text, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Keeps a copy of the bio and picture on this phone so screens can show them at once. */
    public static void saveExtras(Context context, @Nullable String bio, @Nullable byte[] photo) {
        prefs(context).edit()
                .putString(KEY_BIO, bio == null ? "" : bio)
                .putString(KEY_PHOTO, photo == null ? "" : Base64.encodeToString(photo, Base64.NO_WRAP))
                .apply();
    }

    static void save(Context context, String uid, String username, String fullName) {
        prefs(context).edit()
                .putString(KEY_UID, uid)
                .putString(KEY_USERNAME, username)
                .putString(KEY_FULL_NAME, fullName)
                .apply();
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}