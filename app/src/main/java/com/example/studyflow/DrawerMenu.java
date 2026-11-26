package com.example.studyflow;

import android.view.View;
import android.widget.TextView;

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
        bind(activity, drawer, R.id.drawer_activity, ActivityFeedFragment::new);
        bind(activity, drawer, R.id.drawer_saved, SavedLocationsFragment::new);
        bind(activity, drawer, R.id.drawer_album, AlbumFragment::new);
        bind(activity, drawer, R.id.drawer_search, SearchFragment::new);
        bind(activity, drawer, R.id.drawer_profile, ProfileFragment::new);
        bind(activity, drawer, R.id.drawer_settings, SettingsFragment::new);

        drawer.findViewById(R.id.btn_close_drawer).setOnClickListener(v ->
                drawer.closeDrawer(GravityCompat.START));

        drawer.findViewById(R.id.drawer_logout).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("Log out?")
                    .setMessage("You will need to log in again to see your places.")
                    .setPositiveButton("Log out", (d, w) -> AuthRepository.logOut(activity))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        bindUser(drawer);

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

    /** Shows the logged-in user's name and username in the drawer header. */
    public static void bindUser(DrawerLayout drawer) {
        android.content.Context context = drawer.getContext();
        ((TextView) drawer.findViewById(R.id.drawer_name)).setText(UserSession.getDisplayName(context));
        ((TextView) drawer.findViewById(R.id.drawer_handle)).setText("@" + UserSession.getUsername(context));
    }

    /** Makes a brand new screen every time, so no old data or state is carried over. */
    private interface ScreenFactory {
        Fragment create();
    }

    private static void bind(AppCompatActivity activity, DrawerLayout drawer, int id, ScreenFactory factory) {
        drawer.findViewById(id).setOnClickListener(v -> {
            drawer.closeDrawer(GravityCompat.START);
            open(activity, factory.create());
        });
    }
}