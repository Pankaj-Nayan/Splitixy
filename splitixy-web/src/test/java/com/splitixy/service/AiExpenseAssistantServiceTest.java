package com.splitixy.service;

import com.splitixy.entity.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiExpenseAssistantServiceTest {

    private final GeminiExpenseExtractor extractor = mock(GeminiExpenseExtractor.class);
    private final AiExpenseAssistantService assistant =
            new AiExpenseAssistantService(extractor);

    @Test
    void resolvesCurrentUserAndAUniqueGroupMember() {
        User currentUser = user(1, "Pankaj Nayan");
        User rahul = user(2, "Rahul Sharma");
        when(extractor.extract(any(), any())).thenReturn(extraction(
                "Dinner", "500.00", "INR", "2026-11-12", "CURRENT_USER",
                List.of("CURRENT_USER", "Rahul"), "EQUAL", Map.of()));

        AiExpenseAssistantService.Draft draft =
                assistant.extract("I paid 500 for dinner with Rahul, split equally",
                        List.of(currentUser, rahul), currentUser);

        assertEquals(1, draft.getPayerId());
        assertEquals(java.util.Set.of(1, 2), draft.getParticipantIds());
        assertEquals("EQUAL", draft.getSplitType());
        assertTrue(draft.getIssues().isEmpty());
    }

    @Test
    void leavesMissingDetailsForConfirmationInsteadOfInventingThem() {
        User currentUser = user(1, "Pankaj Nayan");
        when(extractor.extract(any(), any())).thenReturn(extraction(
                "Dinner", "500", null, null, "CURRENT_USER",
                List.of(), null, Map.of()));

        AiExpenseAssistantService.Draft draft =
                assistant.extract("I paid 500 for dinner", List.of(currentUser), currentUser);

        assertEquals("", draft.getCurrency());
        assertEquals("", draft.getDate());
        assertEquals("", draft.getSplitType());
        assertTrue(draft.getParticipantIds().isEmpty());
        assertTrue(draft.getIssues().stream().anyMatch(issue -> issue.contains("currency")));
        assertTrue(draft.getIssues().stream().anyMatch(issue -> issue.contains("date")));
        assertTrue(draft.getIssues().stream().anyMatch(issue -> issue.contains("split")));
    }

    @Test
    void reportsAmbiguousNamesAndCustomShareDiscrepancies() {
        User currentUser = user(1, "Pankaj Nayan");
        User firstRahul = user(2, "Rahul Sharma");
        User secondRahul = user(3, "Rahul Verma");
        when(extractor.extract(any(), any())).thenReturn(
                extraction(
                        "Trip", "1000.00", "INR", "2026-11-12", "Rahul",
                        List.of("Rahul"), "CUSTOM", Map.of("Rahul", "900.00")),
                extraction(
                        "Trip", "1000.00", "INR", "2026-11-12", "CURRENT_USER",
                        List.of("CURRENT_USER", "Rahul"), "CUSTOM",
                        Map.of("CURRENT_USER", "100.00", "Rahul", "800.00")));

        AiExpenseAssistantService.Draft ambiguous =
                assistant.extract("Rahul paid 1000 for trip; Rahul owes 900",
                        List.of(currentUser, firstRahul, secondRahul), currentUser);

        assertNull(ambiguous.getPayerId());
        assertTrue(ambiguous.getParticipantIds().isEmpty());
        assertTrue(ambiguous.getIssues().stream().anyMatch(issue -> issue.contains("multiple")));

        AiExpenseAssistantService.Draft mismatch =
                assistant.extract("I paid 1000 for a trip with Rahul; split is custom",
                        List.of(currentUser, firstRahul), currentUser);
        assertTrue(mismatch.getIssues().stream()
                .anyMatch(issue -> issue.contains("Custom shares must add up")));
    }

    @Test
    void rejectsUnsupportedCurrencyAndMultiplePayers() {
        User currentUser = user(1, "Pankaj Nayan");
        User rahul = user(2, "Rahul Sharma");
        when(extractor.extract(any(), any())).thenReturn(new GeminiExpenseExtractor.Extraction(
                "Hotel", "100.00", "USD", "Accommodation", "2026-11-12",
                "CURRENT_USER", List.of("CURRENT_USER", "Rahul"),
                Map.of("CURRENT_USER", "50", "Rahul", "50"), "EQUAL", Map.of(), List.of()));

        AiExpenseAssistantService.Draft draft =
                assistant.extract("We paid 100 USD for a hotel",
                        List.of(currentUser, rahul), currentUser);

        assertTrue(draft.getIssues().stream().anyMatch(issue -> issue.contains("INR only")));
        assertTrue(draft.getIssues().stream()
                .anyMatch(issue -> issue.contains("multiple contributors")));
    }

    @Test
    void flagsUnknownParticipantsAndInvalidAmounts() {
        User currentUser = user(1, "Pankaj Nayan");
        when(extractor.extract(any(), any())).thenReturn(extraction(
                "Dinner", "-500", "INR", null, "Unknown Payer",
                List.of("Nobody"), null, Map.of()));

        AiExpenseAssistantService.Draft draft =
                assistant.extract("Nobody paid an invalid dinner amount",
                        List.of(currentUser), currentUser);

        assertEquals("", draft.getAmount());
        assertNull(draft.getPayerId());
        assertTrue(draft.getParticipantIds().isEmpty());
        assertTrue(draft.getIssues().stream().anyMatch(issue -> issue.contains("positive amount")));
        assertTrue(draft.getIssues().stream().anyMatch(issue -> issue.contains("Could not find")));
    }

    @Test
    void doesNotAcceptAnAmbiguousNumericDateFromGemini() {
        User currentUser = user(1, "Pankaj Nayan");
        when(extractor.extract(any(), any())).thenReturn(extraction(
                "Dinner", "500", "INR", "2026-05-04", "CURRENT_USER",
                List.of("CURRENT_USER"), "EQUAL", Map.of()));

        AiExpenseAssistantService.Draft draft =
                assistant.extract("I paid 500 for dinner on 04/05",
                        List.of(currentUser), currentUser);

        assertEquals("", draft.getDate());
        assertTrue(draft.getIssues().stream()
                .anyMatch(issue -> issue.contains("day/month order")));
    }

    @Test
    void reportsGeminiFailuresWithoutExposingProviderErrors() {
        when(extractor.extract(any(), any())).thenThrow(
                new IllegalStateException("sensitive provider response"));

        AiExpenseAssistantService.ExtractionException exception = assertThrows(
                AiExpenseAssistantService.ExtractionException.class,
                () -> assistant.extract("I paid 500", List.of(user(1, "Pankaj")), user(1, "Pankaj")));

        assertNull(exception.getMessage());
    }

    private GeminiExpenseExtractor.Extraction extraction(
            String description,
            String amount,
            String currency,
            String date,
            String payer,
            List<String> participants,
            String splitType,
            Map<String, String> shares) {
        return new GeminiExpenseExtractor.Extraction(
                description, amount, currency, "Food & Dining", date, payer,
                participants, Map.of(), splitType, shares, List.of());
    }

    private User user(int id, String name) {
        User user = new User();
        user.setId(id);
        user.setFullName(name);
        user.setEmail(name.toLowerCase().replace(" ", ".") + "@example.test");
        return user;
    }
}
