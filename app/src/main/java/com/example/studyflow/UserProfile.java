package com.example.studyflow;

/** Someone else's public profile: just the fields that other people may see. */
public final class UserProfile {

    public final String uid;
    public final String username;
    public final String fullName;

    public UserProfile(String uid, String username, String fullName) {
        this.uid = uid;
        this.username = username == null ? "" : username;
        this.fullName = fullName == null ? "" : fullName;
    }

    /** Full name when the person gave one, otherwise the username. */
    public String displayName() {
        return fullName.isEmpty() ? username : fullName;
    }
}