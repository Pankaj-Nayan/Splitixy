package com.splitixy.controller;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.GroupMemberId;
import com.splitixy.entity.User;

import com.splitixy.service.BalanceService;
import com.splitixy.service.ExpenseGroupService;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class ExpenseGroupController {

    @Autowired
    private ExpenseGroupService expenseGroupService;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private UserService userService;

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private BalanceService balanceService;


    // Create Group Page
    @GetMapping("/create-group")
    public String createGroupPage(HttpSession session) {
        if (getSessionUser(session) == null) {
            return "redirect:/login";
        }
        return "create-group";
    }


    // Create Group
    @PostMapping("/create-group")
    public String createGroup(@RequestParam String groupName,
                              HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (groupName == null || groupName.isBlank()) {
            return "redirect:/create-group?error=invalid";
        }

        ExpenseGroup expenseGroup =
                new ExpenseGroup();

        expenseGroup.setGroupName(groupName.trim());
        expenseGroupService.createGroup(expenseGroup, user.getId());

        return "redirect:/dashboard";
    }


    // My Groups
    @GetMapping("/my-groups")
    public String myGroups(HttpSession session,
                           Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }

        List<ExpenseGroup> groups =
                expenseGroupService.getMyGroups(
                        user.getId()
                );

        model.addAttribute(
                "groups",
                groups
        );

        return "my-groups";
    }


    // Group Details
    @GetMapping("/group/{groupId}")
    public String groupDetails(
            @PathVariable Integer groupId,
            HttpSession session,
            Model model) {

        User currentUser = getSessionUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        ExpenseGroup group = expenseGroupService.getGroup(groupId);
        if (group == null
                || !groupMemberService.isMember(groupId, currentUser.getId())) {
            return "redirect:/my-groups";
        }

        // -------------------------
        // GROUP MEMBERS
        // -------------------------

        List<GroupMember> members =
                groupMemberService.getMembers(
                        groupId
                );

        List<User> users =
                new ArrayList<>();


        for (GroupMember member : members) {

            User user =
                    userService.getUserById(
                            member.getId().getUserId()
                    );

            if (user != null) {

                users.add(user);

            }
        }


        // -------------------------
        // ALL USERS
        // -------------------------

        Set<Integer> existingMemberIds = new HashSet<>();
        for (GroupMember member : members) {
            existingMemberIds.add(member.getId().getUserId());
        }
        List<User> allUsers = userService.getAllUsers().stream()
                .filter(user -> !existingMemberIds.contains(user.getId()))
                .toList();


        // -------------------------
        // EXPENSES
        // -------------------------

        List<com.splitixy.service.ExpenseHistoryItem> history =
                expenseService.getGroupHistory(groupId);


        // -------------------------
        // BALANCES
        // -------------------------

        Map<Integer, java.math.BigDecimal> balances = Map.of();
        List<BalanceService.Debt> debts = List.of();
        String accountingError = null;
        try {
            balances = balanceService.calculateBalances(groupId);
            debts = balanceService.calculateDebts(groupId);
        } catch (BalanceService.IncompleteExpenseDataException exception) {
            accountingError = exception.getMessage();
        }

        model.addAttribute("debts", debts);
        model.addAttribute("accountingError", accountingError);
        model.addAttribute("accountingComplete", accountingError == null);
        model.addAttribute("canManageMembers",
                currentUser.getId().equals(group.getCreatedBy()));
        model.addAttribute("group", group);


        // -------------------------
        // SEND DATA TO THYMELEAF
        // -------------------------

        model.addAttribute(
                "users",
                users
        );

        model.addAttribute(
                "allUsers",
                allUsers
        );

        model.addAttribute(
                "groupId",
                groupId
        );

        model.addAttribute("history", history);

        model.addAttribute(
                "balances",
                balances
        );


        return "group-details";
    }


    // Add Member
    @PostMapping("/group/{groupId}/add-member")
    public String addMember(
            @PathVariable Integer groupId,
            @RequestParam Integer userId,
            HttpSession session) {
        User currentUser = getSessionUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        com.splitixy.entity.ExpenseGroup group = expenseGroupService.getGroup(groupId);
        if (group == null
                || !groupMemberService.isMember(groupId, currentUser.getId())
                || !currentUser.getId().equals(group.getCreatedBy())
                || userService.getUserById(userId) == null) {
            return "redirect:/my-groups";
        }

        GroupMember groupMember =
                new GroupMember();

        GroupMemberId id =
                new GroupMemberId(
                        groupId,
                        userId
                );

        groupMember.setId(id);

        try {
            groupMemberService.addMember(groupMember);
        } catch (DataIntegrityViolationException exception) {
            if (!groupMemberService.isMember(groupId, userId)) {
                throw exception;
            }
        }

        return "redirect:/group/" + groupId;

    }

    private User getSessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (currentUser instanceof User user && user.getId() != null) {
            return user;
        }
        return null;
    }

}