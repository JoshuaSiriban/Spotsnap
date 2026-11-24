package com.example.studyflow;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Full screen map where the user places the pin of a new spot.
 * Tap the map or drag the pin, then press "Use this location".
 * The chosen position is sent back to Add Spot with the Fragment Result API.
 */
public class PickLocationFragment extends Fragment implements OnMapReadyCallback {

    public static final String RESULT_KEY = "pick_location";
    public static final String KEY_LAT = "lat";
    public static final String KEY_LNG = "lng";

    private static final String ARG_LAT = "start_lat";
    private static final String ARG_LNG = "start_lng";

    private Marker marker;
    private LatLng start;

    public PickLocationFragment() {
    }

    public static PickLocationFragment newInstance(double lat, double lng) {
        PickLocationFragment fragment = new PickLocationFragment();
        Bundle args = new Bundle();
        args.putDouble(ARG_LAT, lat);
        args.putDouble(ARG_LNG, lng);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_pick_location, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle args = getArguments();
        double lat = args == null ? 14.5995 : args.getDouble(ARG_LAT, 14.5995);
        double lng = args == null ? 120.9842 : args.getDouble(ARG_LNG, 120.9842);
        start = new LatLng(lat, lng);

        view.findViewById(R.id.btn_back).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        view.findViewById(R.id.btn_confirm_location).setOnClickListener(v -> {
            // If the map did not load, the starting position is returned unchanged
            LatLng chosen = marker != null ? marker.getPosition() : start;

            Bundle result = new Bundle();
            result.putDouble(KEY_LAT, chosen.latitude);
            result.putDouble(KEY_LNG, chosen.longitude);
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);

            requireActivity().getOnBackPressedDispatcher().onBackPressed();
        });

        // The map lives in a child fragment declared in fragment_pick_location.xml
        SupportMapFragment mapFragment = (SupportMapFragment)
                getChildFragmentManager().findFragmentById(R.id.pick_map);
        if (mapFragment != null) mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(start, 16));
        googleMap.getUiSettings().setZoomControlsEnabled(true);

        marker = googleMap.addMarker(new MarkerOptions()
                .position(start)
                .draggable(true)
                .title("Your spot"));

        // Tapping the map moves the pin there
        googleMap.setOnMapClickListener(latLng -> {
            if (marker != null) marker.setPosition(latLng);
        });
    }
}