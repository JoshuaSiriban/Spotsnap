package com.example.studyflow;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.maps.android.clustering.ClusterItem;
import com.google.maps.android.clustering.ClusterManager;
import com.google.maps.android.clustering.view.DefaultClusterRenderer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The map screen. Every public spot (plus the logged in user's private ones) comes live from
 * Firestore and is drawn on the map. Spots that are close together are grouped into a numbered
 * circle (a cluster). The category chips filter the spots, the layers button switches between the
 * normal and the satellite map, and the "my location" button centres the map on the user and
 * lists nearby spots.
 */
public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final LatLng DEFAULT_CENTER = new LatLng(14.5995, 120.9842); // Manila
    private static final float DEFAULT_ZOOM = 12f;
    private static final float LOCATION_ZOOM = 15f;
    private static final float NEARBY_RADIUS_METERS = 5000f;
    private static final int MAX_NEARBY_SPOTS = 15;

    // Sorting of the nearby list
    private static final int SORT_CLOSEST = 0;
    private static final int SORT_NEWEST = 1;
    private static final int SORT_POPULAR = 2;     // most liked
    private static final int SORT_TOP_RATED = 3;   // best rated
    private int nearbySort = SORT_CLOSEST;

    private GoogleMap googleMap;
    private SpotRepository.Subscription spotSubscription; // live updates of the spots from Firestore

    private final Map<String, Spot> allSpots = new LinkedHashMap<>();       // spot id -> spot (everything we know)
    private final Map<String, SpotItem> clusterItems = new HashMap<>();     // spot id -> item given to the cluster manager
    private final Map<Integer, BitmapDescriptor> icons = new HashMap<>();   // marker picture -> icon
    private final Set<String> selectedCategories = new HashSet<>();         // lower case; empty = all
    private ClusterManager<SpotItem> clusterManager;
    private SpotRenderer spotRenderer;
    private boolean satellite;                                              // map type: satellite or normal
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loadErrorShown;
    private Set<String> focusIds;       // when not null, the map only shows these spots (by id)
    private boolean followingOnly;      // the "Following" chip is on: only spots of people you follow
    private Set<String> followingIds;   // uids of the people you follow (null until loaded)
    private FusedLocationProviderClient locationClient;
    private Location lastLocation;     // where the user was when they last tapped "my location"
    private boolean nearbyVisible;
    private NearbyAdapter nearbyAdapter;
    private BottomSheetBehavior<View> nearbySheet;   // the slide-up list of nearby spots
    private View mapButtons;                         // map type / my location / crosshair / add buttons
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
        findViewById(R.id.btn_clear_focus).setOnClickListener(v -> clearFocus());
        setupNearbySheet();
        findViewById(R.id.btn_my_location).setOnClickListener(v -> onMyLocationClicked());
        findViewById(R.id.btn_map_type).setOnClickListener(v -> toggleMapType());

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

    /**
     * No chip selected = every spot. Selected category chips = only spots of those categories.
     * The "Following" chip = only spots of the people you follow (it combines with the categories).
     */
    private void setupFilterChips() {
        ChipGroup group = findViewById(R.id.filter_group);
        group.setOnCheckedStateChangeListener((chipGroup, checkedIds) -> {
            boolean wasFollowingOnly = followingOnly;
            followingOnly = false;
            selectedCategories.clear();
            for (int id : checkedIds) {
                if (id == R.id.filter_following) {
                    followingOnly = true;
                    continue;
                }
                Chip chip = chipGroup.findViewById(id);
                if (chip != null) {
                    selectedCategories.add(chip.getText().toString().toLowerCase(Locale.ROOT));
                }
            }
            if (followingOnly && !wasFollowingOnly) loadFollowingForFilter();
            else applyFilter();
        });
    }

    /** Reads who the user follows, then updates the markers. Called when the chip is switched on. */
    private void loadFollowingForFilter() {
        applyFilter(); // right away, with the list we already have (nothing yet = hidden)
        SocialRepository.loadFollowingIds(ids -> {
            if (isDestroyed() || !followingOnly) return;   // closed, or the chip was switched off
            if (ids == null) {
                if (followingIds == null) {                // nothing to fall back on
                    toast("Could not load who you follow. Check your internet connection.");
                    ((Chip) findViewById(R.id.filter_following)).setChecked(false);
                }
                return;                                    // otherwise keep the list we already have
            }
            boolean firstTime = followingIds == null || !followingIds.equals(ids);
            followingIds = ids;
            applyFilter();
            if (ids.isEmpty() && firstTime) {
                toast("You are not following anyone yet. Search for people or open a place and tap Follow.");
            }
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

        // Closest / Newest / Most liked / Top rated
        ChipGroup sortGroup = findViewById(R.id.nearby_sort_group);
        sortGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            int id = checkedIds.get(0);
            nearbySort = id == R.id.sort_newest ? SORT_NEWEST
                    : id == R.id.sort_popular ? SORT_POPULAR
                      : id == R.id.sort_top_rated ? SORT_TOP_RATED : SORT_CLOSEST;
            refreshNearby();
        });
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
        if (onMap) refreshActivityBadge();
        // You may have followed or unfollowed someone on another screen
        if (onMap && followingOnly && googleMap != null) loadFollowingForFilter();
    }

    /** Shows how many new likes / followers there are, as a small badge on the "Activity" menu row. */
    private void refreshActivityBadge() {
        NotificationRepository.countUnread(count -> {
            if (isDestroyed()) return;
            TextView badge = findViewById(R.id.drawer_activity_badge);
            if (badge == null) return;
            badge.setText(count > 99 ? "99+" : String.valueOf(count));
            badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UserSession.isLoggedIn()) updateMapUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (spotSubscription != null) spotSubscription.remove(); // stop listening to Firestore
    }

    // ---------------------------------------------------------------------- map

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(DEFAULT_CENTER, DEFAULT_ZOOM));
        map.getUiSettings().setMyLocationButtonEnabled(false); // we have our own button

        if (hasLocationPermission()) enableMyLocationDot();

        satellite = AppSettings.useSatellite(this);
        applyMapType();

        // Spots that are close together are grouped into one numbered circle
        clusterManager = new ClusterManager<>(this, map);
        spotRenderer = new SpotRenderer(this, map, clusterManager);
        clusterManager.setRenderer(spotRenderer);
        clusterManager.setOnClusterItemClickListener(item -> {
            Spot latest = allSpots.get(item.spot.getId());   // the newest data (likes, ratings...)
            openDetails(latest != null ? latest : item.spot);
            return true;
        });
        clusterManager.setOnClusterClickListener(cluster -> {
            // tap a circle: zoom in so the spots inside it spread out
            LatLngBounds.Builder bounds = new LatLngBounds.Builder();
            for (SpotItem item : cluster.getItems()) bounds.include(item.getPosition());
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 150));
            } catch (IllegalStateException e) {
                googleMap.animateCamera(CameraUpdateFactory.zoomIn());
            }
            return true;
        });
        map.setOnCameraIdleListener(clusterManager);
        map.setOnMarkerClickListener(clusterManager);

        // The listener runs again whenever a spot is added, changed or deleted,
        // so the map always matches the database.
        spotSubscription = SpotRepository.listenToSpots(this, this::showSpots, this::onSpotsError);
    }

    // -------------------------------------------------------------- map type

    private void toggleMapType() {
        if (googleMap == null) return;
        satellite = !satellite;
        AppSettings.setUseSatellite(this, satellite);
        applyMapType();
        Toast.makeText(this, satellite ? "Satellite view" : "Map view", Toast.LENGTH_SHORT).show();
    }

    /** Satellite uses the "hybrid" type, which keeps the street and place labels on top. */
    private void applyMapType() {
        if (googleMap == null) return;
        googleMap.setMapType(satellite ? GoogleMap.MAP_TYPE_HYBRID : GoogleMap.MAP_TYPE_NORMAL);
    }

    // -------------------------------------------------------------- clustering

    /** One spot as the cluster manager sees it. */
    private static class SpotItem implements ClusterItem {
        final Spot spot;
        private final LatLng position;
        private final String snippet;   // "★ 4.5 (12)" shown in the name bubble, or null

        SpotItem(Spot spot) {
            this.spot = spot;
            this.position = new LatLng(spot.getLatitude(), spot.getLongitude());
            String label = RatingUtils.label(spot);
            this.snippet = label.isEmpty() ? null : label;
        }

        @NonNull
        @Override
        public LatLng getPosition() {
            return position;
        }

        @Nullable
        @Override
        public String getTitle() {
            return spot.getName();
        }

        @Nullable
        @Override
        public String getSnippet() {
            return snippet;
        }

        @Nullable
        public Float getZIndex() {
            return null;
        }

        // Two items that look the same are "equal", so the map keeps their marker
        // instead of removing and drawing it again every time Firestore sends an update.
        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof SpotItem)) return false;
            SpotItem o = (SpotItem) other;
            return Objects.equals(spot.getId(), o.spot.getId())
                    && position.equals(o.position)
                    && Objects.equals(spot.getName(), o.spot.getName())
                    && Objects.equals(spot.getCategory(), o.spot.getCategory())
                    && Objects.equals(snippet, o.snippet);
        }

        @Override
        public int hashCode() {
            return Objects.hash(spot.getId(), position, spot.getName(), spot.getCategory(), snippet);
        }
    }

    /** Draws single spots with the category pictures. Groups use the default numbered circle. */
    private class SpotRenderer extends DefaultClusterRenderer<SpotItem> {

        SpotRenderer(Context context, GoogleMap map, ClusterManager<SpotItem> manager) {
            super(context, map, manager);
        }

        @Override
        protected void onBeforeClusterItemRendered(@NonNull SpotItem item, @NonNull MarkerOptions options) {
            options.icon(iconFor(item.spot.getCategory()))
                    .title(item.getTitle())
                    .snippet(item.getSnippet());
        }

        @Override
        protected void onClusterItemUpdated(@NonNull SpotItem item, @NonNull Marker marker) {
            marker.setIcon(iconFor(item.spot.getCategory()));
            marker.setTitle(item.getTitle());
            marker.setSnippet(item.getSnippet());
        }
    }

    /** Gives the cluster manager the spots that pass the filters, and lets it regroup them. */
    private void rebuildClusters() {
        if (clusterManager == null) return;
        clusterManager.clearItems();
        clusterItems.clear();
        for (Spot spot : allSpots.values()) {
            if (!matchesFilter(spot)) continue;
            SpotItem item = new SpotItem(spot);
            clusterItems.put(spot.getId(), item);
            clusterManager.addItem(item);
        }
        clusterManager.cluster();
    }

    /** Remembers the given spots (new, changed or gone) and redraws the map. */
    private void showSpots(ArrayList<Spot> spots) {
        loadErrorShown = false;
        Set<String> currentIds = new HashSet<>();
        for (Spot spot : spots) {
            if (spot.getId() == null) continue;
            currentIds.add(spot.getId());
            allSpots.put(spot.getId(), spot);
        }
        // Forget the spots that were deleted or made private
        allSpots.keySet().retainAll(currentIds);

        rebuildClusters();
        refreshNearby();
    }

    private void onSpotsError(String message) {
        if (loadErrorShown) return; // the listener can fail repeatedly while offline
        loadErrorShown = true;
        toast(message + " Check your internet connection.");
    }

    // ------------------------------------------------------------------ filter

    private boolean matchesFilter(Spot spot) {
        if (focusIds != null && !focusIds.contains(spot.getId())) return false;
        if (followingOnly && (followingIds == null || !followingIds.contains(spot.getCreatorId()))) {
            return false;
        }
        return selectedCategories.isEmpty()
                || selectedCategories.contains(spot.getCategory().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------- "show on map" for Album, Saved, Search, Profile

    /** Leaves any open screen, centres the map on one spot and opens its name bubble. */
    public void focusOnSpot(Spot spot) {
        if (googleMap == null) return;
        showMapScreen();
        clearFocus();
        ((ChipGroup) findViewById(R.id.filter_group)).clearCheck(); // a category chip must not hide it

        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(spot.getLatitude(), spot.getLongitude()), LOCATION_ZOOM + 2));

        // Once the map has zoomed in (and the cluster has opened up), show the name bubble
        final String id = spot.getId();
        handler.postDelayed(() -> showBubble(id), 1000);
    }

    private void showBubble(String spotId) {
        if (spotId == null || spotRenderer == null) return;
        SpotItem item = clusterItems.get(spotId);
        if (item == null) return;
        Marker marker = spotRenderer.getMarker(item);
        if (marker != null) marker.showInfoWindow();
    }

    /** Leaves any open screen and makes the map show only these spots, zoomed to fit them. */
    public void showSpotsOnMap(List<Spot> spots, String label) {
        if (googleMap == null || spots.isEmpty()) return;
        showMapScreen();
        ((ChipGroup) findViewById(R.id.filter_group)).clearCheck();

        focusIds = new HashSet<>();
        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        for (Spot spot : spots) {
            focusIds.add(spot.getId());
            bounds.include(new LatLng(spot.getLatitude(), spot.getLongitude()));
        }
        applyFilter();

        ((TextView) findViewById(R.id.focus_label)).setText(label + " (" + spots.size() + ")");
        findViewById(R.id.focus_banner).setVisibility(View.VISIBLE);

        if (spots.size() == 1) {
            Spot only = spots.get(0);
            googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(only.getLatitude(), only.getLongitude()), LOCATION_ZOOM + 1));
        } else {
            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), 140));
            } catch (IllegalStateException e) {
                // the map has no size yet: use the first spot instead
                Spot first = spots.get(0);
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                        new LatLng(first.getLatitude(), first.getLongitude()), DEFAULT_ZOOM));
            }
        }
    }

    /** Back to showing every spot (the "Show all" button on the banner). */
    private void clearFocus() {
        if (focusIds == null) return;
        focusIds = null;
        findViewById(R.id.focus_banner).setVisibility(View.GONE);
        applyFilter();
    }

    private void showMapScreen() {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private void applyFilter() {
        rebuildClusters();
        refreshNearby();
    }

    /** Restaurants, Tourist Attractions and Hangouts have their own marker picture; Caf\u00E9s and
     *  Parks reuse one and are tinted (brown / green); Other uses the plain marker. */
    private BitmapDescriptor iconFor(String rawCategory) {
        String category = Categories.normalize(rawCategory);
        int resId;
        switch (category) {
            case Categories.RESTAURANTS: case Categories.CAFES: resId = R.drawable.foodmarker; break;
            case Categories.TOURIST: resId = R.drawable.touristmarker; break;
            case Categories.HANGOUTS: resId = R.drawable.hangoutmarker; break;
            default: resId = R.drawable.othersmarker;
        }
        int tint = Categories.tintFor(category);
        int key = resId * 31 + tint;

        BitmapDescriptor icon = icons.get(key);
        if (icon == null) {
            Bitmap original = BitmapFactory.decodeResource(getResources(), resId);
            Bitmap scaled = Bitmap.createScaledBitmap(original, 100, 120, false);
            if (tint != 0) {
                Bitmap tinted = scaled.copy(Bitmap.Config.ARGB_8888, true);
                android.graphics.Canvas canvas = new android.graphics.Canvas(tinted);
                android.graphics.Paint paint = new android.graphics.Paint();
                paint.setColorFilter(new android.graphics.PorterDuffColorFilter(
                        tint, android.graphics.PorterDuff.Mode.MULTIPLY));
                canvas.drawBitmap(scaled, 0, 0, paint);
                scaled = tinted;
            }
            icon = BitmapDescriptorFactory.fromBitmap(scaled);
            icons.put(key, icon);
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

    /** Rebuilds the nearby list: visible spots within the radius, sorted by the chosen chip. */
    private void refreshNearby() {
        if (!nearbyVisible || lastLocation == null) return;

        List<NearbyAdapter.Item> items = new ArrayList<>();
        float[] distance = new float[1];
        for (Spot spot : allSpots.values()) {
            if (!matchesFilter(spot)) continue;
            if (nearbySort == SORT_TOP_RATED && !spot.hasRatings()) continue;   // nothing to rank yet

            Location.distanceBetween(lastLocation.getLatitude(), lastLocation.getLongitude(),
                    spot.getLatitude(), spot.getLongitude(), distance);
            if (distance[0] <= NEARBY_RADIUS_METERS) items.add(new NearbyAdapter.Item(spot, distance[0]));
        }
        Collections.sort(items, nearbyComparator());
        if (items.size() > MAX_NEARBY_SPOTS) items = new ArrayList<>(items.subList(0, MAX_NEARBY_SPOTS));

        nearbyAdapter.setItems(items);

        String title = nearbySort == SORT_NEWEST ? "Newest nearby"
                : nearbySort == SORT_POPULAR ? "Popular nearby"
                  : nearbySort == SORT_TOP_RATED ? "Top rated nearby" : "Nearby spots";
        ((TextView) findViewById(R.id.nearby_title)).setText(title);

        String radius = AppSettings.formatRadius(this, NEARBY_RADIUS_METERS);
        String subtitle;
        if (items.isEmpty()) {
            subtitle = nearbySort == SORT_TOP_RATED
                    ? "No rated places within " + radius + " yet"
                    : "Nothing within " + radius + " yet";
        } else {
            subtitle = items.size() + (items.size() == 1 ? " place" : " places") + " within " + radius;
        }
        ((TextView) findViewById(R.id.nearby_subtitle)).setText(subtitle);

        TextView empty = findViewById(R.id.nearby_empty);
        empty.setText(nearbySort == SORT_TOP_RATED
                ? "No rated places nearby yet. Open a place and leave the first rating!"
                : "No spots nearby yet. Tap + to pin the first one!");
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        findViewById(R.id.nearby_list).setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** Closest = nearest first. Newest = recently pinned. Most liked = most likes. Top rated = best weighted rating. */
    private Comparator<NearbyAdapter.Item> nearbyComparator() {
        switch (nearbySort) {
            case SORT_NEWEST:
                return (a, b) -> {
                    int byDate = Long.compare(b.spot.getCreatedAt(), a.spot.getCreatedAt());
                    return byDate != 0 ? byDate : Float.compare(a.meters, b.meters);
                };
            case SORT_POPULAR:
                return (a, b) -> {
                    int byLikes = Integer.compare(b.spot.getLikes(), a.spot.getLikes());
                    if (byLikes != 0) return byLikes;
                    int byRating = Double.compare(b.spot.getRankingScore(), a.spot.getRankingScore());
                    if (byRating != 0) return byRating;
                    return Float.compare(a.meters, b.meters);
                };
            case SORT_TOP_RATED:
                return (a, b) -> {
                    int byRating = Double.compare(b.spot.getRankingScore(), a.spot.getRankingScore());
                    if (byRating != 0) return byRating;
                    int byCount = Integer.compare(b.spot.getRatingCount(), a.spot.getRatingCount());
                    if (byCount != 0) return byCount;
                    return Float.compare(a.meters, b.meters);
                };
            default:
                return (a, b) -> Float.compare(a.meters, b.meters);
        }
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