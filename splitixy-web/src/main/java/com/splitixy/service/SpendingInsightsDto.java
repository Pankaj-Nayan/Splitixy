package com.splitixy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class SpendingInsightsDto {

    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal totalExpenses;
    private final BigDecimal totalPaid;
    private final BigDecimal personalShare;
    private final BigDecimal averageMonthlySpending;
    private final int expenseCount;
    private final String highestSpendingCategory;
    private final BigDecimal spendingChangePercent;
    private final String comparisonSummary;
    private final boolean sufficientHistory;
    private final String historyMessage;
    private final boolean excludedNonInrExpenses;
    private final boolean incompleteShareData;
    private final List<CategoryMetric> categories;
    private final List<MonthlyMetric> monthlyTrends;
    private final List<CategoryChange> categoryChanges;
    private final List<SpendingAlert> alerts;

    public SpendingInsightsDto(
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal totalExpenses,
            BigDecimal totalPaid,
            BigDecimal personalShare,
            BigDecimal averageMonthlySpending,
            int expenseCount,
            String highestSpendingCategory,
            BigDecimal spendingChangePercent,
            String comparisonSummary,
            boolean sufficientHistory,
            String historyMessage,
            boolean excludedNonInrExpenses,
            boolean incompleteShareData,
            List<CategoryMetric> categories,
            List<MonthlyMetric> monthlyTrends,
            List<CategoryChange> categoryChanges,
            List<SpendingAlert> alerts) {
        this.startDate = startDate;
        this.endDate = endDate;
        this.totalExpenses = totalExpenses;
        this.totalPaid = totalPaid;
        this.personalShare = personalShare;
        this.averageMonthlySpending = averageMonthlySpending;
        this.expenseCount = expenseCount;
        this.highestSpendingCategory = highestSpendingCategory;
        this.spendingChangePercent = spendingChangePercent;
        this.comparisonSummary = comparisonSummary;
        this.sufficientHistory = sufficientHistory;
        this.historyMessage = historyMessage;
        this.excludedNonInrExpenses = excludedNonInrExpenses;
        this.incompleteShareData = incompleteShareData;
        this.categories = List.copyOf(categories);
        this.monthlyTrends = List.copyOf(monthlyTrends);
        this.categoryChanges = List.copyOf(categoryChanges);
        this.alerts = List.copyOf(alerts);
    }

    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getTotalPaid() { return totalPaid; }
    public BigDecimal getPersonalShare() { return personalShare; }
    public BigDecimal getAverageMonthlySpending() { return averageMonthlySpending; }
    public int getExpenseCount() { return expenseCount; }
    public String getHighestSpendingCategory() { return highestSpendingCategory; }
    public BigDecimal getSpendingChangePercent() { return spendingChangePercent; }
    public String getComparisonSummary() { return comparisonSummary; }
    public boolean isSufficientHistory() { return sufficientHistory; }
    public String getHistoryMessage() { return historyMessage; }
    public boolean isExcludedNonInrExpenses() { return excludedNonInrExpenses; }
    public boolean isIncompleteShareData() { return incompleteShareData; }
    public List<CategoryMetric> getCategories() { return categories; }
    public List<MonthlyMetric> getMonthlyTrends() { return monthlyTrends; }
    public List<CategoryChange> getCategoryChanges() { return categoryChanges; }
    public List<SpendingAlert> getAlerts() { return alerts; }

    public static class CategoryMetric {
        private final String name;
        private final BigDecimal total;
        private final BigDecimal percentage;

        public CategoryMetric(String name, BigDecimal total, BigDecimal percentage) {
            this.name = name;
            this.total = total;
            this.percentage = percentage;
        }

        public String getName() { return name; }
        public BigDecimal getTotal() { return total; }
        public BigDecimal getPercentage() { return percentage; }
    }

    public static class MonthlyMetric {
        private final String label;
        private final BigDecimal total;

        public MonthlyMetric(String label, BigDecimal total) {
            this.label = label;
            this.total = total;
        }

        public String getLabel() { return label; }
        public BigDecimal getTotal() { return total; }
    }

    public static class CategoryChange {
        private final String name;
        private final BigDecimal currentTotal;
        private final BigDecimal previousTotal;
        private final BigDecimal changePercent;
        private final String summary;

        public CategoryChange(
                String name,
                BigDecimal currentTotal,
                BigDecimal previousTotal,
                BigDecimal changePercent,
                String summary) {
            this.name = name;
            this.currentTotal = currentTotal;
            this.previousTotal = previousTotal;
            this.changePercent = changePercent;
            this.summary = summary;
        }

        public String getName() { return name; }
        public BigDecimal getCurrentTotal() { return currentTotal; }
        public BigDecimal getPreviousTotal() { return previousTotal; }
        public BigDecimal getChangePercent() { return changePercent; }
        public String getSummary() { return summary; }
    }

    public static class SpendingAlert {
        private final String title;
        private final String detail;

        public SpendingAlert(String title, String detail) {
            this.title = title;
            this.detail = detail;
        }

        public String getTitle() { return title; }
        public String getDetail() { return detail; }
    }
}
