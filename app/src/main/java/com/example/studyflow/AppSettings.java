package com.example.studyflow;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * The options of the Settings screen, kept on this phone (SharedPreferences):
 *  - distance units (km or mi), used by the nearby list and the other lists
 *  - whether new posts are public by default (used by the Add Spot screen)
 */
public final class AppSettings {

    private static final String FILE = "spotsnap_settings";
    private static final String KEY_MILES = "use_miles";
    private static final String KEY_DEFAULT_PUBLIC = "default_public";

    private static final float METERS_PER_MILE = 1609.344f;

    private AppSettings() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------------ units

    public static boolean useMiles(Context context) {
        return prefs(context).getBoolean(KEY_MILES, false);
    }

    public static void setUseMiles(Context context, boolean miles) {
        prefs(context).edit().putBoolean(KEY_MILES, miles).apply();
    }

    public static String unitName(Context context) {
        return useMiles(context) ? "mi" : "km";
    }

    /** "350 m" / "2.4 km" or, with miles selected, "0.2 mi" / "1.5 mi". */
    public static String formatDistance(Context context, float meters) {
        if (useMiles(context)) {
            return String.format(Locale.US, "%.1f mi", meters / METERS_PER_MILE);
        }
        if (meters < 1000) return String.format(Locale.US, "%d m", Math.round(meters / 10f) * 10);
        return String.format(Locale.US, "%.1f km", meters / 1000f);
    }

    /** A round distance for text like "within 5 km" / "within 3 mi". */
    public static String formatRadius(Context context, float meters) {
        if (useMiles(context)) {
            return Math.round(meters / METERS_PER_MILE) + " mi";
        }
        return Math.round(meters / 1000f) + " km";
    }

    // ---------------------------------------------------------------- privacy

    public static boolean defaultPublic(Context context) {
        return prefs(context).getBoolean(KEY_DEFAULT_PUBLIC, true);
    }

    public static void setDefaultPublic(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_DEFAULT_PUBLIC, value).apply();
    }
}