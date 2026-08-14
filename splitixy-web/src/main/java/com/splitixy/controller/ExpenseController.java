package com.splitixy.controller;

import com.splitixy.entity.Expense;
import com.splitixy.entity.User;
import com.splitixy.service.ExpenseService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    @GetMapping("/group/{groupId}/expense/add")
    public String addExpensePage(@PathVariable Integer groupId,
                                 Model model) {

        model.addAttribute("groupId", groupId);

        return "add-expense";
    }

    @PostMapping("/group/{groupId}/expense/add")
    public String addExpense(@PathVariable Integer groupId,
                             @RequestParam Double amount,
                             @RequestParam String description,
                             HttpSession session) {

        User user = (User) session.getAttribute("loggedInUser");

        if (user == null) {
            return "redirect:/login";
        }

        Expense expense = new Expense();

        expense.setGroupId(groupId);
        expense.setPaidBy(user.getId());
        expense.setAmount(amount);
        expense.setDescription(description);

        expenseService.addExpense(expense);

        return "redirect:/group/" + groupId;
    }

    @GetMapping("/group/{groupId}/expenses")
    public String viewExpenses(@PathVariable Integer groupId,
                               Model model) {

        List<Expense> expenses =
                expenseService.getExpensesByGroup(groupId);

        model.addAttribute("expenses", expenses);
        model.addAttribute("groupId", groupId);

        return "expenses";
    }
}