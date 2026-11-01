package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.User;
import com.splitixy.repository.ExpenseGroupRepository;
import com.splitixy.repository.ExpenseRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import com.splitixy.service.ExpenseHistoryItem.Share;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private static final BigDecimal CENT = new BigDecimal("0.01");

    private final ExpenseRepository expenseRepository;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseGroupRepository expenseGroupRepository;
    private final UserService userService;

    public ExpenseService(
            ExpenseRepository expenseRepository,
            ExpenseSplitRepository expenseSplitRepository,
            GroupMemberRepository groupMemberRepository,
            ExpenseGroupRepository expenseGroupRepository,
            UserService userService) {
        this.expenseRepository = expenseRepository;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.expenseGroupRepository = expenseGroupRepository;
        this.userService = userService;
    }

    @Transactional
    public Expense createExpense(
            Expense expense,
            String splitType,
            Map<Integer, BigDecimal> requestedShares) {
        BigDecimal amount = normalizeAmount(expense.getAmount());
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Expense amount must be positive");
        }
        if (expense.getDescription() == null || expense.getDescription().isBlank()) {
            throw new IllegalArgumentException("Expense description is required");
        }
        if (expense.getDescription().trim().length() > 255) {
            throw new IllegalArgumentException("Description must be at most 255 characters");
        }
        if (!"EQUAL".equals(splitType) && !"CUSTOM".equals(splitType)) {
            throw new IllegalArgumentException("Select equal or custom split");
        }

        List<GroupMember> members =
                groupMemberRepository.findByIdGroupId(expense.getGroupId());
        if (members.isEmpty()) {
            throw new IllegalArgumentException("Group has no members");
        }
        Set<Integer> memberIds = members.stream()
                .map(member -> member.getId().getUserId())
                .collect(Collectors.toSet());
        if (expense.getPaidBy() == null || !memberIds.contains(expense.getPaidBy())) {
            throw new IllegalArgumentException("The selected payer is not a group member");
        }

        Map<Integer, BigDecimal> shares = "EQUAL".equals(splitType)
                ? calculateEqualShares(amount, members)
                : validateCustomShares(amount, requestedShares, memberIds);

        expense.setAmount(amount);
        expense.setDescription(expense.getDescription().trim());
        expense.setSplitType(splitType);
        Expense savedExpense = expenseRepository.save(expense);

        for (Map.Entry<Integer, BigDecimal> share : shares.entrySet()) {
            ExpenseSplit expenseSplit = new ExpenseSplit();
            expenseSplit.setExpenseId(savedExpense.getExpenseId());
            expenseSplit.setUserId(share.getKey());
            expenseSplit.setAmountOwed(share.getValue());
            expenseSplitRepository.save(expenseSplit);
        }
        return savedExpense;
    }

    public List<Expense> getExpensesByGroup(Integer groupId) {
        return expenseRepository.findByGroupIdOrderByExpenseDateDescExpenseIdDesc(groupId);
    }

    public List<Expense> getExpensesForGroups(List<Integer> groupIds) {
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return expenseRepository.findByGroupIdInOrderByExpenseDateDescExpenseIdDesc(groupIds);
    }

    public List<ExpenseHistoryItem> getGroupHistory(Integer groupId) {
        ExpenseGroup group = expenseGroupRepository.findById(groupId).orElse(null);
        String groupName = group == null ? "Unavailable group" : group.getGroupName();
        return createHistoryItems(
                getExpensesByGroup(groupId),
                Set.of(),
                groupName);
    }

    public List<ExpenseHistoryItem> getPersonalHistory(Integer userId) {
        List<Integer> groupIds = groupMemberRepository.findByIdUserId(userId)
                .stream()
                .map(membership -> membership.getId().getGroupId())
                .distinct()
                .toList();
        if (groupIds.isEmpty()) {
            return List.of();
        }
        return createHistoryItems(
                expenseRepository.findByGroupIdInOrderByExpenseDateDescExpenseIdDesc(groupIds),
                Set.of(userId),
                null);
    }

    public Expense getExpenseById(Integer expenseId) {
        return expenseRepository.findById(expenseId).orElse(null);
    }

    private Map<Integer, BigDecimal> validateCustomShares(
            BigDecimal expenseAmount,
            Map<Integer, BigDecimal> requestedShares,
            Set<Integer> memberIds) {
        if (requestedShares == null || requestedShares.isEmpty()) {
            throw new IllegalArgumentException("Enter at least one member share");
        }
        Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Map.Entry<Integer, BigDecimal> entry : requestedShares.entrySet()) {
            Integer userId = entry.getKey();
            BigDecimal share = entry.getValue();
            if (userId == null || !memberIds.contains(userId)) {
                throw new IllegalArgumentException("A selected share is not for a group member");
            }
            if (share == null || share.signum() < 0) {
                throw new IllegalArgumentException("Shares cannot be negative");
            }
            BigDecimal normalizedShare = normalizeAmount(share);
            shares.put(userId, normalizedShare);
            total = total.add(normalizedShare);
        }
        if (total.compareTo(expenseAmount) != 0) {
            throw new IllegalArgumentException("Member shares must equal the expense total");
        }
        return shares;
    }

    private Map<Integer, BigDecimal> calculateEqualShares(
            BigDecimal amount,
            List<GroupMember> members) {
        List<Integer> userIds = members.stream()
                .map(member -> member.getId().getUserId())
                .sorted()
                .toList();
        BigInteger totalCents = amount.movePointRight(2).toBigIntegerExact();
        BigInteger[] quotientAndRemainder =
                totalCents.divideAndRemainder(BigInteger.valueOf(userIds.size()));

        Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
        for (int i = 0; i < userIds.size(); i++) {
            BigInteger cents = quotientAndRemainder[0]
                    .add(i < quotientAndRemainder[1].intValueExact()
                            ? BigInteger.ONE
                            : BigInteger.ZERO);
            shares.put(userIds.get(i), new BigDecimal(cents, 2));
        }
        return shares;
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Expense amount is required");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.precision() > 19) {
            throw new IllegalArgumentException("Amount exceeds the supported currency range");
        }
        return normalized;
    }

    private List<ExpenseHistoryItem> createHistoryItems(
            List<Expense> expenses,
            Set<Integer> onlyUserIds,
            String knownGroupName) {
        if (expenses.isEmpty()) {
            return List.of();
        }

        List<Integer> expenseIds = expenses.stream()
                .map(Expense::getExpenseId)
                .toList();
        Map<Integer, List<ExpenseSplit>> splitsByExpense =
                expenseSplitRepository.findByExpenseIdIn(expenseIds).stream()
                        .collect(Collectors.groupingBy(ExpenseSplit::getExpenseId));
        Map<Integer, User> usersById = userService.getAllUsers().stream()
                .collect(Collectors.toMap(User::getId, user -> user, (first, ignored) -> first));
        Map<Integer, ExpenseGroup> groupsById = knownGroupName == null
                ? expenseGroupRepository.findAllById(
                                expenses.stream().map(Expense::getGroupId).distinct().toList())
                        .stream()
                        .collect(Collectors.toMap(ExpenseGroup::getGroupId, group -> group))
                : Map.of();

        List<ExpenseHistoryItem> history = new ArrayList<>();
        for (Expense expense : expenses) {
            List<ExpenseSplit> expenseSplits =
                    splitsByExpense.getOrDefault(expense.getExpenseId(), List.of());
            if (!onlyUserIds.isEmpty()
                    && !onlyUserIds.contains(expense.getPaidBy())
                    && expenseSplits.stream().noneMatch(
                            split -> onlyUserIds.contains(split.getUserId()))) {
                continue;
            }
            List<Share> shares = expenseSplits.stream()
                    .sorted(Comparator.comparing(ExpenseSplit::getUserId))
                    .map(split -> new Share(
                            displayName(usersById.get(split.getUserId())),
                            split.getAmountOwed()))
                    .toList();
            User payer = usersById.get(expense.getPaidBy());
            User creator = usersById.get(expense.getCreatedBy());
            ExpenseGroup group = groupsById.get(expense.getGroupId());
            history.add(new ExpenseHistoryItem(
                    expense.getExpenseId(),
                    expense.getGroupId(),
                    knownGroupName != null
                            ? knownGroupName
                            : group == null ? "Unavailable group" : group.getGroupName(),
                    expense.getDescription() == null
                            ? "Unavailable expense"
                            : expense.getDescription(),
                    expense.getAmount(),
                    expense.getExpenseDate(),
                    expense.getSplitType() == null ? "Legacy / unknown" : expense.getSplitType(),
                    displayName(payer),
                    creator == null || Objects.equals(expense.getCreatedBy(), expense.getPaidBy())
                            ? null
                            : displayName(creator),
                    shares));
        }
        return history;
    }

    private String displayName(User user) {
        if (user == null) {
            return "Former member";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        if (user.getEmail() != null && !user.getEmail().isBlank()) {
            return user.getEmail();
        }
        return "Former member";
    }
}
