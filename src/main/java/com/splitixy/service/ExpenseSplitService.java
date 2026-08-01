package com.splitixy.service;

import com.splitixy.dao.ExpenseSplitDAO;
import com.splitixy.model.ExpenseSplit;

public class ExpenseSplitService {

    private ExpenseSplitDAO expenseSplitDAO = new ExpenseSplitDAO();

    public boolean addSplit(ExpenseSplit split) {
        return expenseSplitDAO.addSplit(split);
    }
}