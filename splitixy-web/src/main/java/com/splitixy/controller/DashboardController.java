package com.splitixy.controller;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.User;
import com.splitixy.service.BalanceService;
import com.splitixy.service.ExpenseGroupService;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
public class DashboardController {

    private final BalanceService balanceService;
    private final ExpenseGroupService expenseGroupService;

    public DashboardController(
            BalanceService balanceService,
            ExpenseGroupService expenseGroupService) {
        this.balanceService = balanceService;
        this.expenseGroupService = expenseGroupService;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (!(currentUser instanceof User user) || user.getId() == null) {
            return "redirect:/login";
        }
        List<ExpenseGroup> groups = expenseGroupService.getMyGroups(user.getId());
        List<BalanceService.Debt> debts = new ArrayList<>();
        boolean incomplete = false;
        for (ExpenseGroup group : groups) {
            try {
                for (BalanceService.Debt debt :
                        balanceService.calculateDebts(group.getGroupId())) {
                    if (user.getId().equals(debt.getDebtorId())
                            || user.getId().equals(debt.getCreditorId())) {
                        debts.add(debt);
                    }
                }
            } catch (BalanceService.IncompleteExpenseDataException exception) {
                incomplete = true;
            }
        }
        model.addAttribute("groups", groups);
        model.addAttribute("debts", debts);
        model.addAttribute("summary", balanceService.calculatePersonalSummary(user.getId()));
        model.addAttribute("accountingIncomplete", incomplete);
        return "dashboard";
    }
}