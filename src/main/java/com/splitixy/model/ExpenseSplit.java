package com.splitixy.model;

public class ExpenseSplit {

    private int splitId;
    private int expenseId;
    private int userId;
    private double amountOwed;

    public ExpenseSplit() {
    }

    public ExpenseSplit(int expenseId, int userId, double amountOwed) {
        this.expenseId = expenseId;
        this.userId = userId;
        this.amountOwed = amountOwed;
    }

    public int getSplitId() {
        return splitId;
    }

    public void setSplitId(int splitId) {
        this.splitId = splitId;
    }

    public int getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(int expenseId) {
        this.expenseId = expenseId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public double getAmountOwed() {
        return amountOwed;
    }

    public void setAmountOwed(double amountOwed) {
        this.amountOwed = amountOwed;
    }
}
