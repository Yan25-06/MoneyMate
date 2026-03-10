package com.group10.moneymate.ui.wallet;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.ui.common.BaseListAdapter;
import java.util.List;

public class WalletAdapter extends BaseListAdapter<WalletEntity> {

    public void setWallets(List<WalletEntity> wallets) {
        setItems(wallets);
    }

    @Override
    protected int getItemLayoutId() {
        return R.layout.item_wallet;
    }
}
