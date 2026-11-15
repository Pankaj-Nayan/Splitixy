package com.splitixy.controller;

import com.splitixy.service.AiReceiptScannerService;
import com.splitixy.service.ReceiptExtraction;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AiReceiptScannerTemplateTest {

    @Test
    void rendersTheEditableReceiptReviewForTheSelectedGroup() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        TemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);

        MockServletContext servletContext = new MockServletContext();
        JakartaServletWebApplication webApplication =
                JakartaServletWebApplication.buildApplication(servletContext);
        WebContext context = new WebContext(
                webApplication.buildExchange(
                        new MockHttpServletRequest(servletContext),
                        new MockHttpServletResponse()),
                Locale.ROOT);
        context.setVariable("_csrf", Map.of("parameterName", "_csrf", "token", "test-token"));
        context.setVariable("draftToken", "draft-1");
        context.setVariable("draft", new ReceiptExtraction(
                "Cafe", "1 Main Street", "2026-11-13", "12:00", "INR", "Food & Dining",
                new BigDecimal("900.00"), new BigDecimal("90.00"),
                new BigDecimal("30.00"), new BigDecimal("15.00"),
                BigDecimal.ZERO, new BigDecimal("975.00"), "R-123",
                List.of(new ReceiptExtraction.ReceiptItem(
                        "Pizza", BigDecimal.ONE, new BigDecimal("600.00"),
                        new BigDecimal("600.00"), BigDecimal.ZERO, "high", null)),
                List.of(), List.of(), List.of(), false));
        context.setVariable("initialItemsJson",
                "[{\"name\":\"Pizza\",\"quantity\":1,\"totalPrice\":600,"
                        + "\"discount\":0,\"participantIds\":[]}]");
        context.setVariable("groups", List.of());
        context.setVariable("categories", AiReceiptScannerService.categories());
        context.setVariable("members", List.of(
                new AiReceiptScannerController.MemberView(1, "Pankaj")));
        context.setVariable("selectedGroupId", 7);
        context.setVariable("selectedGroupName", "Friends");
        context.setVariable("submitted", new AiReceiptScannerController.SubmittedReview(
                "Cafe", "1 Main Street", "2026-11-13", "Food & Dining", "INR",
                "900.00", "90.00", "30.00", "15.00", "0.00", "975.00",
                "", "[{\"name\":\"Pizza\",\"quantity\":1,\"totalPrice\":600,"
                        + "\"discount\":0,\"participantIds\":[1]}]",
                "ITEMS", "EQUAL", 1, java.util.Set.of(1),
                Map.of(1, new BigDecimal("975.00")),
                Map.of("allocation_tax_1", "90.00")));

        String html = engine.process("ai-receipt-scanner", context);

        assertTrue(html.contains("Review extracted receipt"));
        assertTrue(html.contains("Friends"));
        assertTrue(html.contains("Pizza"));
        assertTrue(html.contains("Confirm and add expense"));
    }
}
