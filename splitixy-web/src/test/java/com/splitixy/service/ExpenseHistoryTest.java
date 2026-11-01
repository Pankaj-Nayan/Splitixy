package com.splitixy.service;

import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.ExpenseSplit;
import com.splitixy.entity.User;
import com.splitixy.repository.ExpenseGroupRepository;
import com.splitixy.repository.ExpenseRepository;
import com.splitixy.repository.ExpenseSplitRepository;
import com.splitixy.repository.GroupMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseHistoryTest {

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
    }

    @Test
    void groupHistoryUsesStoredPayerCreatorNamesSharesAndDate() {
        Expense expense = new Expense();
        expense.setExpenseId(3);
        expense.setGroupId(10);
        expense.setPaidBy(1);
        expense.setCreatedBy(2);
        expense.setDescription("Tickets");
        expense.setAmount(new BigDecimal("1500.00"));
        expense.setSplitType("EQUAL");
        expense.setExpenseDate(LocalDateTime.of(2026, 11, 1, 10, 30));

        ExpenseSplit first = share(1, "750.00");
        ExpenseSplit second = share(2, "750.00");
        when(expenseRepository.findByGroupIdOrderByExpenseDateDescExpenseIdDesc(10))
                .thenReturn(List.of(expense));
        when(expenseSplitRepository.findByExpenseIdIn(List.of(3)))
                .thenReturn(List.of(first, second));
        ExpenseGroup group = new ExpenseGroup();
        group.setGroupId(10);
        group.setGroupName("Trip");
        when(expenseGroupRepository.findById(10)).thenReturn(Optional.of(group));
        when(userService.getAllUsers()).thenReturn(
                List.of(user(1, "Pankaj Nayan"), user(2, "Mew")));

        ExpenseHistoryItem item = expenseService.getGroupHistory(10).getFirst();

        assertEquals("Trip", item.getGroupName());
        assertEquals("Pankaj Nayan", item.getPayerName());
        assertEquals("Mew", item.getCreatorName());
        assertEquals("EQUAL", item.getSplitType());
        assertEquals(expense.getExpenseDate(), item.getExpenseDate());
        assertEquals(List.of("Pankaj Nayan", "Mew"),
                item.getShares().stream().map(ExpenseHistoryItem.Share::getMemberName).toList());
        assertEquals(new BigDecimal("750.00"), item.getShares().getFirst().getAmount());
    }

    private ExpenseSplit share(int userId, String amount) {
        ExpenseSplit split = new ExpenseSplit();
        split.setExpenseId(3);
        split.setUserId(userId);
        split.setAmountOwed(new BigDecimal(amount));
        return split;
    }

    private User user(int id, String fullName) {
        User user = new User();
        user.setId(id);
        user.setFullName(fullName);
        return user;
    }
}
