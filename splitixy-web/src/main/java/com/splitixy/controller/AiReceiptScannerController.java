package com.splitixy.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitixy.entity.Expense;
import com.splitixy.entity.ExpenseGroup;
import com.splitixy.entity.User;
import com.splitixy.service.AiReceiptScannerService;
import com.splitixy.service.AiReceiptScannerService.ExtractionException;
import com.splitixy.service.AiReceiptScannerService.RequestValidationException;
import com.splitixy.service.ExpenseGroupService;
import com.splitixy.service.ExpenseService;
import com.splitixy.service.ReceiptExtraction;
import com.splitixy.service.GroupMemberService;
import com.splitixy.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Controller
public class AiReceiptScannerController {

    private static final Logger log = LoggerFactory.getLogger(AiReceiptScannerController.class);
    private static final String VIEW = "ai-receipt-scanner";
    private static final String DRAFT_ATTRIBUTE = "aiReceiptDraft";
    private static final String TOKEN_ATTRIBUTE = "aiReceiptDraftToken";
    private static final String CONSUMED_ATTRIBUTE = "aiReceiptConsumedToken";
    private static final BigDecimal CENT = new BigDecimal("0.01");
    private static final TypeReference<List<ItemInput>> ITEMS_TYPE =
            new TypeReference<>() { };

