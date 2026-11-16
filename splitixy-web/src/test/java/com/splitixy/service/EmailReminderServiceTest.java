package com.splitixy.service;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.User;
import com.splitixy.service.BalanceService.Debt;
import com.splitixy.service.EmailReminderService.ReminderException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class EmailReminderServiceTest {

    private BalanceService balanceService;
    private ExpenseGroupService expenseGroupService;
    private GroupMemberService groupMemberService;
    private UserService userService;
    private JavaMailSender mailSender;
    private EmailReminderService reminderService;

    @BeforeEach
    void setUp() {
        balanceService = mock(BalanceService.class);
        expenseGroupService = mock(ExpenseGroupService.class);
        groupMemberService = mock(GroupMemberService.class);
        userService = mock(UserService.class);
        mailSender = mock(JavaMailSender.class);
        reminderService = new EmailReminderService(
                balanceService,
                expenseGroupService,
                groupMemberService,
                userService,
                mailSender,
                "reminders@splitixy.example",
                "smtp-user-secret",
                "test-secret");

        ExpenseGroup group = new ExpenseGroup();
        group.setGroupId(7);
        group.setGroupName("Trip");
        group.setCreatedBy(10);
        when(expenseGroupService.getGroup(7)).thenReturn(group);
        when(groupMemberService.isMember(7, 10)).thenReturn(true);
        when(groupMemberService.isMember(7, 20)).thenReturn(true);
        when(balanceService.calculateDebts(7)).thenReturn(List.of(
                debt(20, 10, "Rahul", "Pankaj", "500.00"),
                debt(30, 20, "Maya", "Rahul", "250.00")));
        User debtor = new User();
        debtor.setId(20);
        debtor.setEmail("rahul@example.com");
        when(userService.getUserById(20)).thenReturn(debtor);
        User otherDebtor = new User();
        otherDebtor.setId(30);
        otherDebtor.setEmail("maya@example.com");
        when(userService.getUserById(30)).thenReturn(otherDebtor);
    }

    @Test
    void creditorCanSendReminderForTheirCurrentDebt() {
        User creditor = user(20);

        int sent = reminderService.sendReminders(7, 30, 20, creditor);

        assertEquals(1, sent);
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void memberCannotRemindDebtorWhoDoesNotOweThem() {
        User unrelatedMember = user(20);

        ReminderException exception = assertThrows(
                ReminderException.class,
                () -> reminderService.sendReminders(7, 20, 10, unrelatedMember));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void onlyGroupCreatorCanRemindAllOutstandingDebts() {
        User creator = user(10);

        int sent = reminderService.sendReminders(7, null, null, creator);

        assertEquals(2, sent);
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void memberCannotRemindAllDebtors() {
        User member = user(20);

        ReminderException exception = assertThrows(
                ReminderException.class,
                () -> reminderService.sendReminders(7, null, null, member));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void mailFailurePreservesCauseLogsSanitizedSmtpDetailsAndReturnsFriendlyError(
            CapturedOutput output) throws Exception {
        SMTPAddressFailedException messagingException = new SMTPAddressFailedException(
                new InternetAddress("maya@example.com"),
                "RCPT TO",
                550,
                "550 mailbox rejected: maya@example.com smtp-user-secret test-secret "
                        + "A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6 Hello Maya,\n"
                        + "Rahul is waiting for your outstanding settlement of ₹250.00 in Trip.");
        MailSendException mailException = new MailSendException(
                Map.<Object, Exception>of("failed", messagingException));
        doThrow(mailException).when(mailSender).send(any(SimpleMailMessage.class));

        ReminderException exception = assertThrows(
                ReminderException.class,
                () -> reminderService.sendReminders(7, 30, 20, user(20)));

        assertEquals(HttpStatus.BAD_GATEWAY, exception.getStatus());
        assertSame(mailException, exception.getCause());
        assertSame(messagingException,
                ((MailSendException) exception.getCause()).getMessageExceptions()[0]);
        assertFalse(exception.getMessage().contains("550"));
        assertTrue(output.toString().contains("SMTP response code=550"));
        assertFalse(output.toString().contains("test-secret"));
        assertFalse(output.toString().contains("maya@example.com"));
        assertFalse(output.toString().contains("Hello Maya"));
        assertFalse(output.toString().contains("smtp-user-secret"));
        assertFalse(output.toString().contains("A1B2C3D4E5F6G7H8I9J0K1L2M3N4O5P6"));
        assertTrue(output.toString().contains("SMTP phase=RCPT TO"));
        assertTrue(output.toString().contains("server response="));
        assertTrue(output.toString().contains("SMTPAddressFailedException"));
        assertTrue(output.toString().contains("Exception stack trace [mail.failedMessage[0]]"));
        assertTrue(output.toString().contains(
                "EmailReminderServiceTest.mailFailurePreservesCauseLogsSanitizedSmtpDetailsAndReturnsFriendlyError"));
        assertTrue(output.toString().contains("\tat "));
    }

    private Debt debt(
            int debtorId,
            int creditorId,
            String debtor,
            String creditor,
            String amount) {
        return new Debt(7, debtorId, creditorId, debtor, creditor, "Trip",
                new BigDecimal(amount));
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
