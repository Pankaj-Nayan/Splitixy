package com.splitixy.service;

import com.splitixy.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiExpenseAssistantService {

    private static final Logger log =
            LoggerFactory.getLogger(AiExpenseAssistantService.class);
    private static final int MAX_DESCRIPTION_LENGTH = 2000;
    private static final Pattern NUMERIC_DATE =
            Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b");
    private static final List<String> CATEGORIES = List.of(
            "Food & Dining",
            "Travel",
            "Accommodation",
            "Transport",
            "Utilities",
            "Shopping",
            "Entertainment",
            "Groceries",
            "Other");

    private final GeminiExpenseExtractor extractor;

    public AiExpenseAssistantService(GeminiExpenseExtractor extractor) {
        this.extractor = extractor;
    }

    public Draft extract(String prompt, List<User> members, User currentUser) {
        if (prompt == null || prompt.isBlank()) {
            throw new RequestValidationException(
                    "Describe the expense before continuing.");
        }
        if (prompt.length() > MAX_DESCRIPTION_LENGTH) {
            throw new RequestValidationException(
                    "Keep the expense description under 2,000 characters.");
        }

        GeminiExpenseExtractor.Extraction extraction;
        try {
            extraction = extractor.extract(prompt.trim(), LocalDate.now());
        } catch (RuntimeException exception) {
            log.warn("Gemini expense extraction failed ({})",
                    exception.getClass().getSimpleName());
            throw new ExtractionException();
        }
        if (extraction == null) {
            throw new ExtractionException();
        }
        return prepareDraft(prompt.trim(), extraction, members, currentUser);
    }

    public static List<String> categories() {
        return CATEGORIES;
    }

    private Draft prepareDraft(
            String prompt,
            GeminiExpenseExtractor.Extraction extraction,
            List<User> members,
            User currentUser) {
        List<String> issues = new ArrayList<>();
        if (extraction.missingOrAmbiguous() != null) {
            extraction.missingOrAmbiguous().stream()
                    .filter(value -> value != null && !value.isBlank())
                    .forEach(issues::add);
        }

        String amount = validAmount(extraction.totalAmount())
                ? extraction.totalAmount().trim()
                : "";
        if (amount.isBlank()) {
            issues.add("Enter a positive total amount with no more than two decimal places.");
        }

        String currency = valueOrEmpty(extraction.currency()).toUpperCase(Locale.ROOT);
        if (currency.isBlank()) {
            issues.add("Select the expense currency; it was not clear from the description.");
        } else if (!currency.matches("[A-Z]{3}")) {
            issues.add("The extracted currency is not a valid three-letter currency code.");
        } else if (!"INR".equals(currency)) {
            issues.add("Splitixy balances currently support INR only. Change the currency to INR "
                    + "only if that is correct.");
        }

        boolean ambiguousNumericDate = hasAmbiguousNumericDate(prompt);
        String date = !ambiguousNumericDate && validDate(extraction.expenseDate())
                ? extraction.expenseDate().trim()
                : "";
        if (date.isBlank()) {
            issues.add("Choose the expense date; it was missing or ambiguous.");
        }
        if (ambiguousNumericDate) {
            issues.add("The numeric date could use more than one day/month order. Choose the date.");
        }

        String category = normalizeCategory(extraction.category());
        if (extraction.category() != null && !extraction.category().isBlank()
                && category == null) {
            issues.add("Choose a supported category for the extracted category.");
        }

        Integer payerId = resolveMember(
                extraction.payerName(), members, currentUser, "payer", issues);
        Set<Integer> participantIds = new LinkedHashSet<>();
        if (extraction.participants() != null) {
            for (String name : extraction.participants()) {
                Integer memberId = resolveMember(name, members, currentUser,
                        "participant", issues);
                if (memberId != null) {
                    participantIds.add(memberId);
                }
            }
        }
        if (participantIds.isEmpty()) {
            issues.add("Select at least one group member who shares this expense.");
        }

        String splitType = normalizeSplitType(extraction.splitType());
        if (splitType == null) {
            issues.add("Choose an equal or custom split; the description did not specify one.");
        }

        Map<Integer, String> shares = new LinkedHashMap<>();
        if (extraction.shares() != null) {
            for (Map.Entry<String, String> share : extraction.shares().entrySet()) {
                Integer userId = resolveMember(
                        share.getKey(), members, currentUser, "share", issues);
                if (userId == null) {
                    continue;
                }
                String value = valueOrEmpty(share.getValue()).trim();
                if (!validShare(value)) {
                    issues.add("A custom share is missing or is not a valid amount.");
                    continue;
                }
                shares.put(userId, value);
            }
        }
        if ("CUSTOM".equals(splitType) && !amount.isBlank()) {
            validateCustomShares(amount, participantIds, shares, issues);
        }

        ContributionReview contributionReview = contributionNotes(
                extraction.contributions(), members, currentUser, amount, issues);

        return new Draft(
                prompt,
                valueOrEmpty(extraction.description()),
                amount,
                currency,
                category == null ? "" : category,
                date,
                payerId,
                participantIds,
                splitType == null ? "" : splitType,
                shares,
                distinct(issues),
                contributionReview.notes(),
                contributionReview.multipleContributors());
    }

    private Integer resolveMember(
            String mentionedName,
            List<User> members,
            User currentUser,
            String role,
            List<String> issues) {
        if (mentionedName == null || mentionedName.isBlank()) {
            if ("payer".equals(role)) {
                issues.add("Select who paid; the payer was not clear.");
            }
            return null;
        }
        if ("CURRENT_USER".equalsIgnoreCase(mentionedName.trim())) {
            return currentUser.getId();
        }

        String name = normalize(mentionedName);
        List<User> matches = members.stream()
                .filter(member -> matchesName(member, name))
                .toList();
        if (matches.size() == 1) {
            return matches.getFirst().getId();
        }
        String label = role.equals("participant") ? "participant" : role;
        issues.add(matches.isEmpty()
                ? "Could not find the " + label + " “" + mentionedName
                        + "” in this group. Select the correct member."
                : "The " + label + " name “" + mentionedName
                        + "” matches multiple group members. Select the correct member.");
        return null;
    }

    private boolean matchesName(User user, String mentionedName) {
        String fullName = normalize(user.getFullName());
        String email = normalize(user.getEmail());
        if (mentionedName.equals(fullName) || mentionedName.equals(email)) {
            return true;
        }
        return words(fullName).contains(mentionedName)
                || words(email).contains(mentionedName);
    }

    private Set<String> words(String value) {
        Set<String> words = new LinkedHashSet<>();
        for (String word : value.split("[^\\p{L}\\p{N}]+")) {
            if (!word.isBlank()) {
                words.add(word);
            }
        }
        return words;
    }

    private void validateCustomShares(
            String amount,
            Set<Integer> participantIds,
            Map<Integer, String> shares,
            List<String> issues) {
        if (shares.isEmpty()) {
            issues.add("Enter a share for each selected participant.");
            return;
        }
        if (!shares.keySet().equals(participantIds)) {
            issues.add("Custom shares must be provided for exactly the selected participants.");
            return;
        }
        BigDecimal sum = shares.values().stream()
                .map(BigDecimal::new)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(new BigDecimal(amount)) != 0) {
            issues.add("Custom shares must add up to the expense total.");
        }
    }

    private ContributionReview contributionNotes(
            Map<String, String> contributions,
            List<User> members,
            User currentUser,
            String totalAmount,
            List<String> issues) {
        if (contributions == null || contributions.isEmpty()) {
            return new ContributionReview(List.of(), false);
        }
        List<String> notes = new ArrayList<>();
        Set<Integer> contributorIds = new LinkedHashSet<>();
        BigDecimal contributionTotal = BigDecimal.ZERO;
        for (Map.Entry<String, String> contribution : contributions.entrySet()) {
            String value = valueOrEmpty(contribution.getValue()).trim();
            if (!validShare(value)) {
                issues.add("A stated contribution is not a valid amount.");
                continue;
            }
            Integer contributorId = resolveMember(
                    contribution.getKey(), members, currentUser, "contributor", issues);
            if (contributorId != null) {
                contributorIds.add(contributorId);
            }
            contributionTotal = contributionTotal.add(new BigDecimal(value));
            User contributor = members.stream()
                    .filter(member -> contributorId != null
                            && contributorId.equals(member.getId()))
                    .findFirst()
                    .orElse(null);
            String name = contributor == null
                    ? contribution.getKey()
                    : contributor.getFullName() == null || contributor.getFullName().isBlank()
                            ? contributor.getEmail()
                            : contributor.getFullName();
            notes.add(valueOrEmpty(name) + ": " + value);
        }
        if (contributorIds.size() > 1) {
            issues.add("This expense mentions multiple contributors, but the current expense "
                    + "workflow supports one payer. Revise the details to one payer before saving.");
        }
        if (!totalAmount.isBlank() && !notes.isEmpty()
                && contributionTotal.compareTo(new BigDecimal(totalAmount)) > 0) {
            issues.add("The stated contributions exceed the expense total.");
        }
        if (!totalAmount.isBlank() && contributorIds.size() == 1
                && contributionTotal.compareTo(new BigDecimal(totalAmount)) != 0) {
            issues.add("The stated contribution does not match the expense total. Check who paid "
                    + "the remaining amount.");
        }
        return new ContributionReview(
                List.copyOf(notes), contributorIds.size() > 1 || notes.size() > 1);
    }

    private String normalizeCategory(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return CATEGORIES.stream()
                .filter(category -> category.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(null);
    }

    private String normalizeSplitType(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return "EQUAL".equals(normalized) || "CUSTOM".equals(normalized)
                ? normalized
                : null;
    }

    private boolean validAmount(String value) {
        return validShare(value) && new BigDecimal(value.trim()).signum() > 0;
    }

    private boolean validShare(String value) {
        return value != null
                && value.matches("\\d{1,17}(\\.\\d{1,2})?")
                && new BigDecimal(value).precision() <= 19;
    }

    private boolean validDate(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            LocalDate.parse(value.trim());
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    private boolean hasAmbiguousNumericDate(String prompt) {
        Matcher matcher = NUMERIC_DATE.matcher(prompt);
        while (matcher.find()) {
            int first = Integer.parseInt(matcher.group(1));
            int second = Integer.parseInt(matcher.group(2));
            if (first >= 1 && first <= 12 && second >= 1 && second <= 12) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private record ContributionReview(
            List<String> notes,
            boolean multipleContributors) {
    }

    public static final class Draft {
        private final String prompt;
        private final String description;
        private final String amount;
        private final String currency;
        private final String category;
        private final String date;
        private final Integer payerId;
        private final Set<Integer> participantIds;
        private final String splitType;
        private final Map<Integer, String> shares;
        private final List<String> issues;
        private final List<String> contributionNotes;
        private final boolean multipleContributors;

        public Draft(
                String prompt,
                String description,
                String amount,
                String currency,
                String category,
                String date,
                Integer payerId,
                Set<Integer> participantIds,
                String splitType,
                Map<Integer, String> shares,
                List<String> issues,
                List<String> contributionNotes) {
            this(prompt, description, amount, currency, category, date, payerId,
                    participantIds, splitType, shares, issues, contributionNotes,
                    contributionNotes.size() > 1);
        }

        public Draft(
                String prompt,
                String description,
                String amount,
                String currency,
                String category,
                String date,
                Integer payerId,
                Set<Integer> participantIds,
                String splitType,
                Map<Integer, String> shares,
                List<String> issues,
                List<String> contributionNotes,
                boolean multipleContributors) {
            this.prompt = prompt;
            this.description = description;
            this.amount = amount;
            this.currency = currency;
            this.category = category;
            this.date = date;
            this.payerId = payerId;
            this.participantIds = Set.copyOf(participantIds);
            this.splitType = splitType;
            this.shares = Map.copyOf(shares);
            this.issues = List.copyOf(issues);
            this.contributionNotes = List.copyOf(contributionNotes);
            this.multipleContributors = multipleContributors;
        }

        public String getPrompt() { return prompt; }
        public String getDescription() { return description; }
        public String getAmount() { return amount; }
        public String getCurrency() { return currency; }
        public String getCategory() { return category; }
        public String getDate() { return date; }
        public Integer getPayerId() { return payerId; }
        public Set<Integer> getParticipantIds() { return participantIds; }
        public String getSplitType() { return splitType; }
        public Map<Integer, String> getShares() { return shares; }
        public List<String> getIssues() { return issues; }
        public List<String> getContributionNotes() { return contributionNotes; }
        public boolean isMultipleContributors() { return multipleContributors; }
    }

    public static class ExtractionException extends RuntimeException {
    }

    public static class RequestValidationException extends IllegalArgumentException {
        public RequestValidationException(String message) {
            super(message);
        }
    }
}
