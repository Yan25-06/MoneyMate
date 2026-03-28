package com.group10.moneymate.ui.budget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemBudgetBinding;

public class BudgetAdapter extends ListAdapter<BudgetUIModel, BudgetAdapter.BudgetViewHolder> {

    public interface OnBudgetClickListener {
        void onBudgetClick(BudgetUIModel item);
    }

    private OnBudgetClickListener onBudgetClickListener;

    public BudgetAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnBudgetClickListener(OnBudgetClickListener onBudgetClickListener) {
        this.onBudgetClickListener = onBudgetClickListener;
    }

    private static final DiffUtil.ItemCallback<BudgetUIModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BudgetUIModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull BudgetUIModel oldItem,
                                               @NonNull BudgetUIModel newItem) {
                    return oldItem.getBudgetEntity().getId().equals(newItem.getBudgetEntity().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull BudgetUIModel oldItem,
                                                  @NonNull BudgetUIModel newItem) {
                    return oldItem.getBudgetEntity().getAmount() == newItem.getBudgetEntity().getAmount()
                            && oldItem.getBudgetEntity().getStartDate() == newItem.getBudgetEntity().getStartDate()
                            && oldItem.getBudgetEntity().getEndDate() == newItem.getBudgetEntity().getEndDate()
                            && oldItem.getSpentAmount() == newItem.getSpentAmount()
                            && oldItem.isActive() == newItem.isActive()
                            && oldItem.getCategoryName().equals(newItem.getCategoryName())
                            && oldItem.getCategoryIcon().equals(newItem.getCategoryIcon())
                            && oldItem.getCategoryColorHex().equals(newItem.getCategoryColorHex());
                }
            };

    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBudgetBinding binding = ItemBudgetBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new BudgetViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        holder.bind(getItem(position), onBudgetClickListener);
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {

        private final ItemBudgetBinding binding;

        BudgetViewHolder(@NonNull ItemBudgetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull BudgetUIModel item, OnBudgetClickListener clickListener) {
            Context context = binding.getRoot().getContext();
            float percent = item.getPercent();
            int progress = Math.max(0, Math.min(100, Math.round(percent)));
            @ColorInt int progressColor = resolveProgressColor(context, item);
            @ColorInt int fallbackIconColor =
                    ContextCompat.getColor(context, R.color.budget_safe_green);
            @ColorInt int iconTint = BudgetUiUtils.parseColorOrDefault(
                    item.getCategoryColorHex(),
                    fallbackIconColor
            );

            binding.tvCategory.setText(item.getCategoryName());
            binding.tvAmount.setText(BudgetUiUtils.formatCurrency(item.getBudgetEntity().getAmount()));
            binding.progressBudget.setProgressCompat(progress, false);
            binding.progressBudget.setIndicatorColor(progressColor);
            binding.progressBudget.setTrackColor(
                    ContextCompat.getColor(context, R.color.budget_track_gray)
            );

            binding.ivIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                    context,
                    item.getCategoryIcon(),
                    item.getCategoryName()
            ));
            binding.ivIcon.setImageTintList(ColorStateList.valueOf(iconTint));
            binding.iconContainer.setBackgroundTintList(ColorStateList.valueOf(adjustAlpha(iconTint, 0.16f)));

            if (item.isOverspent()) {
                binding.tvRemaining.setText(context.getString(
                        R.string.budget_overspent_value,
                        BudgetUiUtils.formatCurrency(Math.abs(item.getRemainingAmount()))
                ));
                binding.tvRemaining.setTextColor(ContextCompat.getColor(context, R.color.budget_danger_red));
            } else {
                binding.tvRemaining.setText(context.getString(
                        R.string.budget_left_value,
                        BudgetUiUtils.formatCurrency(item.getRemainingAmount())
                ));
                binding.tvRemaining.setTextColor(ContextCompat.getColor(context, R.color.budget_text_secondary));
            }
            binding.tvToday.setVisibility(item.isActive() ? View.VISIBLE : View.GONE);
            binding.vTodayMarker.setVisibility(item.isActive() ? View.VISIBLE : View.GONE);
            if (item.isActive()) {
                positionTodayMarker(item);
            }

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onBudgetClick(item);
                }
            });
        }

        private void positionTodayMarker(@NonNull BudgetUIModel item) {
            binding.progressMarkerContainer.post(() -> {
                int availableWidth = binding.progressMarkerContainer.getWidth();
                if (availableWidth <= 0) {
                    return;
                }

                float fraction = BudgetUiUtils.getTimelineFraction(item.getBudgetEntity());
                int markerX = Math.round(availableWidth * fraction);

                FrameLayout.LayoutParams markerParams =
                        (FrameLayout.LayoutParams) binding.vTodayMarker.getLayoutParams();
                markerParams.leftMargin = Math.max(0, Math.min(availableWidth, markerX));
                binding.vTodayMarker.setLayoutParams(markerParams);

                binding.tvToday.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                );
                int chipWidth = binding.tvToday.getMeasuredWidth();
                FrameLayout.LayoutParams chipParams =
                        (FrameLayout.LayoutParams) binding.tvToday.getLayoutParams();
                chipParams.leftMargin = Math.max(
                        0,
                        Math.min(availableWidth - chipWidth, markerX - (chipWidth / 2))
                );
                binding.tvToday.setLayoutParams(chipParams);
            });
        }

        @ColorInt
        private static int resolveProgressColor(@NonNull Context context, @NonNull BudgetUIModel item) {
            if (item.getSpentAmount() <= 0d) {
                return ContextCompat.getColor(context, R.color.budget_muted_gray);
            }
            float percent = item.getPercent();
            if (percent > 90f || item.isOverspent()) {
                return ContextCompat.getColor(context, R.color.budget_danger_red);
            }
            if (percent >= 70f) {
                return ContextCompat.getColor(context, R.color.budget_warning_orange);
            }
            return ContextCompat.getColor(context, R.color.budget_safe_green);
        }

        @ColorInt
        private static int adjustAlpha(@ColorInt int color, float factor) {
            int alpha = Math.round(android.graphics.Color.alpha(color) * factor);
            return androidx.core.graphics.ColorUtils.setAlphaComponent(color, alpha);
        }
    }
}
