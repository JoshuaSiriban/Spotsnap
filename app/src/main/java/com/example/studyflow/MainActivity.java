package com.example.studyflow;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private SpotRepository.Subscription spotSubscription; // live updates of the spots from Firestore

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

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        DrawerMenu.setup(this, drawer);

        // Profile missing locally (fresh install, or it could not load at login): fetch it now.
        if (!UserSession.hasProfile(this)) {
            AuthRepository.loadProfile(this, () -> DrawerMenu.bindUser(drawer));
        }

        findViewById(R.id.btn_profile).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.search_bar).setOnClickListener(v -> DrawerMenu.open(this, new SearchFragment()));
        findViewById(R.id.btn_add).setOnClickListener(v -> DrawerMenu.open(this, new AddSpotFragment()));

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);

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

    private void updateMapUi() {
        boolean onMap = getSupportFragmentManager().getBackStackEntryCount() == 0;
        findViewById(R.id.fragment_container).setVisibility(onMap ? View.GONE : View.VISIBLE);
        findViewById(R.id.map_overlay).setVisibility(onMap ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateMapUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (spotSubscription != null) spotSubscription.remove(); // stop listening to Firestore
    }

    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        String owner = UserSession.getUsername(this); // sample spots below belong to the logged-in user
        LatLng manila = new LatLng(14.5995, 120.9842);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(manila, 12));

        ArrayList<Photo> photos = new ArrayList<>();
        photos.add(new Photo(R.drawable.dlsu, "La Salle Building in its full glory"));
        photos.add(new Photo(R.drawable.dlsu2, "Henry Building"));

        ArrayList<Photo> cafePhotos = new ArrayList<>();
        cafePhotos.add(new Photo(R.drawable.cafe1, "Love their coffee"));

        ArrayList<Photo> rooftopPhotos = new ArrayList<>();
        rooftopPhotos.add(new Photo(R.drawable.rooftop1, "Unmatched Atmosphere"));
        rooftopPhotos.add(new Photo(R.drawable.rooftop2, "Ganda ng viewww"));

        ArrayList<Photo> intramurosPhotos = new ArrayList<>();
        intramurosPhotos.add(new Photo(R.drawable.intramuros1, ""));
        intramurosPhotos.add(new Photo(R.drawable.intramuros2, "History in front of me"));
        intramurosPhotos.add(new Photo(R.drawable.intramuros3, "..."));

        ArrayList<Photo> ramenPhotos = new ArrayList<>();
        ramenPhotos.add(new Photo(R.drawable.ramen1, "I love ramen talaga"));

        ArrayList<Photo> venicePhotos = new ArrayList<>();
        venicePhotos.add(new Photo(R.drawable.venice1, "Parang sa Italy"));
        venicePhotos.add(new Photo(R.drawable.venice2, ".."));

        Bitmap othersBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.othersmarker);
        Bitmap smallOthersBitmap = Bitmap.createScaledBitmap(othersBitmap, 100, 120, false);

        Bitmap foodBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.foodmarker);
        Bitmap smallFoodBitmap = Bitmap.createScaledBitmap(foodBitmap, 100, 120, false);

        Bitmap touristBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.touristmarker);
        Bitmap smallTouristBitmap = Bitmap.createScaledBitmap(touristBitmap, 100, 120, false);

        Bitmap hangoutBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.hangoutmarker);
        Bitmap smallHangoutBitmap = Bitmap.createScaledBitmap(hangoutBitmap, 100, 120, false);

        Spot dlsu = new Spot("De La Salle University - Taft", owner, "Sobrang lakas ng aircon!!", "Others", 14.5647, 120.9934, photos, 0, 0);
        Marker marker = googleMap.addMarker(new MarkerOptions().position(new LatLng(dlsu.getLatitude(), dlsu.getLongitude())).title(dlsu.getName()).icon(BitmapDescriptorFactory.fromBitmap(smallOthersBitmap)));
        marker.setTag(dlsu);

        Spot cafe = new Spot("Cafe Ocampo", owner, "A nice place to enjoy coffee and relax", "Food", 14.56482, 120.99968, cafePhotos, 0, 0);
        Marker cafeMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(cafe.getLatitude(), cafe.getLongitude())).title(cafe.getName()).icon(BitmapDescriptorFactory.fromBitmap(smallFoodBitmap)));
        cafeMarker.setTag(cafe);

        Spot rooftop = new Spot("Rooftop Lounge Makati", owner, "A rooftop lounge with views of the Makati skyline", "Food", 14.56469, 121.02925, rooftopPhotos, 0, 0);
        Marker rooftopMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(rooftop.getLatitude(), rooftop.getLongitude())).title(rooftop.getName()).icon(BitmapDescriptorFactory.fromBitmap(smallFoodBitmap)));
        rooftopMarker.setTag(rooftop);

        Spot intramuros = new Spot("Intramuros Walls", owner, "Medjo scary yung haunted area", "Tourist Spot", 14.583333, 120.983333, intramurosPhotos, 0, 0);
        Marker intramurosMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(intramuros.getLatitude(), intramuros.getLongitude())).title(intramuros.getName()).icon(BitmapDescriptorFactory.fromBitmap(smallTouristBitmap)));
        intramurosMarker.setTag(intramuros);

        Spot ramen = new Spot("Ramen Corner Quezon City", owner, "A nice place to enjoy a warm bowl of ramen", "Food", 14.6565, 121.0325, ramenPhotos, 0, 0);
        Marker ramenMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(ramen.getLatitude(), ramen.getLongitude())).title(ramen.getName()).icon(BitmapDescriptorFactory.fromBitmap(smallFoodBitmap)));
        ramenMarker.setTag(ramen);

        Spot venice = new Spot("Venice Grand Canal Mall", owner, "A popular hangout spot for food, shopping, and taking pictures by the canal.", "Hang Out", 14.5337, 121.0565, venicePhotos, 0, 0);
        Marker veniceMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(venice.getLatitude(), venice.getLongitude())).title(venice.getName()).icon(BitmapDescriptorFactory.fromBitmap(smallHangoutBitmap)));
        veniceMarker.setTag(venice);

        ArrayList<Spot> localSpots = new ArrayList<>(Arrays.asList(dlsu, cafe, rooftop, intramuros, ramen, venice));
        SpotRepository.seedIfEmpty(this, localSpots);

        // The sample spots are saved in Firestore with an id made from their name.
        // Those ids are skipped below so the same place is not drawn twice.
        Set<String> sampleIds = new HashSet<>();
        for (Spot local : localSpots) {
            sampleIds.add(local.getName().toLowerCase().replaceAll("[^a-z0-9]+", "-"));
        }

        // Spots pinned by users: every public spot plus the logged in user's private ones.
        // The listener runs again whenever a spot is added, so a new pin shows up right away.
        Map<String, Marker> userMarkers = new HashMap<>();
        spotSubscription = SpotRepository.listenToSpots(this, remoteSpots -> {
            Set<String> currentIds = new HashSet<>();

            for (Spot remote : remoteSpots) {
                if (sampleIds.contains(remote.getId())) continue;
                currentIds.add(remote.getId());

                Marker existing = userMarkers.get(remote.getId());
                if (existing != null) {
                    existing.setTag(remote);
                    continue;
                }

                Bitmap icon = smallOthersBitmap;
                if ("Food".equals(remote.getCategory())) icon = smallFoodBitmap;
                else if ("Tourist Spot".equals(remote.getCategory())) icon = smallTouristBitmap;
                else if ("Hang Out".equals(remote.getCategory())) icon = smallHangoutBitmap;

                Marker m = googleMap.addMarker(new MarkerOptions()
                        .position(new LatLng(remote.getLatitude(), remote.getLongitude()))
                        .title(remote.getName())
                        .icon(BitmapDescriptorFactory.fromBitmap(icon)));
                if (m != null) {
                    m.setTag(remote);
                    userMarkers.put(remote.getId(), m);
                }
            }

            // Remove the markers of spots that were deleted or made private
            Iterator<Map.Entry<String, Marker>> iterator = userMarkers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Marker> entry = iterator.next();
                if (!currentIds.contains(entry.getKey())) {
                    entry.getValue().remove();
                    iterator.remove();
                }
            }
        });

        googleMap.setOnMarkerClickListener(clickedMarker -> {
            Spot spot = (Spot) clickedMarker.getTag();

            if (spot != null)
            {
                PlaceDetailsFragment fragment = PlaceDetailsFragment.newInstance(spot.getName(), spot.getCategory(), spot.getReview());
                fragment.setSpot(spot);

                DrawerMenu.open(this, fragment);
            }

            return true;
        });
    }
}