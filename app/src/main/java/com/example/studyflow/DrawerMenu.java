package com.example.studyflow;

import android.view.View;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/** Wires up the menu drawer and opens fragments. Call DrawerMenu.setup(this, drawer) in MainActivity. */
public class DrawerMenu {

    /** The FrameLayout in activity_main.xml that holds fragments. Change if your id is different. */
    public static final int CONTAINER_ID = R.id.fragment_container;

    public static void open(FragmentActivity activity, Fragment fragment) {
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

        drawer.findViewById(R.id.drawer_logout).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            Toast.makeText(activity, "Logged out (UI only)", Toast.LENGTH_SHORT).show();
        });

        // Back button closes the drawer first, only while the drawer is open
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