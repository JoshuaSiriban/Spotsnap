package com.example.studyflow;

import android.content.Intent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

public class DrawerMenu {

    public static final int CONTAINER_ID = R.id.fragment_container;

    public static void open(FragmentActivity activity, Fragment fragment) {
        activity.findViewById(CONTAINER_ID).setVisibility(View.VISIBLE);
        View overlay = activity.findViewById(R.id.map_overlay);
        if (overlay != null) overlay.setVisibility(View.GONE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .replace(CONTAINER_ID, fragment)
                .addToBackStack(null)
                .commit();
    }

    public static void setup(AppCompatActivity activity, DrawerLayout drawer) {
        bind(activity, drawer, R.id.drawer_saved, new SavedLocationsFragment());
        bind(activity, drawer, R.id.drawer_album, new AlbumFragment());
        bind(activity, drawer, R.id.drawer_search, new SearchFragment());
        bind(activity, drawer, R.id.drawer_profile, new ProfileFragment());
        bind(activity, drawer, R.id.drawer_settings, new SettingsFragment());

        drawer.findViewById(R.id.btn_close_drawer).setOnClickListener(v ->
                drawer.closeDrawer(GravityCompat.START));

        drawer.findViewById(R.id.drawer_logout).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            Toast.makeText(activity, "Logged out (UI only)", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(activity, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK); // ensure user cant go back
            activity.startActivity(intent);
        });

        OnBackPressedCallback callback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                drawer.closeDrawer(GravityCompat.START);
            }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, callback);
        drawer.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override public void onDrawerOpened(View v) { callback.setEnabled(true); }
            @Override public void onDrawerClosed(View v) { callback.setEnabled(false); }
        });
    }

    private static void bind(AppCompatActivity activity, DrawerLayout drawer, int id, Fragment fragment) {
        drawer.findViewById(id).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            open(activity, fragment);
        });
    }
}
