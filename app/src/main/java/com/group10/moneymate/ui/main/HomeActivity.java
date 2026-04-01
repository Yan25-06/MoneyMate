package com.group10.moneymate.ui.main;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
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
    private int lastSystemBarsBottom = 0;
    private final Set<Integer> topLevelDestinations = new HashSet<>(Arrays.asList(
            R.id.homeFragment,
            R.id.transactionListFragment,
            R.id.budgetListFragment,
            R.id.settingsFragment
    ));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityHomeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();
        applyWindowInsets();
        setupContentInsets();
    }

    private void setupNavigation() {
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_main);

        if (navHostFragment == null) {
            return;
        }
        navController = navHostFragment.getNavController();

        binding.navOverview.setOnClickListener(v -> navigateToBottomDestination(R.id.homeFragment, null));
        binding.navTransactions.setOnClickListener(v -> navigateToBottomDestination(R.id.transactionListFragment, null));
        binding.navBudgets.setOnClickListener(v -> navigateToBottomDestination(R.id.budgetListFragment, null));
        binding.navSettings.setOnClickListener(v -> navigateToBottomDestination(R.id.settingsFragment, null));
        binding.navAdd.setOnClickListener(v -> navController.navigate(R.id.addEditTransactionFragment));

        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            int id = destination.getId();
            if (topLevelDestinations.contains(id)) {
                binding.bottomNavShell.setVisibility(View.VISIBLE);
                updateSelectedTab(id);
            } else {
                binding.bottomNavShell.setVisibility(View.GONE);
            }
            updateContentBottomInset();
        });
    }

    private void applyWindowInsets() {
        final int initialBottomPadding = binding.bottomNavShell.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavShell, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            lastSystemBarsBottom = systemBars.bottom;
            view.setPadding(
                    view.getPaddingLeft(),
                    view.getPaddingTop(),
                    view.getPaddingRight(),
                    initialBottomPadding + systemBars.bottom
            );
            updateContentBottomInset();
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.bottomNavShell);
    }

    private void setupContentInsets() {
        binding.navHostMain.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateContentBottomInset());
        binding.bottomNavShell.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                updateContentBottomInset());
    }

    private void updateContentBottomInset() {
        int barHeight = binding.bottomNavShell.getVisibility() == View.VISIBLE
                ? binding.bottomNavShell.getHeight()
                : 0;
        int targetPaddingBottom = barHeight + lastSystemBarsBottom;
        if (binding.navHostMain.getPaddingBottom() != targetPaddingBottom) {
            binding.navHostMain.setPadding(
                    binding.navHostMain.getPaddingLeft(),
                    binding.navHostMain.getPaddingTop(),
                    binding.navHostMain.getPaddingRight(),
                    targetPaddingBottom
            );
        }
    }

    private void updateSelectedTab(@IdRes int destinationId) {
        setTabState(binding.ivNavOverview, binding.tvNavOverview, destinationId == R.id.homeFragment);
        setTabState(binding.ivNavTransactions, binding.tvNavTransactions, destinationId == R.id.transactionListFragment);
        setTabState(binding.ivNavBudgets, binding.tvNavBudgets, destinationId == R.id.budgetListFragment);
        setTabState(binding.ivNavSettings, binding.tvNavSettings, destinationId == R.id.settingsFragment);
    }

    private void setTabState(@NonNull ImageView iconView,
                             @NonNull TextView labelView,
                             boolean selected) {
        int color = ContextCompat.getColor(
                this,
                selected ? R.color.statistics_text_primary : R.color.bottom_nav_inactive
        );
        iconView.setColorFilter(color);
        labelView.setTextColor(color);
        labelView.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        labelView.setVisibility(View.VISIBLE);
    }

    public boolean navigateToBottomDestination(int destinationId, @Nullable Bundle args) {
        if (navController == null || !topLevelDestinations.contains(destinationId)) {
            return false;
        }

        int currentDestinationId = navController.getCurrentDestination() != null
                ? navController.getCurrentDestination().getId()
                : 0;
        if (currentDestinationId == destinationId && (args == null || args.isEmpty())) {
            updateSelectedTab(destinationId);
            return true;
        }

        NavOptions navOptions = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setRestoreState(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false, true)
                .build();

        navController.navigate(destinationId, args, navOptions);
        updateSelectedTab(destinationId);
        return true;
    }
}
