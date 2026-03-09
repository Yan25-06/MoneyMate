package com.group10.moneymate.ui.debt;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.repository.DebtRepository;
import java.util.List;

public class DebtViewModel extends ViewModel {
    private final DebtRepository debtRepository;

    public DebtViewModel(DebtRepository debtRepository) {
        this.debtRepository = debtRepository;
    }

    public LiveData<List<DebtEntity>> getAllDebts(String userId) {
        return debtRepository.getAllDebts(userId);
    }

    public void insert(DebtEntity debt) { debtRepository.insert(debt); }
    public void update(DebtEntity debt) { debtRepository.update(debt); }
    public void softDelete(String id) { debtRepository.softDelete(id); }
}