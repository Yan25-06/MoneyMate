package com.group10.moneymate.ui.main;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ActivityHomeBinding;
import com.google.android.material.bottomnavigation.BottomNavigationView;

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

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(binding.navHostMain.getId());

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            setupBottomNavigation(binding.bottomNavigation, navController);
        }
    }

    public void setBottomNavigationVisible(boolean visible) {
        if (binding == null) return;
        binding.bottomNavigation.setVisibility(visible ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private void setupBottomNavigation(BottomNavigationView bottomNav, NavController navController) {
        bottomNav.setOnItemSelectedListener(item -> navigateToRootDestination(navController, item.getItemId()));

        bottomNav.setOnItemReselectedListener(item -> navController.popBackStack(item.getItemId(), false));

        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> updateSelectedBottomItem(bottomNav, destination));
    }

    private boolean navigateToRootDestination(NavController navController, int destinationId) {
        NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();
        navController.navigate(destinationId, null, navOptions);
        return true;
    }

    private void updateSelectedBottomItem(BottomNavigationView bottomNav, NavDestination destination) {
        int destinationId = destination.getId();
        if (destinationId == R.id.walletListFragment || destinationId == R.id.addEditWalletFragment) {
            MenuItem homeItem = bottomNav.getMenu().findItem(R.id.homeFragment);
            if (homeItem != null) {
                homeItem.setChecked(true);
            }
            return;
        }

        MenuItem destinationItem = bottomNav.getMenu().findItem(destinationId);
        if (destinationItem != null) {
            destinationItem.setChecked(true);
        }
    }
}
