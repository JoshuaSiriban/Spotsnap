package com.example.studyflow;

import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The activity list ("X liked your spot", "Y started following you").
 *
 *   users/{recipientUid}/notifications/{id}
 *
 * The person who likes or follows writes the notification in the other person's list. The id is
 * fixed ("like_{spotId}_{actorUid}" / "follow_{actorUid}"), so doing it again never makes copies,
 * and undoing it (unlike / unfollow) deletes the notification again.
 *
 * Sending is "best effort": if it fails, the like or follow itself still worked.
 */
public final class NotificationRepository {

    private static final String TAG = "NotificationRepository";
    private static final int MAX_SHOWN = 50;

    public static final String TYPE_LIKE = "like";
    public static final String TYPE_FOLLOW = "follow";

    private NotificationRepository() {}

    /** One line of the activity list. */
    public static final class Item {
        public final String id;
        public final String type;           // TYPE_LIKE or TYPE_FOLLOW
        public final String actorUid;       // who did it
        public final String actorUsername;
        public final String spotId;         // likes only
        public final String spotName;       // likes only
        public final long createdAt;        // milliseconds, 0 if unknown
        public final boolean read;

        Item(String id, String type, String actorUid, String actorUsername, String spotId,
             String spotName, long createdAt, boolean read) {
            this.id = id;
            this.type = type;
            this.actorUid = actorUid;
            this.actorUsername = actorUsername;
            this.spotId = spotId;
            this.spotName = spotName;
            this.createdAt = createdAt;
            this.read = read;
        }
    }

    private static CollectionReference box(String uid) {
        return FirebaseFirestore.getInstance().collection("users").document(uid)
                .collection("notifications");
    }

    // ------------------------------------------------------------------ sending

    /** "{me} liked your spot". Nothing is sent for your own spots. */
    public static void sendLike(Spot spot, String actorUsername) {
        String me = UserSession.getUid();
        String owner = spot == null ? null : spot.getCreatorId();
        if (me.isEmpty() || spot.getId() == null || owner == null || owner.isEmpty() || owner.equals(me)) return;

        Map<String, Object> data = base(TYPE_LIKE, me, actorUsername);
        data.put("spotId", spot.getId());
        data.put("spotName", spot.getName());
        box(owner).document(likeId(spot.getId(), me)).set(data)
                .addOnFailureListener(e -> Log.w(TAG, "Could not send the like notification", e));
    }

    /** Takes the "liked your spot" notification back (after an unlike). */
    public static void removeLike(Spot spot) {
        String me = UserSession.getUid();
        String owner = spot == null ? null : spot.getCreatorId();
        if (me.isEmpty() || spot.getId() == null || owner == null || owner.isEmpty() || owner.equals(me)) return;

        box(owner).document(likeId(spot.getId(), me)).delete()
                .addOnFailureListener(e -> Log.w(TAG, "Could not remove the like notification", e));
    }

    /** "{me} started following you". */
    public static void sendFollow(String actorUsername, String targetUid) {
        String me = UserSession.getUid();
        if (me.isEmpty() || targetUid == null || targetUid.isEmpty() || targetUid.equals(me)) return;

        box(targetUid).document("follow_" + me).set(base(TYPE_FOLLOW, me, actorUsername))
                .addOnFailureListener(e -> Log.w(TAG, "Could not send the follow notification", e));
    }

    /** Takes the "started following you" notification back (after an unfollow). */
    public static void removeFollow(String targetUid) {
        String me = UserSession.getUid();
        if (me.isEmpty() || targetUid == null || targetUid.isEmpty() || targetUid.equals(me)) return;

        box(targetUid).document("follow_" + me).delete()
                .addOnFailureListener(e -> Log.w(TAG, "Could not remove the follow notification", e));
    }

    private static String likeId(String spotId, String actorUid) {
        return "like_" + spotId + "_" + actorUid;
    }

    private static Map<String, Object> base(String type, String actorUid, String actorUsername) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("actorUid", actorUid);
        data.put("actorUsername", actorUsername == null ? "" : actorUsername);
        data.put("read", false);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
    }

    // ------------------------------------------------------------------ reading

    /** The logged in user's newest notifications (up to 50). */
    public static void load(Consumer<List<Item>> onResult, Consumer<String> onError) {
        if (!UserSession.isLoggedIn()) {
            onResult.accept(new ArrayList<>());
            return;
        }
        box(UserSession.getUid())
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(MAX_SHOWN)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Item> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        String type = doc.getString("type");
                        String actorUid = doc.getString("actorUid");
                        if (type == null || actorUid == null) continue;
                        Timestamp created = doc.getTimestamp("createdAt");
                        list.add(new Item(doc.getId(), type, actorUid,
                                text(doc.getString("actorUsername")),
                                text(doc.getString("spotId")),
                                text(doc.getString("spotName")),
                                created == null ? 0 : created.toDate().getTime(),
                                Boolean.TRUE.equals(doc.getBoolean("read"))));
                    }
                    onResult.accept(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not load the activity list", e);
                    onError.accept("Could not load your activity.");
                });
    }

    /** How many notifications have not been seen yet (0 when it cannot be counted). */
    public static void countUnread(Consumer<Integer> callback) {
        if (!UserSession.isLoggedIn()) {
            callback.accept(0);
            return;
        }
        box(UserSession.getUid()).whereEqualTo("read", false)
                .count().get(AggregateSource.SERVER)
                .addOnSuccessListener(result -> callback.accept((int) result.getCount()))
                .addOnFailureListener(e -> callback.accept(0));
    }

    /** Marks the given notifications as seen. */
    public static void markAllRead(List<Item> items) {
        if (!UserSession.isLoggedIn()) return;
        WriteBatch batch = FirebaseFirestore.getInstance().batch();
        CollectionReference box = box(UserSession.getUid());
        int changes = 0;
        for (Item item : items) {
            if (item.read) continue;
            batch.update(box.document(item.id), "read", true);
            changes++;
        }
        if (changes == 0) return;
        batch.commit().addOnFailureListener(e -> Log.w(TAG, "Could not mark the activity as read", e));
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}