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
import com.google.android.material.color.MaterialColors;

import java.util.Objects;

public class BudgetAdapter extends ListAdapter<BudgetUIModel, RecyclerView.ViewHolder> {

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
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull BudgetUIModel oldItem,
                                               @NonNull BudgetUIModel newItem) {
                    return oldItem.getBudgetEntity().getId().equals(newItem.getBudgetEntity().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull BudgetUIModel oldItem,
                                                  @NonNull BudgetUIModel newItem) {
                    return oldItem.getBudgetEntity().getUpdatedAt() == newItem.getBudgetEntity().getUpdatedAt()
                            && Double.compare(oldItem.getBudgetEntity().getAmount(),
                            newItem.getBudgetEntity().getAmount()) == 0
                            && oldItem.getBudgetEntity().getStartDate() == newItem.getBudgetEntity().getStartDate()
                            && oldItem.getBudgetEntity().getEndDate() == newItem.getBudgetEntity().getEndDate()
                            && Double.compare(oldItem.getSpentAmount(), newItem.getSpentAmount()) == 0
                            && oldItem.isWalletArchived() == newItem.isWalletArchived()
                            && oldItem.isActive() == newItem.isActive()
                            && Objects.equals(oldItem.getCategoryName(), newItem.getCategoryName())
                            && Objects.equals(oldItem.getWalletName(), newItem.getWalletName())
                            && Objects.equals(oldItem.getCategoryIcon(), newItem.getCategoryIcon());
                }
            };

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBudgetBinding binding = ItemBudgetBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new BudgetViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof BudgetViewHolder) {
            ((BudgetViewHolder) holder).bind(getItem(position), onBudgetClickListener);
        }
    }

    private static final class BudgetViewHolder extends RecyclerView.ViewHolder {

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
            boolean walletArchived = item.isWalletArchived();

            binding.tvCategory.setText(item.getCategoryName());
            binding.tvWalletScope.setText(item.getWalletName());
            binding.tvWalletArchivedBadge.setVisibility(walletArchived ? View.VISIBLE : View.GONE);
            binding.tvPeriod.setText(BudgetUiUtils.formatDateRange(
                    item.getBudgetEntity().getStartDate(),
                    item.getBudgetEntity().getEndDate()
            ));
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
            binding.ivIcon.setImageTintList(null);
            binding.iconContainer.setBackgroundTintList(ColorStateList.valueOf(
                    MaterialColors.getColor(binding.iconContainer,
                            com.google.android.material.R.attr.colorSurface)
            ));
            binding.tvWalletScope.setTextColor(ContextCompat.getColor(
                    context,
                    walletArchived ? R.color.statistics_text_muted : R.color.budget_text_secondary
            ));
            binding.progressBudget.setAlpha(walletArchived ? 0.72f : 1f);
            binding.vTodayMarker.setAlpha(walletArchived ? 0.72f : 1f);
            binding.tvToday.setAlpha(walletArchived ? 0.72f : 1f);

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
            if (walletArchived) {
                binding.tvToday.setText(R.string.budget_wallet_archived_badge);
            } else {
                binding.tvToday.setText(R.string.budget_today);
            }

            itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onBudgetClick(item);
                }
            });
        }

        private void positionTodayMarker(@NonNull BudgetUIModel item) {
            binding.progressMarkerContainer.post(() -> {
                int trackWidth = binding.progressBudget.getWidth();
                if (trackWidth <= 0) {
                    return;
                }

                float fraction = BudgetUiUtils.getTimelineFraction(item.getBudgetEntity());
                int trackLeft = binding.progressBudget.getLeft();
                int markerX = trackLeft + Math.round(trackWidth * fraction);

                FrameLayout.LayoutParams markerParams =
                        (FrameLayout.LayoutParams) binding.vTodayMarker.getLayoutParams();
                int markerWidth = binding.vTodayMarker.getWidth() > 0
                        ? binding.vTodayMarker.getWidth()
                        : markerParams.width;
                int centeredMarkerLeft = markerX - (markerWidth / 2);
                markerParams.leftMargin = Math.max(
                        0,
                        Math.min(binding.progressMarkerContainer.getWidth() - markerWidth, centeredMarkerLeft)
                );
                binding.vTodayMarker.setLayoutParams(markerParams);

                binding.tvToday.measure(
                        View.MeasureSpec.UNSPECIFIED,
                        View.MeasureSpec.UNSPECIFIED
                );
                int chipWidth = binding.tvToday.getMeasuredWidth();
                FrameLayout.LayoutParams chipParams =
                        (FrameLayout.LayoutParams) binding.tvToday.getLayoutParams();
                int centeredChipLeft = markerX - (chipWidth / 2);
                chipParams.leftMargin = Math.max(
                        0,
                        Math.min(binding.progressMarkerContainer.getWidth() - chipWidth, centeredChipLeft)
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
    }
}
