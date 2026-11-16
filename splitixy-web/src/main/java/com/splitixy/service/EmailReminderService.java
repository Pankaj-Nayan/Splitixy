package com.splitixy.service;

import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.User;
import com.splitixy.service.BalanceService.Debt;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import org.eclipse.angus.mail.smtp.SMTPAddressFailedException;
import org.eclipse.angus.mail.smtp.SMTPSendFailedException;
import org.eclipse.angus.mail.smtp.SMTPSenderFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class EmailReminderService {

    private static final Logger log = LoggerFactory.getLogger(EmailReminderService.class);
    private static final Pattern EMAIL_ADDRESS =
            Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern API_KEY =
            Pattern.compile("(?i)\\b(?:re_[A-Z0-9_-]{8,}|(?=[A-Z0-9_-]{32,}\\b)"
                    + "(?=[A-Z0-9_-]*[0-9_-])[A-Z0-9_-]{32,})\\b");
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\r\\n\\t]");

    private final BalanceService balanceService;
    private final ExpenseGroupService expenseGroupService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final JavaMailSender mailSender;
    private final String senderAddress;
    private final String smtpUsername;
    private final String smtpPassword;

    public EmailReminderService(
            BalanceService balanceService,
            ExpenseGroupService expenseGroupService,
            GroupMemberService groupMemberService,
            UserService userService,
            JavaMailSender mailSender,
            @Value("${splitixy.mail.from:}") String senderAddress,
            @Value("${spring.mail.username:}") String smtpUsername,
            @Value("${spring.mail.password:}") String smtpPassword) {
        this.balanceService = balanceService;
        this.expenseGroupService = expenseGroupService;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
    }

    public int sendReminders(
            Integer groupId,
            Integer debtorId,
            Integer creditorId,
            User requester) {
        ExpenseGroup group = expenseGroupService.getGroup(groupId);
        if (group == null) {
            throw new ReminderException(HttpStatus.NOT_FOUND, "This group could not be found.");
        }
        if (!groupMemberService.isMember(groupId, requester.getId())) {
            throw new ReminderException(
                    HttpStatus.FORBIDDEN, "You are not a member of this group.");
        }

        boolean groupCreator = requester.getId().equals(group.getCreatedBy());
        List<Debt> currentDebts;
        try {
            currentDebts = balanceService.calculateDebts(groupId);
        } catch (BalanceService.IncompleteExpenseDataException exception) {
            throw new ReminderException(
                    HttpStatus.CONFLICT,
                    "The group balances need review before reminders can be sent.");
        }

        List<Debt> selectedDebts;
        if (debtorId == null && creditorId == null) {
            if (!groupCreator) {
                throw new ReminderException(
                        HttpStatus.FORBIDDEN,
                        "Only the group creator can remind all debtors.");
            }
            selectedDebts = currentDebts;
            if (selectedDebts.isEmpty()) {
                throw new ReminderException(
                        HttpStatus.CONFLICT, "There are no outstanding debts to remind.");
            }
        } else if (debtorId == null || creditorId == null) {
            throw new ReminderException(
                    HttpStatus.BAD_REQUEST,
                    "Specify both the debtor and creditor for an individual reminder.");
        } else {
            selectedDebts = currentDebts.stream()
                    .filter(debt -> debtorId.equals(debt.getDebtorId())
                            && creditorId.equals(debt.getCreditorId()))
                    .toList();
            if (selectedDebts.isEmpty()) {
                throw new ReminderException(
                        HttpStatus.CONFLICT,
                        "That member has no current outstanding debt in this group.");
            }
            if (!groupCreator && selectedDebts.stream()
                    .noneMatch(debt -> requester.getId().equals(debt.getCreditorId()))) {
                throw new ReminderException(
                        HttpStatus.FORBIDDEN,
                        "You can only remind members who owe you.");
            }
        }

        if (senderAddress == null || senderAddress.isBlank()) {
            throw new ReminderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email reminders are not configured. Please contact support.");
        }
        try {
            new InternetAddress(senderAddress, true);
        } catch (jakarta.mail.internet.AddressException exception) {
            throw new ReminderException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Email reminders are not configured. Please contact support.");
        }

        List<ReminderMail> messages = selectedDebts.stream()
                .map(debt -> createReminderMail(debt, group.getGroupName()))
                .toList();
        int sent = 0;
        for (ReminderMail message : messages) {
            try {
                mailSender.send(message.message());
                sent++;
            } catch (MailException exception) {
                log.warn("Reminder email delivery failed for group {} after {} of {} messages. "
                                + "SMTP diagnostics: {}\nSanitized exception stack trace:\n{}",
                        groupId, sent, messages.size(),
                        formatMailException(exception, message.message()),
                        formatStackTrace(exception, message.message()));
                String detail = sent == 0
                        ? "The reminder email could not be sent. Please try again later."
                        : "Sent " + sent + " of " + messages.size()
                                + " reminders before email delivery failed. Please review the "
                                + "group before retrying.";
                throw new ReminderException(HttpStatus.BAD_GATEWAY, detail, exception);
            }
        }
        return sent;
    }

    private String formatMailException(MailException exception, SimpleMailMessage message) {
        StringBuilder diagnostics = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendException(exception, "mail", message, visited, diagnostics);
        if (!diagnostics.toString().contains("SMTP phase=")) {
            diagnostics.append(" | SMTP phase=unknown (no SMTP command found in exception chain)");
        }
        return diagnostics.toString();
    }

    private void appendException(
            Throwable exception,
            String path,
            SimpleMailMessage message,
            Set<Throwable> visited,
            StringBuilder diagnostics) {
        if (exception == null || !visited.add(exception)) {
            return;
        }
        if (!diagnostics.isEmpty()) {
            diagnostics.append(" | ");
        }
        diagnostics.append(path).append('=').append(exception.getClass().getName());

        String exceptionMessage = exception.getMessage();
        if (exceptionMessage != null && !exceptionMessage.isBlank()) {
            diagnostics.append(": ").append(sanitizeDiagnostic(exceptionMessage, message));
        }

        Integer responseCode = getSmtpResponseCode(exception);
        String smtpPhase = getSmtpPhase(exception);
        if (responseCode != null || smtpPhase != null) {
            diagnostics.append(" [");
            if (responseCode != null) {
                diagnostics.append("SMTP response code=").append(responseCode);
            }
            if (smtpPhase != null) {
                if (responseCode != null) {
                    diagnostics.append(", ");
                }
                diagnostics.append("SMTP phase=").append(sanitizeDiagnostic(smtpPhase, message));
            }
            if (responseCode != null) {
                diagnostics.append(", server response=\"")
                        .append(sanitizeDiagnostic(exceptionMessage, message)).append("\"");
            }
            diagnostics.append("]");
        }

        if (exception instanceof MailSendException mailSendException) {
            Exception[] messageExceptions = mailSendException.getMessageExceptions();
            for (int i = 0; i < messageExceptions.length; i++) {
                appendException(messageExceptions[i], path + ".failedMessage[" + i + "]",
                        message, visited, diagnostics);
            }
        }
        if (exception instanceof MessagingException messagingException) {
            appendException(messagingException.getNextException(), path + ".next",
                    message, visited, diagnostics);
        }
        appendException(exception.getCause(), path + ".cause", message, visited, diagnostics);
        for (int i = 0; i < exception.getSuppressed().length; i++) {
            appendException(exception.getSuppressed()[i], path + ".suppressed[" + i + "]",
                    message, visited, diagnostics);
        }
    }

    private Integer getSmtpResponseCode(Throwable exception) {
        if (exception instanceof SMTPAddressFailedException smtpException) {
            return smtpException.getReturnCode();
        }
        if (exception instanceof SMTPSenderFailedException smtpException) {
            return smtpException.getReturnCode();
        }
        if (exception instanceof SMTPSendFailedException smtpException) {
            return smtpException.getReturnCode();
        }
        return null;
    }

    private String getSmtpPhase(Throwable exception) {
        String command = null;
        if (exception instanceof SMTPAddressFailedException smtpException) {
            command = smtpException.getCommand();
        } else if (exception instanceof SMTPSenderFailedException smtpException) {
            command = smtpException.getCommand();
        } else if (exception instanceof SMTPSendFailedException smtpException) {
            command = smtpException.getCommand();
        }
        if (command == null || command.isBlank()) {
            return null;
        }
        String normalizedCommand = command.trim().toUpperCase(Locale.ROOT);
        if (normalizedCommand.startsWith("RCPT TO")) {
            return "RCPT TO";
        }
        if (normalizedCommand.startsWith("DATA")) {
            return "DATA";
        }
        return "SMTP command " + command.trim();
    }

    private String formatStackTrace(Throwable exception, SimpleMailMessage message) {
        StringBuilder stackTraces = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendStackTrace(exception, "mail", message, visited, stackTraces);
        return stackTraces.toString();
    }

    private void appendStackTrace(
            Throwable exception,
            String path,
            SimpleMailMessage message,
            Set<Throwable> visited,
            StringBuilder stackTraces) {
        if (exception == null || !visited.add(exception)) {
            return;
        }
        if (!stackTraces.isEmpty()) {
            stackTraces.append(System.lineSeparator());
        }
        stackTraces.append("Exception stack trace [").append(path).append("]:")
                .append(System.lineSeparator());
        StringWriter stackTrace = new StringWriter();
        try (PrintWriter writer = new PrintWriter(stackTrace)) {
            exception.printStackTrace(writer);
        }
        stackTraces.append(sanitizeDiagnostic(stackTrace.toString(), message, false));

        if (exception instanceof MailSendException mailSendException) {
            Exception[] messageExceptions = mailSendException.getMessageExceptions();
            for (int i = 0; i < messageExceptions.length; i++) {
                appendStackTrace(messageExceptions[i], path + ".failedMessage[" + i + "]",
                        message, visited, stackTraces);
            }
        }
        if (exception instanceof MessagingException messagingException) {
            appendStackTrace(messagingException.getNextException(), path + ".next",
                    message, visited, stackTraces);
        }
        appendStackTrace(exception.getCause(), path + ".cause", message, visited, stackTraces);
        for (int i = 0; i < exception.getSuppressed().length; i++) {
            appendStackTrace(exception.getSuppressed()[i], path + ".suppressed[" + i + "]",
                    message, visited, stackTraces);
        }
    }

    private String sanitizeDiagnostic(String value, SimpleMailMessage message) {
        return sanitizeDiagnostic(value, message, true);
    }

    private String sanitizeDiagnostic(
            String value,
            SimpleMailMessage message,
            boolean singleLine) {
        if (value == null) {
            return "<no message>";
        }
        String sanitized = singleLine
                ? CONTROL_CHARACTERS.matcher(value).replaceAll(" ")
                : value;
        if (smtpUsername != null && !smtpUsername.isEmpty()) {
            sanitized = sanitized.replace(smtpUsername, "[REDACTED]");
        }
        if (smtpPassword != null && !smtpPassword.isEmpty()) {
            sanitized = sanitized.replace(smtpPassword, "[REDACTED]");
        }
        sanitized = redact(sanitized, message.getFrom());
        sanitized = redact(sanitized, message.getSubject());
        sanitized = redactMailContent(sanitized, message.getText());
        sanitized = redact(sanitized, message.getTo());
        sanitized = redact(sanitized, message.getCc());
        sanitized = redact(sanitized, message.getBcc());
        sanitized = EMAIL_ADDRESS.matcher(sanitized).replaceAll("[REDACTED_EMAIL]");
        sanitized = API_KEY.matcher(sanitized).replaceAll("[REDACTED_SECRET]");
        if (singleLine && sanitized.length() > 500) {
            return sanitized.substring(0, 500) + "...";
        }
        return sanitized;
    }

    private String redactMailContent(String value, String mailContent) {
        if (mailContent == null || mailContent.isBlank()) {
            return value;
        }
        String sanitized = redact(value, mailContent.replaceAll("\\s+", " ").trim());
        for (String line : mailContent.split("\\R")) {
            String contentLine = line.replaceAll("\\s+", " ").trim();
            if (!contentLine.isEmpty()) {
                sanitized = redact(sanitized, contentLine);
            }
        }
        return sanitized;
    }

    private String redact(String value, String sensitiveValue) {
        if (sensitiveValue != null && !sensitiveValue.isEmpty()) {
            return value.replace(sensitiveValue, "[REDACTED]");
        }
        return value;
    }

    private String redact(String value, String[] sensitiveValues) {
        if (sensitiveValues == null) {
            return value;
        }
        String sanitized = value;
        for (String sensitiveValue : sensitiveValues) {
            sanitized = redact(sanitized, sensitiveValue);
        }
        return sanitized;
    }

    private ReminderMail createReminderMail(Debt debt, String groupName) {
        User debtor = userService.getUserById(debt.getDebtorId());
        if (debtor == null || debtor.getEmail() == null || debtor.getEmail().isBlank()) {
            throw new ReminderException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "A debtor in this group does not have a usable email address.");
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress.trim());
        message.setTo(debtor.getEmail());
        message.setSubject("A Splitixy settlement reminder");
        message.setText("Hello " + debt.getDebtor() + ",\n\n"
                + debt.getCreditor() + " is waiting for your outstanding settlement of ₹"
                + debt.getAmount().toPlainString() + " in " + groupName + ".\n\n"
                + "You can review the group and its expenses in Splitixy.");
        return new ReminderMail(message);
    }

    private record ReminderMail(SimpleMailMessage message) {
    }

    public static class ReminderException extends RuntimeException {
        private final HttpStatus status;

        public ReminderException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public ReminderException(HttpStatus status, String message, Throwable cause) {
            super(message, cause);
            this.status = status;
        }

        public HttpStatus getStatus() {
            return status;
        }
    }
}
