package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.entity.User;
import com.splitixy.repository.ExpenseGroupRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import com.splitixy.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private ExpenseService expenseService;
    @Mock
    private ExpenseSplitRepository expenseSplitRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ExpenseGroupRepository expenseGroupRepository;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(
                expenseService,
                expenseSplitRepository,
                groupMemberRepository,
                userRepository,
                expenseGroupRepository);
        when(groupMemberRepository.findByIdGroupId(10))
                .thenReturn(List.of(member(1), member(2)));
    }

    private void mockNamesAndGroup() {
        when(expenseGroupRepository.findById(10))
                .thenReturn(Optional.of(group()));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Pankaj Nayan")));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, "Mew")));
    }

    @Test
    void equalSplitCreatesExpectedBalancesAndNamedSettlement() {
        mockNamesAndGroup();
        Expense expense = expense(1, 1500, 1);
        when(expenseService.getExpensesByGroup(10)).thenReturn(List.of(expense));
        when(expenseSplitRepository.findByExpenseId(1))
                .thenReturn(List.of(share(1, 750), share(2, 750)));

        assertEquals(
                Map.of(1, new BigDecimal("750.00"), 2, new BigDecimal("-750.00")),
                balanceService.calculateBalances(10));

        BalanceService.Debt debt = balanceService.calculateDebts(10).getFirst();
        assertEquals("Mew", debt.getDebtor());
        assertEquals("Pankaj Nayan", debt.getCreditor());
        assertEquals(new BigDecimal("750.00"), debt.getAmount());
        assertEquals("Trip", debt.getGroupName());
    }

    @Test
    void multipleExpensesAndCustomSharesAggregateExactly() {
        mockNamesAndGroup();
        Expense first = expense(1, 1500, 1);
        Expense second = expense(2, 100, 2);
        when(expenseService.getExpensesByGroup(10)).thenReturn(List.of(first, second));
        when(expenseSplitRepository.findByExpenseId(1))
                .thenReturn(List.of(share(1, 750), share(2, 750)));
        when(expenseSplitRepository.findByExpenseId(2))
                .thenReturn(List.of(share(1, 30), share(2, 70)));

        assertEquals(
                Map.of(1, new BigDecimal("720.00"), 2, new BigDecimal("-720.00")),
                balanceService.calculateBalances(10));
        assertEquals(new BigDecimal("720.00"),
                balanceService.calculateDebts(10).getFirst().getAmount());
    }

    @Test
    void splitsMustReconcileAndMissingSharesAreNotReportedAsSettled() {
        Expense expense = expense(1, 1500, 1);
        when(expenseService.getExpensesByGroup(10)).thenReturn(List.of(expense));
        when(expenseSplitRepository.findByExpenseId(1))
                .thenReturn(List.of(share(1, 700), share(2, 750)));

        assertThrows(
                BalanceService.IncompleteExpenseDataException.class,
                () -> balanceService.calculateDebts(10));
    }

    @Test
    void singleMemberSettledExpenseProducesNoTransfers() {
        when(groupMemberRepository.findByIdGroupId(10)).thenReturn(List.of(member(1)));
        when(expenseGroupRepository.findById(10)).thenReturn(Optional.of(group()));
        when(expenseService.getExpensesByGroup(10)).thenReturn(List.of(expense(1, 100, 1)));
        when(expenseSplitRepository.findByExpenseId(1)).thenReturn(List.of(share(1, 100)));

        assertTrue(balanceService.calculateDebts(10).isEmpty());
        assertEquals(Map.of(1, new BigDecimal("0.00")), balanceService.calculateBalances(10));
    }

    @Test
    void multiMemberSettlementReconcilesAndUsesCorrectParties() {
        when(groupMemberRepository.findByIdGroupId(10))
                .thenReturn(List.of(member(1), member(2), member(3), member(4)));
        when(expenseService.getExpensesByGroup(10)).thenReturn(List.of(expense(1, 100, 1)));
        when(expenseSplitRepository.findByExpenseId(1))
                .thenReturn(List.of(
                        share(1, 0), share(2, 50), share(3, 25), share(4, 25)));
        when(expenseGroupRepository.findById(10)).thenReturn(Optional.of(group()));
        when(userRepository.findById(1)).thenReturn(Optional.of(user(1, "Payer")));
        when(userRepository.findById(2)).thenReturn(Optional.of(user(2, "Debtor A")));
        when(userRepository.findById(3)).thenReturn(Optional.of(user(3, "Debtor B")));
        when(userRepository.findById(4)).thenReturn(Optional.of(user(4, "Debtor C")));

        List<BalanceService.Debt> debts = balanceService.calculateDebts(10);

        assertEquals(3, debts.size());
        assertTrue(debts.stream().allMatch(debt ->
                debt.getCreditorId().equals(1) && debt.getCreditor().equals("Payer")));
        assertEquals(
                new BigDecimal("100.00"),
                debts.stream().map(BalanceService.Debt::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private GroupMember member(int userId) {
        GroupMember member = new GroupMember();
        member.setId(new GroupMemberId(10, userId));
        return member;
    }

    private Expense expense(int id, int amount, int payerId) {
        Expense expense = new Expense();
        expense.setExpenseId(id);
        expense.setGroupId(10);
        expense.setAmount(BigDecimal.valueOf(amount, 0).setScale(2));
        expense.setPaidBy(payerId);
        return expense;
    }

    private ExpenseSplit share(int userId, int amount) {
        ExpenseSplit split = new ExpenseSplit();
        split.setUserId(userId);
        split.setAmountOwed(BigDecimal.valueOf(amount, 0).setScale(2));
        return split;
    }

    private ExpenseGroup group() {
        ExpenseGroup group = new ExpenseGroup();
        group.setGroupId(10);
        group.setGroupName("Trip");
        return group;
    }

    private User user(int id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        return user;
    }
}
