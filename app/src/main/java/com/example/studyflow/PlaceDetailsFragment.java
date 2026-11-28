package com.example.studyflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.RadioGroup;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.fragment.app.FragmentManager;

public class PlaceDetailsFragment extends Fragment {

    private Spot spot;

    private static final String MAPS_PACKAGE = "com.google.android.apps.maps";

    /** Asked only once per app run, so the permission dialog does not keep popping up. */
    private static boolean askedForLocation = false;

    /** The text after the coordinates, e.g. " · 1.2 km away". */
    private String distanceNote = "";
    private boolean gpsOff = false;

    private final ActivityResultLauncher<String[]> requestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (getView() == null) return;
                if (hasLocationPermission()) {
                    loadDistance();
                } else {
                    setDistanceNote(" · distance unavailable (location permission is off)");
                }
            });

    public PlaceDetailsFragment() {
    }

    public void setSpot(Spot spot)
    {
        this.spot = spot;
    }

    public static PlaceDetailsFragment newInstance(@Nullable String name, @Nullable String category,
                                                   @Nullable String review) {
        PlaceDetailsFragment f = new PlaceDetailsFragment();
        Bundle b = new Bundle();
        b.putString("name", name);
        b.putString("category", category);
        b.putString("review", review);
        f.setArguments(b);
        return f;
    }

    /** Opens Place Details for a spot from any screen. */
    public static void open(androidx.fragment.app.FragmentActivity activity, Spot spot) {
        PlaceDetailsFragment fragment = newInstance(spot.getName(), spot.getCategory(), spot.getReview());
        fragment.setSpot(spot);
        DrawerMenu.open(activity, fragment);
    }

    /** Shows a message over the photo strip, or hides it when {@code message} is null. */
    private void showStatus(TextView status, @Nullable String message) {
        status.setText(message);
        status.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_place_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (spot != null)
        {
            ((TextView) view.findViewById(R.id.txt_name))
                    .setText(spot.getName());

            ((TextView) view.findViewById(R.id.txt_category))
                    .setText(Categories.normalize(spot.getCategory()).toUpperCase());

            ((TextView) view.findViewById(R.id.txt_review))
                    .setText(spot.getReview());

            ((TextView) view.findViewById(R.id.likesCount)).setText(String.valueOf(spot.getLikes()));

            ((TextView) view.findViewById(R.id.savesCount)).setText(String.valueOf(spot.getSaves()));

            ((TextView) view.findViewById(R.id.photosCount)).setText(String.valueOf(Math.max(spot.getPhotoCount(), spot.images.size())));

            ((TextView) view.findViewById(R.id.spotCreator)).setText("Pinned by @" + spot.getCreator());

            ((TextView) view.findViewById(R.id.noteCreator)).setText("— @" + spot.getCreator());

            ((TextView) view.findViewById(R.id.txt_visibility)).setText(
                    spot.isPublic() ? "Public post" : "Private post (only you can see it)");
        }

        showLocationLine();
        startDistance();

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        // Edit and delete: only for the owner of the post
        View ownerActions = view.findViewById(R.id.owner_actions);
        if (SpotRepository.isOwner(spot)) {
            ownerActions.setVisibility(View.VISIBLE);
            view.findViewById(R.id.btn_edit).setOnClickListener(v -> startEditing());
            view.findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        } else {
            ownerActions.setVisibility(View.GONE);
        }

        RecyclerView rv = view.findViewById(R.id.rv_photos);
        rv.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        PolaroidAdapter adapter = new PolaroidAdapter(spot != null ? spot.images : new java.util.ArrayList<Photo>());
        rv.setAdapter(adapter);
        adapter.setOnPhotoClick(index -> openPhotoViewer(index));

        // Spots from the database only carry a small thumbnail, so load their pictures now
        TextView status = view.findViewById(R.id.txt_photos_status);
        if (spot != null && spot.images.isEmpty() && spot.getPhotoCount() > 0 && spot.getId() != null) {
            showStatus(status, "Loading photos...");
            SpotRepository.loadPhotos(spot, loaded -> {
                spot.images = loaded;
                adapter.setPhotos(loaded);

                View root = getView();
                if (root == null) return; // the screen was closed while loading
                ((TextView) root.findViewById(R.id.photosCount)).setText(String.valueOf(loaded.size()));
                showStatus(root.findViewById(R.id.txt_photos_status),
                        loaded.isEmpty() ? "No photos for this spot yet" : null);
            }, message -> {
                View root = getView();
                if (root == null) return;
                showStatus(root.findViewById(R.id.txt_photos_status),
                        message + " Check your internet connection and try again.");
            });
        } else if (adapter.getItemCount() == 0) {
            showStatus(status, "No photos for this spot yet");
        }

        setupLikeButton(view);
        setupSaveButton(view);
        setupVisitToggle(view);
        setupFollowButton(view);
        setupCreatorLink(view);
        setupRatingAndReviews(view);
        setupShareAndReport(view);

        view.findViewById(R.id.btn_go).setOnClickListener(v -> openDirections());
    }

    // ---------------------------------------------------------------------
    // Directions and distance
    // ---------------------------------------------------------------------

    private boolean hasValidLocation() {
        return spot != null && !(spot.getLatitude() == 0 && spot.getLongitude() == 0);
    }

    private boolean hasLocationPermission() {
        android.content.Context context = getContext();
        if (context == null) return false;
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Shows "latitude, longitude" followed by the distance note. */
    private void showLocationLine() {
        View root = getView();
        if (root == null || spot == null) return;
        String coordinates = String.format(java.util.Locale.US, "%.4f, %.4f",
                spot.getLatitude(), spot.getLongitude());
        ((TextView) root.findViewById(R.id.txt_location)).setText(coordinates + distanceNote);
    }

    private void setDistanceNote(String note) {
        distanceNote = note;
        showLocationLine();
    }

    /** Works out the distance from the phone to the spot, asking for permission if needed. */
    private void startDistance() {
        if (!hasValidLocation()) return;
        if (hasLocationPermission()) {
            loadDistance();
            return;
        }
        setDistanceNote(" · distance unavailable (location permission is off)");
        if (!askedForLocation) {
            askedForLocation = true;
            requestLocation.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    @SuppressLint("MissingPermission")
    private void loadDistance() {
        if (!hasValidLocation() || !hasLocationPermission()) return;
        setDistanceNote(" · finding distance...");
        try {
            FusedLocationProviderClient client =
                    LocationServices.getFusedLocationProviderClient(requireContext());
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,
                            new CancellationTokenSource().getToken())
                    .addOnSuccessListener(location -> {
                        if (getView() == null) return;
                        if (location != null) {
                            showDistance(location);
                            return;
                        }
                        client.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (getView() == null) return;
                                    if (last != null) showDistance(last);
                                    else distanceUnavailableGps();
                                })
                                .addOnFailureListener(e -> {
                                    if (getView() != null) distanceUnavailableGps();
                                });
                    })
                    .addOnFailureListener(e -> {
                        if (getView() != null) distanceUnavailableGps();
                    });
        } catch (SecurityException e) {
            setDistanceNote(" · distance unavailable (location permission is off)");
        }
    }

    private void distanceUnavailableGps() {
        gpsOff = true;
        setDistanceNote(" · distance unavailable (turn on GPS)");
    }

    private void showDistance(Location here) {
        gpsOff = false;
        float[] result = new float[1];
        Location.distanceBetween(here.getLatitude(), here.getLongitude(),
                spot.getLatitude(), spot.getLongitude(), result);
        float meters = result[0];
        String text = meters < 1000
                ? String.format(java.util.Locale.US, "%d m away", Math.round(meters))
                : String.format(java.util.Locale.US, "%.1f km away", meters / 1000f);
        setDistanceNote(" · " + text);
    }

    /** "Take me there": opens the spot in Google Maps, where the person can tap Directions. */
    private void openDirections() {
        if (!hasValidLocation()) {
            Toast.makeText(requireContext(),
                    "This place has no saved location, so it cannot be opened on the map.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        // The distance is optional: explain why it is missing, but still open directions
        if (!hasLocationPermission()) {
            Toast.makeText(requireContext(),
                    "Location permission is off, so the distance is not shown. Opening the map anyway.",
                    Toast.LENGTH_LONG).show();
        } else if (gpsOff) {
            Toast.makeText(requireContext(),
                    "GPS is off, so the distance is not shown. Opening the map anyway.",
                    Toast.LENGTH_LONG).show();
        }

        // Locale.US so the numbers always use a dot, never a comma
        String latLng = String.format(java.util.Locale.US, "%.6f,%.6f",
                spot.getLatitude(), spot.getLongitude());

        // 1) Google Maps app: shows the place with a pin, the person taps Directions themselves
        String label = spot.getName() == null ? "" : "(" + Uri.encode(spot.getName()) + ")";
        try {
            Intent preview = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + latLng + label));
            preview.setPackage(MAPS_PACKAGE);
            startActivity(preview);
            return;
        } catch (ActivityNotFoundException ignored) {
            // Google Maps is not installed: use the web link below
        }

        // 2) Normal web link (opens in the browser)
        try {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.google.com/maps/search/?api=1&query=" + latLng));
            startActivity(web);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(),
                    "No app was found to open the map.", Toast.LENGTH_LONG).show();
        }
    }

    private void startEditing() {
        AddSpotFragment edit = new AddSpotFragment();
        edit.setEditingSpot(spot);
        DrawerMenu.open(requireActivity(), edit);
    }

    private void confirmDelete() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete this post?")
                .setMessage("\"" + spot.getName() + "\" and its photos will be removed for good.")
                .setPositiveButton("Delete", (dialog, which) -> deleteSpot())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSpot() {
        android.content.Context appContext = requireContext().getApplicationContext();
        androidx.fragment.app.FragmentActivity activity = requireActivity();
        SpotRepository.deleteSpot(spot, new SpotRepository.OnDone() {
            @Override
            public void onDone() {
                Toast.makeText(appContext, "Post deleted", Toast.LENGTH_SHORT).show();
                FragmentManager fm = activity.getSupportFragmentManager();
                if (!fm.isStateSaved()) {
                    fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    // ---------------------------------------------------- visited / want to go

    private boolean updatingVisitRow;   // true while we select a button ourselves (not the person)

    /** Shows the "Want to go | Visited" buttons while the spot is saved; {@code status} null = not saved. */
    private void showVisitRow(@Nullable String status) {
        View root = getView();
        if (root == null) return;
        root.findViewById(R.id.visit_row).setVisibility(status == null ? View.GONE : View.VISIBLE);
        if (status == null) return;

        com.google.android.material.button.MaterialButtonToggleGroup group = root.findViewById(R.id.visit_group);
        updatingVisitRow = true;
        group.check(SavedRepository.STATUS_VISITED.equals(status) ? R.id.btn_visited : R.id.btn_want);
        updatingVisitRow = false;
    }

    private void setupVisitToggle(View root) {
        if (spot == null || spot.getId() == null) return;
        com.google.android.material.button.MaterialButtonToggleGroup group = root.findViewById(R.id.visit_group);
        group.addOnButtonCheckedListener((g, checkedId, isChecked) -> {
            if (!isChecked || updatingVisitRow) return;
            String status = checkedId == R.id.btn_visited
                    ? SavedRepository.STATUS_VISITED : SavedRepository.STATUS_WANT;
            SavedRepository.setStatus(spot.getId(), status, error -> {
                if (getView() == null) return;
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    // put the buttons back to what is really saved
                    SavedRepository.getStatus(spot.getId(), this::showVisitRow);
                    return;
                }
                Toast.makeText(requireContext(),
                        SavedRepository.STATUS_VISITED.equals(status) ? "Marked as visited" : "Marked as want to go",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** The bookmark button: shows whether the spot is saved and saves / unsaves it. */    private void setupSaveButton(View root) {
        View saveButton = root.findViewById(R.id.btn_save);
        if (spot == null || spot.getId() == null) {
            // old sample spots are not in the database, so they cannot be saved
            saveButton.setOnClickListener(v -> Toast.makeText(requireContext(),
                    "This sample place cannot be saved.", Toast.LENGTH_SHORT).show());
            return;
        }

        saveButton.setEnabled(false); // until we know the current state
        SavedRepository.getStatus(spot.getId(), status -> {   // null = not saved
            if (getView() == null) return;
            saveButton.setSelected(status != null);
            saveButton.setEnabled(true);
            showVisitRow(status);
        });

        saveButton.setOnClickListener(v -> {
            boolean save = !v.isSelected();
            v.setEnabled(false);
            SavedRepository.setSaved(spot, save, error -> {
                if (getView() == null) return;
                v.setEnabled(true);
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    return;
                }
                v.setSelected(save);
                showVisitRow(save ? SavedRepository.STATUS_WANT : null);
                spot.setSaves(Math.max(0, spot.getSaves() + (save ? 1 : -1)));
                ((TextView) getView().findViewById(R.id.savesCount)).setText(String.valueOf(spot.getSaves()));
                Toast.makeText(requireContext(), save ? "Saved to your places" : "Removed from saved",
                        Toast.LENGTH_SHORT).show();
            });
        });
    }

    /** The heart button: shows whether you liked the spot and likes / unlikes it in Firestore. */
    private void setupLikeButton(View root) {
        View likeButton = root.findViewById(R.id.btn_like);
        if (spot == null || spot.getId() == null) {
            likeButton.setOnClickListener(v -> Toast.makeText(requireContext(),
                    "This sample place cannot be liked.", Toast.LENGTH_SHORT).show());
            return;
        }

        likeButton.setEnabled(false); // until we know the current state
        SocialRepository.isLiked(spot.getId(), liked -> {
            if (getView() == null) return;
            likeButton.setSelected(liked);
            likeButton.setEnabled(true);
        });

        likeButton.setOnClickListener(v -> {
            boolean like = !v.isSelected();
            v.setEnabled(false);
            SocialRepository.setLiked(UserSession.getUsername(requireContext()), spot, like, error -> {                if (getView() == null) return;
                v.setEnabled(true);
                if (error != null) {
                    Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                    return;
                }
                v.setSelected(like);
                spot.setLikes(Math.max(0, spot.getLikes() + (like ? 1 : -1)));
                ((TextView) getView().findViewById(R.id.likesCount)).setText(String.valueOf(spot.getLikes()));
            });
        });
    }

    /** Tapping the creator's name opens their profile (your own name opens your profile). */
    private void setupCreatorLink(View root) {
        String creatorId = spot == null ? null : spot.getCreatorId();
        if (creatorId == null || creatorId.isEmpty()) return; // old sample spot: nobody to open

        int padding = (int) (8 * getResources().getDisplayMetrics().density);
        View.OnClickListener openProfile = v ->
                UserProfileFragment.open(requireActivity(), creatorId, spot.getCreator());

        for (int id : new int[]{R.id.spotCreator, R.id.noteCreator}) {
            TextView name = root.findViewById(id);
            name.setOnClickListener(openProfile);
            name.setTextColor(ContextCompat.getColor(requireContext(), R.color.coral));
            name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
            name.setPadding(0, padding, 0, padding);   // a bigger area to tap
        }
    }

    /** Follow / Following button for the person who pinned the spot (hidden for your own posts). */
    private void setupFollowButton(View root) {
        MaterialButton follow = root.findViewById(R.id.btn_follow);
        String creatorId = spot == null ? null : spot.getCreatorId();
        if (creatorId == null || creatorId.equals(UserSession.getUid())) {
            follow.setVisibility(View.GONE); // your own post, or an old sample spot
            return;
        }

        boolean[] following = {false};
        follow.setEnabled(false);
        SocialRepository.isFollowing(creatorId, isFollowing -> {
            if (getView() == null) return;
            following[0] = isFollowing;
            follow.setText(isFollowing ? "Following" : "Follow");
            follow.setEnabled(true);
        });

        follow.setOnClickListener(v -> {
            boolean follow_ = !following[0];
            follow.setEnabled(false);
            SocialRepository.setFollowing(UserSession.getUsername(requireContext()), creatorId,
                    spot.getCreator(), follow_, error -> {
                        if (getView() == null) return;
                        follow.setEnabled(true);
                        if (error != null) {
                            Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        following[0] = follow_;
                        follow.setText(follow_ ? "Following" : "Follow");
                        Toast.makeText(requireContext(), follow_
                                ? "You follow @" + spot.getCreator()
                                : "Unfollowed @" + spot.getCreator(), Toast.LENGTH_SHORT).show();
                    });
        });
    }

    // ---------------------------------------------------------------------
    // Star ratings and reviews
    // ---------------------------------------------------------------------

    private Review myReview;   // the logged in user's own review of this spot (null = none yet)

    private void setupRatingAndReviews(View root) {
        showRatingSummary();
        if (spot == null || spot.getId() == null) {
            // old sample spots are not in the database, so they cannot have reviews
            root.findViewById(R.id.reviews_section).setVisibility(View.GONE);
            return;
        }
        root.findViewById(R.id.btn_write_review).setOnClickListener(v -> showReviewDialog());
        loadReviews();
    }

    /** The stars and the average under the spot name, e.g. "★★★★☆  4.3 (12)". */
    private void showRatingSummary() {
        View root = getView();
        if (root == null || spot == null) return;
        ((TextView) root.findViewById(R.id.txt_rating_stars))
                .setText(RatingUtils.stars(spot.getAverageRating()));
        ((TextView) root.findViewById(R.id.txt_rating_text))
                .setText(RatingUtils.summary(spot));
    }

    private void loadReviews() {
        View root = getView();
        if (root == null || spot == null || spot.getId() == null) return;

        TextView status = root.findViewById(R.id.txt_reviews_status);
        status.setText("Loading reviews...");
        status.setVisibility(View.VISIBLE);

        ReviewRepository.loadReviews(spot.getId(), list -> {
            if (getView() == null) return; // the screen was closed while loading
            showReviews(list);
        }, message -> {
            View r = getView();
            if (r == null) return;
            TextView s = r.findViewById(R.id.txt_reviews_status);
            s.setText(message + " Check your internet connection.");
            s.setVisibility(View.VISIBLE);
        });
    }

    private void showReviews(List<Review> list) {
        View root = getView();
        if (root == null) return;

        LinearLayout container = root.findViewById(R.id.reviews_container);
        container.removeAllViews();
        myReview = null;

        String me = UserSession.getUid();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM);

        for (Review review : list) {
            boolean mine = review.uid != null && review.uid.equals(me);
            if (mine) myReview = review;

            View row = inflater.inflate(R.layout.item_review, container, false);
            ((TextView) row.findViewById(R.id.review_author))
                    .setText("@" + review.username + (mine ? " (you)" : ""));
            ((TextView) row.findViewById(R.id.review_stars))
                    .setText(RatingUtils.stars(review.rating));

            TextView text = row.findViewById(R.id.review_text);
            if (review.text == null || review.text.isEmpty()) {
                text.setVisibility(View.GONE);   // a rating without a written review
            } else {
                text.setText(review.text);
            }

            TextView date = row.findViewById(R.id.review_date);
            if (review.createdAt > 0) {
                date.setText(dateFormat.format(new Date(review.createdAt)));
            } else {
                date.setVisibility(View.GONE);
            }
            container.addView(row);
        }

        TextView status = root.findViewById(R.id.txt_reviews_status);
        status.setText("No reviews yet. Be the first to share a recommendation!");
        status.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);

        ((TextView) root.findViewById(R.id.txt_reviews_count))
                .setText(list.size() + (list.size() == 1 ? " review" : " reviews"));

        ((com.google.android.material.button.MaterialButton) root.findViewById(R.id.btn_write_review))
                .setText(myReview == null ? "Write a review" : "Edit your review");
    }

    /** Dialog with the star bar and the text box. Used for writing and for editing. */
    private void showReviewDialog() {
        if (spot == null || spot.getId() == null) return;

        View form = getLayoutInflater().inflate(R.layout.dialog_review, null);
        RatingBar bar = form.findViewById(R.id.review_rating);
        EditText input = form.findViewById(R.id.review_text);
        if (myReview != null) {
            bar.setRating(myReview.rating);
            input.setText(myReview.text);
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(myReview == null ? "Write a review" : "Edit your review")
                .setView(form)
                .setPositiveButton("Save", null)      // set below so a mistake does not close the dialog
                .setNegativeButton("Cancel", null);
        if (myReview != null) builder.setNeutralButton("Delete", null);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int stars = Math.round(bar.getRating());
            if (stars < 1) {
                toast("Tap the stars to give a rating first.");
                return;
            }
            v.setEnabled(false);
            String username = UserSession.getUsername(requireContext());
            ReviewRepository.saveReview(spot, username, stars, input.getText().toString().trim(),
                    new ReviewRepository.OnChanged() {
                        @Override
                        public void onDone(long ratingSumChange, int ratingCountChange) {
                            dialog.dismiss();
                            applyRatingChange(ratingSumChange, ratingCountChange);
                            toast("Thanks! Your review was saved.");
                        }

                        @Override
                        public void onError(String message) {
                            v.setEnabled(true);
                            toast(message);
                        }
                    });
        });

        if (myReview != null) {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                dialog.dismiss();
                confirmDeleteReview();
            });
        }
    }

    private void confirmDeleteReview() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete your review?")
                .setMessage("Your rating and review will be removed from this place.")
                .setPositiveButton("Delete", (d, which) ->
                        ReviewRepository.deleteMyReview(spot, new ReviewRepository.OnChanged() {
                            @Override
                            public void onDone(long ratingSumChange, int ratingCountChange) {
                                applyRatingChange(ratingSumChange, ratingCountChange);
                                toast("Review deleted");
                            }

                            @Override
                            public void onError(String message) {
                                toast(message);
                            }
                        }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Updates the numbers on this screen right away and reloads the list of reviews. */
    private void applyRatingChange(long ratingSumChange, int ratingCountChange) {
        if (spot != null) {
            spot.setRating(spot.getRatingSum() + ratingSumChange,
                    spot.getRatingCount() + ratingCountChange);
        }
        if (getView() == null) return;
        showRatingSummary();
        loadReviews();
    }

    private void toast(String message) {
        android.content.Context context = getContext();
        if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    // ---------------------------------------------------------------------
    // Share and report
    // ---------------------------------------------------------------------

    private void setupShareAndReport(View root) {
        root.findViewById(R.id.btn_share).setOnClickListener(v -> shareSpot());

        // You cannot report your own post, and old sample spots are not in the database
        View report = root.findViewById(R.id.btn_report);
        boolean reportable = spot != null && spot.getId() != null && !SpotRepository.isOwner(spot);
        report.setVisibility(reportable ? View.VISIBLE : View.GONE);
        if (reportable) report.setOnClickListener(v -> showReportDialog());
    }

    /** Opens the Android share sheet with the name of the spot and a Google Maps link. */
    private void shareSpot() {
        if (spot == null) return;

        StringBuilder text = new StringBuilder("Check out " + spot.getName() + " on SpotSnap");
        if (hasValidLocation()) {
            // Locale.US so the numbers always use a dot, never a comma
            String latLng = String.format(java.util.Locale.US, "%.6f,%.6f",
                    spot.getLatitude(), spot.getLongitude());
            text.append("\n").append("https://www.google.com/maps/search/?api=1&query=").append(latLng);
        }

        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, spot.getName());
        send.putExtra(Intent.EXTRA_TEXT, text.toString());
        try {
            startActivity(Intent.createChooser(send, "Share this place"));
        } catch (ActivityNotFoundException e) {
            toast("No app was found to share with.");
        }
    }

    /** Dialog to choose why the post is reported, then saves the report for the owner to review. */
    private void showReportDialog() {
        if (spot == null || spot.getId() == null) return;

        View form = getLayoutInflater().inflate(R.layout.dialog_report, null);
        RadioGroup reasons = form.findViewById(R.id.report_reasons);
        EditText details = form.findViewById(R.id.report_details);

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Report this place")
                .setView(form)
                .setPositiveButton("Send report", null)   // set below so the dialog stays open on a mistake
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checked = reasons.getCheckedRadioButtonId();
            if (checked == View.NO_ID) {
                toast("Choose a reason first.");
                return;
            }
            String reason = checked == R.id.report_inappropriate ? ReportRepository.REASON_INAPPROPRIATE
                    : checked == R.id.report_wrong_place ? ReportRepository.REASON_WRONG_PLACE
                      : checked == R.id.report_spam ? ReportRepository.REASON_SPAM
                        : ReportRepository.REASON_OTHER;

            v.setEnabled(false);
            ReportRepository.submitReport(spot, UserSession.getUsername(requireContext()), reason,
                    details.getText().toString().trim(), error -> {
                        if (error == null) {
                            dialog.dismiss();
                            toast("Thanks! Your report was sent and will be reviewed.");
                        } else {
                            v.setEnabled(true);
                            toast(error);
                        }
                    });
        });
    }

    /** Opens the full screen photo viewer (swipe + pinch to zoom) on the tapped photo. */
    private void openPhotoViewer(int index) {
        if (spot == null || spot.images == null || spot.images.isEmpty()) return;
        PhotoViewerFragment.open(requireActivity(), spot.images, index);
    }
}