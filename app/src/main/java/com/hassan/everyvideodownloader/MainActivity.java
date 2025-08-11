package com.hassan.everyvideodownloader;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.hassan.everyvideodownloader.fragments.DownloadedFragment;
import com.hassan.everyvideodownloader.fragments.HomeFragment;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;
            if (item.getItemId() == R.id.nav_home) {
                selectedFragment = new HomeFragment();
            } else {
                selectedFragment = new DownloadedFragment();
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, selectedFragment)
                    .commit();
            return true;
        });
        bottomNav.setSelectedItemId(R.id.nav_home);

    }
}
