package com.group10.moneymate.ui.main;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ActivityHomeBinding;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Main activity after login. Hosts bottom navigation and NavHostFragment.
 */
public class HomeActivity extends AppCompatActivity {

    private ActivityHomeBinding binding;
    private NavController navController;
    private final Set<Integer> topLevelDestinations = new HashSet<>(Arrays.asList(
            R.id.homeFragment,
            R.id.transactionListFragment,
            R.id.budgetListFragment,
            R.id.settingsFragment
    ));

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
            navController = navHostFragment.getNavController();
            binding.bottomNavigation.setOnItemSelectedListener(item ->
                    navigateToBottomDestination(item.getItemId(), null)
            );
            navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
                int id = destination.getId();
                if (topLevelDestinations.contains(id)) {
                    binding.bottomNavigation.setVisibility(View.VISIBLE);
                    if (binding.bottomNavigation.getSelectedItemId() != id) {
                        binding.bottomNavigation.getMenu().findItem(id).setChecked(true);
                    }
                } else {
                    binding.bottomNavigation.setVisibility(View.GONE);
                }
            });
        }
    }

    public boolean navigateToBottomDestination(int destinationId, Bundle args) {
        if (navController == null || !topLevelDestinations.contains(destinationId)) {
            return false;
        }

        int currentDestinationId = navController.getCurrentDestination() != null
                ? navController.getCurrentDestination().getId()
                : 0;
        if (currentDestinationId == destinationId && (args == null || args.isEmpty())) {
            return true;
        }

        NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                .build();

        navController.navigate(destinationId, args, navOptions);
        binding.bottomNavigation.getMenu().findItem(destinationId).setChecked(true);
        return true;
    }
}