    private final AiReceiptScannerService scannerService;
    private final ExpenseGroupService expenseGroupService;
    private final ExpenseService expenseService;
    private final GroupMemberService groupMemberService;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public AiReceiptScannerController(
            AiReceiptScannerService scannerService,
            ExpenseGroupService expenseGroupService,
            ExpenseService expenseService,
            GroupMemberService groupMemberService,
            UserService userService,
            ObjectMapper objectMapper) {
        this.scannerService = scannerService;
        this.expenseGroupService = expenseGroupService;
        this.expenseService = expenseService;
        this.groupMemberService = groupMemberService;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/receipt-scanner")
    public String page(
            @RequestParam(required = false) String draftToken,
            @RequestParam(required = false) Integer groupId,
            HttpSession session,
            Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        addBaseModel(user, model);
        ReceiptExtraction draft = getDraft(session, draftToken);
        if (draft != null) {
            model.addAttribute("draft", draft);
            model.addAttribute("draftToken", draftToken);
        }
        if (groupId != null) {
            if (!groupMemberService.isMember(groupId, user.getId())) {
                model.addAttribute("error", "Choose a group that you belong to.");
            } else {
                model.addAttribute("selectedGroupId", groupId);
                model.addAttribute("selectedGroupName", selectedGroupName(groupId));
                model.addAttribute("members", getGroupMembers(groupId));
            }
        }
        return VIEW;
    }

    @PostMapping("/receipt-scanner/extract")
    public String extract(
            @RequestParam("receipt") MultipartFile upload,
            HttpSession session,
            Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        addBaseModel(user, model);
        try {
            ReceiptExtraction extraction = scannerService.extract(upload);
            String token = java.util.UUID.randomUUID().toString();
            session.setAttribute(DRAFT_ATTRIBUTE, extraction);
            session.setAttribute(TOKEN_ATTRIBUTE, token);
            session.removeAttribute(CONSUMED_ATTRIBUTE);
            model.addAttribute("draft", extraction);
            model.addAttribute("draftToken", token);
        } catch (RequestValidationException exception) {
            model.addAttribute("error", exception.getMessage());
        } catch (ExtractionException exception) {
            model.addAttribute("error",
                    "Gemini could not read this receipt. Try a clearer, complete image.");
        } catch (RuntimeException exception) {
            log.warn("Receipt extraction failed ({})", exception.getClass().getSimpleName());
            model.addAttribute("error",
                    "Receipt scanning is temporarily unavailable. Please try again.");
        }
        return VIEW;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String uploadTooLarge(HttpSession session, Model model) {
        User user = getSessionUser(session);
        if (user == null) {
            return "redirect:/login";
        }
        addBaseModel(user, model);
        model.addAttribute("error", "Receipt images must be 10 MB or smaller.");
        return VIEW;
    }

    @PostMapping("/receipt-scanner/confirm")
    public String confirm(
            @RequestParam String draftToken,
            @RequestParam(required = false) Integer groupId,
            @RequestParam(required = false) String merchantName,
            @RequestParam(required = false) String merchantAddress,
            @RequestParam(required = false) String receiptDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String subtotal,
            @RequestParam(required = false) String tax,
            @RequestParam(required = false) String discount,
            @RequestParam(required = false) String tip,
            @RequestParam(required = false) String serviceCharge,
            @RequestParam(required = false) String finalTotal,
            @RequestParam(required = false) String otherAdjustment,
            @RequestParam(required = false) String receiptItems,
            @RequestParam(required = false) String splittingMethod,
            @RequestParam(required = false) String splitType,
            @RequestParam(required = false) String payerId,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestParam(name = "participantIds", required = false)
                    List<String> participantIds,
            @RequestParam MultiValueMap<String, String> parameters,
            HttpSession session,
            Model model) {
        User currentUser = getSessionUser(session);
        if (currentUser == null) {
            return "redirect:/login";
        }

        synchronized (session) {
            ReceiptExtraction originalDraft = getDraft(session, draftToken);
            if (originalDraft == null
                    || draftToken.equals(session.getAttribute(CONSUMED_ATTRIBUTE))) {
                addBaseModel(currentUser, model);
                model.addAttribute("error",
                        "This receipt review has expired or was already submitted. Scan it again.");
                return VIEW;
            }

            List<String> errors = new ArrayList<>();
            if (!confirmed) {
                errors.add("Confirm the final receipt summary before saving.");
            }
            List<MemberView> members = groupId != null
                    && groupMemberService.isMember(groupId, currentUser.getId())
                    ? getGroupMembers(groupId)
                    : List.of();
            Set<Integer> memberIds = members.stream().map(MemberView::id)
                    .collect(java.util.stream.Collectors.toSet());
            if (members.isEmpty()) {
                errors.add("Select a group that you belong to.");
            }
            Integer validPayerId = parseMember(payerId, memberIds,
                    "Select the payer from this group's members.", errors);
            Set<Integer> participants = parseParticipants(participantIds, memberIds, errors);
            BigDecimal amount = parseMoney(finalTotal, "Enter a positive final total.", errors);
            BigDecimal parsedTax = parseOptionalMoney(tax, "Tax", errors);
            BigDecimal parsedDiscount = parseOptionalMoney(discount, "Discount", errors);
            BigDecimal parsedTip = parseOptionalMoney(tip, "Tip", errors);
            BigDecimal parsedServiceCharge =
                    parseOptionalMoney(serviceCharge, "Service charge", errors);
            BigDecimal parsedOtherAdjustment =
                    parseOptionalMoney(otherAdjustment, "Receipt difference", errors);
            BigDecimal parsedSubtotal = blank(subtotal)
                    ? null : parseOptionalMoney(subtotal, "Subtotal", errors);
            LocalDate date = parseDate(receiptDate, errors);
            String normalizedCategory = normalizeCategory(category, errors);
            String normalizedCurrency = currency == null
                    ? "" : currency.trim().toUpperCase(Locale.ROOT);
            if (!"INR".equals(normalizedCurrency)) {
                errors.add("Splitixy balances currently support INR only. Keep the receipt's "
                        + "original currency in the review, and save only if it is actually INR.");
            }
            String description = expenseDescription(merchantName);
            if (description.length() > 255) {
                errors.add("Merchant name must be at most 245 characters.");
            }
            List<ItemInput> items = parseItems(receiptItems, errors);

            Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
            if (amount != null && !participants.isEmpty()) {
                if ("TOTAL".equals(splittingMethod)) {
                    if ("EQUAL".equals(splitType)) {
                        shares.putAll(equalShares(amount, participants));
                    } else if ("CUSTOM".equals(splitType)) {
                        shares.putAll(readCustomShares(
                                parameters, participants, amount, errors));
                    } else {
                        errors.add("Choose equal or custom splitting for the entire bill.");
                    }
                } else if ("ITEMS".equals(splittingMethod)) {
                    shares.putAll(calculateItemShares(
                            items, participants, parsedTax, parsedDiscount, parsedTip,
                            parsedServiceCharge, parsedOtherAdjustment, amount,
                            parsedSubtotal, parameters, errors));
                    Map<Integer, BigDecimal> overrides =
                            readOptionalOverrides(parameters, participants, errors);
                    if (!overrides.isEmpty()) {
                        if (!overrides.keySet().equals(participants)) {
                            errors.add("Enter a manual share for every selected participant.");
                        } else {
                            shares = overrides;
                            validateShareTotal(amount, shares, errors);
                        }
                    }
                } else {
                    errors.add("Choose how this receipt should be split.");
                }
            }

            if (!errors.isEmpty()) {
                addReviewModel(currentUser, draftToken, originalDraft, groupId, members, model);
                model.addAttribute("errors", distinct(errors));
                model.addAttribute("submitted", new SubmittedReview(
                        merchantName, merchantAddress, receiptDate, category, currency,
                        subtotal, tax, discount, tip, serviceCharge, finalTotal,
                        otherAdjustment, receiptItems, splittingMethod, splitType,
                        validPayerId, participants, shares, readAdjustmentInputs(parameters)));
                return VIEW;
            }

            Expense expense = new Expense();
            expense.setGroupId(groupId);
            expense.setPaidBy(validPayerId);
            expense.setCreatedBy(currentUser.getId());
            expense.setAmount(amount);
            expense.setDescription(description);
            expense.setExpenseDate(date.atStartOfDay());
            expense.setCurrency(normalizedCurrency);
            expense.setCategory(normalizedCategory);
            try {
                String expenseSplitType = "EQUAL".equals(splitType)
                        && "TOTAL".equals(splittingMethod) ? "EQUAL" : "CUSTOM";
                expenseService.createExpense(
                        expense, expenseSplitType, shares, participants);
                session.setAttribute(CONSUMED_ATTRIBUTE, draftToken);
                session.removeAttribute(DRAFT_ATTRIBUTE);
                session.removeAttribute(TOKEN_ATTRIBUTE);
                return "redirect:/group/" + groupId;
            } catch (IllegalArgumentException exception) {
                addReviewModel(currentUser, draftToken, originalDraft, groupId, members, model);
                model.addAttribute("errors", List.of(exception.getMessage()));
                model.addAttribute("submitted", new SubmittedReview(
                        merchantName, merchantAddress, receiptDate, category, currency,
                        subtotal, tax, discount, tip, serviceCharge, finalTotal,
                        otherAdjustment, receiptItems, splittingMethod, splitType,
                        validPayerId, participants, shares, readAdjustmentInputs(parameters)));
                return VIEW;
            }
        }
    }

    private void addBaseModel(User user, Model model) {
        List<ExpenseGroup> groups = expenseGroupService.getMyGroups(user.getId()).stream()
                .filter(group -> groupMemberService.isMember(group.getGroupId(), user.getId()))
                .toList();
        model.addAttribute("groups", groups);
        model.addAttribute("categories", AiReceiptScannerService.categories());
    }

    private void addReviewModel(
            User user,
            String token,
            ReceiptExtraction draft,
            Integer groupId,
            List<MemberView> members,
            Model model) {
        addBaseModel(user, model);
        model.addAttribute("draft", draft);
        model.addAttribute("draftToken", token);
        model.addAttribute("initialItemsJson", itemsJson(draft));
        model.addAttribute("members", members);
        if (groupId != null && !members.isEmpty()) {
            model.addAttribute("selectedGroupId", groupId);
            model.addAttribute("selectedGroupName", selectedGroupName(groupId));
        }
    }

    private String selectedGroupName(Integer groupId) {
        ExpenseGroup group = expenseGroupService.getGroup(groupId);
        return group == null ? "Selected group" : group.getGroupName();
    }

    private String itemsJson(ReceiptExtraction draft) {
        List<ItemInput> items = draft.items() == null ? List.of() : draft.items().stream()
                .filter(item -> item != null)
                .map(item -> new ItemInput(
                        item.name(),
                        item.quantity(),
                        item.unitPrice(),
                        item.totalPrice(),
                        item.discount(),
                        List.of()))
                .toList();
        try {
            return objectMapper.writeValueAsString(items);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Could not prepare receipt items for review.", exception);
        }
    }

    private ReceiptExtraction getDraft(HttpSession session, String token) {
        Object savedToken = session.getAttribute(TOKEN_ATTRIBUTE);
        Object savedDraft = session.getAttribute(DRAFT_ATTRIBUTE);
        if (token != null && token.equals(savedToken)
                && savedDraft instanceof ReceiptExtraction extraction) {
            return extraction;
        }
        return null;
    }

    private List<MemberView> getGroupMembers(Integer groupId) {
        return groupMemberService.getMembers(groupId).stream()
                .map(member -> userService.getUserById(member.getId().getUserId()))
                .filter(user -> user != null)
                .map(user -> new MemberView(
                        user.getId(),
                        user.getFullName() == null || user.getFullName().isBlank()
                                ? user.getEmail() : user.getFullName()))
                .toList();
    }

    private List<ItemInput> parseItems(String json, List<String> errors) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<ItemInput> items = objectMapper.readValue(json, ITEMS_TYPE);
            if (items == null || items.size() > 250) {
                errors.add("A receipt can contain at most 250 reviewed items.");
                return List.of();
            }
            return items;
        } catch (Exception exception) {
            errors.add("Review the item list and try again.");
            return List.of();
        }
    }

