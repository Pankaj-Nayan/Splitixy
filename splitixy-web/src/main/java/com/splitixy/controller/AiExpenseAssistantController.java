package com.splitixy.controller;

import com.splitixy.entity.Expense;
import com.splitixy.entity.User;
import com.splitixy.service.AiExpenseAssistantService;
import com.splitixy.service.AiExpenseAssistantService.Draft;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.GeminiExpenseExtractor;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Controller
public class AiExpenseAssistantController {

    private static final Logger log =
            LoggerFactory.getLogger(AiExpenseAssistantController.class);
    private static final String VIEW = "ai-expense-assistant";

    private final AiExpenseAssistantService assistantService;
    private final ExpenseService expenseService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;

    public AiExpenseAssistantController(
            AiExpenseAssistantService assistantService,
            ExpenseService expenseService,
            GroupMemberService groupMemberService,
            UserService userService) {
        this.assistantService = assistantService;
        this.expenseService = expenseService;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
    }

    @GetMapping("/group/{groupId}/expense/assistant")
    public String assistantPage(
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
        addPageModel(groupId, model);
        return VIEW;
    }

    @PostMapping("/group/{groupId}/expense/assistant/extract")
    public String extract(
            @PathVariable Integer groupId,
            @RequestParam(required = false) String prompt,
            HttpSession session,
            Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        if (!groupMemberService.isMember(groupId, user.getId())) {
            return "redirect:/my-groups";
        }

        addPageModel(groupId, model);
        try {
            Draft draft = assistantService.extract(prompt, groupUsers(groupId), user);
            session.setAttribute(multiplePayersSessionKey(groupId),
                    draft.isMultipleContributors());
            model.addAttribute("draft", draft);
            model.addAttribute("confirmation", true);
        } catch (AiExpenseAssistantService.RequestValidationException exception) {
            model.addAttribute("prompt", prompt);
            model.addAttribute("error", exception.getMessage());
        } catch (AiExpenseAssistantService.ExtractionException exception) {
            model.addAttribute("prompt", prompt);
            model.addAttribute("error",
                    "The assistant could not process this description. Please try again.");
        } catch (RuntimeException exception) {
            log.warn("Gemini expense extraction failed ({})",
                    exception.getClass().getSimpleName());
            model.addAttribute("prompt", prompt);
            model.addAttribute("error",
                    "The assistant could not process this description. Please try again.");
        }
        return VIEW;
    }

    @PostMapping("/group/{groupId}/expense/assistant/confirm")
    public String confirm(
            @PathVariable Integer groupId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String amount,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String expenseDate,
            @RequestParam(required = false) String paidBy,
            @RequestParam(required = false) String splitType,
            @RequestParam(name = "participantIds", required = false)
                    List<String> participantIds,
            @RequestParam Map<String, String> parameters,
            HttpSession session,
            Model model) {
        User currentUser = getSessionUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }
        if (!groupMemberService.isMember(groupId, currentUser.getId())) {
            return "redirect:/my-groups";
        }

        if (Boolean.TRUE.equals(session.getAttribute(multiplePayersSessionKey(groupId)))) {
            addPageModel(groupId, model);
            model.addAttribute("confirmation", true);
            model.addAttribute("requiresSinglePayerRevision", true);
            model.addAttribute("draft", submittedDraft(
                    description, amount, currency, category, expenseDate, null,
                    Set.of(), splitType, parameters));
            model.addAttribute("errors", List.of(
                    "This expense describes more than one payer, which the current expense "
                            + "ledger cannot represent. Revise the description to one payer "
                            + "or record separate expenses."));
            return VIEW;
        }

        List<User> members = groupUsers(groupId);
        List<String> errors = new ArrayList<>();
        BigDecimal parsedAmount = parseAmount(amount, errors);
        Integer payerId = parseMember(paidBy, members, "Select a payer from this group.", errors);
        Set<Integer> selectedParticipants = parseParticipants(
                participantIds, members, errors);
        LocalDate parsedDate = parseDate(expenseDate, errors);
        String normalizedCategory = normalizeCategory(category, errors);
        String normalizedCurrency = currency == null
                ? ""
                : currency.trim().toUpperCase(Locale.ROOT);
        if (!"INR".equals(normalizedCurrency)) {
            errors.add("Only INR expenses can currently be saved because Splitixy balances "
                    + "are rupee-based.");
        }
        if (description == null || description.isBlank()) {
            errors.add("Expense description is required.");
        } else if (description.trim().length() > 255) {
            errors.add("Description must be at most 255 characters.");
        }
        if (!"EQUAL".equals(splitType) && !"CUSTOM".equals(splitType)) {
            errors.add("Choose an equal or custom split.");
        }

        Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
        if ("CUSTOM".equals(splitType) && !selectedParticipants.isEmpty()) {
            for (Integer memberId : selectedParticipants) {
                String rawShare = parameters.get("share_" + memberId);
                BigDecimal share = parseShare(rawShare, errors);
                if (share != null) {
                    shares.put(memberId, share);
                }
            }
        }

