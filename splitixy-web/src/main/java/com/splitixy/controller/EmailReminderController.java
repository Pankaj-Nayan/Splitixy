package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.service.EmailReminderService;
import com.splitixy.service.EmailReminderService.ReminderException;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class EmailReminderController {

    private final EmailReminderService emailReminderService;

    public EmailReminderController(EmailReminderService emailReminderService) {
        this.emailReminderService = emailReminderService;
    }

    @PostMapping("/group/{groupId}/reminders")
    public ResponseEntity<Map<String, String>> sendReminder(
            @PathVariable Integer groupId,
            @RequestParam(name = "debtorId", required = false) Integer debtorId,
            @RequestParam(name = "creditorId", required = false) Integer creditorId,
            HttpSession session) {
        Object loggedInUser = session.getAttribute("loggedInUser");
        if (!(loggedInUser instanceof User user) || user.getId() == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("message", "Sign in to send an email reminder."));
        }
        int sent = emailReminderService.sendReminders(
                groupId, debtorId, creditorId, user);
        String message = sent == 1
                ? "Email reminder sent."
                : sent + " email reminders sent.";
        return ResponseEntity.ok(Map.of("message", message));
    }

    @ExceptionHandler(ReminderException.class)
    public ResponseEntity<Map<String, String>> handleReminderError(ReminderException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("message", exception.getMessage()));
    }
}
