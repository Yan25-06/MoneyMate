package com.group10.moneymate.ui.budget;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.ui.common.BaseListAdapter;
import java.util.List;

public class BudgetAdapter extends BaseListAdapter<BudgetEntity> {

    public void setBudgets(List<BudgetEntity> budgets) {
        setItems(budgets);
    }

    @Override
    protected int getItemLayoutId() {
        return R.layout.item_budget;
    }
}
