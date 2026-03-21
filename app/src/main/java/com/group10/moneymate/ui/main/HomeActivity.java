package com.group10.moneymate.ui.main;

import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.group10.moneymate.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * Main activity after login. Hosts bottom navigation and NavHostFragment.
 */
public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);

        if (navHostFragment != null) {
            NavController navController = navHostFragment.getNavController();
            setupBottomNavigation(bottomNav, navController);
        }
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
