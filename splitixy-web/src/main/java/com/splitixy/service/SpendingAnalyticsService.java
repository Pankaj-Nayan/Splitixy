package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.repository.ExpenseRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import com.splitixy.service.SpendingInsightsDto.CategoryChange;
import com.splitixy.service.SpendingInsightsDto.CategoryMetric;
import com.splitixy.service.SpendingInsightsDto.MonthlyMetric;
import com.splitixy.service.SpendingInsightsDto.SpendingAlert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class SpendingAnalyticsService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int HISTORY_MONTHS = 6;

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;

    public SpendingAnalyticsService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            GroupMemberRepository groupMemberRepository) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    @Transactional(readOnly = true)
    public SpendingInsightsDto analyze(
            Integer userId,
            LocalDate startDate,
            LocalDate endDate,
            String period) {
        validateRange(startDate, endDate);
        List<Integer> groupIds = groupMemberRepository.findByIdUserId(userId).stream()
                .map(membership -> membership.getId().getGroupId())
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return emptyInsights(startDate, endDate, "No group memberships were found.");
        }

        DateRange comparison = comparisonRange(startDate, endDate, period);
        LocalDate historyStart = startDate.withDayOfMonth(1).minusMonths(HISTORY_MONTHS);
        LocalDate queryStart = comparison.start().isBefore(historyStart)
                ? comparison.start()
                : historyStart;
        List<Expense> expenses = expenseRepository
                .findByGroupIdInAndExpenseDateGreaterThanEqualAndExpenseDateLessThan(
                        groupIds,
                        queryStart.atStartOfDay(),
                        endDate.plusDays(1).atStartOfDay());
        List<Integer> expenseIds = expenses.stream()
                .map(Expense::getExpenseId)
                .filter(id -> id != null)
                .toList();
        Map<Integer, Expense> expensesById = expenses.stream()
                .filter(expense -> expense.getExpenseId() != null)
                .collect(Collectors.toMap(Expense::getExpenseId, expense -> expense));
        Map<Integer, List<ExpenseSplit>> splitsByExpense = expenseIds.isEmpty()
                ? Map.of()
                : expenseSplitRepository.findByExpenseIdInAndUserId(expenseIds, userId).stream()
                        .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));
        Map<Integer, BigDecimal> userShares = new HashMap<>();
        Set<Integer> incompleteShareExpenseIds = new HashSet<>();
        splitsByExpense.forEach((expenseId, splits) -> {
            Expense expense = expensesById.get(expenseId);
            if (splits.size() != 1
                    || expense == null
                    || expense.getAmount() == null
                    || expense.getAmount().signum() <= 0
                    || splits.getFirst().getAmountOwed() == null
                    || splits.getFirst().getAmountOwed().signum() < 0
                    || splits.getFirst().getAmountOwed()
                            .compareTo(expense.getAmount().setScale(2, RoundingMode.HALF_UP)) > 0) {
                incompleteShareExpenseIds.add(expenseId);
            } else {
                userShares.put(
                        expenseId,
                        splits.getFirst().getAmountOwed().setScale(2, RoundingMode.HALF_UP));
            }
        });

        BigDecimal totalExpenses = ZERO;
        BigDecimal totalPaid = ZERO;
        BigDecimal personalShare = ZERO;
        int expenseCount = 0;
        boolean excludedNonInr = false;
        boolean incompleteShareData = false;
        boolean incompleteSelectedShares = false;
        boolean incompleteComparisonShares = false;
        boolean incompleteHistoricalShares = false;
        Map<String, BigDecimal> categoryTotals = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<YearMonth, BigDecimal> monthTotals = new TreeMap<>();
        Map<String, BigDecimal> previousCategoryTotals =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        BigDecimal previousPersonalShare = ZERO;
        Map<String, List<BigDecimal>> historicalShares =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<ExpenseShare> selectedShares = new ArrayList<>();
        Map<YearMonth, BigDecimal> historicalMonthlyTotals = new TreeMap<>();
        for (Expense expense : expenses) {
            LocalDate expenseLocalDate = expense.getExpenseDate() == null
                    ? null
                    : expense.getExpenseDate().toLocalDate();
            boolean selectedDate = expenseLocalDate != null
                    && !expenseLocalDate.isBefore(startDate)
                    && !expenseLocalDate.isAfter(endDate);
            boolean comparisonDate = expenseLocalDate != null
                    && !expenseLocalDate.isBefore(comparison.start())
                    && !expenseLocalDate.isAfter(comparison.end());
            if (expenseLocalDate != null
                    && incompleteShareExpenseIds.contains(expense.getExpenseId())) {
                incompleteShareData = true;
                if (selectedDate) {
                    incompleteSelectedShares = true;
                }
                if (comparisonDate && expenseLocalDate.isBefore(startDate)) {
                    incompleteComparisonShares = true;
                }
                if (!expenseLocalDate.isBefore(historyStart)
                        && expenseLocalDate.isBefore(startDate)) {
                    incompleteHistoricalShares = true;
                }
            }
            if (!"INR".equalsIgnoreCase(expense.getCurrency())) {
                excludedNonInr = true;
                if (selectedDate) {
                    incompleteSelectedShares = true;
                }
                if (comparisonDate && expenseLocalDate.isBefore(startDate)) {
                    incompleteComparisonShares = true;
                }
                if (expenseLocalDate != null
                        && !expenseLocalDate.isBefore(historyStart)
                        && expenseLocalDate.isBefore(startDate)) {
                    incompleteHistoricalShares = true;
                }
                continue;
            }
            if (expense.getAmount() == null || expense.getAmount().signum() < 0
                    || expense.getExpenseDate() == null) {
                continue;
            }
            LocalDate date = expense.getExpenseDate().toLocalDate();
            BigDecimal amount = expense.getAmount().setScale(2, RoundingMode.HALF_UP);
            BigDecimal share = userShares.get(expense.getExpenseId());
            boolean selected = selectedDate;
            boolean inComparison = comparisonDate;
            if (selected) {
                totalExpenses = totalExpenses.add(amount);
                if (userId.equals(expense.getPaidBy())) {
                    totalPaid = totalPaid.add(amount);
                }
                if (share != null) {
                    expenseCount++;
                    personalShare = personalShare.add(share);
                    String category = category(expense.getCategory());
                    categoryTotals.merge(category, share, BigDecimal::add);
                    YearMonth month = YearMonth.from(date);
                    monthTotals.merge(month, share, BigDecimal::add);
                    selectedShares.add(new ExpenseShare(category, share, date, expense));
                }
            } else {
                if (inComparison && share != null) {
                    previousPersonalShare = previousPersonalShare.add(share);
                    previousCategoryTotals.merge(
                            category(expense.getCategory()), share, BigDecimal::add);
                }
                if (!date.isBefore(historyStart) && date.isBefore(startDate) && share != null) {
                    YearMonth month = YearMonth.from(date);
                    historicalMonthlyTotals.merge(month, share, BigDecimal::add);
                    historicalShares.computeIfAbsent(category(expense.getCategory()),
                            ignored -> new ArrayList<>()).add(share);
                }
            }
        }

        List<CategoryMetric> categories = createCategoryMetrics(categoryTotals, personalShare);
        List<MonthlyMetric> monthly = createMonthlyMetrics(startDate, endDate, monthTotals);
        List<CategoryChange> categoryChanges =
                createCategoryChanges(
                        categoryTotals, previousCategoryTotals, !incompleteComparisonShares);
        String highestCategory = categories.isEmpty() ? null : categories.getFirst().getName();
        BigDecimal changePercent = incompleteComparisonShares
                ? null
                : percentageChange(personalShare, previousPersonalShare);
        String comparisonSummary = incompleteComparisonShares
                ? "Some comparison-period shares are incomplete; percentage change is unavailable."
                : comparisonSummary(personalShare, previousPersonalShare, changePercent);
        BigDecimal averageMonthly = personalShare.divide(
                BigDecimal.valueOf(monthCount(startDate, endDate)),
                2,
                RoundingMode.HALF_UP);

        long monthsWithHistory = historicalMonthlyTotals.values().stream()
                .filter(total -> total.signum() > 0)
                .count();
        boolean sufficientHistory = monthsWithHistory >= 3
                && !incompleteHistoricalShares
                && !incompleteComparisonShares
                && !incompleteSelectedShares;
        String historyMessage = incompleteSelectedShares || incompleteComparisonShares
                ? "Some selected or comparison-period shares are incomplete, so alerts are withheld."
                : incompleteHistoricalShares
                ? "Some historical shares are incomplete, so unusual-spending alerts are withheld."
                : sufficientHistory
                ? "Unusual-spending alerts use your previous six months of recorded shares."
                : "There is not enough recorded history yet for reliable unusual-spending alerts.";
        List<SpendingAlert> alerts = sufficientHistory && !incompleteComparisonShares
                ? createAlerts(personalShare, previousPersonalShare, changePercent,
                        selectedShares, historicalShares, categoryChanges)
                : List.of();

        return new SpendingInsightsDto(
                startDate,
                endDate,
                money(totalExpenses),
                money(totalPaid),
                money(personalShare),
                averageMonthly,
                expenseCount,
                highestCategory,
                changePercent,
                comparisonSummary,
                sufficientHistory,
                historyMessage,
                excludedNonInr,
                incompleteShareData,
                categories,
                monthly,
                categoryChanges,
                alerts);
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Choose a valid date range.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("A custom date range cannot exceed 366 days.");
        }
        if (endDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("The end date cannot be in the future.");
        }
    }

    private DateRange comparisonRange(LocalDate start, LocalDate end, String period) {
        LocalDate previousEnd = start.minusDays(1);
        return switch (period) {
            case "this-month" -> {
                LocalDate previousStart = start.minusMonths(1).withDayOfMonth(1);
                long elapsedDays = ChronoUnit.DAYS.between(start, end);
                LocalDate previousPeriodEnd = previousStart.plusDays(elapsedDays);
                LocalDate previousMonthEnd = previousStart.plusMonths(1).minusDays(1);
                yield new DateRange(previousStart,
                        previousPeriodEnd.isAfter(previousMonthEnd)
                                ? previousMonthEnd
                                : previousPeriodEnd);
            }
            case "last-month" -> {
                LocalDate previousStart = start.minusMonths(1).withDayOfMonth(1);
                yield new DateRange(previousStart, previousStart.plusMonths(1).minusDays(1));
            }
            case "last-3-months" -> new DateRange(
                    start.minusMonths(3), end.minusMonths(3));
            case "last-6-months" -> new DateRange(
                    start.minusMonths(6), end.minusMonths(6));
            case "this-year" -> new DateRange(start.minusYears(1), end.minusYears(1));
            default -> {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                yield new DateRange(previousEnd.minusDays(days - 1), previousEnd);
            }
        };
    }

    private List<CategoryMetric> createCategoryMetrics(
            Map<String, BigDecimal> totals,
            BigDecimal personalShare) {
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new CategoryMetric(
                        entry.getKey(),
                        money(entry.getValue()),
                        personalShare.signum() == 0
                                ? ZERO
                                : entry.getValue().multiply(HUNDRED)
                                        .divide(personalShare, 1, RoundingMode.HALF_UP)))
                .toList();
    }

    private List<MonthlyMetric> createMonthlyMetrics(
            LocalDate start,
            LocalDate end,
            Map<YearMonth, BigDecimal> totals) {
        List<MonthlyMetric> metrics = new ArrayList<>();
        YearMonth month = YearMonth.from(start);
        YearMonth lastMonth = YearMonth.from(end);
        while (!month.isAfter(lastMonth)) {
            String label = month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                    + " " + month.getYear();
            metrics.add(new MonthlyMetric(label, money(totals.getOrDefault(month, ZERO))));
            month = month.plusMonths(1);
        }
        return metrics;
    }

    private List<CategoryChange> createCategoryChanges(
            Map<String, BigDecimal> current,
            Map<String, BigDecimal> previous,
            boolean comparisonComplete) {
        Set<String> names = new java.util.TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(current.keySet());
        names.addAll(previous.keySet());
        return names.stream()
                .map(name -> {
                    BigDecimal currentTotal = current.getOrDefault(name, ZERO);
                    BigDecimal previousTotal = previous.getOrDefault(name, ZERO);
                    BigDecimal percent = comparisonComplete
                            ? percentageChange(currentTotal, previousTotal)
                            : null;
                    String summary = !comparisonComplete
                            ? name + " comparison is unavailable because some expense shares "
                                    + "are incomplete."
                            : percent == null
                            ? (currentTotal.signum() == 0
                                    ? name + " had no spending in either period."
                                    : name + " spending started this period; a percentage "
                                            + "change is unavailable because the previous "
                                            + "period was zero.")
                            : name + " spending " + (percent.signum() > 0 ? "increased " : "decreased ")
                                    + percent.abs().toPlainString() + "% compared with the "
                                    + "previous period.";
                    return new CategoryChange(
                            name, money(currentTotal), money(previousTotal), percent, summary);
                })
                .toList();
    }

    private List<SpendingAlert> createAlerts(
            BigDecimal current,
            BigDecimal previous,
            BigDecimal percent,
            List<ExpenseShare> selectedShares,
            Map<String, List<BigDecimal>> historicalShares,
            List<CategoryChange> categoryChanges) {
        List<SpendingAlert> alerts = new ArrayList<>();
        if (percent != null && percent.compareTo(new BigDecimal("50")) >= 0
                && current.subtract(previous).compareTo(new BigDecimal("1000")) >= 0) {
            alerts.add(new SpendingAlert(
                    "Monthly spending rose sharply",
                    "Your share is " + percent.toPlainString()
                            + "% higher than the comparison period."));
        }

        categoryChanges.stream()
                .filter(change -> change.getPreviousTotal().signum() == 0
                        ? change.getCurrentTotal().compareTo(new BigDecimal("1000")) >= 0
                        : change.getChangePercent() != null
                                && change.getChangePercent().compareTo(new BigDecimal("50")) >= 0
                                && change.getCurrentTotal().subtract(change.getPreviousTotal())
                                        .compareTo(new BigDecimal("500")) >= 0)
                .sorted(Comparator.comparing(CategoryChange::getChangePercent).reversed())
                .limit(3)
                .forEach(change -> alerts.add(new SpendingAlert(
                        change.getName() + " spending increased",
                        change.getSummary())));

        selectedShares.stream()
                .filter(item -> {
                    List<BigDecimal> previousShares =
                            historicalShares.getOrDefault(item.category(), List.of());
                    if (previousShares.size() < 5) {
                        return false;
                    }
                    BigDecimal median = median(previousShares);
                    return median.signum() > 0
                            && item.amount().compareTo(median.multiply(new BigDecimal("3"))
                                    .max(median.add(new BigDecimal("500")))) >= 0;
                })
                .sorted(Comparator.comparing(ExpenseShare::amount).reversed())
                .limit(3)
                .forEach(item -> alerts.add(new SpendingAlert(
                        "A larger-than-usual " + item.category() + " share",
                        "Your share for “" + safeDescription(item.expense())
                                + "” was ₹" + item.amount().toPlainString()
                                + ", at least three times your historical category median.")));
        return List.copyOf(alerts);
    }

    private BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return sorted.get(middle - 1).add(sorted.get(middle))
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentageChange(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(HUNDRED)
                .divide(previous, 1, RoundingMode.HALF_UP);
    }

    private String comparisonSummary(
            BigDecimal current,
            BigDecimal previous,
            BigDecimal percent) {
        if (previous.signum() == 0) {
            return current.signum() == 0
                    ? "No spending was recorded in either period."
                    : "No spending was recorded in the comparison period; percentage change "
                            + "is unavailable.";
        }
        String direction = current.compareTo(previous) > 0 ? "increased"
                : current.compareTo(previous) < 0 ? "decreased" : "was unchanged";
        return percent == null
                ? "Your share " + direction + " compared with the previous period."
                : "Your share " + direction + " by " + percent.abs().toPlainString()
                        + "% compared with the previous period.";
    }

    private SpendingInsightsDto emptyInsights(
            LocalDate startDate,
            LocalDate endDate,
            String historyMessage) {
        return new SpendingInsightsDto(
                startDate, endDate, ZERO, ZERO, ZERO, ZERO, 0, null,
                null, "No expense data is available for this period.",
                false, historyMessage, false, false,
                List.of(),
                createMonthlyMetrics(startDate, endDate, Map.of()),
                List.of(), List.of());
    }

    private String category(String value) {
        return value == null || value.isBlank() ? "Uncategorized" : value.trim();
    }

    private int monthCount(LocalDate start, LocalDate end) {
        return Math.toIntExact(ChronoUnit.MONTHS.between(
                YearMonth.from(start), YearMonth.from(end)) + 1);
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String safeDescription(Expense expense) {
        String description = expense.getDescription();
        return description == null || description.isBlank()
                ? "expense"
                : description.replaceAll("[\\r\\n\\p{Cntrl}]", " ").trim();
    }

    private record DateRange(LocalDate start, LocalDate end) {
    }

    private record ExpenseShare(
            String category,
            BigDecimal amount,
            LocalDate date,
            Expense expense) {
    }
}
