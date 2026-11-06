package com.example.studyflow;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpotRepository {

    private static final String TAG = "SpotRepository";

    public interface OnSpotsLoaded {
        void onLoaded(ArrayList<Spot> spots);
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ---------------------------------------------------------------- users

    public static void saveUser(String username, String fullName, String email) {
        Map<String, Object> user = new HashMap<>();
        user.put("username", username);
        user.put("fullName", fullName);
        user.put("email", email);
        user.put("createdAt", FieldValue.serverTimestamp());

        db().collection("users").document(username).set(user)
                .addOnSuccessListener(unused -> Log.d(TAG, "User saved: " + username))
                .addOnFailureListener(e -> Log.e(TAG, "Could not save user", e));
    }

    public static void saveSpot(Context context, Spot spot, boolean isPublic) {
        db().collection("spots").add(toMap(context, spot, isPublic))
                .addOnSuccessListener(ref -> Log.d(TAG, "Spot saved: " + ref.getId()))
                .addOnFailureListener(e -> Log.e(TAG, "Could not save spot", e));
    }

    public static void seedIfEmpty(Context context, List<Spot> spots) {
        db().collection("spots").limit(1).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.isEmpty()) return;
            for (Spot spot : spots) {
                String id = spot.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-");
                db().collection("spots").document(id).set(toMap(context, spot, true));
            }
            Log.d(TAG, "Seeded " + spots.size() + " sample spots");
        }).addOnFailureListener(e -> Log.e(TAG, "Seed failed", e));
    }

    public static void loadSpots(Context context, OnSpotsLoaded callback) {
        db().collection("spots").whereEqualTo("isPublic", true).get()
                .addOnSuccessListener(snapshot -> {
                    ArrayList<Spot> result = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Spot spot = toSpot(context, doc);
                        if (spot != null) result.add(spot);
                    }
                    callback.onLoaded(result);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Could not load spots", e));
    }

    private static Map<String, Object> toMap(Context context, Spot spot, boolean isPublic) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", spot.getName());
        data.put("creator", spot.getCreator());
        data.put("review", spot.getReview());
        data.put("category", spot.getCategory());
        data.put("latitude", spot.getLatitude());
        data.put("longitude", spot.getLongitude());
        data.put("likes", spot.getLikes());
        data.put("saves", spot.getSaves());
        data.put("isPublic", isPublic);
        data.put("createdAt", FieldValue.serverTimestamp());

        // Photos are stored by drawable name for now. Later this becomes a Firebase Storage URL.
        List<Map<String, Object>> photos = new ArrayList<>();
        if (spot.images != null) {
            for (Photo p : spot.images) {
                Map<String, Object> photo = new HashMap<>();
                photo.put("image", context.getResources().getResourceEntryName(p.image));
                photo.put("caption", p.caption);
                photos.add(photo);
            }
        }
        data.put("photos", photos);
        return data;
    }

    @SuppressWarnings("unchecked")
    private static Spot toSpot(Context context, DocumentSnapshot doc) {
        String name = doc.getString("name");
        Double lat = doc.getDouble("latitude");
        Double lng = doc.getDouble("longitude");
        if (name == null || lat == null || lng == null) return null;

        Long likes = doc.getLong("likes");
        Long saves = doc.getLong("saves");

        ArrayList<Photo> photos = new ArrayList<>();
        List<Map<String, Object>> stored = (List<Map<String, Object>>) doc.get("photos");
        if (stored != null) {
            for (Map<String, Object> p : stored) {
                String imageName = (String) p.get("image");
                String caption = (String) p.get("caption");
                int resId = context.getResources()
                        .getIdentifier(imageName, "drawable", context.getPackageName());
                if (resId == 0) resId = R.drawable.dlsu; // fallback if the picture name is unknown
                photos.add(new Photo(resId, caption == null ? "" : caption));
            }
        }

        return new Spot(
                name,
                doc.getString("creator") == null ? "Unknown" : doc.getString("creator"),
                doc.getString("review") == null ? "" : doc.getString("review"),
                doc.getString("category") == null ? "Others" : doc.getString("category"),
                lat, lng, photos,
                likes == null ? 0 : likes.intValue(),
                saves == null ? 0 : saves.intValue());
    }
}