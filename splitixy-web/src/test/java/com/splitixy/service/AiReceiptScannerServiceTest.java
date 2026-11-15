package com.splitixy.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.MimeTypeUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReceiptScannerServiceTest {

    private GeminiReceiptExtractor extractor;
    private AiReceiptScannerService scanner;
    private ReceiptExtraction extraction;

    @BeforeEach
    void setUp() {
        extractor = mock(GeminiReceiptExtractor.class);
        scanner = new AiReceiptScannerService(extractor);
        extraction = new ReceiptExtraction(
                "Cafe", null, "2026-11-13", null, "INR", "Food & Dining",
                null, null, null, null, null, null, null, null,
                null, null, null, false);
        when(extractor.extract(any(byte[].class), any())).thenReturn(extraction);
    }

    @Test
    void detectsImageTypeFromBytesInsteadOfUploadMetadata() throws IOException {
        byte[] image = png();
        MockMultipartFile upload = new MockMultipartFile(
                "receipt", "untrusted.jpg", "image/jpeg", image);

        assertEquals("Cafe", scanner.extract(upload).merchantName());
        verify(extractor).extract(eq(image), eq(MimeTypeUtils.IMAGE_PNG));
    }

    @Test
    void rejectsEmptyAndUnsupportedUploadsBeforeGemini() {
        assertThrows(AiReceiptScannerService.RequestValidationException.class,
                () -> scanner.extract(new MockMultipartFile(
                        "receipt", "empty.png", "image/png", new byte[0])));
        assertThrows(AiReceiptScannerService.RequestValidationException.class,
                () -> scanner.extract(new MockMultipartFile(
                        "receipt", "receipt.png", "image/png", new byte[]{1, 2, 3})));
    }

    @Test
    void rejectsMalformedImageAndOversizedUploadBeforeGemini() {
        byte[] malformedPng = {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 0
        };
        assertThrows(AiReceiptScannerService.RequestValidationException.class,
                () -> scanner.extract(new MockMultipartFile(
                        "receipt", "receipt.png", "image/png", malformedPng)));

        byte[] oversized = new byte[AiReceiptScannerService.MAX_IMAGE_BYTES + 1];
        assertThrows(AiReceiptScannerService.RequestValidationException.class,
                () -> scanner.extract(new MockMultipartFile(
                        "receipt", "receipt.png", "image/png", oversized)));
    }

    private byte[] png() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
