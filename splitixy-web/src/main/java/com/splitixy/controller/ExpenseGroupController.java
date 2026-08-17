package com.splitixy.controller;

import com.splitixy.entity.Expense;
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
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    public String createGroupPage() {
        return "create-group";
    }


    // Create Group
    @PostMapping("/create-group")
    public String createGroup(@RequestParam String groupName,
                              HttpSession session) {

        User user =
                (User) session.getAttribute("loggedInUser");

        ExpenseGroup expenseGroup =
                new ExpenseGroup();

        expenseGroup.setGroupName(groupName);

        expenseGroup.setCreatedBy(
                user.getId()
        );

        expenseGroupService.createGroup(
                expenseGroup
        );


        // Add creator as first member
        GroupMember groupMember =
                new GroupMember();

        GroupMemberId groupMemberId =
                new GroupMemberId(
                        expenseGroup.getGroupId(),
                        user.getId()
                );

        groupMember.setId(groupMemberId);

        groupMemberService.addMember(
                groupMember
        );


        return "redirect:/dashboard";
    }


    // My Groups
    @GetMapping("/my-groups")
    public String myGroups(HttpSession session,
                           Model model) {

        User user =
                (User) session.getAttribute(
                        "loggedInUser"
                );

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
            Model model) {


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

        List<User> allUsers =
                userService.getAllUsers();


        // -------------------------
        // EXPENSES
        // -------------------------

        List<Expense> expenses =
                expenseService.getExpensesByGroup(
                        groupId
                );


        // -------------------------
        // BALANCES
        // -------------------------

        Map<Integer, Double> balances =
                balanceService.calculateBalances(
                        groupId
                );


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

        model.addAttribute(
                "expenses",
                expenses
        );

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
            @RequestParam Integer userId) {


        GroupMember groupMember =
                new GroupMember();

        GroupMemberId id =
                new GroupMemberId(
                        groupId,
                        userId
                );

        groupMember.setId(id);

        groupMemberService.addMember(
                groupMember
        );


        return "redirect:/group/" + groupId;
    }

}