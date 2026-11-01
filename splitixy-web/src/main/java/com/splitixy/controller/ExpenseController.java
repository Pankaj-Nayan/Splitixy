package com.splitixy.controller;

import com.splitixy.entity.Expense;
import com.splitixy.entity.GroupMember;
import com.splitixy.entity.User;
import com.splitixy.service.ExpenseHistoryItem;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
public class ExpenseController {

    private final ExpenseService expenseService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    public ExpenseController(
            ExpenseService expenseService,
            GroupMemberService groupMemberService,
            UserService userService) {
        this.expenseService = expenseService;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
    }

    @GetMapping("/group/{groupId}/expense/add")
    public String addExpensePage(
            @PathVariable Integer groupId,
            HttpSession session,
            Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!groupMemberService.isMember(groupId, user.getId())) {
            return "redirect:/my-groups";
        }

        List<GroupMember> members = groupMemberService.getMembers(groupId);
        addExpenseFormModel(groupId, members, model);
        model.addAttribute("selectedPayerId", user.getId());
        model.addAttribute("splitType", "EQUAL");
        return "add-expense";
    }

    @PostMapping("/group/{groupId}/expense/add")
    public String addExpense(
            @PathVariable Integer groupId,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String splitType,
            @RequestParam(required = false) String paidBy,
            @RequestParam Map<String, String> params,
            HttpSession session,
            Model model) {
        User currentUser = getSessionUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!groupMemberService.isMember(groupId, currentUser.getId())) {
            return "redirect:/my-groups";
        }

        List<GroupMember> members = groupMemberService.getMembers(groupId);
        Map<Integer, BigDecimal> shares = new HashMap<>();
        Map<String, String> displayedShares = new HashMap<>();
        BigDecimal parsedAmount = null;
        Integer payerId = currentUser.getId();
        try {
            if (amount == null || amount.isBlank()) {
                throw new NumberFormatException();
            }
            parsedAmount = new BigDecimal(amount);
            if (paidBy != null && !paidBy.isBlank()) {
                payerId = Integer.valueOf(paidBy);
            }
            if ("CUSTOM".equals(splitType)) {
                for (Map.Entry<String, String> parameter : params.entrySet()) {
                    if (!parameter.getKey().startsWith("amount_")) {
                        continue;
                    }
                    String memberId = parameter.getKey().substring("amount_".length());
                    displayedShares.put(memberId, parameter.getValue());
                    if (parameter.getValue() != null && !parameter.getValue().isBlank()) {
                        shares.put(Integer.valueOf(memberId),
                                new BigDecimal(parameter.getValue()));
                    }
                }
            }
        } catch (NumberFormatException exception) {
            addExpenseFormModel(groupId, members, model);
            addFormValues(model, amount, description, splitType, paidBy, displayedShares);
            model.addAttribute("error", "Enter a valid amount, payer, and member share.");
            return "add-expense";
        }

        Expense expense = new Expense();
        expense.setGroupId(groupId);
        expense.setPaidBy(payerId);
        expense.setCreatedBy(currentUser.getId());
        expense.setAmount(parsedAmount);
        expense.setDescription(description);
        try {
            expenseService.createExpense(expense, splitType, shares);
            return "redirect:/group/" + groupId;
        } catch (IllegalArgumentException exception) {
            addExpenseFormModel(groupId, members, model);
            addFormValues(model, amount, description, splitType, paidBy, displayedShares);
            model.addAttribute("error", exception.getMessage());
            return "add-expense";
        }
    }

    @GetMapping("/group/{groupId}/expenses")
    public String groupExpenseHistory(
            @PathVariable Integer groupId,
            HttpSession session,
            Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!groupMemberService.isMember(groupId, user.getId())) {
            return "redirect:/my-groups";
        }
        List<ExpenseHistoryItem> history = expenseService.getGroupHistory(groupId);
        model.addAttribute("history", history);
        model.addAttribute("groupId", groupId);
        return "expense-history";
    }

    @GetMapping("/history")
    public String personalExpenseHistory(HttpSession session, Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("history", expenseService.getPersonalHistory(user.getId()));
        model.addAttribute("personalHistory", true);
        return "expense-history";
    }

    private User getSessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (currentUser instanceof User user && user.getId() != null) {
            return user;
        }
        return null;
    }

    private void addExpenseFormModel(
            Integer groupId,
            List<GroupMember> members,
            Model model) {
        List<User> users = new ArrayList<>();
        for (GroupMember member : members) {
            User user = userService.getUserById(member.getId().getUserId());
            if (user != null) {
                users.add(user);
            }
        }
        model.addAttribute("groupId", groupId);
        model.addAttribute("users", users);
    }

    private void addFormValues(
            Model model,
            String amount,
            String description,
            String splitType,
            String paidBy,
            Map<String, String> shares) {
        model.addAttribute("amountValue", amount);
        model.addAttribute("descriptionValue", description);
        model.addAttribute("splitType", splitType);
        model.addAttribute("selectedPayerId", paidBy);
        model.addAttribute("customShares", shares);
    }
}
