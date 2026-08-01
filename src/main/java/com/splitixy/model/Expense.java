package com.splitixy.model;

public class Expense {

    private int expenseId;
    private int groupId;
    private int paidBy;
    private double amount;
    private String description;

    public Expense() {
    }

    public Expense(int groupId, int paidBy, double amount, String description) {
        this.groupId = groupId;
        this.paidBy = paidBy;
        this.amount = amount;
        this.description = description;
    }

    public int getExpenseId() {
        return expenseId;
    }

    public void setExpenseId(int expenseId) {
        this.expenseId = expenseId;
    }

    public int getGroupId() {
        return groupId;
    }

    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }

    public int getPaidBy() {
        return paidBy;
    }

    public void setPaidBy(int paidBy) {
        this.paidBy = paidBy;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}