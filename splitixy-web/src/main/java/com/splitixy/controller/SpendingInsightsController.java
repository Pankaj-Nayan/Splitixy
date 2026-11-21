package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.service.AiSpendingInsightsService;
import com.splitixy.service.AiSpendingInsightsService.AiResult;
import com.splitixy.service.SpendingAnalyticsService;
import com.splitixy.service.SpendingInsightsDto;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Set;

@Controller
public class SpendingInsightsController {

    private static final String VIEW = "spending-insights";
    private static final Set<String> PRESETS = Set.of(
            "this-month", "last-month", "last-3-months", "last-6-months", "this-year");

    private final SpendingAnalyticsService analyticsService;
    private final AiSpendingInsightsService aiService;

    public SpendingInsightsController(
            SpendingAnalyticsService analyticsService,
            AiSpendingInsightsService aiService) {
        this.analyticsService = analyticsService;
        this.aiService = aiService;
    }

    @GetMapping("/spending-insights")
    public String page(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpSession session,
            Model model) {
        User user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Selection selection = resolveSelection(period, startDate, endDate);
        addPageModel(user.getId(), selection, model);
        return VIEW;
    }

    @PostMapping("/spending-insights/ai")
    public String generateAiInsights(
            @RequestParam(defaultValue = "this-month") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        User user = sessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        Selection selection = resolveSelection(period, startDate, endDate);
        if (selection.error() != null) {
            redirectAttributes.addFlashAttribute("analyticsError", selection.error());
        } else {
            try {
                SpendingInsightsDto metrics = analyticsService.analyze(
                        user.getId(),
                        selection.startDate(),
                        selection.endDate(),
                        selection.period());
                AiResult result = metrics.getExpenseCount() == 0
                        ? new AiResult(false,
                                "There are no shared expenses in this selection for the AI to "
                                        + "interpret.")
                        : aiService.generate(metrics);
                redirectAttributes.addFlashAttribute("aiInsights", result.text());
                redirectAttributes.addFlashAttribute("aiInsightAvailable", result.available());
            } catch (IllegalArgumentException exception) {
                redirectAttributes.addFlashAttribute("analyticsError", exception.getMessage());
            }
        }
        return redirectUrl(selection);
    }

    private void addPageModel(Integer userId, Selection selection, Model model) {
        model.addAttribute("selectedPeriod", selection.period());
        model.addAttribute("startDate", selection.startDate());
        model.addAttribute("endDate", selection.endDate());
        if (selection.error() != null) {
            model.addAttribute("analyticsError", selection.error());
            model.addAttribute("insights", emptyInsights(
                    selection.startDate(), selection.endDate()));
            return;
        }
        try {
            model.addAttribute("insights", analyticsService.analyze(
                    userId,
                    selection.startDate(),
                    selection.endDate(),
                    selection.period()));
        } catch (IllegalArgumentException exception) {
            model.addAttribute("analyticsError", exception.getMessage());
            model.addAttribute("insights", emptyInsights(
                    selection.startDate(), selection.endDate()));
        }
    }

    private Selection resolveSelection(String period, String rawStart, String rawEnd) {
        LocalDate today = LocalDate.now();
        if ("custom".equals(period)) {
            try {
                LocalDate start = LocalDate.parse(rawStart);
                LocalDate end = LocalDate.parse(rawEnd);
                return new Selection(period, start, end, null);
            } catch (DateTimeParseException | NullPointerException exception) {
                return new Selection(period, today.withDayOfMonth(1), today,
                        "Choose valid start and end dates for the custom range.");
            }
        }
        if (!PRESETS.contains(period)) {
            return new Selection("this-month", today.withDayOfMonth(1), today,
                    "Choose one of the available date filters.");
        }
        LocalDate start = switch (period) {
            case "last-month" -> today.minusMonths(1).withDayOfMonth(1);
            case "last-3-months" -> today.withDayOfMonth(1).minusMonths(2);
            case "last-6-months" -> today.withDayOfMonth(1).minusMonths(5);
            case "this-year" -> today.withDayOfYear(1);
            default -> today.withDayOfMonth(1);
        };
        LocalDate end = "last-month".equals(period)
                ? start.plusMonths(1).minusDays(1)
                : today;
        return new Selection(period, start, end, null);
    }

    private String redirectUrl(Selection selection) {
        return "redirect:/spending-insights?period=" + selection.period()
                + "&startDate=" + selection.startDate()
                + "&endDate=" + selection.endDate();
    }

    private SpendingInsightsDto emptyInsights(LocalDate startDate, LocalDate endDate) {
        BigDecimal zero = new BigDecimal("0.00");
        return new SpendingInsightsDto(
                startDate, endDate, zero, zero, zero, zero, 0,
                null, null, "Select a valid date range.",
                false, "Historical comparisons are unavailable for this range.",
                false, false, List.of(), List.of(), List.of(), List.of());
    }

    private User sessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        return currentUser instanceof User user && user.getId() != null ? user : null;
    }

    private record Selection(
            String period,
            LocalDate startDate,
            LocalDate endDate,
            String error) {
    }
}
