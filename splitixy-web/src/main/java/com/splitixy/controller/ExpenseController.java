package com.splitixy.controller;

import com.splitixy.entity.Expense;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.User;

import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;

import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    @Autowired
    private GroupMemberService groupMemberService;

    @Autowired
    private UserService userService;


    // =========================
    // ADD EXPENSE PAGE
    // =========================

    @GetMapping("/group/{groupId}/expense/add")
    public String addExpensePage(
            @PathVariable Integer groupId,
            HttpSession session,
            Model model) {


        User loggedInUser =
                (User) session.getAttribute(
                        "loggedInUser"
                );


        if (loggedInUser == null) {

            return "redirect:/login";
        }


        // Check whether logged-in user
        // belongs to this group

        List<GroupMember> members =
                groupMemberService.getMembers(
                        groupId
                );


        boolean isMember = false;


        for (GroupMember member : members) {

            if (member.getId()
                    .getUserId()
                    .equals(loggedInUser.getId())) {

                isMember = true;
                break;
            }
        }


        if (!isMember) {

            return "redirect:/my-groups";
        }


        // Get users
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


        model.addAttribute(
                "groupId",
                groupId
        );

        model.addAttribute(
                "users",
                users
        );


        return "add-expense";
    }


    // =========================
    // SAVE EXPENSE
    // =========================

    @PostMapping("/group/{groupId}/expense/add")
    public String addExpense(
            @PathVariable Integer groupId,
            @RequestParam Double amount,
            @RequestParam String description,
            @RequestParam String splitType,
            @RequestParam Map<String, String> params,
            HttpSession session,
            Model model) {


        User loggedInUser =
                (User) session.getAttribute(
                        "loggedInUser"
                );


        if (loggedInUser == null) {

            return "redirect:/login";
        }


        // =========================
        // CHECK GROUP MEMBERSHIP
        // =========================

        List<GroupMember> members =
                groupMemberService.getMembers(
                        groupId
                );


        boolean isMember = false;


        for (GroupMember member : members) {

            if (member.getId()
                    .getUserId()
                    .equals(loggedInUser.getId())) {

                isMember = true;
                break;
            }
        }


        if (!isMember) {

            return "redirect:/my-groups";
        }


        // =========================
        // CREATE EXPENSE
        // =========================

        Expense expense =
                new Expense();

        expense.setGroupId(
                groupId
        );

        expense.setPaidBy(
                loggedInUser.getId()
        );

        expense.setAmount(
                amount
        );

        expense.setDescription(
                description
        );


        // =========================
        // EQUAL SPLIT
        // =========================

        if (splitType.equals("equal")) {

            expenseService.addEqualExpense(
                    expense
            );

            return "redirect:/group/" + groupId;
        }


        // =========================
        // CUSTOM SPLIT
        // =========================

        if (splitType.equals("custom")) {

            Map<Integer, Double> splitAmounts =
                    new HashMap<>();


            for (String key : params.keySet()) {

                if (!key.startsWith("amount_")) {

                    continue;
                }


                String userIdString =
                        key.substring(
                                "amount_".length()
                        );


                try {

                    Integer userId =
                            Integer.parseInt(
                                    userIdString
                            );


                    Double userAmount =
                            Double.parseDouble(
                                    params.get(key)
                            );


                    if (userAmount < 0) {

                        userAmount = 0.0;
                    }


                    splitAmounts.put(
                            userId,
                            userAmount
                    );


                } catch (NumberFormatException ignored) {

                }
            }


            try {

                expenseService.addCustomExpense(
                        expense,
                        splitAmounts
                );


                return "redirect:/group/"
                        + groupId;


            } catch (IllegalArgumentException e) {

                model.addAttribute(
                        "error",
                        e.getMessage()
                );


                model.addAttribute(
                        "groupId",
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


                model.addAttribute(
                        "users",
                        users
                );


                return "add-expense";
            }
        }


        return "redirect:/group/" + groupId;
    }


    // =========================
    // VIEW EXPENSES
    // =========================

    @GetMapping("/group/{groupId}/expenses")
    public String viewExpenses(
            @PathVariable Integer groupId,
            Model model) {


        List<Expense> expenses =
                expenseService.getExpensesByGroup(
                        groupId
                );


        model.addAttribute(
                "expenses",
                expenses
        );

        model.addAttribute(
                "groupId",
                groupId
        );


        return "expenses";
    }
}