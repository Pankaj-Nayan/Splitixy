package com.splitixy.service;

import java.math.BigDecimal;
import java.util.List;

public record ReceiptExtraction(
        String merchantName,
        String merchantAddress,
        String receiptDate,
        String receiptTime,
        String currency,
        String category,
        BigDecimal subtotal,
        BigDecimal tax,
        BigDecimal discount,
        BigDecimal tip,
        BigDecimal serviceCharge,
        BigDecimal finalTotal,
        String receiptNumber,
        List<ReceiptItem> items,
        List<String> missingOrUnreadableFields,
        List<String> inconsistencies,
        List<String> uncertainFields,
        Boolean incomplete) {

    public record ReceiptItem(
            String name,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal discount,
            String confidence,
            String uncertainty) {
    }
}
