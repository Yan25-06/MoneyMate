package com.group10.moneymate.ui.wallet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import java.util.ArrayList;
import java.util.List;

public class WalletAdapter extends RecyclerView.Adapter<WalletAdapter.ViewHolder> {
    private List<WalletEntity> wallets = new ArrayList<>();

    public void setWallets(List<WalletEntity> wallets) { this.wallets = wallets; notifyDataSetChanged(); }

    @NonNull @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wallet, parent, false);
        return new ViewHolder(view);
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) { /* TODO */ }
    @Override public int getItemCount() { return wallets.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ViewHolder(@NonNull View itemView) { super(itemView); }
    }
}
