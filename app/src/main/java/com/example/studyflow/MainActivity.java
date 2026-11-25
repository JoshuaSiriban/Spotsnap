package com.example.studyflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The map screen. Every public spot (plus the logged in user's private ones) comes live from
 * Firestore and is drawn as a marker. The Food / Hang Out / Tourist Spot chips filter the
 * markers, and the "my location" button centres the map on the user and lists nearby spots.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final LatLng DEFAULT_CENTER = new LatLng(14.5995, 120.9842); // Manila
    private static final float DEFAULT_ZOOM = 12f;
    private static final float LOCATION_ZOOM = 15f;
    private static final float NEARBY_RADIUS_METERS = 5000f;
    private static final int MAX_NEARBY_SPOTS = 15;

    private GoogleMap googleMap;
    private SpotRepository.Subscription spotSubscription; // live updates of the spots from Firestore

    private final Map<String, Marker> markers = new HashMap<>();            // spot id -> marker
    private final Map<Integer, BitmapDescriptor> icons = new HashMap<>();   // marker picture -> icon
    private final Set<String> selectedCategories = new HashSet<>();         // lower case; empty = all
    private boolean loadErrorShown;

    private FusedLocationProviderClient locationClient;
    private Location lastLocation;     // where the user was when they last tapped "my location"
    private boolean nearbyVisible;
    private NearbyAdapter nearbyAdapter;
    private BottomSheetBehavior<View> nearbySheet;   // the slide-up list of nearby spots
    private View mapButtons;                         // my location / crosshair / add buttons
    private OnBackPressedCallback collapseSheetOnBack;

    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean granted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (granted) {
                    showMyLocation();
                } else {
                    toast("Location permission denied. You can allow it in your phone's Settings > Apps > SpotSnap.");
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Not logged in (or logged out): go to the login screen.
        if (!UserSession.isLoggedIn()) {
            Intent intent = new Intent(MainActivity.this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        keepMapUiInSyncWithFragments();

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        DrawerMenu.setup(this, drawer);

        // Profile missing locally (fresh install, or it could not load at login): fetch it now.
        if (!UserSession.hasProfile(this)) {
            AuthRepository.loadProfile(this, () -> DrawerMenu.bindUser(drawer));
        }

        findViewById(R.id.btn_profile).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.search_bar).setOnClickListener(v -> DrawerMenu.open(this, new SearchFragment()));
        findViewById(R.id.btn_add).setOnClickListener(v -> DrawerMenu.open(this, new AddSpotFragment()));

        setupCrosshair();
        setupFilterChips();
        setupNearbySheet();
        findViewById(R.id.btn_my_location).setOnClickListener(v -> onMyLocationClicked());

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    // ------------------------------------------------------------- screen setup

    /** Shows the map buttons only while no other screen is open on top of the map. */
    private void keepMapUiInSyncWithFragments() {
        getSupportFragmentManager().addOnBackStackChangedListener(() ->
                findViewById(R.id.fragment_container).post(this::updateMapUi));

        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                                      @NonNull View v, @Nullable Bundle savedInstanceState) {
                        if (f.getId() == R.id.fragment_container) {
                            v.setClickable(true);
                            v.setFocusable(true);
                        }
                    }

                    @Override
                    public void onFragmentViewDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
                        findViewById(R.id.fragment_container).post(MainActivity.this::updateMapUi);
                    }
                }, false);
    }

    private void setupCrosshair() {
        ImageView crosshair = findViewById(R.id.crosshair);
        ImageButton crosshairButton = findViewById(R.id.btn_crosshair);

        crosshairButton.setOnClickListener(v -> {
            if (crosshair.getVisibility() == View.VISIBLE) {
                crosshairButton.setImageResource(R.drawable.togglecrosshair);
                crosshair.setVisibility(View.GONE);
            } else {
                crosshair.setVisibility(View.VISIBLE);
                crosshairButton.setImageResource(R.drawable.togglecrosshair2);
            }
        });
    }

    /** No chip selected = every spot. Selected chips = only spots of those categories. */
    private void setupFilterChips() {
        ChipGroup group = findViewById(R.id.filter_group);
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            selectedCategories.clear();
            for (int id : checkedIds) {
                Chip chip = chipGroup.findViewById(id);
                if (chip != null) {
                    selectedCategories.add(chip.getText().toString().toLowerCase(Locale.ROOT));
                }
            }
            applyFilter();
        });
    }

    /**
     * The nearby spots list is a bottom sheet, like in Google Maps: it slides up when "my
     * location" is tapped, can be dragged up to see more spots, and swiped down to hide.
     */
    private void setupNearbySheet() {
        nearbyAdapter = new NearbyAdapter(this::onNearbySpotClicked);
        RecyclerView list = findViewById(R.id.nearby_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(nearbyAdapter);

        View sheetView = findViewById(R.id.nearby_sheet);
        mapButtons = findViewById(R.id.map_buttons);
        nearbySheet = BottomSheetBehavior.from(sheetView);
        nearbySheet.setState(BottomSheetBehavior.STATE_HIDDEN);

        // Back first closes a wide-open sheet, before leaving the screen
        collapseSheetOnBack = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                nearbySheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        };
        getOnBackPressedDispatcher().addCallback(this, collapseSheetOnBack);

        nearbySheet.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View sheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                    nearbyVisible = false; // stop updating the list while it is not on screen
                    setMapBottomPadding(0);
                }
                updateBackCallback();
                moveButtonsWithSheet(sheet);
            }

            @Override
            public void onSlide(@NonNull View sheet, float slideOffset) {
                moveButtonsWithSheet(sheet);
            }
        });

        findViewById(R.id.btn_close_nearby).setOnClickListener(v ->
                nearbySheet.setState(BottomSheetBehavior.STATE_HIDDEN));
    }

    /** The map buttons follow the top of the sheet, then fade out once it is opened wide. */
    private void moveButtonsWithSheet(View sheet) {
        View parent = (View) sheet.getParent();
        float visibleHeight = Math.max(0f, parent.getHeight() - sheet.getTop());
        float followLimit = parent.getHeight() * 0.45f;
        float fadeRange = parent.getHeight() * 0.1f;

        mapButtons.setTranslationY(-Math.min(visibleHeight, followLimit));
        float alpha = visibleHeight <= followLimit
                ? 1f : Math.max(0f, 1f - (visibleHeight - followLimit) / fadeRange);
        mapButtons.setAlpha(alpha);
        mapButtons.setVisibility(alpha == 0f ? View.INVISIBLE : View.VISIBLE);
    }

    /** Keeps the centre of the map inside the part of the map that the sheet does not cover. */
    private void setMapBottomPadding(int pixels) {
        if (googleMap != null) googleMap.setPadding(0, 0, 0, pixels);
    }

    private void updateBackCallback() {
        if (collapseSheetOnBack == null || nearbySheet == null) return;
        int state = nearbySheet.getState();
        boolean wide = state == BottomSheetBehavior.STATE_EXPANDED
                || state == BottomSheetBehavior.STATE_HALF_EXPANDED;
        boolean onMap = getSupportFragmentManager().getBackStackEntryCount() == 0;
        collapseSheetOnBack.setEnabled(wide && onMap); // not while Place Details etc. are open
    }

    private void updateMapUi() {
        boolean onMap = getSupportFragmentManager().getBackStackEntryCount() == 0;
        findViewById(R.id.fragment_container).setVisibility(onMap ? View.GONE : View.VISIBLE);
        findViewById(R.id.map_overlay).setVisibility(onMap ? View.VISIBLE : View.GONE);
        updateBackCallback();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UserSession.isLoggedIn()) updateMapUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotSubscription != null) spotSubscription.remove(); // stop listening to Firestore
    }

    // ---------------------------------------------------------------------- map

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM));
        map.getUiSettings().setMyLocationButtonEnabled(false); // we have our own button

        if (hasLocationPermission()) enableMyLocationDot();

        map.setOnMarkerClickListener(marker -> {
            Object tag = marker.getTag();
            if (tag instanceof Spot) openDetails((Spot) tag);
            return true;
        });

        // The listener runs again whenever a spot is added, changed or deleted,
        // so the map always matches the database.
        spotSubscription = SpotRepository.listenToSpots(this, this::showSpots, this::onSpotsError);
    }

    /** Makes the markers match the given spots: add new ones, update changed, remove gone. */
    private void showSpots(ArrayList<Spot> spots) {
        loadErrorShown = false;
        Set<String> currentIds = new HashSet<>();

        for (Spot spot : spots) {
            if (spot.getId() == null) continue;
            currentIds.add(spot.getId());

            LatLng position = new LatLng(spot.getLatitude(), spot.getLongitude());
            Marker marker = markers.get(spot.getId());
            if (marker == null) {
                marker = googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(spot.getName())
                        .icon(iconFor(spot.getCategory())));
                if (marker == null) continue;
                markers.put(spot.getId(), marker);
            } else {
                marker.setPosition(position);
                marker.setTitle(spot.getName());
                marker.setIcon(iconFor(spot.getCategory()));
            }
            marker.setTag(spot);
            marker.setVisible(matchesFilter(spot));
        }

        // Remove the markers of spots that were deleted or made private
        Iterator<Map.Entry<String, Marker>> iterator = markers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Marker> entry = iterator.next();
            if (!currentIds.contains(entry.getKey())) {
                entry.getValue().remove();
                iterator.remove();
            }
        }
        refreshNearby();
    }

    private void onSpotsError(String message) {
        if (loadErrorShown) return; // the listener can fail repeatedly while offline
        loadErrorShown = true;
        toast(message + " Check your internet connection.");
    }

    // ------------------------------------------------------------------ filter

    private boolean matchesFilter(Spot spot) {
        return selectedCategories.isEmpty()
                || selectedCategories.contains(spot.getCategory().toLowerCase(Locale.ROOT));
    }

    private void applyFilter() {
        for (Marker marker : markers.values()) {
            Object tag = marker.getTag();
            if (tag instanceof Spot) marker.setVisible(matchesFilter((Spot) tag));
        }
        refreshNearby();
    }

    private BitmapDescriptor iconFor(String category) {
        int resId = R.drawable.othersmarker;
        if ("Food".equalsIgnoreCase(category)) resId = R.drawable.foodmarker;
        else if ("Tourist Spot".equalsIgnoreCase(category)) resId = R.drawable.touristmarker;
        else if ("Hang Out".equalsIgnoreCase(category)) resId = R.drawable.hangoutmarker;

        BitmapDescriptor icon = icons.get(resId);
        if (icon == null) {
            Bitmap original = BitmapFactory.decodeResource(getResources(), resId);
            icon = BitmapDescriptorFactory.fromBitmap(Bitmap.createScaledBitmap(original, 100, 120, false));
            icons.put(resId, icon);
        }
        return icon;
    }

    // ------------------------------------------------------ my location / nearby

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void onMyLocationClicked() {
        if (hasLocationPermission()) {
            showMyLocation();
            return;
        }
        // Explain why we ask before showing the system permission dialog
        new AlertDialog.Builder(this)
                .setTitle("Allow location?")
                .setMessage("SpotSnap uses your location to show where you are on the map and to "
                        + "find spots near you. Your location is not shared with other users.")
                .setPositiveButton("Continue", (dialog, which) -> locationPermissionLauncher.launch(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION}))
                .setNegativeButton("Not now", null)
                .show();
    }

    @SuppressLint("MissingPermission") // only called after the permission check
    private void enableMyLocationDot() {
        googleMap.setMyLocationEnabled(true);
    }

    @SuppressLint("MissingPermission") // only called after the permission check
    private void showMyLocation() {
        if (googleMap == null) return; // the map is still loading
        enableMyLocationDot();

        View button = findViewById(R.id.btn_my_location);
        button.setEnabled(false); // avoid several requests while we wait for the GPS
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        button.setEnabled(true);
                        onLocationFound(location);
                    } else {
                        useLastKnownLocation(button);
                    }
                })
                .addOnFailureListener(e -> useLastKnownLocation(button));
    }

    @SuppressLint("MissingPermission")
    private void useLastKnownLocation(View button) {
        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    button.setEnabled(true);
                    if (location != null) onLocationFound(location);
                    else toast("Could not find your location. Make sure location is turned on.");
                })
                .addOnFailureListener(e -> {
                    button.setEnabled(true);
                    toast("Could not find your location. Make sure location is turned on.");
                });
    }

    private void onLocationFound(Location location) {
        lastLocation = location;
        nearbyVisible = true;
        refreshNearby();

        // Slide the list up (if it is hidden) and keep the user's position above it
        setMapBottomPadding(nearbySheet.getPeekHeight());
        if (nearbySheet.getState() == BottomSheetBehavior.STATE_HIDDEN) {
            nearbySheet.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(location.getLatitude(), location.getLongitude()), LOCATION_ZOOM));
    }

    /** Rebuilds the nearby list: visible spots within the radius, closest first. */
    private void refreshNearby() {
        if (!nearbyVisible || lastLocation == null) return;

        List<NearbyAdapter.Item> items = new ArrayList<>();
        float[] distance = new float[1];
        for (Marker marker : markers.values()) {
            Object tag = marker.getTag();
            if (!(tag instanceof Spot)) continue;
            Spot spot = (Spot) tag;
            if (!matchesFilter(spot)) continue;

            Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
                    spot.getLatitude(), spot.getLongitude(), distance);
            if (distance[0] <= NEARBY_RADIUS_METERS) items.add(new NearbyAdapter.Item(spot, distance[0]));
        }
        Collections.sort(items, (a, b) -> Float.compare(a.meters, b.meters));
        if (items.size() > MAX_NEARBY_SPOTS) items = new ArrayList<>(items.subList(0, MAX_NEARBY_SPOTS));

        nearbyAdapter.setItems(items);

        int km = Math.round(NEARBY_RADIUS_METERS / 1000f);
        String subtitle = items.isEmpty()
                ? "Nothing within " + km + " km yet"
                : items.size() + (items.size() == 1 ? " place" : " places") + " within " + km + " km";
        ((TextView) findViewById(R.id.nearby_subtitle)).setText(subtitle);
        findViewById(R.id.nearby_empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.nearby_list).setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ helpers

    /** Tapping a nearby row: move the map to that spot, then open its details. */
    private void onNearbySpotClicked(Spot spot) {
        if (googleMap != null) {
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(spot.getLatitude(), spot.getLongitude()), LOCATION_ZOOM + 1));
        }
        openDetails(spot);
    }

    /** Opens Place Details; it loads the spot's real pictures from Firestore itself. */
    private void openDetails(Spot spot) {
        PlaceDetailsFragment fragment = PlaceDetailsFragment.newInstance(
                spot.getName(), spot.getCategory(), spot.getReview());
        fragment.setSpot(spot);
        DrawerMenu.open(this, fragment);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}