        if (!errors.isEmpty()) {
            addPageModel(groupId, model);
            model.addAttribute("confirmation", true);
            model.addAttribute("draft", submittedDraft(
                    description, amount, normalizedCurrency, normalizedCategory,
                    expenseDate, payerId, selectedParticipants, splitType, parameters));
            model.addAttribute("errors", errors);
            return VIEW;
        }

        Expense expense = new Expense();
        expense.setGroupId(groupId);
        expense.setPaidBy(payerId);
        expense.setCreatedBy(currentUser.getId());
        expense.setAmount(parsedAmount);
        expense.setDescription(description.trim());
        expense.setExpenseDate(parsedDate.atStartOfDay());
        expense.setCurrency(normalizedCurrency);
        expense.setCategory(normalizedCategory);
        try {
            expenseService.createExpense(
                    expense, splitType, shares, selectedParticipants);
            session.removeAttribute(multiplePayersSessionKey(groupId));
            return "redirect:/group/" + groupId;
        } catch (IllegalArgumentException exception) {
            addPageModel(groupId, model);
            model.addAttribute("confirmation", true);
            model.addAttribute("draft", submittedDraft(
                    description, amount, normalizedCurrency, normalizedCategory,
                    expenseDate, payerId, selectedParticipants, splitType, parameters));
            model.addAttribute("errors", List.of(exception.getMessage()));
            return VIEW;
        }
    }

    private void addPageModel(Integer groupId, Model model) {
        model.addAttribute("groupId", groupId);
        model.addAttribute("users", groupUsers(groupId));
        model.addAttribute("categories", AiExpenseAssistantService.categories());
    }

    private List<User> groupUsers(Integer groupId) {
        return groupMemberService.getMembers(groupId).stream()
                .map(member -> userService.getUserById(member.getId().getUserId()))
                .filter(user -> user != null)
                .toList();
    }

    private User getSessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (currentUser instanceof User user && user.getId() != null) {
            return user;
        }
        return null;
    }

    private String multiplePayersSessionKey(Integer groupId) {
        return "aiExpenseAssistantMultiplePayers:" + groupId;
    }

    private BigDecimal parseAmount(String value, List<String> errors) {
        if (value == null || !value.matches("\\d{1,17}(\\.\\d{1,2})?")) {
            errors.add("Enter a positive amount with no more than two decimal places.");
            return null;
        }
        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            errors.add("Expense amount must be positive.");
            return null;
        }
        return amount;
    }

    private BigDecimal parseShare(String value, List<String> errors) {
        if (value == null || !value.matches("\\d{1,17}(\\.\\d{1,2})?")) {
            errors.add("Enter a valid custom share for every selected participant.");
            return null;
        }
        return new BigDecimal(value);
    }

    private Integer parseMember(
            String value,
            List<User> members,
            String error,
            List<String> errors) {
        try {
            Integer id = Integer.valueOf(value);
            if (members.stream().anyMatch(member -> id.equals(member.getId()))) {
                return id;
            }
        } catch (NumberFormatException | NullPointerException ignored) {
            // Report the same validation message for missing and invalid member ids.
        }
        errors.add(error);
        return null;
    }

    private Set<Integer> parseParticipants(
            List<String> values,
            List<User> members,
            List<String> errors) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                Integer id = parseMember(
                        value, members, "A selected participant is not in this group.", errors);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            errors.add("Select at least one group member who shares this expense.");
        }
        return ids;
    }

    private LocalDate parseDate(String value, List<String> errors) {
        try {
            if (value == null || value.isBlank()) {
                throw new DateTimeParseException("Missing date", "", 0);
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            errors.add("Choose a valid expense date.");
            return null;
        }
    }

    private String normalizeCategory(String value, List<String> errors) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AiExpenseAssistantService.categories().stream()
                .filter(categoryOption -> categoryOption.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseGet(() -> {
                    errors.add("Choose a category from the list.");
                    return null;
                });
    }

    private Draft submittedDraft(
            String description,
            String amount,
            String currency,
            String category,
            String date,
            Integer payerId,
            Set<Integer> participants,
            String splitType,
            Map<String, String> parameters) {
        Map<Integer, String> shares = new LinkedHashMap<>();
        for (Map.Entry<String, String> parameter : parameters.entrySet()) {
            if (!parameter.getKey().startsWith("share_")) {
                continue;
            }
            try {
                shares.put(
                        Integer.valueOf(parameter.getKey().substring("share_".length())),
                        parameter.getValue());
            } catch (NumberFormatException ignored) {
                // Ignore non-share form parameters.
            }
        }
        return new Draft(
                "",
                description == null ? "" : description,
                amount == null ? "" : amount,
                currency == null ? "" : currency,
                category == null ? "" : category,
                date == null ? "" : date,
                payerId,
                participants,
                splitType == null ? "" : splitType,
                shares,
                List.of(),
                List.of());
    }
}
