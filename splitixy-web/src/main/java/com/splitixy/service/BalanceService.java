package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.User;
import com.splitixy.repository.ExpenseGroupRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import com.splitixy.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BalanceService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final ExpenseService expenseService;
    private final ExpenseSplitRepository expenseSplitRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ExpenseGroupRepository expenseGroupRepository;

    public BalanceService(
            ExpenseService expenseService,
            ExpenseSplitRepository expenseSplitRepository,
            GroupMemberRepository groupMemberRepository,
            UserRepository userRepository,
            ExpenseGroupRepository expenseGroupRepository) {
        this.expenseService = expenseService;
        this.expenseSplitRepository = expenseSplitRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.expenseGroupRepository = expenseGroupRepository;
    }

    public Map<Integer, BigDecimal> calculateBalances(Integer groupId) {
        List<GroupMember> members = groupMemberRepository.findByIdGroupId(groupId);
        Set<Integer> memberIds = members.stream()
                .map(member -> member.getId().getUserId())
                .collect(Collectors.toSet());
        Map<Integer, BigDecimal> balances = new LinkedHashMap<>();
        memberIds.stream().sorted().forEach(id -> balances.put(id, ZERO));

        for (Expense expense : expenseService.getExpensesByGroup(groupId)) {
            if (expense.getAmount() == null
                    || expense.getAmount().setScale(2, RoundingMode.HALF_UP).signum() <= 0
                    || expense.getPaidBy() == null
                    || !memberIds.contains(expense.getPaidBy())) {
                throw incompleteExpense("the payer or total is invalid");
            }

            List<ExpenseSplit> splits =
                    expenseSplitRepository.findByExpenseId(expense.getExpenseId());
            if (splits == null || splits.isEmpty()) {
                throw incompleteExpense("member shares have not been recorded");
            }

            BigDecimal shareTotal = ZERO;
            Set<Integer> splitUserIds = new HashSet<>();
            for (ExpenseSplit split : splits) {
                if (split.getAmountOwed() == null
                        || split.getAmountOwed().signum() < 0
                        || !memberIds.contains(split.getUserId())
                        || !splitUserIds.add(split.getUserId())) {
                    throw incompleteExpense("a member share is invalid");
                }
                BigDecimal shareAmount =
                        split.getAmountOwed().setScale(2, RoundingMode.HALF_UP);
                shareTotal = shareTotal.add(shareAmount);
                balances.compute(
                        split.getUserId(),
                        (id, balance) -> balance.subtract(shareAmount));
            }
            BigDecimal amount = expense.getAmount().setScale(2, RoundingMode.HALF_UP);
            if (shareTotal.compareTo(amount) != 0) {
                throw incompleteExpense("member shares do not equal the total");
            }
            balances.compute(
                    expense.getPaidBy(),
                    (id, balance) -> balance.add(amount));
        }

        balances.replaceAll((id, amount) -> amount.setScale(2, RoundingMode.HALF_UP));
        return balances;
    }

    public List<Debt> calculateDebts(Integer groupId) {
        Map<Integer, BigDecimal> balances = calculateBalances(groupId);
        if (balances.values().stream().reduce(ZERO, BigDecimal::add).signum() != 0) {
            throw new IncompleteExpenseDataException(
                    "Group balances do not reconcile; review the stored expense shares.");
        }

        List<PartyBalance> debtors = new ArrayList<>();
        List<PartyBalance> creditors = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : balances.entrySet()) {
            if (entry.getValue().signum() < 0) {
                debtors.add(new PartyBalance(
                        entry.getKey(), entry.getValue().negate(), displayName(entry.getKey())));
            } else if (entry.getValue().signum() > 0) {
                creditors.add(new PartyBalance(
                        entry.getKey(), entry.getValue(), displayName(entry.getKey())));
            }
        }
        Comparator<PartyBalance> largestFirst =
                Comparator.comparing(PartyBalance::amount).reversed()
                        .thenComparing(PartyBalance::userId);
        debtors.sort(largestFirst);
        creditors.sort(largestFirst);

        ExpenseGroup group = expenseGroupRepository.findById(groupId).orElse(null);
        String groupName = group == null ? "Unavailable group" : group.getGroupName();
        List<Debt> debts = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            PartyBalance debtor = debtors.get(debtorIndex);
            PartyBalance creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.amount().min(creditor.amount())
                    .setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() > 0) {
                debts.add(new Debt(
                        debtor.userId(),
                        creditor.userId(),
                        debtor.name(),
                        creditor.name(),
                        groupName,
                        amount));
            }
            debtors.set(debtorIndex, debtor.withAmount(debtor.amount().subtract(amount)));
            creditors.set(creditorIndex, creditor.withAmount(creditor.amount().subtract(amount)));
            if (debtors.get(debtorIndex).amount().signum() == 0) {
                debtorIndex++;
            }
            if (creditors.get(creditorIndex).amount().signum() == 0) {
                creditorIndex++;
            }
        }
        if (debtorIndex != debtors.size() || creditorIndex != creditors.size()) {
            throw new IncompleteExpenseDataException(
                    "Settlement amounts could not be reconciled to the cent.");
        }
        return debts;
    }

    public PersonalSummary calculatePersonalSummary(Integer userId) {
        List<Integer> groupIds = groupMemberRepository.findByIdUserId(userId)
                .stream()
                .map(membership -> membership.getId().getGroupId())
                .distinct()
                .toList();

        BigDecimal totalPaid = ZERO;
        BigDecimal personalShares = ZERO;
        boolean complete = true;
        BigDecimal totalOwed = ZERO;
        BigDecimal totalReceivable = ZERO;
        List<Integer> validGroupIds = new ArrayList<>();
        for (Integer groupId : groupIds) {
            try {
                BigDecimal balance = calculateBalances(groupId).getOrDefault(userId, ZERO);
                validGroupIds.add(groupId);
                if (balance.signum() < 0) {
                    totalOwed = totalOwed.add(balance.negate());
                } else {
                    totalReceivable = totalReceivable.add(balance);
                }
            } catch (IncompleteExpenseDataException exception) {
                complete = false;
            }
        }
        for (Expense expense : expenseService.getExpensesForGroups(validGroupIds)) {
            if (userId.equals(expense.getPaidBy())) {
                totalPaid = totalPaid.add(
                        expense.getAmount().setScale(2, RoundingMode.HALF_UP));
            }
            for (ExpenseSplit share :
                    expenseSplitRepository.findByExpenseId(expense.getExpenseId())) {
                if (userId.equals(share.getUserId())) {
                    personalShares = personalShares.add(
                            share.getAmountOwed().setScale(2, RoundingMode.HALF_UP));
                }
            }
        }
        return new PersonalSummary(
                totalPaid.setScale(2, RoundingMode.HALF_UP),
                personalShares.setScale(2, RoundingMode.HALF_UP),
                totalOwed.setScale(2, RoundingMode.HALF_UP),
                totalReceivable.setScale(2, RoundingMode.HALF_UP),
                totalPaid.subtract(personalShares).setScale(2, RoundingMode.HALF_UP),
                complete);
    }

    private IncompleteExpenseDataException incompleteExpense(String reason) {
        return new IncompleteExpenseDataException(
                "A stored expense is incomplete: " + reason + ".");
    }

    private String displayName(Integer userId) {
        return userRepository.findById(userId)
                .map(user -> user.getFullName() != null && !user.getFullName().isBlank()
                        ? user.getFullName()
                        : user.getEmail())
                .filter(name -> name != null && !name.isBlank())
                .orElse("Former member");
    }

    private record PartyBalance(Integer userId, BigDecimal amount, String name) {
        PartyBalance withAmount(BigDecimal nextAmount) {
            return new PartyBalance(userId, nextAmount, name);
        }
    }

    public static class IncompleteExpenseDataException extends RuntimeException {
        public IncompleteExpenseDataException(String message) {
            super(message);
        }
    }

    public static class PersonalSummary {
        private final BigDecimal totalPaid;
        private final BigDecimal personalShares;
        private final BigDecimal totalOwed;
        private final BigDecimal totalReceivable;
        private final BigDecimal netBalance;
        private final boolean complete;

        public PersonalSummary(
                BigDecimal totalPaid,
                BigDecimal personalShares,
                BigDecimal totalOwed,
                BigDecimal totalReceivable,
                BigDecimal netBalance,
                boolean complete) {
            this.totalPaid = totalPaid;
            this.personalShares = personalShares;
            this.totalOwed = totalOwed;
            this.totalReceivable = totalReceivable;
            this.netBalance = netBalance;
            this.complete = complete;
        }

        public BigDecimal getTotalPaid() {
            return totalPaid;
        }

        public BigDecimal getPersonalShares() {
            return personalShares;
        }

        public BigDecimal getTotalOwed() {
            return totalOwed;
        }

        public BigDecimal getTotalReceivable() {
            return totalReceivable;
        }

        public BigDecimal getNetBalance() {
            return netBalance;
        }

        public boolean isComplete() {
            return complete;
        }
    }

    public static class Debt {
        private final Integer debtorId;
        private final Integer creditorId;
        private final String debtor;
        private final String creditor;
        private final String groupName;
        private final BigDecimal amount;

        public Debt(
                Integer debtorId,
                Integer creditorId,
                String debtor,
                String creditor,
                String groupName,
                BigDecimal amount) {
            this.debtorId = debtorId;
            this.creditorId = creditorId;
            this.debtor = debtor;
            this.creditor = creditor;
            this.groupName = groupName;
            this.amount = amount;
        }

        public Integer getDebtorId() {
            return debtorId;
        }

        public Integer getCreditorId() {
            return creditorId;
        }

        public String getDebtor() {
            return debtor;
        }

        public String getCreditor() {
            return creditor;
        }

        public String getGroupName() {
            return groupName;
        }

        public BigDecimal getAmount() {
            return amount;
        }
    }
}