    private Map<Integer, BigDecimal> calculateItemShares(
            List<ItemInput> items,
            Set<Integer> selectedParticipants,
            BigDecimal tax,
            BigDecimal discount,
            BigDecimal tip,
            BigDecimal serviceCharge,
            BigDecimal otherAdjustment,
            BigDecimal finalTotal,
            BigDecimal receiptSubtotal,
            MultiValueMap<String, String> parameters,
            List<String> errors) {
        if (items.isEmpty()) {
            errors.add("Add at least one item before splitting by item.");
            return Map.of();
        }
        Map<Integer, BigDecimal> itemShares = new LinkedHashMap<>();
        selectedParticipants.stream().sorted().forEach(id -> itemShares.put(id, BigDecimal.ZERO));
        BigDecimal itemTotal = BigDecimal.ZERO.setScale(2);
        BigDecimal itemGross = BigDecimal.ZERO.setScale(2);
        for (ItemInput item : items) {
            if (item == null || item.name() == null || item.name().isBlank()
                    || item.name().trim().length() > 160
                    || !validQuantity(item.quantity())
                    || !validAmount(item.totalPrice())
                    || !validAmount(item.discount())
                    || item.discount().compareTo(item.totalPrice()) > 0) {
                errors.add("Every item needs a name, positive quantity, valid price, and valid discount.");
                continue;
            }
            Set<Integer> assigned = item.participantIds() == null
                    ? Set.of() : new LinkedHashSet<>(item.participantIds());
            if (assigned.isEmpty() || !selectedParticipants.containsAll(assigned)) {
                errors.add("Assign every item to one or more selected group participants.");
                continue;
            }
            BigDecimal net = item.totalPrice().subtract(item.discount()).setScale(2);
            itemGross = itemGross.add(item.totalPrice());
            itemTotal = itemTotal.add(net);
            Map<Integer, BigDecimal> lineShares = equalShares(net, assigned);
            lineShares.forEach((id, share) ->
                    itemShares.compute(id, (ignored, current) -> current.add(share)));
        }
        if (!errors.isEmpty()) {
            return Map.of();
        }
        if (receiptSubtotal != null
                && receiptSubtotal.compareTo(itemGross) != 0) {
            errors.add("Receipt subtotal (" + receiptSubtotal.toPlainString()
                    + ") does not match the reviewed item totals ("
                    + itemGross.toPlainString() + "). Correct the subtotal or items before saving.");
            return Map.of();
        }

        BigDecimal parsedTax = zeroIfNull(tax);
        BigDecimal parsedDiscount = zeroIfNull(discount);
        BigDecimal parsedTip = zeroIfNull(tip);
        BigDecimal parsedServiceCharge = zeroIfNull(serviceCharge);
        BigDecimal expectedTotal = itemTotal.subtract(parsedDiscount)
                .add(parsedTax).add(parsedTip).add(parsedServiceCharge);
        BigDecimal difference = finalTotal.subtract(expectedTotal).setScale(2);
        BigDecimal explicitAdjustment = zeroIfNull(otherAdjustment);
        if (difference.signum() != 0 && difference.compareTo(explicitAdjustment) != 0) {
            errors.add("Receipt amounts do not reconcile. Review items and charges, or enter "
                    + "the exact receipt difference (" + difference.toPlainString()
                    + ") in the reconciliation field.");
            return Map.of();
        }
        if (difference.signum() == 0 && explicitAdjustment.signum() != 0) {
            errors.add("The receipt difference must be zero when item totals and charges "
                    + "already equal the final total.");
            return Map.of();
        }

        Map<Integer, BigDecimal> weights = new LinkedHashMap<>();
        itemShares.forEach((id, share) -> weights.put(id, share.max(BigDecimal.ZERO)));
        Map<Integer, BigDecimal> result = new LinkedHashMap<>(itemShares);
        addAllocation(result, adjustmentAllocation(
                "tax", parsedTax, selectedParticipants, weights, parameters, errors));
        addAllocation(result, adjustmentAllocation(
                "tip", parsedTip, selectedParticipants, weights, parameters, errors));
        addAllocation(result, adjustmentAllocation(
                "serviceCharge", parsedServiceCharge,
                selectedParticipants, weights, parameters, errors));
        Map<Integer, BigDecimal> discountAllocation = adjustmentAllocation(
                "discount", parsedDiscount, selectedParticipants, weights, parameters, errors);
        discountAllocation.replaceAll((id, value) -> value.negate());
        addAllocation(result, discountAllocation);
        addAllocation(result, allocateProportionally(difference, weights));
        result.replaceAll((id, share) -> share.setScale(2, RoundingMode.UNNECESSARY));
        validateShareTotal(finalTotal, result, errors);
        return result;
    }

