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


    // Show Add Expense page
    @GetMapping("/group/{groupId}/expense/add")
    public String addExpensePage(
            @PathVariable Integer groupId,
            Model model) {

        List<GroupMember> members =
                groupMemberService.getMembers(groupId);

        List<User> users = new ArrayList<>();

        for (GroupMember member : members) {

            User user =
                    userService.getUserById(
                            member.getId().getUserId()
                    );

            if (user != null) {
                users.add(user);
            }
        }

        model.addAttribute("groupId", groupId);
        model.addAttribute("users", users);

        return "add-expense";
    }


    // Save Expense
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
                (User) session.getAttribute("loggedInUser");

        if (loggedInUser == null) {
            return "redirect:/login";
        }


        // Create expense
        Expense expense = new Expense();

        expense.setGroupId(groupId);

        expense.setPaidBy(
                loggedInUser.getId()
        );

        expense.setAmount(amount);

        expense.setDescription(description);


        // Save expense
        Expense savedExpense =
                expenseService.addExpense(expense);


        // Equal split
        if (splitType.equals("equal")) {

            expenseService.createEqualSplits(
                    savedExpense
            );
        }


        // Custom split
        else if (splitType.equals("custom")) {

            Map<Integer, Double> splitAmounts =
                    new HashMap<>();

            double total = 0;


            for (String key : params.keySet()) {

                if (!key.startsWith("amount_")) {
                    continue;
                }

                String userIdString =
                        key.substring("amount_".length());

                try {

                    Integer userId =
                            Integer.parseInt(userIdString);

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

                    total += userAmount;

                } catch (NumberFormatException ignored) {
                }
            }


            // Check total
            if (Math.abs(total - amount) > 0.01) {

                model.addAttribute(
                        "error",
                        "Split amounts must add up to ₹"
                                + amount
                );

                model.addAttribute(
                        "groupId",
                        groupId
                );

                List<GroupMember> members =
                        groupMemberService.getMembers(groupId);

                List<User> users = new ArrayList<>();

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


            expenseService.createCustomSplits(
                    savedExpense,
                    splitAmounts
            );
        }


        return "redirect:/group/" + groupId;
    }


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