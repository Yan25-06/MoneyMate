package com.group10.moneymate.ui.main;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ActivityHomeBinding;

/**
 * Main activity after login. Hosts bottom navigation and NavHostFragment.
 */
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();

            NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (id == R.id.homeFragment ||
                        id == R.id.transactionListFragment ||
                        id == R.id.budgetListFragment ||
                        id == R.id.settingsFragment) {
                    binding.bottomNavigation.setVisibility(View.VISIBLE);
                } else {
                    binding.bottomNavigation.setVisibility(View.GONE);
                }
            });
        }
    }
}
