package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.repository.ExpenseGroupRepository;
import com.splitixy.repository.ExpenseRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private ExpenseSplitRepository expenseSplitRepository;
    @Mock
    private GroupMemberRepository groupMemberRepository;
    @Mock
    private ExpenseGroupRepository expenseGroupRepository;
    @Mock
    private UserService userService;

    private ExpenseService expenseService;

    @BeforeEach
    void setUp() {
        expenseService = new ExpenseService(
                expenseRepository,
                expenseSplitRepository,
                groupMemberRepository,
                expenseGroupRepository,
                userService);
        when(groupMemberRepository.findByIdGroupId(10))
                .thenReturn(List.of(member(1), member(2)));
    }

    @Test
    void equalSplitPreservesTotalAndUsesSelectedPayer() {
        Expense expense = expense("1500.00", 1);
        mockExpenseSave();

        expenseService.createExpense(expense, "EQUAL", Map.of());

        assertEquals(1, expense.getPaidBy());
        assertEquals(new BigDecimal("1500.00"), expense.getAmount());
        ArgumentCaptor<ExpenseSplit> splits = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(expenseSplitRepository, times(2)).save(splits.capture());
        assertEquals(
                List.of(new BigDecimal("750.00"), new BigDecimal("750.00")),
                splits.getAllValues().stream().map(ExpenseSplit::getAmountOwed).toList());
        assertEquals(150000L, splits.getAllValues().stream()
                .map(ExpenseSplit::getAmountOwed)
                .map(amount -> amount.movePointRight(2).longValueExact())
                .mapToLong(Long::longValue)
                .sum());
    }

    @Test
    void equalSplitAllocatesRemainderCentsExactly() {
        when(groupMemberRepository.findByIdGroupId(10))
                .thenReturn(List.of(member(1), member(2), member(3)));
        mockExpenseSave();

        expenseService.createExpense(expense("10.00", 1), "EQUAL", Map.of());

        ArgumentCaptor<ExpenseSplit> splits = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(expenseSplitRepository, times(3)).save(splits.capture());
        assertEquals(
                List.of(
                        new BigDecimal("3.34"),
                        new BigDecimal("3.33"),
                        new BigDecimal("3.33")),
                splits.getAllValues().stream().map(ExpenseSplit::getAmountOwed).toList());
    }

    @Test
    void customSplitPersistsProvidedSharesAndRejectsIncorrectTotals() {
        Expense expense = expense("100.00", 2);
        mockExpenseSave();
        expenseService.createExpense(
                expense,
                "CUSTOM",
                Map.of(1, new BigDecimal("25.00"), 2, new BigDecimal("75.00")));

        ArgumentCaptor<ExpenseSplit> splits = ArgumentCaptor.forClass(ExpenseSplit.class);
        verify(expenseSplitRepository, times(2)).save(splits.capture());
        assertEquals(new BigDecimal("100.00"), splits.getAllValues().stream()
                .map(ExpenseSplit::getAmountOwed)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        reset(expenseRepository, expenseSplitRepository);
        assertThrows(IllegalArgumentException.class, () -> expenseService.createExpense(
                expense("100.00", 2),
                "CUSTOM",
                Map.of(1, new BigDecimal("24.99"), 2, new BigDecimal("75.00"))));
        verify(expenseRepository, never()).save(any(Expense.class));
        verify(expenseSplitRepository, never()).save(any(ExpenseSplit.class));
    }

    @Test
    void rejectsPayerOrShareOutsideGroup() {
        assertThrows(IllegalArgumentException.class, () -> expenseService.createExpense(
                expense("10.00", 99),
                "EQUAL",
                Map.of()));
        assertThrows(IllegalArgumentException.class, () -> expenseService.createExpense(
                expense("10.00", 1),
                "CUSTOM",
                Map.of(99, new BigDecimal("10.00"))));
    }

    private GroupMember member(int userId) {
        GroupMember member = new GroupMember();
        member.setId(new GroupMemberId(10, userId));
        return member;
    }

    private Expense expense(String amount, int payerId) {
        Expense expense = new Expense();
        expense.setGroupId(10);
        expense.setPaidBy(payerId);
        expense.setCreatedBy(1);
        expense.setDescription("Test expense");
        expense.setAmount(new BigDecimal(amount));
        return expense;
    }

    private void mockExpenseSave() {
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setExpenseId(33);
            return expense;
        });
    }
}
