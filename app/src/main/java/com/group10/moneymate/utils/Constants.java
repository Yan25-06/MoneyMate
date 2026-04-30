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
        public final String id;
        public final String name;
        public final String iconName;
        public final String type;       // TYPE_EXPENSE hoặc TYPE_INCOME

        public DefaultCategory(String id, String name, String iconName, String type) {
            this.id = id;
            this.name      = name;
            this.iconName  = iconName;
            this.type      = type;
        }
    }

    // Stable UUIDs for system default categories (must match Supabase seed SQL).
    public static final String CATEGORY_ID_EXP_FOOD = "00000000-0000-0000-0000-000000000001";
    public static final String CATEGORY_ID_EXP_TRANSPORT = "00000000-0000-0000-0000-000000000002";
    public static final String CATEGORY_ID_EXP_SHOPPING = "00000000-0000-0000-0000-000000000003";
    public static final String CATEGORY_ID_EXP_ENTERTAIN = "00000000-0000-0000-0000-000000000004";
    public static final String CATEGORY_ID_EXP_HEALTH = "00000000-0000-0000-0000-000000000005";
    public static final String CATEGORY_ID_EXP_EDUCATION = "00000000-0000-0000-0000-000000000006";
    public static final String CATEGORY_ID_EXP_BILL = "00000000-0000-0000-0000-000000000007";
    public static final String CATEGORY_ID_EXP_HOUSE = "00000000-0000-0000-0000-000000000008";
    public static final String CATEGORY_ID_EXP_TRAVEL = "00000000-0000-0000-0000-000000000009";
    public static final String CATEGORY_ID_EXP_OTHER = "00000000-0000-0000-0000-00000000000a";
    public static final String CATEGORY_ID_INC_SALARY = "00000000-0000-0000-0000-00000000000b";
    public static final String CATEGORY_ID_INC_BONUS = "00000000-0000-0000-0000-00000000000c";
    public static final String CATEGORY_ID_INC_INVEST = "00000000-0000-0000-0000-00000000000d";
    public static final String CATEGORY_ID_INC_SALE = "00000000-0000-0000-0000-00000000000e";
    public static final String CATEGORY_ID_INC_GIFT = "00000000-0000-0000-0000-00000000000f";
    public static final String CATEGORY_ID_INC_OTHER = "00000000-0000-0000-0000-000000000010";
    public static final String CATEGORY_ID_EXP_TRANSFER_OUT = "00000000-0000-0000-0000-000000000011";
    public static final String CATEGORY_ID_INC_TRANSFER_IN = "00000000-0000-0000-0000-000000000012";

    /**
     * Trả về 18 danh mục mặc định (10 Chi + 6 Thu + 2 Chuyển khoản).
     * Dùng trong {@link com.group10.moneymate.data.repository.CategoryRepository()}.
     */
    public static List<DefaultCategory> getDefaultCategories() {
        List<DefaultCategory> list = new ArrayList<>();

        // ── Chi tiêu (EXPENSE) ──────────────────────────────────────────────
        list.add(new DefaultCategory(CATEGORY_ID_EXP_FOOD, "Ăn uống",    "ic_category_food", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_TRANSPORT, "Di chuyển",  "ic_category_transport", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_SHOPPING, "Mua sắm",    "ic_category_shopping", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_ENTERTAIN, "Giải trí",   "ic_category_entertain", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_HEALTH, "Y tế",       "ic_category_health", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_EDUCATION, "Giáo dục",   "ic_category_education", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_BILL, "Hoá đơn",    "ic_category_bill", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_HOUSE, "Nhà ở",      "ic_category_house", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_TRAVEL, "Du lịch",    "ic_category_travel", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_EXP_OTHER, "Khác (Chi)", "ic_category_other", TYPE_EXPENSE));

        // ── Thu nhập (INCOME) ───────────────────────────────────────────────
        list.add(new DefaultCategory(CATEGORY_ID_INC_SALARY, "Lương",      "ic_category_salary", TYPE_INCOME));
        list.add(new DefaultCategory(CATEGORY_ID_INC_BONUS, "Thưởng",     "ic_category_bonus", TYPE_INCOME));
        list.add(new DefaultCategory(CATEGORY_ID_INC_INVEST, "Đầu tư",     "ic_category_invest", TYPE_INCOME));
        list.add(new DefaultCategory(CATEGORY_ID_INC_SALE, "Bán hàng",   "ic_category_sale", TYPE_INCOME));
        list.add(new DefaultCategory(CATEGORY_ID_INC_GIFT, "Quà tặng",   "ic_category_gift", TYPE_INCOME));
        list.add(new DefaultCategory(CATEGORY_ID_INC_OTHER, "Khác (Thu)", "ic_category_other_in", TYPE_INCOME));

        // ── Chuyển khoản ────────────────────────────────────────────────────
        list.add(new DefaultCategory(CATEGORY_ID_EXP_TRANSFER_OUT, CATEGORY_NAME_TRANSFER_OUT, "ic_category_transfer_out", TYPE_EXPENSE));
        list.add(new DefaultCategory(CATEGORY_ID_INC_TRANSFER_IN, CATEGORY_NAME_TRANSFER_IN,  "ic_category_transfer_in",  TYPE_INCOME));

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
