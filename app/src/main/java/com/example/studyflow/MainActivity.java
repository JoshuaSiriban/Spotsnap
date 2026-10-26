package com.example.studyflow;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

/** OPTIONAL: only use this if you don't already have a MainActivity with a drawer. */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        DrawerMenu.setup(this, drawer);

        findViewById(R.id.btn_menu).setOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        findViewById(R.id.btn_add).setOnClickListener(v -> DrawerMenu.open(this, new AddSpotFragment()));
        findViewById(R.id.btn_place).setOnClickListener(v ->
                DrawerMenu.open(this, PlaceDetailsFragment.newInstance(null, null, null)));
    }
}