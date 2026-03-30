package com.group10.moneymate.ui.wallet;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemWalletIconOnlyBinding;
import com.group10.moneymate.utils.IconProvider;

import java.util.Objects;

public class WalletIconOnlyAdapter extends ListAdapter<WalletIconOnlyAdapter.WalletIconItem,
        WalletIconOnlyAdapter.ViewHolder> {

    public interface OnIconClickListener {
        void onIconClick(@NonNull WalletIconItem item);
    }

    public static final class WalletIconItem {
        @NonNull
        public final String iconName;

        public WalletIconItem(@NonNull String iconName) {
            this.iconName = iconName;
        }
    }

    @Nullable
    private OnIconClickListener clickListener;
    @Nullable
    private String selectedIconName;
    @ColorInt
    private int accentColor;

    public WalletIconOnlyAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnIconClickListener(@Nullable OnIconClickListener listener) {
        this.clickListener = listener;
    }

    public void setSelectedIconName(@Nullable String selectedIconName) {
        this.selectedIconName = selectedIconName;
        notifyDataSetChanged();
    }

    public void setAccentColor(@ColorInt int accentColor) {
        this.accentColor = accentColor;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemWalletIconOnlyBinding binding = ItemWalletIconOnlyBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener, selectedIconName, accentColor);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemWalletIconOnlyBinding binding;

        ViewHolder(@NonNull ItemWalletIconOnlyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull WalletIconItem item,
                  @Nullable OnIconClickListener listener,
                  @Nullable String selectedIconName,
                  @ColorInt int accentColor) {
            Context context = binding.getRoot().getContext();
            binding.ivIconOption.setImageResource(
                    IconProvider.resolveWalletIcon(context, item.iconName, null)
            );

            int effectiveAccent = accentColor == 0
                    ? context.getColor(R.color.statistics_wallet_icon)
                    : accentColor;
            boolean selected = Objects.equals(item.iconName, selectedIconName);
            int strokeColor = selected
                    ? effectiveAccent
                    : context.getColor(R.color.transaction_border);
            int backgroundColor = selected
                    ? ColorUtils.setAlphaComponent(effectiveAccent, 28)
                    : context.getColor(android.R.color.white);
            binding.getRoot().setStrokeColor(strokeColor);
            binding.getRoot().setCardBackgroundColor(backgroundColor);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onIconClick(item);
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<WalletIconItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WalletIconItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull WalletIconItem oldItem,
                                               @NonNull WalletIconItem newItem) {
                    return oldItem.iconName.equals(newItem.iconName);
                }

                @Override
                public boolean areContentsTheSame(@NonNull WalletIconItem oldItem,
                                                  @NonNull WalletIconItem newItem) {
                    return oldItem.iconName.equals(newItem.iconName);
                }
            };
}
