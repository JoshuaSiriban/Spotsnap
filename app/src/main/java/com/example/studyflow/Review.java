package com.example.studyflow;

/** One person's review of one spot: stars (1 to 5) and an optional written recommendation. */
public class Review {

    public final String uid;          // who wrote it (also the Firestore document id)
    public final String username;
    public final int rating;          // 1 to 5
    public final String text;         // may be empty
    public final long createdAt;      // milliseconds, 0 if unknown

    public Review(String uid, String username, int rating, String text, long createdAt) {
        this.uid = uid;
        this.username = username;
        this.rating = rating;
        this.text = text;
        this.createdAt = createdAt;
    }
}