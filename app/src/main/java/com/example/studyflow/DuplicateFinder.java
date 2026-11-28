package com.example.studyflow;

import android.location.Location;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Finds places that are probably the same as the one a person is about to pin:
 * a similar name AND close by. Used by the Add Spot screen to avoid duplicates.
 */
public final class DuplicateFinder {

    /** A place counts as "nearby" within this distance. */
    public static final float RADIUS_METERS = 250f;
    public static final int MAX_SUGGESTIONS = 3;

    // Words that say little about which place it is ("Cafe" is in thousands of names)
    private static final Set<String> COMMON_WORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "of", "and", "at", "cafe", "coffee", "restaurant", "resto", "shop",
            "store", "park", "house", "place", "bar", "grill", "kitchen", "food", "hangout"));

    private DuplicateFinder() {}

    public static class Match {
        public final Spot spot;
        public final float meters;

        Match(Spot spot, float meters) {
            this.spot = spot;
            this.meters = meters;
        }
    }

    /** Up to {@link #MAX_SUGGESTIONS} similar places nearby, closest first. */
    public static List<Match> find(List<Spot> existing, String newName, double latitude, double longitude) {
        List<Match> matches = new ArrayList<>();
        float[] distance = new float[1];

        for (Spot spot : existing) {
            Location.distanceBetween(latitude, longitude, spot.getLatitude(), spot.getLongitude(), distance);
            if (distance[0] > RADIUS_METERS) continue;
            if (!similarNames(newName, spot.getName())) continue;
            matches.add(new Match(spot, distance[0]));
        }
        Collections.sort(matches, (a, b) -> Float.compare(a.meters, b.meters));
        if (matches.size() > MAX_SUGGESTIONS) return new ArrayList<>(matches.subList(0, MAX_SUGGESTIONS));
        return matches;
    }

    /** "Starbucks" ~ "Starbucks Coffee", "Jolibee" ~ "Jollibee", "Cafe Ocampo" ~ "Ocampo Cafe". */
    static boolean similarNames(String first, String second) {
        String a = normalize(first);
        String b = normalize(second);
        if (a.isEmpty() || b.isEmpty()) return false;
        if (a.equals(b)) return true;

        String compactA = a.replace(" ", "");
        String compactB = b.replace(" ", "");

        // one name is inside the other ("Rizal Park" and "Rizal Park Manila")
        if (Math.min(compactA.length(), compactB.length()) >= 4
                && (compactA.contains(compactB) || compactB.contains(compactA))) {
            return true;
        }

        // small spelling differences
        int longest = Math.max(compactA.length(), compactB.length());
        double similarity = 1.0 - (double) editDistance(compactA, compactB) / longest;
        if (similarity >= 0.75) return true;

        // the same important words in another order
        Set<String> wordsA = importantWords(a);
        Set<String> wordsB = importantWords(b);
        if (wordsA.isEmpty() || wordsB.isEmpty()) return false;
        Set<String> shared = new HashSet<>(wordsA);
        shared.retainAll(wordsB);
        Set<String> all = new HashSet<>(wordsA);
        all.addAll(wordsB);
        return (double) shared.size() / all.size() >= 0.5;
    }

    /** Lower case, no accents, only letters and digits separated by single spaces. */
    private static String normalize(String text) {
        if (text == null) return "";
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Set<String> importantWords(String normalized) {
        Set<String> words = new HashSet<>();
        for (String word : normalized.split(" ")) {
            if (word.length() >= 2 && !COMMON_WORDS.contains(word)) words.add(word);
        }
        return words;
    }

    /** Number of single letter changes needed to turn one text into the other. */
    private static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;

        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}