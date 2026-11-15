package com.splitixy.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.entity.User;
import com.splitixy.service.AiReceiptScannerService;
import com.splitixy.service.ExpenseGroupService;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.ReceiptExtraction;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReceiptScannerControllerTest {

    @Test
    void itemSplitAllocatesChargesByItemSubtotalsAndUsesExistingExpenseWorkflow()
            throws Exception {
        Fixture fixture = new Fixture();
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        String items = """
                [
                  {"name":"Pizza","quantity":1,"unitPrice":600,"totalPrice":600,
                   "discount":0,"participantIds":[1]},
                  {"name":"Burger","quantity":1,"unitPrice":300,"totalPrice":300,
                   "discount":0,"participantIds":[2]}
                ]
                """;

        String result = fixture.controller.confirm(
                "draft-1", 7, "Cafe", null, "2026-11-13", "Food & Dining",
                "INR", "900", "90", "30", "15", "0", "975", "",
                items, "ITEMS", null, "1", true, List.of("1", "2"),
                parameters, fixture.session, new ExtendedModelMap());

        assertEquals("redirect:/group/7", result);
        ArgumentCaptor<Expense> expense = ArgumentCaptor.forClass(Expense.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Integer, BigDecimal>> shares = ArgumentCaptor.forClass(Map.class);
        verify(fixture.expenseService).createExpense(
                expense.capture(), eq("CUSTOM"), shares.capture(), eq(Set.of(1, 2)));
        assertEquals(new BigDecimal("975.00"), expense.getValue().getAmount());
        assertEquals(new BigDecimal("650.00"), shares.getValue().get(1));
        assertEquals(new BigDecimal("325.00"), shares.getValue().get(2));
    }

    @Test
    void rejectsParticipantsOutsideTheSelectedGroup() throws Exception {
        Fixture fixture = new Fixture();

        String result = fixture.controller.confirm(
                "draft-1", 7, "Cafe", null, "2026-11-13", "Food & Dining",
                "INR", null, null, null, null, null, "100", "",
                "[]", "TOTAL", "EQUAL", "1", true, List.of("999"),
                new LinkedMultiValueMap<>(), fixture.session, new ExtendedModelMap());

        assertEquals("ai-receipt-scanner", result);
        verify(fixture.expenseService, never()).createExpense(
                any(Expense.class), any(), any(), any());
    }

    @Test
    void acceptsExplicitPerMemberChargeAllocationOverrides() throws Exception {
        Fixture fixture = new Fixture();
        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("allocation_tax_1", "40.00");
        parameters.add("allocation_tax_2", "50.00");
        String items = """
                [
                  {"name":"Pizza","quantity":1,"totalPrice":600,"discount":0,
                   "participantIds":[1]},
                  {"name":"Burger","quantity":1,"totalPrice":300,"discount":0,
                   "participantIds":[2]}
                ]
                """;

        String result = fixture.controller.confirm(
                "draft-1", 7, "Cafe", null, "2026-11-13", "Food & Dining",
                "INR", "900", "90", "30", "15", "0", "975", "",
                items, "ITEMS", null, "1", true, List.of("1", "2"),
                parameters, fixture.session, new ExtendedModelMap());

        assertEquals("redirect:/group/7", result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Integer, BigDecimal>> shares = ArgumentCaptor.forClass(Map.class);
        verify(fixture.expenseService).createExpense(
                any(Expense.class), eq("CUSTOM"), shares.capture(), eq(Set.of(1, 2)));
        assertEquals(new BigDecimal("630.00"), shares.getValue().get(1));
        assertEquals(new BigDecimal("345.00"), shares.getValue().get(2));
    }

    private static class Fixture {
        private final ExpenseService expenseService = mock(ExpenseService.class);
        private final GroupMemberService groupMemberService = mock(GroupMemberService.class);
        private final UserService userService = mock(UserService.class);
        private final ExpenseGroupService expenseGroupService = mock(ExpenseGroupService.class);
        private final HttpSession session = mock(HttpSession.class);
        private final AiReceiptScannerController controller;

        private Fixture() {
            User first = user(1, "Pankaj");
            User second = user(2, "Rahul");
            when(session.getAttribute("loggedInUser")).thenReturn(first);
            when(session.getAttribute("aiReceiptDraftToken")).thenReturn("draft-1");
            when(session.getAttribute("aiReceiptDraft")).thenReturn(new ReceiptExtraction(
                    "Cafe", null, "2026-11-13", null, "INR", "Food & Dining",
                    null, null, null, null, null, new BigDecimal("975"),
                    null, List.of(), List.of(), List.of(), List.of(), false));
            when(groupMemberService.isMember(7, 1)).thenReturn(true);
            when(groupMemberService.getMembers(7)).thenReturn(
                    List.of(member(7, 1), member(7, 2)));
            when(userService.getUserById(1)).thenReturn(first);
            when(userService.getUserById(2)).thenReturn(second);
            when(expenseGroupService.getMyGroups(1)).thenReturn(List.of());
            ExpenseGroup group = new ExpenseGroup();
            group.setGroupId(7);
            group.setGroupName("Friends");
            when(expenseGroupService.getGroup(7)).thenReturn(group);
            controller = new AiReceiptScannerController(
                    mock(AiReceiptScannerService.class),
                    expenseGroupService,
                    expenseService,
                    groupMemberService,
                    userService,
                    new ObjectMapper());
        }

        private static User user(Integer id, String name) {
            User user = new User();
            user.setId(id);
            user.setFullName(name);
            return user;
        }

        private static GroupMember member(Integer groupId, Integer userId) {
            GroupMember member = new GroupMember();
            member.setId(new GroupMemberId(groupId, userId));
            return member;
        }
    }
}
