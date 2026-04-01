package com.group10.moneymate.ui.transaction;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.databinding.ItemReportTransactionDayHeaderBinding;

public class ReportTransactionDayHeaderAdapter extends RecyclerView.Adapter<ReportTransactionDayHeaderAdapter.ViewHolder> {

    @NonNull
    private final DayHeaderItem item;

    public ReportTransactionDayHeaderAdapter(@NonNull DayHeaderItem item) {
        this.item = item;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReportTransactionDayHeaderBinding binding = ItemReportTransactionDayHeaderBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final ItemReportTransactionDayHeaderBinding binding;

        ViewHolder(@NonNull ItemReportTransactionDayHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DayHeaderItem item) {
            binding.tvDayNumber.setText(item.getDayNumber());
            binding.tvDayLabel.setText(item.getDayLabel());
            binding.tvMonthYearLabel.setText(item.getMonthYearLabel());
            binding.tvDayTotal.setText(item.getDayTotalLabel());
        }
    }

    public static class DayHeaderItem {
        @NonNull private final String dayNumber;
        @NonNull private final String dayLabel;
        @NonNull private final String monthYearLabel;
        @NonNull private final String dayTotalLabel;

        public DayHeaderItem(@NonNull String dayNumber,
                             @NonNull String dayLabel,
                             @NonNull String monthYearLabel,
                             @NonNull String dayTotalLabel) {
            this.dayNumber = dayNumber;
            this.dayLabel = dayLabel;
            this.monthYearLabel = monthYearLabel;
            this.dayTotalLabel = dayTotalLabel;
        }

        @NonNull
        public String getDayNumber() {
            return dayNumber;
        }

        @NonNull
        public String getDayLabel() {
            return dayLabel;
        }

        @NonNull
        public String getMonthYearLabel() {
            return monthYearLabel;
        }

        @NonNull
        public String getDayTotalLabel() {
            return dayTotalLabel;
        }
    }
}
