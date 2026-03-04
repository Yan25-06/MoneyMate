package com.group10.moneymate.ui.transaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import java.util.ArrayList;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.ViewHolder> {

    private List<TransactionEntity> transactions = new ArrayList<>();
    private OnTransactionClickListener listener;

    public interface OnTransactionClickListener {
        void onTransactionClick(TransactionEntity transaction);
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.listener = listener;
    }

    public void setTransactions(List<TransactionEntity> transactions) {
        this.transactions = transactions;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TransactionEntity transaction = transactions.get(position);
        // TODO: Bind transaction data to views
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onTransactionClick(transaction);
        });
    }

    @Override
    public int getItemCount() {
        return transactions.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) {
            super(itemView);
            // TODO: Initialize views
        }
    }
}