    private Map<Integer, BigDecimal> adjustmentAllocation(
            String adjustment,
            BigDecimal amount,
            Set<Integer> participants,
            Map<Integer, BigDecimal> weights,
            MultiValueMap<String, String> parameters,
            List<String> errors) {
        boolean overridden = participants.stream()
                .anyMatch(id -> !blank(first(parameters, "allocation_" + adjustment + "_" + id)));
        if (!overridden) {
            return allocateProportionally(amount, weights);
        }
        Map<Integer, BigDecimal> allocation = new LinkedHashMap<>();
        for (Integer id : participants) {
            String raw = first(parameters, "allocation_" + adjustment + "_" + id);
            if (blank(raw)) {
                errors.add("Enter an allocation for every participant or clear all "
                        + adjustment + " allocation overrides.");
                continue;
            }
            BigDecimal share = parseShare(raw, "Enter valid " + adjustment
                    + " allocations.", errors);
            if (share != null) {
                allocation.put(id, share);
            }
        }
        BigDecimal sum = allocation.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(amount) != 0) {
            errors.add(adjustment + " allocations must add up exactly to "
                    + amount.toPlainString() + ".");
        }
        return allocation;
    }

    private Map<String, String> readAdjustmentInputs(
            MultiValueMap<String, String> parameters) {
        Map<String, String> values = new LinkedHashMap<>();
        parameters.forEach((key, entries) -> {
            if (key.startsWith("allocation_") && entries != null && !entries.isEmpty()) {
                values.put(key, entries.getFirst());
            }
        });
        return values;
    }

    private void addAllocation(
            Map<Integer, BigDecimal> shares,
            Map<Integer, BigDecimal> allocation) {
        allocation.forEach((id, value) ->
                shares.compute(id, (ignored, current) -> current.add(value)));
    }

    private Map<Integer, BigDecimal> allocateProportionally(
            BigDecimal amount,
            Map<Integer, BigDecimal> weights) {
        BigDecimal normalized = zeroIfNull(amount).setScale(2, RoundingMode.HALF_UP);
        List<Integer> ids = weights.keySet().stream().sorted().toList();
        Map<Integer, BigDecimal> result = new LinkedHashMap<>();
        if (ids.isEmpty() || normalized.signum() == 0) {
            return result;
        }
        BigDecimal totalWeight = weights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.signum() <= 0) {
            return result;
        }

        BigInteger cents = normalized.abs().movePointRight(2).toBigIntegerExact();
        BigInteger allocated = BigInteger.ZERO;
        List<AllocationRemainder> remainders = new ArrayList<>();
        for (Integer id : ids) {
            BigDecimal exactCents = new BigDecimal(cents)
                    .multiply(weights.get(id))
                    .divide(totalWeight, 16, RoundingMode.DOWN);
            BigInteger wholeCents = exactCents.toBigInteger();
            allocated = allocated.add(wholeCents);
            remainders.add(new AllocationRemainder(id, wholeCents,
                    exactCents.subtract(new BigDecimal(wholeCents))));
        }
        BigInteger remaining = cents.subtract(allocated);
        remainders.sort(Comparator.comparing(AllocationRemainder::fraction).reversed()
                .thenComparing(AllocationRemainder::userId));
        Map<Integer, BigInteger> finalCents = new LinkedHashMap<>();
        remainders.forEach(entry -> finalCents.put(entry.userId(), entry.cents()));
        for (int i = 0; i < remaining.intValueExact(); i++) {
            AllocationRemainder entry = remainders.get(i);
            finalCents.compute(entry.userId(), (ignored, old) -> old.add(BigInteger.ONE));
        }
        finalCents.forEach((id, value) -> {
            BigDecimal share = new BigDecimal(value, 2);
            result.put(id, normalized.signum() < 0 ? share.negate() : share);
        });
        return result;
    }

    private Map<Integer, BigDecimal> equalShares(
            BigDecimal amount,
            Set<Integer> participantIds) {
        List<Integer> ids = participantIds.stream().sorted().toList();
        BigInteger cents = amount.setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).toBigIntegerExact();
        BigInteger[] quotientAndRemainder = cents.divideAndRemainder(
                BigInteger.valueOf(ids.size()));
        Map<Integer, BigDecimal> result = new LinkedHashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            BigInteger share = quotientAndRemainder[0]
                    .add(i < quotientAndRemainder[1].intValueExact()
                            ? BigInteger.ONE : BigInteger.ZERO);
            result.put(ids.get(i), new BigDecimal(share, 2));
        }
        return result;
    }

    private Map<Integer, BigDecimal> readCustomShares(
            MultiValueMap<String, String> parameters,
            Set<Integer> participants,
            BigDecimal total,
            List<String> errors) {
        Map<Integer, BigDecimal> shares = new LinkedHashMap<>();
        for (Integer memberId : participants) {
            BigDecimal share = parseShare(
                    first(parameters, "share_" + memberId),
                    "Enter a valid custom share for every selected participant.", errors);
            if (share != null) {
                shares.put(memberId, share);
            }
        }
        validateShareTotal(total, shares, errors);
        return shares;
    }

    private Map<Integer, BigDecimal> readOptionalOverrides(
            MultiValueMap<String, String> parameters,
            Set<Integer> participants,
            List<String> errors) {
        Map<Integer, BigDecimal> overrides = new LinkedHashMap<>();
        boolean any = participants.stream()
                .anyMatch(id -> !blank(first(parameters, "itemShare_" + id)));
        if (!any) {
            return overrides;
        }
        for (Integer id : participants) {
            String raw = first(parameters, "itemShare_" + id);
            if (blank(raw)) {
                errors.add("Enter a manual share for every selected participant.");
                continue;
            }
            BigDecimal share = parseShare(raw, "Enter valid manual member shares.", errors);
            if (share != null) {
                overrides.put(id, share);
            }
        }
        return overrides;
    }

    private void validateShareTotal(
            BigDecimal total,
            Map<Integer, BigDecimal> shares,
            List<String> errors) {
        if (shares.values().stream().anyMatch(value -> value.signum() < 0)) {
            errors.add("Member shares cannot be negative.");
            return;
        }
        BigDecimal sum = shares.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (sum.compareTo(total) != 0) {
            errors.add("Member shares must add up exactly to the final receipt total.");
        }
    }

    private Set<Integer> parseParticipants(
            List<String> rawIds,
            Set<Integer> validMemberIds,
            List<String> errors) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (rawIds != null) {
            for (String value : rawIds) {
                Integer id = parseMember(value, validMemberIds,
                        "A selected participant is not in this group.", errors);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (ids.isEmpty()) {
            errors.add("Select at least one group participant.");
        }
        return ids;
    }

    private Integer parseMember(
            String value,
            Set<Integer> validMemberIds,
            String message,
            List<String> errors) {
        try {
            Integer id = Integer.valueOf(value);
            if (validMemberIds.contains(id)) {
                return id;
            }
        } catch (NumberFormatException | NullPointerException ignored) {
            // Use one validation message for missing and invalid member ids.
        }
        errors.add(message);
        return null;
    }

    private BigDecimal parseMoney(String raw, String message, List<String> errors) {
        if (raw == null || !raw.matches("\\d{1,17}(\\.\\d{1,2})?")) {
            errors.add(message);
            return null;
        }
        BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
        if (value.signum() <= 0 || value.precision() > 19) {
            errors.add(message);
            return null;
        }
        return value;
    }

    private BigDecimal parseShare(String raw, String message, List<String> errors) {
        if (raw == null || !raw.matches("\\d{1,17}(\\.\\d{1,2})?")) {
            errors.add(message);
            return null;
        }
        BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
        if (value.signum() < 0 || value.precision() > 19) {
            errors.add(message);
            return null;
        }
        return value;
    }

    private BigDecimal parseOptionalMoney(
            String raw,
            String field,
            List<String> errors) {
        if (blank(raw)) {
            return BigDecimal.ZERO.setScale(2);
        }
        if (!raw.matches("-?\\d{1,17}(\\.\\d{1,2})?")) {
            errors.add(field + " must be a valid amount with no more than two decimal places.");
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal value = new BigDecimal(raw).setScale(2, RoundingMode.UNNECESSARY);
        if (value.precision() > 19) {
            errors.add(field + " exceeds the supported amount.");
            return BigDecimal.ZERO.setScale(2);
        }
        if (!"Receipt difference".equals(field) && value.signum() < 0) {
            errors.add(field + " cannot be negative.");
            return BigDecimal.ZERO.setScale(2);
        }
        return value;
    }

    private LocalDate parseDate(String raw, List<String> errors) {
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException exception) {
            errors.add("Choose a valid receipt date.");
            return null;
        }
    }

    private String normalizeCategory(String raw, List<String> errors) {
        if (blank(raw)) {
            return null;
        }
        return AiReceiptScannerService.categories().stream()
                .filter(value -> value.equalsIgnoreCase(raw.trim()))
                .findFirst()
                .orElseGet(() -> {
                    errors.add("Choose a supported expense category.");
                    return null;
                });
    }

    private String expenseDescription(String merchant) {
        String cleaned = merchant == null ? "" : merchant.trim();
        return cleaned.isBlank() ? "Receipt expense" : "Receipt: " + cleaned;
    }

    private boolean validQuantity(BigDecimal quantity) {
        return quantity != null && quantity.signum() > 0
                && quantity.scale() <= 3 && quantity.precision() - quantity.scale() <= 9;
    }

    private boolean validAmount(BigDecimal amount) {
        return amount != null && amount.signum() >= 0
                && amount.scale() <= 2 && amount.precision() - amount.scale() <= 17;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2);
    }

    private String first(MultiValueMap<String, String> values, String key) {
        List<String> found = values.get(key);
        return found == null || found.isEmpty() ? null : found.getFirst();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private List<String> distinct(List<String> values) {
        return values.stream().distinct().toList();
    }

    private User getSessionUser(HttpSession session) {
        Object currentUser = session.getAttribute("loggedInUser");
        if (currentUser instanceof User user && user.getId() != null) {
            return user;
        }
        return null;
    }

    public record MemberView(Integer id, String name) {
    }

    public record ItemInput(
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal discount,
            List<Integer> participantIds) {
    }

    public record SubmittedReview(
            String merchantName,
            String merchantAddress,
            String receiptDate,
            String category,
            String currency,
            String subtotal,
            String tax,
            String discount,
            String tip,
            String serviceCharge,
            String finalTotal,
            String otherAdjustment,
            String receiptItems,
            String splittingMethod,
            String splitType,
            Integer payerId,
            Set<Integer> participantIds,
            Map<Integer, BigDecimal> shares,
            Map<String, String> adjustmentAllocations) {
    }

    private record AllocationRemainder(Integer userId, BigInteger cents, BigDecimal fraction) {
    }
}
