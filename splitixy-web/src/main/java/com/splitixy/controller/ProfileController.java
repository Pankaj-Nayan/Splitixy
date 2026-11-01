package com.splitixy.controller;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.User;
import com.splitixy.service.BalanceService;
import com.splitixy.service.ExpenseGroupService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class ProfileController {

    private final UserService userService;
    private final ExpenseGroupService expenseGroupService;
    private final BalanceService balanceService;

    public ProfileController(
            UserService userService,
            ExpenseGroupService expenseGroupService,
            BalanceService balanceService) {
        this.userService = userService;
        this.expenseGroupService = expenseGroupService;
        this.balanceService = balanceService;
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("user", user);
        model.addAttribute("summary", balanceService.calculatePersonalSummary(user.getId()));
        model.addAttribute("groups", expenseGroupService.getMyGroups(user.getId()));
        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(
            @RequestParam(required = false) String fullName,
            HttpSession session) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 120) {
            return "redirect:/profile?error=invalid-name";
        }
        User updated = userService.updateFullName(user.getId(), fullName);
        User sessionUser = new User();
        sessionUser.setId(updated.getId());
        sessionUser.setFullName(updated.getFullName());
        sessionUser.setEmail(updated.getEmail());
        session.setAttribute("loggedInUser", sessionUser);
        return "redirect:/profile?updated";
    }

    private User getSessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (currentUser instanceof User user && user.getId() != null) {
            return user;
        }
        return null;
    }
}
