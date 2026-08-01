package com.splitixy.service;

import com.splitixy.dao.BalanceDAO;
import com.splitixy.model.Balance;

import java.util.List;

public class BalanceService {

    private BalanceDAO balanceDAO = new BalanceDAO();

    public List<Balance> getBalances(int groupId) {
        return balanceDAO.getBalances(groupId);
    }
}