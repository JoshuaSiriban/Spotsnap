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
        setupFollowButton(view);

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

    /** The bookmark button: shows whether the spot is saved and saves / unsaves it. */
    private void setupSaveButton(View root) {
        View saveButton = root.findViewById(R.id.btn_save);
        if (spot == null || spot.getId() == null) {
            // old sample spots are not in the database, so they cannot be saved
            saveButton.setOnClickListener(v -> Toast.makeText(requireContext(),
                    "This sample place cannot be saved.", Toast.LENGTH_SHORT).show());
            return;
        }

        saveButton.setEnabled(false); // until we know the current state
        SavedRepository.isSaved(spot.getId(), saved -> {
            if (getView() == null) return;
            saveButton.setSelected(saved);
            saveButton.setEnabled(true);
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
            SocialRepository.setLiked(spot, like, error -> {
                if (getView() == null) return;
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
}