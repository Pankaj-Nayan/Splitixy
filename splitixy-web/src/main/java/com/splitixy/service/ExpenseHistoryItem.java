package com.splitixy.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class ExpenseHistoryItem {

    private final Integer expenseId;
    private final Integer groupId;
    private final String groupName;
    private final String description;
    private final BigDecimal amount;
    private final LocalDateTime expenseDate;
    private final String splitType;
    private final String payerName;
    private final String creatorName;
    private final List<Share> shares;

    public ExpenseHistoryItem(
            Integer expenseId,
            Integer groupId,
            String groupName,
            String description,
            BigDecimal amount,
            LocalDateTime expenseDate,
            String splitType,
            String payerName,
            String creatorName,
            List<Share> shares) {
        this.expenseId = expenseId;
        this.groupId = groupId;
        this.groupName = groupName;
        this.description = description;
        this.amount = amount;
        this.expenseDate = expenseDate;
        this.splitType = splitType;
        this.payerName = payerName;
        this.creatorName = creatorName;
        this.shares = List.copyOf(shares);
    }

    public Integer getExpenseId() {
        return expenseId;
    }

    public Integer getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getExpenseDate() {
        return expenseDate;
    }

    public String getSplitType() {
        return splitType;
    }

    public String getPayerName() {
        return payerName;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public List<Share> getShares() {
        return shares;
    }

    public static class Share {
        private final String memberName;
        private final BigDecimal amount;

        public Share(String memberName, BigDecimal amount) {
            this.memberName = memberName;
            this.amount = amount;
        }

        public String getMemberName() {
            return memberName;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
