/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.mail.service.MailTemplateRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MailTemplateRendererTest {

    private static final Path TEMPLATE_ROOT = Path.of("templates/mail");

    private static final Map<String, String> VARS = Map.ofEntries(
            Map.entry("name", "Alice"),
            Map.entry("senderName", "Ember"),
            Map.entry("baseUrl", "https://example.test"),
            Map.entry("url", "https://example.test/action"),
            Map.entry("loginUrl", "https://example.test/login"),
            Map.entry("stationName", "Test Station"),
            Map.entry("reason", "Out of scope"),
            Map.entry("oldEmail", "old@example.test"),
            Map.entry("newEmail", "new@example.test"),
            Map.entry("category", "News"),
            Map.entry("message", "Something happened"),
            Map.entry("actionUrl", "https://example.test/action"),
            Map.entry("count", "3"),
            Map.entry("actor", "Admin"),
            Map.entry("resetAt", "2026-01-01 12:00"),
            Map.entry("logoHtml", "<img src=\"x\">"),
            Map.entry("items", "<li>one</li><li>two</li>"));

    private final MailTemplateRenderer renderer = new MailTemplateRenderer();

    static Stream<Object[]> allTemplates() throws IOException {
        try (var stream = Files.walk(TEMPLATE_ROOT, 2)) {
            return stream
                    .filter(p -> p.toString().endsWith(".html"))
                    .filter(p -> !p.getFileName().toString().startsWith("_"))
                    .map(p -> new Object[] {
                        TEMPLATE_ROOT.relativize(p.getParent()).toString(),
                        p.getFileName().toString()
                    })
                    .toList()
                    .stream();
        }
    }

    @ParameterizedTest(name = "{0}/{1}")
    @MethodSource("allTemplates")
    void renders(String locale, String name) {
        // Pass a mutable copy so the renderer can add its own keys (e.g. lang).
        var vars = new HashMap<>(VARS);
        String rendered = renderer.render(name, locale, vars);
        assertFalse(rendered.isBlank(), "rendered output is empty");
        assertTrue(rendered.contains("<html"), "rendered output is missing <html> tag");
        assertTrue(
                rendered.contains("Ember") || rendered.contains("Test Station"),
                "shared layout did not render senderName or stationName");
        // Confirm no unsubstituted {{var}} placeholders remain.
        assertFalse(
                rendered.matches("(?s).*\\{\\{\\s*\\w+\\s*}}.*"),
                "rendered output still contains unsubstituted placeholders");
    }

    @Test
    void renderEscapesUserSuppliedHtmlByDefault() {
        var vars = new HashMap<>(VARS);
        vars.put("name", "<script>alert(1)</script>");
        String rendered = renderer.render("verify-email.html", "en", vars);
        assertFalse(rendered.contains("<script>alert(1)</script>"), "unsafe HTML was rendered raw");
        assertTrue(rendered.contains("&lt;script&gt;"), "expected HTML-escaped variant");
    }

    @Test
    void renderHonoursRawFilterForLogoHtml() {
        var vars = new HashMap<>(VARS);
        vars.put("logoHtml", "<img src=\"https://example.test/logo.png\">");
        String rendered = renderer.render("station-notification.html", "en", vars);
        assertTrue(
                rendered.contains("<img src=\"https://example.test/logo.png\">"),
                "logoHtml should be rendered raw via the {{ ... | raw }} filter");
    }

    @Test
    void renderFallsBackToEnglishWhenLocaleMissing() {
        // en/email-change.html exists but de/ does not — should fall back to en.
        var vars = new HashMap<>(VARS);
        String rendered = renderer.render("email-change.html", "de", vars);
        assertTrue(rendered.contains("Confirm"), "expected English fallback content");
    }
}
