package com.splitixy.service;

import com.splitixy.dao.ExpenseDAO;
import com.splitixy.model.Expense;

public class ExpenseService {

    private ExpenseDAO expenseDAO = new ExpenseDAO();

    public int addExpense(Expense expense) {
        return expenseDAO.addExpense(expense);
    }
}