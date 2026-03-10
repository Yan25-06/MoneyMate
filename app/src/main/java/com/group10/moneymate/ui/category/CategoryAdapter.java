package com.group10.moneymate.ui.category;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.ui.common.BaseListAdapter;
import java.util.List;

public class CategoryAdapter extends BaseListAdapter<CategoryEntity> {

    public void setCategories(List<CategoryEntity> categories) {
        setItems(categories);
    }

    @Override
    protected int getItemLayoutId() {
        return R.layout.item_category;
    }
}
