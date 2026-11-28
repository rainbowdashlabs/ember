/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            Map.entry("items", "<li>one</li><li>two</li>"),
            Map.entry("appointmentName", "Dienstabend"),
            Map.entry("appointmentDate", "12.05.2026"),
            Map.entry("appointmentTime", "18:00 - 20:00"),
            Map.entry("arrivalTime", "17:45"),
            Map.entry("location", "Feuerwache, Musterstadt"),
            Map.entry("validFor", "30 days"),
            Map.entry("removedIn", "14 days"));

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
        var vars = new HashMap<>(VARS);
        String rendered = renderer.render(name, locale, vars);
        assertFalse(rendered.isBlank(), "rendered output is empty");
        assertTrue(rendered.contains("<html"), "rendered output is missing <html> tag");
        assertTrue(
                rendered.contains("Ember") || rendered.contains("Test Station"),
                "shared layout did not render senderName or stationName");
        assertFalse(
                rendered.matches("(?s).*\\{\\{\\s*\\w+\\s*}}.*"),
                "rendered output still contains unsubstituted placeholders");
    }

    /**
     * The weekly reminder names the dates still open, each in one box with its details, and words itself
     * for one date or several.
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "de, Diese Termine sind noch offen, Dieser Termin ist noch offen",
        "en, These dates are still open, This date is still open"
    })
    void theReminderNamesEachOpenDateInOneBox(String locale, String several, String one) {
        var drill = Map.of(
                "appointmentName", "Dienstabend",
                "appointmentDate", "12.05.2099",
                "appointmentTime", "18:00 - 20:00",
                "arrivalTime", "17:45",
                "location", "Feuerwache");
        var exercise = Map.of(
                "appointmentName", "Übung",
                "appointmentDate", "19.05.2099",
                "appointmentTime", "18:00 - 20:00",
                "arrivalTime", "",
                "location", "");

        var vars = new HashMap<String, Object>(VARS);
        vars.put("appointments", List.of(drill, exercise));
        String twoDates = renderer.render("waitlist-invitation-reminder.html", locale, vars);
        var boxes = Pattern.compile("(?s)<li class=\"notification-item\">(.*?)</li>")
                .matcher(twoDates)
                .results()
                .map(match -> match.group(1))
                .toList();
        assertEquals(3, boxes.size(), "the station's message and one box per date");
        assertTrue(boxes.get(1).contains("Dienstabend") && boxes.get(1).contains("17:45"));
        assertTrue(boxes.get(2).contains("Übung") && boxes.get(2).contains("19.05.2099"));
        assertTrue(twoDates.contains(several));

        vars.put("appointments", List.of(drill));
        assertTrue(renderer.render("waitlist-invitation-reminder.html", locale, vars)
                .contains(one));
    }

    /**
     * Each date a waiting-list mail offers stands in one box with its name, day, time, arrival and
     * place, rather than one box per line, which left the reader to work out which lines belonged
     * together.
     */
    @ParameterizedTest(name = "{0}/{1}")
    @CsvSource({
        "de, waitlist-invitation.html",
        "de, waitlist-other-dates.html",
        "en, waitlist-invitation.html",
        "en, waitlist-other-dates.html"
    })
    void eachOfferedDateStandsInOneBox(String locale, String name) {
        var vars = new HashMap<String, Object>(VARS);
        vars.put("message", "");
        vars.put(
                "appointments",
                List.of(
                        Map.of(
                                "appointmentName", "Dienstabend",
                                "appointmentDate", "12.05.2026",
                                "appointmentTime", "18:00 - 20:00",
                                "arrivalTime", "17:45",
                                "location", "Feuerwache"),
                        Map.of(
                                "appointmentName", "Übung",
                                "appointmentDate", "19.05.2026",
                                "appointmentTime", "18:00 - 20:00",
                                "arrivalTime", "",
                                "location", "")));

        String rendered = renderer.render(name, locale, vars);

        var boxes = Pattern.compile("(?s)<li class=\"notification-item\">(.*?)</li>")
                .matcher(rendered)
                .results()
                .map(match -> match.group(1))
                .toList();
        assertEquals(2, boxes.size(), "one box per offered date");
        assertTrue(boxes.get(0).contains("Dienstabend")
                && boxes.get(0).contains("12.05.2026")
                && boxes.get(0).contains("17:45")
                && boxes.get(0).contains("Feuerwache"));
        assertTrue(boxes.get(1).contains("Übung") && boxes.get(1).contains("19.05.2026"));
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
        String rendered = renderer.render("notification-digest.html", "en", vars);
        assertTrue(
                rendered.contains("<img src=\"https://example.test/logo.png\">"),
                "logoHtml should be rendered raw via the {{ ... | raw }} filter");
    }

    /** A station mail shows the station's logo in its header, and a mail without one shows no empty image. */
    @Test
    void theHeaderShowsTheStationLogoWhereOneIsGiven() {
        var vars = new HashMap<>(VARS);
        vars.put("logoUrl", "https://example.test/api/v1/public/stations/abc/logo?size=128");
        assertTrue(renderer.render("application-accepted.html", "en", vars)
                .contains("<img src=\"https://example.test/api/v1/public/stations/abc/logo?size=128\""));
        assertFalse(renderer.render("application-accepted.html", "en", VARS).contains("<img"));
    }

    @Test
    void renderFallsBackToEnglishWhenLocaleMissing() {
        var vars = new HashMap<>(VARS);
        String rendered = renderer.render("station-delete.html", "de", vars);
        assertTrue(rendered.contains("Confirm"), "expected English fallback content");
    }

    /** Short spans read in hours, long ones in days, and the German wording fits after "nach". */
    @ParameterizedTest
    @CsvSource({
        "1, en, 1 hour",
        "24, en, 24 hours",
        "72, en, 3 days",
        "720, en, 30 days",
        "1, de, 1 Stunde",
        "24, de, 24 Stunden",
        "720, de, 30 Tagen"
    })
    void aSpanIsWrittenInTheUnitItIsMeasuredIn(long hours, String locale, String expected) {
        assertEquals(expected, renderer.duration(Duration.ofHours(hours), locale));
    }

    /** A link mail prints the lifetime it is given, not a number of its own. */
    @Test
    void aLinkMailPrintsTheLifetimeItIsGiven() {
        var vars = new HashMap<>(VARS);
        vars.put("validFor", renderer.duration(Duration.ofHours(1), "de"));
        String rendered = renderer.render("reset-password.html", "de", vars);
        assertTrue(rendered.contains("verfällt nach 1 Stunde"), rendered);
    }
}
