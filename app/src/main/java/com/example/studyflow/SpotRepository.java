package com.example.studyflow;

import android.content.Context;
import android.util.Log;

import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All the Cloud Firestore reading and writing for spots lives here.
 *
 *   spots/{spotId}                 the spot (details + a small thumbnail)
 *   spots/{spotId}/photos/{id}     one document per picture (the picture itself as JPEG bytes)
 *
 * Pictures are saved in Firestore instead of Firebase Storage, so no paid plan is needed.
 * They are shrunk first (see ImageUtils) because a Firestore document can hold 1 MB at most.
 */
public class SpotRepository {

    private static final String TAG = "SpotRepository";
    private static final String SPOTS = "spots";
    private static final String PHOTOS = "photos";

    public interface OnSpotsLoaded {
        void onLoaded(ArrayList<Spot> spots);
    }

    public interface OnSpotsChanged {
        void onChanged(ArrayList<Spot> spots);
    }

    public interface OnPhotosLoaded {
        void onLoaded(ArrayList<Photo> photos);
    }

    public interface OnSaved {
        void onSaved(String spotId);

        void onError(String message);
    }

    /** Lets a screen stop listening when it is closed. */
    public static class Subscription {
        private ListenerRegistration publicRegistration;
        private ListenerRegistration mineRegistration;

        public void remove() {
            if (publicRegistration != null) publicRegistration.remove();
            if (mineRegistration != null) mineRegistration.remove();
        }
    }

    private static FirebaseFirestore db() {
        return FirebaseFirestore.getInstance();
    }

    // ---------------------------------------------------------------- spots

    /**
     * Saves a new spot together with its photos in one batch, so a spot never exists without
     * its pictures. The spot needs its creator, privacy and location set before calling this.
     */
    public static void saveNewSpot(Spot spot, ArrayList<Photo> photos, OnSaved callback) {
        DocumentReference spotRef = db().collection(SPOTS).document(); // new automatic id
        WriteBatch batch = db().batch();

        Map<String, Object> data = new HashMap<>();
        data.put("name", spot.getName());
        data.put("creator", spot.getCreator());
        data.put("creatorId", spot.getCreatorId()); // owner, used for private posts, edit and delete
        data.put("review", spot.getReview());
        data.put("category", spot.getCategory());
        data.put("latitude", spot.getLatitude());
        data.put("longitude", spot.getLongitude());
        data.put("likes", 0);
        data.put("saves", 0);
        data.put("isPublic", spot.isPublic());
        data.put("photoCount", photos.size());
        if (!photos.isEmpty() && photos.get(0).thumb != null) {
            data.put("thumbnail", Blob.fromBytes(photos.get(0).thumb));
        }
        data.put("createdAt", FieldValue.serverTimestamp());
        batch.set(spotRef, data);

        for (int i = 0; i < photos.size(); i++) {
            Photo photo = photos.get(i);
            Map<String, Object> photoData = new HashMap<>();
            photoData.put("data", Blob.fromBytes(photo.bytes));
            photoData.put("caption", photo.caption == null ? "" : photo.caption);
            photoData.put("order", i);
            photoData.put("createdAt", FieldValue.serverTimestamp());
            batch.set(spotRef.collection(PHOTOS).document(), photoData);
        }

        batch.commit()
                .addOnSuccessListener(unused -> callback.onSaved(spotRef.getId()))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not save the spot", e);
                    callback.onError("Could not save the spot. " + e.getLocalizedMessage());
                });
    }

    public static void seedIfEmpty(Context context, List<Spot> spots) {
        db().collection(SPOTS).limit(1).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.isEmpty()) return;
            for (Spot spot : spots) {
                String id = spot.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-");
                db().collection(SPOTS).document(id).set(toMap(context, spot, true));
            }
            Log.d(TAG, "Seeded " + spots.size() + " sample spots");
        }).addOnFailureListener(e -> Log.e(TAG, "Seed failed", e));
    }

    public static void loadSpots(Context context, OnSpotsLoaded callback) {
        db().collection(SPOTS).whereEqualTo("isPublic", true).get()
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

    /**
     * Keeps the map up to date. Reports every public spot plus the logged in user's own spots
     * (including private ones), now and again whenever something changes, for example when the
     * user pins a new spot.
     */
    public static Subscription listenToSpots(Context context, OnSpotsChanged callback) {
        Context app = context.getApplicationContext();
        Subscription subscription = new Subscription();
        Map<String, Spot> publicSpots = new LinkedHashMap<>();
        Map<String, Spot> mySpots = new LinkedHashMap<>();

        Runnable publish = () -> {
            Map<String, Spot> all = new LinkedHashMap<>(publicSpots);
            all.putAll(mySpots);
            callback.onChanged(new ArrayList<>(all.values()));
        };

        subscription.publicRegistration = db().collection(SPOTS)
                .whereEqualTo("isPublic", true)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        Log.e(TAG, "Could not load public spots", error);
                        return;
                    }
                    publicSpots.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Spot spot = toSpot(app, doc);
                        if (spot != null) publicSpots.put(spot.getId(), spot);
                    }
                    publish.run();
                });

        if (UserSession.isLoggedIn()) {
            subscription.mineRegistration = db().collection(SPOTS)
                    .whereEqualTo("creatorId", UserSession.getUid())
                    .addSnapshotListener((snapshot, error) -> {
                        if (error != null || snapshot == null) {
                            Log.e(TAG, "Could not load my spots", error);
                            return;
                        }
                        mySpots.clear();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            Spot spot = toSpot(app, doc);
                            if (spot != null) mySpots.put(spot.getId(), spot);
                        }
                        publish.run();
                    });
        }
        return subscription;
    }

    /** Loads the pictures of a spot (they are not part of the spot document). */
    public static void loadPhotos(Spot spot, OnPhotosLoaded callback) {
        db().collection(SPOTS).document(spot.getId()).collection(PHOTOS)
                .orderBy("order")
                .get()
                .addOnSuccessListener(snapshot -> {
                    ArrayList<Photo> photos = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Blob blob = doc.getBlob("data");
                        if (blob == null) continue;
                        String caption = doc.getString("caption");
                        photos.add(new Photo(blob.toBytes(), caption == null ? "" : caption));
                    }
                    callback.onLoaded(photos);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Could not load photos", e));
    }

    // ------------------------------------------------------------ converting

    /** Used for the sample spots: their pictures are saved by drawable name. */
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

        // Sample spots keep their pictures as a list of drawable names
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

        Spot spot = new Spot(
                name,
                doc.getString("creator") == null ? "Unknown" : doc.getString("creator"),
                doc.getString("review") == null ? "" : doc.getString("review"),
                doc.getString("category") == null ? "Others" : doc.getString("category"),
                lat, lng, photos,
                likes == null ? 0 : likes.intValue(),
                saves == null ? 0 : saves.intValue());

        // Spots pinned by users also have an id, an owner, a privacy setting and a thumbnail
        Boolean isPublic = doc.getBoolean("isPublic");
        Long photoCount = doc.getLong("photoCount");
        Blob thumbnail = doc.getBlob("thumbnail");
        spot.setId(doc.getId());
        spot.setCreatorId(doc.getString("creatorId"));
        spot.setPublic(isPublic == null || isPublic);
        spot.setPhotoCount(photoCount == null ? photos.size() : photoCount.intValue());
        if (thumbnail != null) spot.setThumbnail(thumbnail.toBytes());
        return spot;
    }
}