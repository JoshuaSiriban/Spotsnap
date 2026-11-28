package com.example.studyflow;

/** Someone else's public profile: just the fields that other people may see. */
public final class UserProfile {

    public final String uid;
    public final String username;
    public final String fullName;
    public final String bio;       // "" when they did not write one
    public final byte[] photo;     // small JPEG, or null when they have no picture

    public UserProfile(String uid, String username, String fullName) {
        this(uid, username, fullName, "", null);
    }

    public UserProfile(String uid, String username, String fullName, String bio, byte[] photo) {
        this.uid = uid;
        this.username = username == null ? "" : username;
        this.fullName = fullName == null ? "" : fullName;
        this.bio = bio == null ? "" : bio;
        this.photo = photo;
    }

    /** Full name when the person gave one, otherwise the username. */
    public String displayName() {
        return fullName.isEmpty() ? username : fullName;
    }
}