package com.group10.moneymate.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public abstract class BaseListAdapter<T> extends RecyclerView.Adapter<BaseListAdapter.ViewHolder> {

    protected List<T> items = new ArrayList<>();

    public void setItems(List<T> newItems) {
        final List<T> oldItems = new ArrayList<>(this.items);
        final List<T> updatedItems = newItems == null ? new ArrayList<>() : new ArrayList<>(newItems);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return oldItems.size();
            }

            @Override
            public int getNewListSize() {
                return updatedItems.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                T oldItem = oldItems.get(oldItemPosition);
                T newItem = updatedItems.get(newItemPosition);
                return oldItem == null ? newItem == null : oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                T oldItem = oldItems.get(oldItemPosition);
                T newItem = updatedItems.get(newItemPosition);
                return oldItem == null ? newItem == null : oldItem.equals(newItem);
            }
        });
        this.items = updatedItems;
        diffResult.dispatchUpdatesTo(this);
    }

    @LayoutRes
    protected abstract int getItemLayoutId();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(getItemLayoutId(), parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Subclasses override to bind data to views
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }
}
