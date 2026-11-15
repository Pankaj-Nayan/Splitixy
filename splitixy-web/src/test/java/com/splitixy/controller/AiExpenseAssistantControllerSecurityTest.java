package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.entity.Expense;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.service.AiExpenseAssistantService;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class AiExpenseAssistantControllerSecurityTest {

    @Test
    void unauthenticatedUserCannotOpenGroupAssistant() {
        AiExpenseAssistantService assistantService = mock(AiExpenseAssistantService.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        GroupMemberService membershipService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        AiExpenseAssistantController controller = new AiExpenseAssistantController(
                assistantService, expenseService, membershipService, userService);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(null);

        String result = controller.assistantPage(7, session, new ExtendedModelMap());

        assertEquals("redirect:/login", result);
        verifyNoInteractions(assistantService, expenseService, membershipService, userService);
    }

    @Test
    void nonMemberCannotExtractForAnotherGroupsAssistant() {
        AiExpenseAssistantService assistantService = mock(AiExpenseAssistantService.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        GroupMemberService membershipService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        AiExpenseAssistantController controller = new AiExpenseAssistantController(
                assistantService, expenseService, membershipService, userService);
        User user = new User();
        user.setId(5);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(user);
        when(membershipService.isMember(7, 5)).thenReturn(false);

        String result = controller.extract(
                7, "I paid 500", session, new ExtendedModelMap());

        assertEquals("redirect:/my-groups", result);
        verifyNoInteractions(assistantService, expenseService, userService);
    }

    @Test
    void multiplePayerDraftCannotBeSavedThroughTheConfirmationEndpoint() {
        AiExpenseAssistantService assistantService = mock(AiExpenseAssistantService.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        GroupMemberService membershipService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        AiExpenseAssistantController controller = new AiExpenseAssistantController(
                assistantService, expenseService, membershipService, userService);
        User user = new User();
        user.setId(5);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(user);
        when(session.getAttribute("aiExpenseAssistantMultiplePayers:7")).thenReturn(true);
        when(membershipService.isMember(7, 5)).thenReturn(true);
        when(membershipService.getMembers(7)).thenReturn(List.of());

        String result = controller.confirm(
                7, "Hotel", "1000", "INR", "Travel", "2026-11-12", "5",
                "EQUAL", java.util.List.of("5"), java.util.Map.of(), session,
                new ExtendedModelMap());

        assertEquals("ai-expense-assistant", result);
        verifyNoInteractions(assistantService, expenseService);
        verify(membershipService).getMembers(7);
    }

    @Test
    void confirmationUsesTheExistingExpenseCreationService() {
        AiExpenseAssistantService assistantService = mock(AiExpenseAssistantService.class);
        ExpenseService expenseService = mock(ExpenseService.class);
        GroupMemberService membershipService = mock(GroupMemberService.class);
        UserService userService = mock(UserService.class);
        AiExpenseAssistantController controller = new AiExpenseAssistantController(
                assistantService, expenseService, membershipService, userService);
        User payer = user(5, "Pankaj");
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(payer);
        when(membershipService.isMember(7, 5)).thenReturn(true);
        when(membershipService.getMembers(7)).thenReturn(List.of(member(7, 5), member(7, 6)));
        when(userService.getUserById(5)).thenReturn(payer);
        when(userService.getUserById(6)).thenReturn(user(6, "Rahul"));

        String result = controller.confirm(
                7,
                "Dinner",
                "500.00",
                "INR",
                "Food & Dining",
                "2026-11-12",
                "5",
                "EQUAL",
                List.of("5", "6"),
                Map.of(),
                session,
                new ExtendedModelMap());

        assertEquals("redirect:/group/7", result);
        ArgumentCaptor<Expense> expense = ArgumentCaptor.forClass(Expense.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<Integer>> participants = ArgumentCaptor.forClass(Set.class);
        verify(expenseService).createExpense(
                expense.capture(), eq("EQUAL"), eq(Map.of()), participants.capture());
        assertEquals(new BigDecimal("500.00"), expense.getValue().getAmount());
        assertEquals("INR", expense.getValue().getCurrency());
        assertEquals("Food & Dining", expense.getValue().getCategory());
        assertEquals(LocalDateTime.of(2026, 11, 12, 0, 0),
                expense.getValue().getExpenseDate());
        assertEquals(Set.of(5, 6), participants.getValue());
        verifyNoInteractions(assistantService);
    }

    private GroupMember member(int groupId, int userId) {
        GroupMember member = new GroupMember();
        member.setId(new GroupMemberId(groupId, userId));
        return member;
    }

    private User user(int id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        return user;
    }
}
