package com.splitixy.controller;

import com.splitixy.entity.User;
import com.splitixy.service.EmailReminderService;
import com.splitixy.service.EmailReminderService.ReminderException;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.*;

class EmailReminderControllerTest {

    @Test
    void individualAndBulkRequestsUseTheSharedReminderService() {
        EmailReminderService reminderService = mock(EmailReminderService.class);
        EmailReminderController controller = new EmailReminderController(reminderService);
        User user = new User();
        user.setId(5);
        HttpSession session = mock(HttpSession.class);
        when(session.getAttribute("loggedInUser")).thenReturn(user);
        when(reminderService.sendReminders(7, 9, 5, user)).thenReturn(1);
        when(reminderService.sendReminders(7, null, null, user)).thenReturn(2);

        ResponseEntity<Map<String, String>> individual =
                controller.sendReminder(7, 9, 5, session);
        ResponseEntity<Map<String, String>> bulk =
                controller.sendReminder(7, null, null, session);

        assertEquals(HttpStatus.OK, individual.getStatusCode());
        assertEquals("Email reminder sent.", individual.getBody().get("message"));
        assertEquals(HttpStatus.OK, bulk.getStatusCode());
        assertEquals("2 email reminders sent.", bulk.getBody().get("message"));
        verify(reminderService).sendReminders(7, 9, 5, user);
        verify(reminderService).sendReminders(7, null, null, user);
    }

    @Test
    void missingSessionUserCannotSendReminder() {
        EmailReminderService reminderService = mock(EmailReminderService.class);
        EmailReminderController controller = new EmailReminderController(reminderService);
        HttpSession session = mock(HttpSession.class);

        ResponseEntity<Map<String, String>> response =
                controller.sendReminder(7, 9, 5, session);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        verifyNoInteractions(reminderService);
    }

    @Test
    void reminderFailureResponseDoesNotExposeMailCause() {
        EmailReminderController controller =
                new EmailReminderController(mock(EmailReminderService.class));
        ReminderException exception = new ReminderException(
                HttpStatus.BAD_GATEWAY,
                "The reminder email could not be sent. Please try again later.",
                new MailSendException("SMTP authentication failed"));

        ResponseEntity<Map<String, String>> response =
                controller.handleReminderError(exception);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("The reminder email could not be sent. Please try again later.",
                response.getBody().get("message"));
        assertFalse(response.getBody().toString().contains("SMTP"));
    }
}
