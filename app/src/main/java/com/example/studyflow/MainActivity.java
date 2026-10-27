package com.example.studyflow;

import android.os.Bundle;
import android.view.View;

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
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        findViewById(R.id.btn_profile).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.search_bar).setOnClickListener(v -> DrawerMenu.open(this, new SearchFragment()));
        findViewById(R.id.btn_add).setOnClickListener(v -> DrawerMenu.open(this, new AddSpotFragment()));

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);

        mapFragment.getMapAsync(this);
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
    public void onMapReady(GoogleMap googleMap)
    {
        LatLng manila = new LatLng(14.5995, 120.9842);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(manila, 12));

        ArrayList<Photo> photos = new ArrayList<>();
        photos.add(new Photo(R.drawable.dlsu, "La Salle Building in its full glory"));
        photos.add(new Photo(R.drawable.dlsu, "Cool place"));

        Spot dlsu = new Spot("De La Salle University - Taft", "Jerimaya", "I love and hate this place", "School", 14.5647, 120.9934, photos, 0, 0);
        Marker marker = googleMap.addMarker(new MarkerOptions().position(new LatLng(dlsu.getLatitude(), dlsu.getLongitude())).title(dlsu.getName()));

        Spot cafe = new Spot("The Coffee Academïcs BGC", "Jerimaya", "A nice place to enjoy coffee and relax.", "Cafe", 14.5547, 121.0465, photos, 0, 0);

        Marker cafeMarker = googleMap.addMarker(new MarkerOptions().position(new LatLng(cafe.getLatitude(), cafe.getLongitude())).title(cafe.getName()));

        if (cafeMarker != null) {
            cafeMarker.setTag(cafe);
        }

        if (marker != null)
        {
            marker.setTag(dlsu);
        }

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