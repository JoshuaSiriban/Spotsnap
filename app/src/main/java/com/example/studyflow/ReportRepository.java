package com.example.studyflow;

import android.util.Log;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reports of posts that are inappropriate or at the wrong place.
 *
 *   reports/{spotId}_{reporterUid}     one report per person per spot
 *
 * The app can only write reports. The owner of the app reads them in the Firebase Console
 * (Firestore Database > reports) and changes "status" from "open" to "resolved" when done.
 */
public final class ReportRepository {

    private static final String TAG = "ReportRepository";

    public static final String REASON_INAPPROPRIATE = "inappropriate";
    public static final String REASON_WRONG_PLACE = "wrong_place";
    public static final String REASON_SPAM = "spam";
    public static final String REASON_OTHER = "other";

    private ReportRepository() {}

    /** Saves a report. Calls back with an error message, or null when it worked. */
    public static void submitReport(Spot spot, String reporterUsername, String reason,
                                    String details, Consumer<String> callback) {
        if (!UserSession.isLoggedIn()) {
            callback.accept("You need to be logged in to report a place.");
            return;
        }
        if (spot == null || spot.getId() == null) {
            callback.accept("This place cannot be reported.");
            return;
        }
        if (SpotRepository.isOwner(spot)) {
            callback.accept("You cannot report your own post.");
            return;
        }

        String uid = UserSession.getUid();
        Map<String, Object> data = new HashMap<>();
        data.put("spotId", spot.getId());
        data.put("spotName", spot.getName());
        data.put("spotOwnerId", spot.getCreatorId() == null ? "" : spot.getCreatorId());
        data.put("latitude", spot.getLatitude());
        data.put("longitude", spot.getLongitude());
        data.put("reporterId", uid);
        data.put("reporterUsername", reporterUsername == null ? "" : reporterUsername);
        data.put("reason", reason);
        data.put("details", details == null ? "" : details);
        data.put("status", "open");
        data.put("createdAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance().collection("reports")
                .document(spot.getId() + "_" + uid)
                .set(data)
                .addOnSuccessListener(unused -> callback.accept(null))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Could not send the report", e);
                    // The rules only allow creating a report, so reporting the same spot a second
                    // time is refused with PERMISSION_DENIED.
                    if (e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode()
                            == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        callback.accept("You have already reported this place.");
                    } else {
                        callback.accept("Could not send your report. Check your internet connection.");
                    }
                });
    }
}