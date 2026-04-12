package com.group10.moneymate.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Application-wide constants.
 */
public class Constants {

    // ─── SharedPreferences ────────────────────────────────────────────────────
    public static final String PREFS_NAME       = "moneymate_prefs";
    public static final String PREF_THEME       = "pref_theme";
    public static final String PREF_LANGUAGE    = "pref_language";
    public static final String PREF_CURRENCY    = "pref_currency";
    public static final String PREF_DATE_FORMAT = "pref_date_format";
    public static final String PREF_HIDE_BALANCE = "pref_hide_balance";

    // ─── Defaults ─────────────────────────────────────────────────────────────
    public static final String DEFAULT_CURRENCY    = "VND";
    public static final String DEFAULT_LANGUAGE    = "vi";
    public static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy";
    public static final int    RECENT_TRANSACTION_LIMIT = 10;

    // ─── Category types — lưu dạng String trong CategoryEntity.type ──────────
    public static final String TYPE_EXPENSE = "EXPENSE";
    public static final String TYPE_INCOME  = "INCOME";

    // ─── Budget ───────────────────────────────────────────────────────────────
    public static final float BUDGET_WARNING_THRESHOLD = 0.8f;
    public static final String CATEGORY_ID_OTHER = "VIRTUAL_OTHER";
    public static final String CATEGORY_ID_OTHER_LEGACY = "VIRTUAL_OTHER_CATEGORIES";
    public static final String CATEGORY_TYPE_VIRTUAL_BUDGET = "VIRTUAL_BUDGET";

    // ─── Transaction ───────────────────────────────────────────────────────────────
    public static final String CATEGORY_NAME_TRANSFER_OUT = "Tiền chuyển đi";
    public static final String CATEGORY_NAME_TRANSFER_IN = "Tiền chuyển đến";

    // ─── Passcode ─────────────────────────────────────────────────────────────
    public static final int PASSCODE_MAX_ATTEMPTS    = 3;
    public static final int PASSCODE_LOCKOUT_SECONDS = 30;

    // ─── Default Categories ──────────────────────────────────────────────────

    /**
     * Mô tả một danh mục mặc định dùng để seed vào Room.
     * Các field khớp với {@link com.group10.moneymate.data.local.entity.CategoryEntity}.
     */
    public static class DefaultCategory {
        public final String name;
        public final String iconName;
        public final String type;       // TYPE_EXPENSE hoặc TYPE_INCOME

        public DefaultCategory(String name, String iconName, String type) {
            this.name      = name;
            this.iconName  = iconName;
            this.type      = type;
        }
    }

    /**
     * Trả về 16 danh mục mặc định (10 Chi + 6 Thu).
     * Dùng trong {@link com.group10.moneymate.data.repository.CategoryRepository()}.
     */
    public static List<DefaultCategory> getDefaultCategories() {
        List<DefaultCategory> list = new ArrayList<>();

        // ── Chi tiêu (EXPENSE) ──────────────────────────────────────────────
        list.add(new DefaultCategory("Ăn uống",    "ic_category_food", TYPE_EXPENSE));
        list.add(new DefaultCategory("Di chuyển",  "ic_category_transport", TYPE_EXPENSE));
        list.add(new DefaultCategory("Mua sắm",    "ic_category_shopping", TYPE_EXPENSE));
        list.add(new DefaultCategory("Giải trí",   "ic_category_entertain", TYPE_EXPENSE));
        list.add(new DefaultCategory("Y tế",       "ic_category_health", TYPE_EXPENSE));
        list.add(new DefaultCategory("Giáo dục",   "ic_category_education", TYPE_EXPENSE));
        list.add(new DefaultCategory("Hoá đơn",    "ic_category_bill", TYPE_EXPENSE));
        list.add(new DefaultCategory("Nhà ở",      "ic_category_house", TYPE_EXPENSE));
        list.add(new DefaultCategory("Du lịch",    "ic_category_travel", TYPE_EXPENSE));
        list.add(new DefaultCategory("Khác (Chi)", "ic_category_other", TYPE_EXPENSE));

        // ── Thu nhập (INCOME) ───────────────────────────────────────────────
        list.add(new DefaultCategory("Lương",      "ic_category_salary", TYPE_INCOME));
        list.add(new DefaultCategory("Thưởng",     "ic_category_bonus", TYPE_INCOME));
        list.add(new DefaultCategory("Đầu tư",     "ic_category_invest", TYPE_INCOME));
        list.add(new DefaultCategory("Bán hàng",   "ic_category_sale", TYPE_INCOME));
        list.add(new DefaultCategory("Quà tặng",   "ic_category_gift", TYPE_INCOME));
        list.add(new DefaultCategory("Khác (Thu)", "ic_category_other_in", TYPE_INCOME));

        // ── Chuyển khoản ────────────────────────────────────────────────────
        list.add(new DefaultCategory(CATEGORY_NAME_TRANSFER_OUT, "ic_category_transfer_out", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_NAME_TRANSFER_IN,  "ic_category_transfer_in",  TYPE_INCOME));

        return list;
    }

    private Constants() {
        // Prevent instantiation
    }

    public static boolean isOtherCategoryId(String categoryId) {
        return CATEGORY_ID_OTHER.equals(categoryId)
                || CATEGORY_ID_OTHER_LEGACY.equals(categoryId);
    }
}
