package com.example.moneymate.ui.budget;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.moneymate.R;
import com.example.moneymate.data.local.entity.BudgetEntity;
import java.util.ArrayList;
import java.util.List;

public class BudgetAdapter extends RecyclerView.Adapter<BudgetAdapter.ViewHolder> {
    private List<BudgetEntity> budgets = new ArrayList<>();

    public void setBudgets(List<BudgetEntity> budgets) { this.budgets = budgets; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_budget, parent, false);
        return new ViewHolder(view);
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { /* TODO */ }
    @Override public int getItemCount() { return budgets.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) { super(itemView); }
    }
}
