package com.hfc.myetrans;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.hfc.myetrans.databinding.ActivityMainMenuBinding;

public class MainMenuActivity extends AppCompatActivity {
    ActivityMainMenuBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainMenuBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Set default fragment
        replaceFragment(new HomeFragment());

        // Set up BottomNavigationView
        binding.bottomNavigationView.setOnItemSelectedListener(item -> {
            Fragment selectedFragment;
            int itemId = item.getItemId();
            if (itemId == R.id.dashboard) {
                selectedFragment = new HomeFragment();
            } else if (itemId == R.id.power) {
                selectedFragment = new PowerFragment();
            } else if (itemId == R.id.maps) {
                selectedFragment = new MapsFragment();
             }  else {
                return false;
            }
            replaceFragment(selectedFragment);
            return true;
        });
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.frame_layout, fragment)
                .commit();
    }
}

