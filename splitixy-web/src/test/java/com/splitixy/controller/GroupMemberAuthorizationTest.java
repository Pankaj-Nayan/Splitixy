package com.splitixy.controller;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.User;
import com.splitixy.service.BalanceService;
import com.splitixy.service.ExpenseGroupService;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class GroupMemberAuthorizationTest {

    @Test
    void groupMemberWhoIsNotCreatorCannotAddMembers() {
        ExpenseGroupService groups = mock(ExpenseGroupService.class);
        GroupMemberService memberships = mock(GroupMemberService.class);
        UserService users = mock(UserService.class);
        ExpenseGroupController controller = new ExpenseGroupController();
        ReflectionTestUtils.setField(controller, "expenseGroupService", groups);
        ReflectionTestUtils.setField(controller, "groupMemberService", memberships);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "expenseService", mock(ExpenseService.class));
        ReflectionTestUtils.setField(controller, "balanceService", mock(BalanceService.class));

        ExpenseGroup group = new ExpenseGroup();
        group.setGroupId(7);
        group.setCreatedBy(1);
        when(groups.getGroup(7)).thenReturn(group);
        when(memberships.isMember(7, 2)).thenReturn(true);
        User currentUser = new User();
        currentUser.setId(2);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(currentUser);

        assertEquals("redirect:/my-groups", controller.addMember(7, 3, session));
        verify(memberships, never()).addMember(any());
        verifyNoInteractions(users);
    }
}
