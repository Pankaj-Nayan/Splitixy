package com.splitixy.service;

import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Service
public class AiReceiptScannerService {

    private static final Logger log = LoggerFactory.getLogger(AiReceiptScannerService.class);
    public static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 20_000_000L;
    private static final List<String> CATEGORIES = List.of(
            "Food & Dining", "Travel", "Accommodation", "Transport", "Utilities",
            "Shopping", "Entertainment", "Groceries", "Other");

    private final GeminiReceiptExtractor extractor;

    public AiReceiptScannerService(GeminiReceiptExtractor extractor) {
        this.extractor = extractor;
    }

    public ReceiptExtraction extract(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new RequestValidationException("Choose a non-empty receipt image.");
        }
        if (upload.getSize() > MAX_IMAGE_BYTES) {
            throw new RequestValidationException("Receipt images must be 10 MB or smaller.");
        }

        try {
            byte[] image = upload.getBytes();
            if (image.length == 0) {
                throw new RequestValidationException("Choose a non-empty receipt image.");
            }
            String detectedType = detectAndValidate(image);
            ReceiptExtraction extraction = extractor.extract(
                    image, GeminiReceiptExtractor.imageType(detectedType));
            if (extraction == null) {
                throw new ExtractionException();
            }
            return normalize(extraction);
        } catch (IOException exception) {
            throw new RequestValidationException(
                    "The image could not be read. Choose a valid JPG, PNG, or WebP image.");
        } catch (RequestValidationException | ExtractionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            log.warn("Gemini receipt extraction failed ({})",
                    exception.getClass().getSimpleName());
            throw new ExtractionException();
        }
    }

    public static List<String> categories() {
        return CATEGORIES;
    }

    private ReceiptExtraction normalize(ReceiptExtraction extraction) {
        List<String> missing = clean(extraction.missingOrUnreadableFields());
        List<String> inconsistencies = clean(extraction.inconsistencies());
        List<String> uncertain = clean(extraction.uncertainFields());
        List<ReceiptExtraction.ReceiptItem> items = extraction.items() == null
                ? List.of()
                : extraction.items().stream()
                        .filter(item -> item != null)
                        .map(item -> new ReceiptExtraction.ReceiptItem(
                                bounded(item.name(), 160),
                                validQuantity(item.quantity()) ? normalizedQuantity(item.quantity()) : null,
                                normalizedMoney(item.unitPrice()),
                                normalizedMoney(item.totalPrice()),
                                normalizedMoney(item.discount()),
                                bounded(item.confidence(), 40),
                                bounded(item.uncertainty(), 300)))
                        .limit(250)
                        .toList();
        for (ReceiptExtraction.ReceiptItem item : items) {
            if (item.name() == null || item.name().isBlank()) {
                missing.add("One or more item names need review.");
            }
            if (invalidMoney(item.totalPrice()) || item.totalPrice().signum() < 0) {
                uncertain.add("An item price is missing or needs review.");
            }
            if (item.quantity() != null && item.quantity().signum() <= 0) {
                uncertain.add("An item quantity needs review.");
            }
            if (item.unitPrice() != null && !validMoney(item.unitPrice())) {
                uncertain.add("An item unit price needs review.");
            }
            if (item.discount() != null && !validMoney(item.discount())) {
                uncertain.add("An item discount needs review.");
            }
            if (item.uncertainty() != null && !item.uncertainty().isBlank()) {
                uncertain.add("Item " + valueOrDefault(item.name(), "entry")
                        + ": " + item.uncertainty());
            } else if (item.confidence() != null
                    && !item.confidence().equalsIgnoreCase("high")) {
                uncertain.add("Item " + valueOrDefault(item.name(), "entry")
                        + " has " + item.confidence() + " extraction confidence.");
            }
        }
        BigDecimal itemGross = items.stream()
                .filter(item -> item.totalPrice() != null && validMoney(item.totalPrice()))
                .map(ReceiptExtraction.ReceiptItem::totalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal itemDiscounts = items.stream()
                .filter(item -> item.discount() != null && validMoney(item.discount()))
                .map(ReceiptExtraction.ReceiptItem::discount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (extraction.subtotal() != null && validMoney(extraction.subtotal())
                && extraction.subtotal().compareTo(itemGross) != 0 && !items.isEmpty()) {
            inconsistencies.add("The printed subtotal differs from the sum of extracted item totals.");
        }
        if (extraction.finalTotal() != null && validMoney(extraction.finalTotal())) {
            BigDecimal calculated = itemGross.subtract(itemDiscounts)
                    .add(zeroIfNull(extraction.tax()))
                    .add(zeroIfNull(extraction.tip()))
                    .add(zeroIfNull(extraction.serviceCharge()))
                    .subtract(zeroIfNull(extraction.discount()));
            if (!items.isEmpty() && calculated.compareTo(extraction.finalTotal()) != 0) {
                inconsistencies.add("Extracted items and additional charges do not match the final total.");
            }
        }
        if (extraction.finalTotal() == null || extraction.finalTotal().signum() <= 0) {
            missing.add("Final total needs review.");
        }
        if (extraction.currency() == null || extraction.currency().isBlank()) {
            missing.add("Currency needs review.");
        }
        if (extraction.merchantName() == null || extraction.merchantName().isBlank()) {
            missing.add("Merchant needs review.");
        }
        if (extraction.receiptDate() == null || extraction.receiptDate().isBlank()) {
            missing.add("Receipt date needs review.");
        }
        flagInvalidMoney(extraction.subtotal(), "Subtotal", uncertain);
        flagInvalidMoney(extraction.tax(), "Tax", uncertain);
        flagInvalidMoney(extraction.discount(), "Discount", uncertain);
        flagInvalidMoney(extraction.tip(), "Tip", uncertain);
        flagInvalidMoney(extraction.serviceCharge(), "Service charge", uncertain);
        flagInvalidMoney(extraction.finalTotal(), "Final total", uncertain);
        if (extraction.category() != null
                && normalizeCategory(extraction.category()) == null) {
            uncertain.add("The extracted expense category needs review.");
        }
        if (extraction.receiptDate() != null && !extraction.receiptDate().isBlank()) {
            try {
                LocalDate.parse(extraction.receiptDate());
            } catch (DateTimeParseException exception) {
                uncertain.add("The receipt date needs review.");
            }
        }
        if (Boolean.TRUE.equals(extraction.incomplete())) {
            inconsistencies.add("The receipt image may be incomplete.");
        }
        return new ReceiptExtraction(
                bounded(extraction.merchantName(), 160),
                bounded(extraction.merchantAddress(), 300),
                bounded(extraction.receiptDate(), 32),
                bounded(extraction.receiptTime(), 32),
                bounded(extraction.currency(), 3),
                normalizeCategory(extraction.category()),
                normalizedMoney(extraction.subtotal()),
                normalizedMoney(extraction.tax()),
                normalizedMoney(extraction.discount()),
                normalizedMoney(extraction.tip()),
                normalizedMoney(extraction.serviceCharge()),
                normalizedMoney(extraction.finalTotal()),
                bounded(extraction.receiptNumber(), 100),
                items,
                distinct(missing),
                distinct(inconsistencies),
                distinct(uncertain),
                Boolean.TRUE.equals(extraction.incomplete()));
    }

    private String detectAndValidate(byte[] bytes) throws IOException {
        String type;
        if (isJpeg(bytes)) {
            type = "image/jpeg";
        } else if (isPng(bytes)) {
            type = "image/png";
        } else if (isWebp(bytes)) {
            validateWebp(bytes);
            return "image/webp";
        } else {
            throw new RequestValidationException(
                    "Unsupported image. Upload a JPG, JPEG, PNG, or WebP receipt.");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw new RequestValidationException("The receipt image is malformed.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new RequestValidationException("The receipt image is malformed.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0
                        || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new RequestValidationException(
                            "The receipt image dimensions are invalid or too large.");
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new RequestValidationException("The receipt image is malformed.");
                }
                decoded.flush();
            } catch (IndexOutOfBoundsException | IllegalStateException exception) {
                throw new RequestValidationException("The receipt image is malformed.");
            } finally {
                reader.dispose();
            }
        }
        return type;
    }

    private void validateWebp(byte[] bytes) {
        if (bytes.length < 30) {
            throw new RequestValidationException("The WebP receipt image is malformed.");
        }
        long declaredLength = littleEndian32(bytes, 4) + 8;
        if (declaredLength != bytes.length) {
            throw new RequestValidationException("The WebP receipt image is malformed.");
        }
        int offset = 12;
        boolean hasImageData = false;
        long width = 0;
        long height = 0;
        while (offset + 8 <= bytes.length) {
            String chunk = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            long chunkSize = littleEndian32(bytes, offset + 4);
            int dataOffset = offset + 8;
            long chunkEnd = (long) offset + 8 + chunkSize + (chunkSize & 1);
            if (chunkEnd > bytes.length) {
                throw new RequestValidationException("The WebP receipt image is malformed.");
            }
            if (chunk.equals("VP8 ") || chunk.equals("VP8L") || chunk.equals("VP8X")) {
                hasImageData |= chunkSize > 0;
                if (chunk.equals("VP8X") && chunkSize >= 10) {
                    width = 1 + littleEndian24(bytes, dataOffset + 4);
                    height = 1 + littleEndian24(bytes, dataOffset + 7);
                } else if (chunk.equals("VP8 ") && chunkSize >= 10
                        && bytes[dataOffset + 3] == (byte) 0x9d
                        && bytes[dataOffset + 4] == 0x01
                        && bytes[dataOffset + 5] == 0x2a) {
                    width = littleEndian16(bytes, dataOffset + 6) & 0x3fff;
                    height = littleEndian16(bytes, dataOffset + 8) & 0x3fff;
                } else if (chunk.equals("VP8L") && chunkSize >= 5
                        && bytes[dataOffset] == 0x2f) {
                    int b1 = bytes[dataOffset + 1] & 0xff;
                    int b2 = bytes[dataOffset + 2] & 0xff;
                    int b3 = bytes[dataOffset + 3] & 0xff;
                    int b4 = bytes[dataOffset + 4] & 0xff;
                    width = 1 + b1 + ((b2 & 0x3f) << 8);
                    height = 1 + ((b2 & 0xc0) >> 6) + (b3 << 2) + ((b4 & 0x0f) << 10);
                }
            }
            offset = (int) chunkEnd;
        }
        if (!hasImageData || offset != bytes.length || width <= 0 || height <= 0
                || width * height > MAX_IMAGE_PIXELS) {
            throw new RequestValidationException("The WebP receipt image is malformed.");
        }
    }

    private int littleEndian16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private int littleEndian24(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16);
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    private long littleEndian32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL)
                | ((bytes[offset + 1] & 0xffL) << 8)
                | ((bytes[offset + 2] & 0xffL) << 16)
                | ((bytes[offset + 3] & 0xffL) << 24);
    }

    private boolean invalidMoney(BigDecimal value) {
        return value == null || !validMoney(value);
    }

    private boolean validMoney(BigDecimal value) {
        return value != null && value.signum() >= 0 && value.scale() <= 2
                && value.precision() - value.scale() <= 17;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null || !validMoney(value) ? BigDecimal.ZERO : value;
    }

    private BigDecimal normalizedMoney(BigDecimal value) {
        return validMoney(value) ? value.setScale(2, RoundingMode.UNNECESSARY) : null;
    }

    private BigDecimal normalizedQuantity(BigDecimal value) {
        return value.setScale(Math.max(0, value.scale()), RoundingMode.UNNECESSARY);
    }

    private boolean validQuantity(BigDecimal value) {
        return value != null && value.signum() > 0 && value.scale() <= 3
                && value.precision() - value.scale() <= 9;
    }

    private String normalizeCategory(String value) {
        if (value == null) {
            return null;
        }
        return CATEGORIES.stream()
                .filter(category -> category.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElse(null);
    }

    private String bounded(String value, int maximum) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maximum ? trimmed : trimmed.substring(0, maximum);
    }

    private void flagInvalidMoney(
            BigDecimal value,
            String field,
            List<String> uncertain) {
        if (value != null && !validMoney(value)) {
            uncertain.add(field + " needs review because the extracted amount is invalid.");
        }
    }

    private String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private List<String> clean(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> bounded(value, 300))
                .distinct()
                .limit(50)
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private List<String> distinct(List<String> values) {
        return values.stream().distinct().limit(50).toList();
    }

    public static class RequestValidationException extends RuntimeException {
        public RequestValidationException(String message) {
            super(message);
        }
    }

    public static class ExtractionException extends RuntimeException {
    }
}
