package com.example.studyflow;

import java.util.Locale;

/** Small helpers to show star ratings as text. */
public final class RatingUtils {

    private RatingUtils() {}

    /** 4.4 -> "★★★★☆", 0 -> "☆☆☆☆☆" */
    public static String stars(double rating) {
        int full = (int) Math.round(rating);
        full = Math.max(0, Math.min(5, full));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(i < full ? '★' : '☆');
        return sb.toString();
    }

    /** "4.5 (12)" or "No ratings yet". */
    public static String summary(Spot spot) {
        if (spot == null || !spot.hasRatings()) return "No ratings yet";
        return String.format(Locale.US, "%.1f (%d)", spot.getAverageRating(), spot.getRatingCount());
    }

    /** "★ 4.5 (12)" for lists and map bubbles, or "" when the spot has no ratings. */
    public static String label(Spot spot) {
        if (spot == null || !spot.hasRatings()) return "";
        return String.format(Locale.US, "★ %.1f (%d)", spot.getAverageRating(), spot.getRatingCount());
    }
}