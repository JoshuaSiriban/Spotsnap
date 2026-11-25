package com.example.studyflow;

import java.util.Locale;

/**
 * The six categories from the proposal, in one place. Spots saved by earlier versions of the
 * app used Food / Hang Out / Tourist Spot / Others; normalize() maps those to the new names.
 */
public final class Categories {

    public static final String RESTAURANTS = "Restaurants";
    public static final String CAFES = "Caf\u00E9s";
    public static final String PARKS = "Parks";
    public static final String TOURIST = "Tourist Attractions";
    public static final String HANGOUTS = "Hangouts";
    public static final String OTHER = "Other";

    public static final String[] ALL = {RESTAURANTS, CAFES, PARKS, TOURIST, HANGOUTS, OTHER};

    private Categories() {}

    /** Turns any stored category (new or old) into one of the six names. */
    public static String normalize(String raw) {
        if (raw == null) return OTHER;
        String c = raw.trim().toLowerCase(Locale.ROOT);
        switch (c) {
            case "restaurants": case "restaurant": case "food": return RESTAURANTS;
            case "caf\u00E9s": case "cafes": case "cafe": case "caf\u00E9": return CAFES;
            case "parks": case "park": return PARKS;
            case "tourist attractions": case "tourist attraction": case "tourist spot": return TOURIST;
            case "hangouts": case "hangout": case "hang out": return HANGOUTS;
            default: return OTHER;
        }
    }

    /** Marker colour (ARGB) used to tint the marker picture of categories without their own. */
    public static int tintFor(String category) {
        switch (normalize(category)) {
            case CAFES: return 0xFF8B5E3C;   // coffee brown
            case PARKS: return 0xFF3E9B5A;   // green
            default: return 0;               // no tint
        }
    }
}