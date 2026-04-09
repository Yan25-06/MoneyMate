package com.group10.moneymate.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;

public class LoadingHelper {

    private AlertDialog progressDialog;
    private OnBackPressedCallback backPressedCallback;

    public void show(@NonNull Fragment fragment, @StringRes int messageResId) {
        Context context = fragment.getContext();
        if (context == null) {
            return;
        }

        dismiss();

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(48, 40, 48, 40);
        container.setGravity(Gravity.CENTER_VERTICAL);

        ProgressBar progressBar = new ProgressBar(context);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        progressParams.setMarginEnd(32);
        progressBar.setLayoutParams(progressParams);

        TextView messageView = new TextView(context);
        messageView.setText(context.getString(messageResId));
        messageView.setTextSize(16f);
        messageView.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        container.addView(progressBar);
        container.addView(messageView);

        progressDialog = new AlertDialog.Builder(context)
                .setView(container)
                .setCancelable(false)
                .create();
        progressDialog.show();

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // Block back while a write operation is in progress.
            }
        };
        fragment.requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(fragment.getViewLifecycleOwner(), backPressedCallback);
    }

    public void dismiss() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
        if (backPressedCallback != null) {
            backPressedCallback.remove();
        }
        backPressedCallback = null;
    }
}


