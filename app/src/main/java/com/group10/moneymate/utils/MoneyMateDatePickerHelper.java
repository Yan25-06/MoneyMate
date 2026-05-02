package com.group10.moneymate.utils;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.group10.moneymate.R;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public final class MoneyMateDatePickerHelper {

    private MoneyMateDatePickerHelper() {
    }

    public static void showSingleDatePicker(@NonNull Fragment fragment,
                                            @NonNull LocalDate initialDate,
                                            @NonNull String tag,
                                            @NonNull DateSelectedListener listener) {
        long selection = initialDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(value -> {
            if (value == null) {
                return;
            }
            LocalDate selectedDate = Instant.ofEpochMilli(value)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            listener.onDateSelected(selectedDate);
        });
        picker.show(fragment.getChildFragmentManager(), tag);
    }

    public interface DateSelectedListener {
        void onDateSelected(@NonNull LocalDate date);
    }
}
