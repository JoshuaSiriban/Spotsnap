package com.example.studyflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AddSpotFragment extends Fragment {

    private static final int MAX_PHOTOS = 5;
    private static final int FULL_SIDE = 1024;
    private static final int FULL_QUALITY = 75;
    private static final int THUMB_SIDE = 240;
    private static final long SAVE_TIMEOUT_MS = 20000;
    private static final long DUPLICATE_CHECK_TIMEOUT_MS = 6000;
    private static final double DEFAULT_LAT = 14.5995;
    private static final double DEFAULT_LNG = 120.9842;

    private final ArrayList<Photo> photos = new ArrayList<>();
    private int photosBeingPrepared = 0;

    private double latitude = DEFAULT_LAT;
    private double longitude = DEFAULT_LNG;
    private boolean locationReady = false;
    private boolean usingCurrentLocation = false;
    private boolean saving = false;

    private Spot editingSpot;   // not null when this screen edits an existing post

    private Uri cameraUri;
    private File cameraFile;

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable saveTimeout;

    private final ActivityResultLauncher<Uri> takePicture = registerForActivityResult(
            new ActivityResultContracts.TakePicture(), success -> {
                if (Boolean.TRUE.equals(success) && cameraUri != null) {
                    addPhoto(cameraUri, cameraFile);
                } else if (cameraFile != null) {
                    cameraFile.delete();
                }
            });

    private final ActivityResultLauncher<PickVisualMediaRequest> pickImages = registerForActivityResult(
            new ActivityResultContracts.PickMultipleVisualMedia(MAX_PHOTOS), uris -> {
                int room = MAX_PHOTOS - photos.size() - photosBeingPrepared;
                if (uris.size() > room) {
                    toast("Only " + MAX_PHOTOS + " photos are allowed per spot");
                }
                for (int i = 0; i < uris.size() && i < room; i++) {
                    addPhoto(uris.get(i), null);
                }
            });

    private final ActivityResultLauncher<String[]> requestLocation = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    fetchCurrentLocation();
                } else {
                    toast("Location permission denied. Tap Adjust to place the pin on the map.");
                    updateLocationUi();
                }
            });

    public AddSpotFragment() {
    }

    /** Turns this screen into "edit this post". Call before the fragment is shown. */
    public void setEditingSpot(Spot spot) {
        this.editingSpot = spot;
        this.latitude = spot.getLatitude();
        this.longitude = spot.getLongitude();
        this.locationReady = true;
        this.usingCurrentLocation = false;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getParentFragmentManager().setFragmentResultListener(PickLocationFragment.RESULT_KEY, this,
                (requestKey, result) -> {
                    latitude = result.getDouble(PickLocationFragment.KEY_LAT);
                    longitude = result.getDouble(PickLocationFragment.KEY_LNG);
                    locationReady = true;
                    usingCurrentLocation = false;
                    updateLocationUi();
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_add_spot, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        view.findViewById(R.id.photo_frame).setOnClickListener(v -> showPhotoChooser());
        view.findViewById(R.id.btn_camera).setOnClickListener(v -> launchCamera());
        view.findViewById(R.id.btn_gallery).setOnClickListener(v -> launchGallery());

        view.findViewById(R.id.btn_use_current).setOnClickListener(v -> fetchCurrentLocation());
        view.findViewById(R.id.btn_adjust_pin).setOnClickListener(v ->
                DrawerMenu.open(requireActivity(), PickLocationFragment.newInstance(latitude, longitude)));

        view.findViewById(R.id.btn_pin_it).setOnClickListener(v -> savePin());

        if (editingSpot != null) {
            prefillForEditing(view);
        } else {
            // Settings > "New posts are public by default" (a restored choice overrides this)
            MaterialButtonToggleGroup privacyGroup = view.findViewById(R.id.toggle_privacy);
            privacyGroup.check(AppSettings.defaultPublic(requireContext())
                    ? R.id.btn_public : R.id.btn_private);
        }

        renderPhotos();
        updateLocationUi();
        setSaving(saving);

        if (!locationReady) fetchCurrentLocation();
    }

    /** Fills the form with the post being edited and loads its pictures. */
    private void prefillForEditing(View root) {
        ((TextView) root.findViewById(R.id.txt_title)).setText("Edit Snap");
        ((TextInputEditText) root.findViewById(R.id.input_name)).setText(editingSpot.getName());
        ((TextInputEditText) root.findViewById(R.id.input_review)).setText(editingSpot.getReview());

        ChipGroup chips = root.findViewById(R.id.chip_group);
        String category = Categories.normalize(editingSpot.getCategory());
        for (int i = 0; i < chips.getChildCount(); i++) {
            Chip chip = (Chip) chips.getChildAt(i);
            if (chip.getText().toString().equals(category)) chip.setChecked(true);
        }

        MaterialButtonToggleGroup privacy = root.findViewById(R.id.toggle_privacy);
        privacy.check(editingSpot.isPublic() ? R.id.btn_public : R.id.btn_private);

        // Load the existing pictures once (not again after rotating the screen)
        if (photos.isEmpty() && photosBeingPrepared == 0 && editingSpot.getId() != null) {
            photosBeingPrepared++;
            SpotRepository.loadPhotos(editingSpot, loaded -> {
                worker.execute(() -> {
                    ArrayList<Photo> ready = new ArrayList<>();
                    for (Photo p : loaded) {
                        if (p.bytes == null) continue;
                        ready.add(new Photo(p.bytes, ImageUtils.makeThumbnail(p.bytes, THUMB_SIDE), p.caption));
                    }
                    mainHandler.post(() -> {
                        photosBeingPrepared--;
                        photos.addAll(ready);
                        renderPhotos();
                    });
                });
            }, message -> {
                photosBeingPrepared--;
                toast(message + " Go back and try again.");
                renderPhotos();
            });
        }
        ((MaterialButton) root.findViewById(R.id.btn_pin_it)).setText("Save changes");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        worker.shutdown();
        if (saveTimeout != null) mainHandler.removeCallbacks(saveTimeout);
    }


    private void showPhotoChooser() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add a photo")
                .setItems(new String[]{"Take a photo", "Choose from gallery"}, (dialog, which) -> {
                    if (which == 0) launchCamera();
                    else launchGallery();
                })
                .show();
    }

    private void launchCamera() {
        if (photos.size() + photosBeingPrepared >= MAX_PHOTOS) {
            toast("You can add up to " + MAX_PHOTOS + " photos");
            return;
        }
        try {
            File folder = new File(requireContext().getCacheDir(), "camera");
            if (!folder.exists()) folder.mkdirs();
            cameraFile = File.createTempFile("snap_", ".jpg", folder);
            cameraUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", cameraFile);
            takePicture.launch(cameraUri);
        } catch (IOException | ActivityNotFoundException | IllegalArgumentException e) {
            toast("Could not open the camera");
        }
    }

    private void launchGallery() {
        if (photos.size() + photosBeingPrepared >= MAX_PHOTOS) {
            toast("You can add up to " + MAX_PHOTOS + " photos");
            return;
        }
        pickImages.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
    }

    private void addPhoto(Uri uri, @Nullable File temporaryFile) {
        if (photos.size() + photosBeingPrepared >= MAX_PHOTOS) return;

        photosBeingPrepared++;
        renderPhotos();

        Context appContext = requireContext().getApplicationContext();
        worker.execute(() -> {
            byte[] full = ImageUtils.compress(appContext, uri, FULL_SIDE, FULL_QUALITY);
            byte[] thumb = full == null ? null : ImageUtils.makeThumbnail(full, THUMB_SIDE);
            if (temporaryFile != null) temporaryFile.delete();

            mainHandler.post(() -> {
                photosBeingPrepared--;
                if (full == null || thumb == null) {
                    toast("Could not read that picture");
                } else {
                    photos.add(new Photo(full, thumb, ""));
                }
                renderPhotos();
            });
        });
    }

    private void renderPhotos() {
        View root = getView();
        if (root == null) return;

        ImageView preview = root.findViewById(R.id.img_preview);
        View placeholder = root.findViewById(R.id.photo_placeholder);
        TextView frameCaption = root.findViewById(R.id.txt_frame_caption);
        TextView counter = root.findViewById(R.id.txt_photo_counter);
        View stripScroll = root.findViewById(R.id.photo_strip_scroll);
        LinearLayout strip = root.findViewById(R.id.photo_strip);

        if (photos.isEmpty()) {
            preview.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
            stripScroll.setVisibility(View.GONE);
            frameCaption.setText("Add up to " + MAX_PHOTOS + " photos of this spot");
        } else {
            preview.setImageBitmap(ImageUtils.decode(photos.get(0).bytes));
            placeholder.setVisibility(View.GONE);
            stripScroll.setVisibility(View.VISIBLE);
            frameCaption.setText("Tap a small photo to add a caption");
        }

        strip.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Photo photo : photos) {
            View item = inflater.inflate(R.layout.item_photo_thumb, strip, false);
            ((ImageView) item.findViewById(R.id.thumb_image)).setImageBitmap(ImageUtils.decode(photo.thumb));
            item.findViewById(R.id.thumb_image).setOnClickListener(v -> showCaptionDialog(photo));
            item.findViewById(R.id.thumb_remove).setOnClickListener(v -> {
                photos.remove(photo);
                renderPhotos();
            });
            strip.addView(item);
        }

        String text = photos.size() + " of " + MAX_PHOTOS + " photos";
        if (photosBeingPrepared > 0) text += "  (preparing...)";
        counter.setText(text);
    }

    private void showCaptionDialog(Photo photo) {
        EditText input = new EditText(requireContext());
        input.setHint("Caption (optional)");
        input.setSingleLine(true);
        input.setText(photo.caption);

        FrameLayout holder = new FrameLayout(requireContext());
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        holder.setPadding(pad, pad / 2, pad, 0);
        holder.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Photo caption")
                .setView(holder)
                .setPositiveButton("Save", (dialog, which) ->
                        photo.caption = input.getText().toString().trim())
                .setNegativeButton("Cancel", null)
                .show();
    }


    @SuppressLint("MissingPermission")
    private void fetchCurrentLocation() {
        Context context = getContext();
        if (context == null) return;

        boolean granted =
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            requestLocation.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }

        setLocationText("Finding your location...", "");
        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        applyCurrentLocation(location);
                    } else {
                        client.getLastLocation()
                                .addOnSuccessListener(last -> {
                                    if (last != null) applyCurrentLocation(last);
                                    else locationNotFound();
                                })
                                .addOnFailureListener(e -> locationNotFound());
                    }
                })
                .addOnFailureListener(e -> locationNotFound());
    }

    private void applyCurrentLocation(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        locationReady = true;
        usingCurrentLocation = true;
        updateLocationUi();
    }

    private void locationNotFound() {
        if (!isAdded()) return;
        toast("Could not find your location. Turn on GPS or tap Adjust to place the pin on the map.");
        updateLocationUi();
    }

    private void updateLocationUi() {
        if (!locationReady) {
            setLocationText("Location not set", "Use your location or tap Adjust");
            return;
        }
        String label = usingCurrentLocation ? "Your current location" : "Location chosen on the map";
        String coordinates = String.format(Locale.US, "%.5f\u00B0 %s, %.5f\u00B0 %s",
                Math.abs(latitude), latitude >= 0 ? "N" : "S",
                Math.abs(longitude), longitude >= 0 ? "E" : "W");
        setLocationText(label, coordinates);
    }

    private void setLocationText(String label, String coordinates) {
        View root = getView();
        if (root == null) return;
        ((TextView) root.findViewById(R.id.txt_location_label)).setText(label);
        ((TextView) root.findViewById(R.id.txt_location_coords)).setText(coordinates);
    }


    private void savePin() {
        View root = getView();
        if (root == null || saving) return;

        TextInputLayout nameLayout = root.findViewById(R.id.layout_name);
        TextInputEditText nameInput = root.findViewById(R.id.input_name);
        TextInputEditText reviewInput = root.findViewById(R.id.input_review);
        ChipGroup chips = root.findViewById(R.id.chip_group);
        MaterialButtonToggleGroup privacy = root.findViewById(R.id.toggle_privacy);

        String name = String.valueOf(nameInput.getText()).trim();
        String review = String.valueOf(reviewInput.getText()).trim();

        if (name.isEmpty()) {
            nameLayout.setError("Give this spot a name");
            return;
        }
        nameLayout.setError(null);

        if (photosBeingPrepared > 0) {
            toast("Your photos are still being prepared. Try again in a moment.");
            return;
        }
        if (photos.isEmpty()) {
            toast("Add at least one photo");
            return;
        }
        if (!locationReady) {
            toast("Set the location first: tap Use my current location or Adjust");
            return;
        }
        if (!UserSession.isLoggedIn()) {
            toast("Please log in again");
            return;
        }

        Chip chip = root.findViewById(chips.getCheckedChipId());
        String category = chip == null ? Categories.OTHER : chip.getText().toString();
        boolean isPublic = privacy.getCheckedButtonId() == R.id.btn_public;

        ArrayList<Photo> photosToSave = new ArrayList<>(photos);
        if (editingSpot != null) {
            saveEdit(name, review, category, isPublic, photosToSave);
            return;
        }
        // A new post: first check that the place is not already pinned
        checkForDuplicatesThenSave(name, review, category, isPublic, photosToSave);
    }

    /**
     * Looks for a place with a similar name close to this pin. If there is one, asks the person
     * (they can open it, or pin anyway). If the check cannot finish in a few seconds (for example
     * offline), the spot is simply saved, so adding a place is never blocked.
     */
    private void checkForDuplicatesThenSave(String name, String review, String category,
                                            boolean isPublic, ArrayList<Photo> photosToSave) {
        setSaving(true);   // shows "Saving..." and stops double taps while we check

        boolean[] answered = {false};
        Runnable giveUp = () -> {
            if (answered[0]) return;
            answered[0] = true;
            if (!isAdded()) return;
            saveNewSpot(name, review, category, isPublic, photosToSave);
        };
        mainHandler.postDelayed(giveUp, DUPLICATE_CHECK_TIMEOUT_MS);

        SpotRepository.loadVisibleSpots(requireContext(), existing -> {
            if (answered[0]) return;
            answered[0] = true;
            mainHandler.removeCallbacks(giveUp);
            if (!isAdded() || getView() == null) return;

            List<DuplicateFinder.Match> matches = DuplicateFinder.find(existing, name, latitude, longitude);
            if (matches.isEmpty()) {
                saveNewSpot(name, review, category, isPublic, photosToSave);
            } else {
                setSaving(false);
                showDuplicateDialog(matches, () ->
                        saveNewSpot(name, review, category, isPublic, photosToSave));
            }
        }, message -> {
            if (answered[0]) return;
            answered[0] = true;
            mainHandler.removeCallbacks(giveUp);
            if (!isAdded() || getView() == null) return;
            saveNewSpot(name, review, category, isPublic, photosToSave);   // could not check: do not block
        });
    }

    private void showDuplicateDialog(List<DuplicateFinder.Match> matches, Runnable pinAnyway) {
        String[] labels = new String[matches.size()];
        for (int i = 0; i < labels.length; i++) {
            DuplicateFinder.Match match = matches.get(i);
            labels[i] = match.spot.getName() + "  ·  "
                    + AppSettings.formatDistance(requireContext(), match.meters) + " away";
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Similar places nearby (tap to view)")
                .setItems(labels, (dialog, which) ->
                        PlaceDetailsFragment.open(requireActivity(), matches.get(which).spot))
                .setPositiveButton("Pin it anyway", (dialog, which) -> pinAnyway.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Saves a brand new post to Firestore, then shows it. */
    private void saveNewSpot(String name, String review, String category, boolean isPublic,
                             ArrayList<Photo> photosToSave) {
        Spot spot = new Spot(name, UserSession.getUsername(requireContext()), review, category,
                latitude, longitude, photosToSave, 0, 0);
        spot.setCreatorId(UserSession.getUid());
        spot.setPublic(isPublic);
        spot.setPhotoCount(photosToSave.size());
        spot.setThumbnail(photosToSave.get(0).thumb);

        setSaving(true);
        FragmentActivity activity = requireActivity();
        Context appContext = requireContext().getApplicationContext();
        boolean[] finished = {false};

        saveTimeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            Toast.makeText(appContext, "You seem to be offline. Your spot will upload when you reconnect.",
                    Toast.LENGTH_LONG).show();
            backToMap(activity, null);
        };
        mainHandler.postDelayed(saveTimeout, SAVE_TIMEOUT_MS);

        SpotRepository.saveNewSpot(spot, photosToSave, new SpotRepository.OnSaved() {
            @Override
            public void onSaved(String spotId) {
                if (finished[0]) return;
                finished[0] = true;
                mainHandler.removeCallbacks(saveTimeout);
                spot.setId(spotId);
                Toast.makeText(appContext, "Spot pinned!", Toast.LENGTH_SHORT).show();
                backToMap(activity, spot);
            }

            @Override
            public void onError(String message) {
                if (finished[0]) return;
                finished[0] = true;
                mainHandler.removeCallbacks(saveTimeout);
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
                setSaving(false);
            }
        });
    }

    /** Saves the changes of an existing post, then shows the updated post. */
    private void saveEdit(String name, String review, String category, boolean isPublic,
                          ArrayList<Photo> photosToSave) {
        Spot updated = new Spot(name, editingSpot.getCreator(), review, category,
                latitude, longitude, photosToSave, editingSpot.getLikes(), editingSpot.getSaves());
        updated.setId(editingSpot.getId());
        updated.setCreatorId(editingSpot.getCreatorId());
        updated.setPublic(isPublic);
        updated.setPhotoCount(photosToSave.size());
        updated.setThumbnail(photosToSave.get(0).thumb);
        updated.setRating(editingSpot.getRatingSum(), editingSpot.getRatingCount());
        setSaving(true);
        FragmentActivity activity = requireActivity();
        Context appContext = requireContext().getApplicationContext();
        boolean[] finished = {false};

        saveTimeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            Toast.makeText(appContext, "You seem to be offline. Your changes will upload when you reconnect.",
                    Toast.LENGTH_LONG).show();
            backToMap(activity, null);
        };
        mainHandler.postDelayed(saveTimeout, SAVE_TIMEOUT_MS);

        SpotRepository.updateSpot(updated, photosToSave, new SpotRepository.OnDone() {
            @Override
            public void onDone() {
                if (finished[0]) return;
                finished[0] = true;
                mainHandler.removeCallbacks(saveTimeout);
                Toast.makeText(appContext, "Post updated", Toast.LENGTH_SHORT).show();
                backToMap(activity, updated);
            }

            @Override
            public void onError(String message) {
                if (finished[0]) return;
                finished[0] = true;
                mainHandler.removeCallbacks(saveTimeout);
                Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
                setSaving(false);
            }
        });
    }

    private static void backToMap(FragmentActivity activity, @Nullable Spot spot) {
        FragmentManager fm = activity.getSupportFragmentManager();
        if (fm.isStateSaved()) return;

        fm.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (spot != null) {
            PlaceDetailsFragment details = PlaceDetailsFragment.newInstance(
                    spot.getName(), spot.getCategory(), spot.getReview());
            details.setSpot(spot);
            DrawerMenu.open(activity, details);
        }
    }

    private void setSaving(boolean value) {
        saving = value;
        View root = getView();
        if (root == null) return;
        MaterialButton button = root.findViewById(R.id.btn_pin_it);
        button.setEnabled(!value);
        button.setText(value ? "Saving..." : (editingSpot != null ? "Save changes" : "Pin it"));
    }

    private void toast(String message) {
        Context context = getContext();
        if (context != null) Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}