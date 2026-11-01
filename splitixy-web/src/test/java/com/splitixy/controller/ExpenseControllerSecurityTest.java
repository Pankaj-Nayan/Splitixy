package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class ExpenseControllerSecurityTest {

    @Test
    void unauthenticatedRequestCannotCreateExpense() {
        ExpenseService expenseService = mock(ExpenseService.class);
        GroupMemberService membershipService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        ExpenseController controller =
                new ExpenseController(expenseService, membershipService, userService);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(null);

        String result = controller.addExpense(
                7, "100.00", "Dinner", "EQUAL", "1",
                Map.of(), session, new ExtendedModelMap());

        assertEquals("redirect:/login", result);
        verifyNoInteractions(expenseService, membershipService);
    }

    @Test
    void nonMemberCannotSubmitExpenseForGroup() {
        ExpenseService expenseService = mock(ExpenseService.class);
        GroupMemberService membershipService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        ExpenseController controller =
                new ExpenseController(expenseService, membershipService, userService);
        User user = new User();
        user.setId(5);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(user);
        when(membershipService.isMember(7, 5)).thenReturn(false);

        String result = controller.addExpense(
                7, "100.00", "Dinner", "EQUAL", "5",
                Map.of(), session, new ExtendedModelMap());

        assertEquals("redirect:/my-groups", result);
        verifyNoInteractions(expenseService);
    }
}
