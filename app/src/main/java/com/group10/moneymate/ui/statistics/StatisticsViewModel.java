package com.group10.moneymate.ui.statistics;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.data.repository.TransactionRepository;

public class StatisticsViewModel extends ViewModel {

    private final TransactionRepository transactionRepository;

    public StatisticsViewModel(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final TransactionRepository transactionRepository;

        public Factory(TransactionRepository transactionRepository) {
            this.transactionRepository = transactionRepository;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(StatisticsViewModel.class)) {
                return (T) new StatisticsViewModel(transactionRepository);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }
}
