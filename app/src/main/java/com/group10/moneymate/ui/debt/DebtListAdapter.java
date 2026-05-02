package com.group10.moneymate.ui.debt;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.databinding.ItemDebtBinding;
import com.group10.moneymate.databinding.ItemDebtSectionHeaderBinding;
import com.group10.moneymate.models.DebtStatus;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;

import java.util.ArrayList;
import java.util.List;

public class DebtListAdapter extends ListAdapter<DebtListAdapter.ListItem, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_DEBT = 1;

    public interface OnDebtClickListener {
        void onDebtClick(DebtEntity debt);
    }

    private OnDebtClickListener listener;
    private boolean isCollectTab; // true = Cần thu (LEND), false = Cần trả (BORROW)

    public DebtListAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnDebtClickListener(OnDebtClickListener listener) {
        this.listener = listener;
    }

    public void setCollectTab(boolean collectTab) {
        this.isCollectTab = collectTab;
    }

    /**
     * Build sectioned list from flat debt list.
     * Sections: Ongoing (Chưa trả/nhận hết) + Settled (Đã trả/nhận hết)
     */
    public void submitDebtList(List<DebtEntity> debts) {
        List<ListItem> items = new ArrayList<>();
        List<DebtEntity> ongoing = new ArrayList<>();
        List<DebtEntity> settled = new ArrayList<>();

        if (debts != null) {
            for (DebtEntity debt : debts) {
                if (DebtStatus.SETTLED.name().equals(debt.getStatus())) {
                    settled.add(debt);
                } else {
                    ongoing.add(debt);
                }
            }
        }

        if (!ongoing.isEmpty()) {
            String headerTitle = isCollectTab
                    ? "CHƯA NHẬN HẾT"
                    : "CHƯA TRẢ HẾT";
            double totalRemaining = 0;
            for (DebtEntity d : ongoing) {
                totalRemaining += d.getRemainingAmount();
            }
            items.add(ListItem.header(headerTitle, totalRemaining));
            for (DebtEntity d : ongoing) {
                items.add(ListItem.debt(d));
            }
        }

        if (!settled.isEmpty()) {
            String headerTitle = isCollectTab
                    ? "ĐÃ NHẬN HẾT"
                    : "ĐÃ TRẢ HẾT";
            items.add(ListItem.header(headerTitle, 0));
            for (DebtEntity d : settled) {
                items.add(ListItem.debt(d));
            }
        }

        submitList(items);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isHeader ? VIEW_TYPE_HEADER : VIEW_TYPE_DEBT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            ItemDebtSectionHeaderBinding binding = ItemDebtSectionHeaderBinding.inflate(inflater, parent, false);
            return new HeaderViewHolder(binding);
        } else {
            ItemDebtBinding binding = ItemDebtBinding.inflate(inflater, parent, false);
            return new DebtViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ListItem item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof DebtViewHolder) {
            ((DebtViewHolder) holder).bind(item.debt, listener);
        }
    }

    // ─── ViewHolders ──────────────────────────────────────────────────────────

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemDebtSectionHeaderBinding binding;

        HeaderViewHolder(ItemDebtSectionHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(ListItem item) {
            binding.tvSectionTitle.setText(item.headerTitle);
            if (item.headerTotal > 0) {
                binding.tvSectionTotal.setVisibility(View.VISIBLE);
                binding.tvSectionTotal.setText(CurrencyFormatter.format(item.headerTotal, "VND"));
            } else {
                binding.tvSectionTotal.setVisibility(View.GONE);
            }
        }
    }

    static class DebtViewHolder extends RecyclerView.ViewHolder {
        private final ItemDebtBinding binding;

        DebtViewHolder(ItemDebtBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(DebtEntity debt, OnDebtClickListener listener) {
            if (debt == null) return;

            // Avatar letter
            String personName = debt.getPersonName();
            if (personName != null && !personName.isEmpty()) {
                binding.tvAvatarLetter.setText(String.valueOf(personName.charAt(0)).toUpperCase());
            } else {
                binding.tvAvatarLetter.setText("?");
            }

            // Color for avatar based on person name hash
            int[] colors = {0xFF4CAF50, 0xFF2196F3, 0xFFFF9800, 0xFF9C27B0, 0xFFE91E63, 0xFF00BCD4};
            int colorIndex = personName != null ? Math.abs(personName.hashCode()) % colors.length : 0;
            View avatarBg = binding.tvAvatarLetter.getParent() instanceof ViewGroup
                    ? (View) binding.tvAvatarLetter.getParent() : null;
            if (avatarBg != null && avatarBg.getBackground() instanceof GradientDrawable) {
                ((GradientDrawable) avatarBg.getBackground().mutate()).setColor(colors[colorIndex]);
            }

            binding.tvPersonName.setText(personName);

            // Due date
            Long dueDate = debt.getDueDate();
            if (dueDate != null && dueDate > 0) {
                binding.tvDueDate.setVisibility(View.VISIBLE);
                binding.tvDueDate.setText(
                        binding.getRoot().getContext().getString(R.string.debt_due_date_label,
                                DateUtils.formatDate(dueDate)));
            } else {
                binding.tvDueDate.setVisibility(View.GONE);
            }

            // Amounts
            boolean isSettled = DebtStatus.SETTLED.name().equals(debt.getStatus());
            if (isSettled) {
                binding.tvRemainingAmount.setText(CurrencyFormatter.format(debt.getAmount(), "VND"));
                binding.tvRemainingAmount.setTextColor(
                        binding.getRoot().getContext().getColor(android.R.color.holo_green_dark));
            } else {
                binding.tvRemainingAmount.setText(CurrencyFormatter.format(debt.getRemainingAmount(), "VND"));
                binding.tvRemainingAmount.setTextColor(
                        binding.getRoot().getContext().getColor(android.R.color.holo_red_dark));
            }
            binding.tvTotalAmount.setText(
                    binding.getRoot().getContext().getString(R.string.debt_of_total,
                            CurrencyFormatter.format(debt.getAmount(), "VND")));

            // Click
            if (listener != null) {
                binding.getRoot().setOnClickListener(v -> listener.onDebtClick(debt));
            }
        }
    }

    // ─── ListItem model ───────────────────────────────────────────────────────

    public static final class ListItem {
        final boolean isHeader;
        final String headerTitle;
        final double headerTotal;
        final DebtEntity debt;
        final String stableId;

        private ListItem(boolean isHeader, String headerTitle, double headerTotal, DebtEntity debt) {
            this.isHeader = isHeader;
            this.headerTitle = headerTitle;
            this.headerTotal = headerTotal;
            this.debt = debt;
            this.stableId = isHeader ? "HEADER_" + headerTitle : (debt != null ? debt.getId() : "UNKNOWN");
        }

        static ListItem header(String title, double total) {
            return new ListItem(true, title, total, null);
        }

        static ListItem debt(DebtEntity debt) {
            return new ListItem(false, null, 0, debt);
        }
    }

    private static final DiffUtil.ItemCallback<ListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
                    return oldItem.stableId.equals(newItem.stableId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ListItem oldItem, @NonNull ListItem newItem) {
                    if (oldItem.isHeader && newItem.isHeader) {
                        return oldItem.headerTotal == newItem.headerTotal;
                    }
                    if (oldItem.debt != null && newItem.debt != null) {
                        return oldItem.debt.getUpdatedAt() == newItem.debt.getUpdatedAt();
                    }
                    return false;
                }
            };
}
