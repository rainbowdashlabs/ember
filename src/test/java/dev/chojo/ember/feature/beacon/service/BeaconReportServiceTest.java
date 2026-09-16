/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import dev.chojo.ember.feature.system.entity.ProblemReport;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What leaves an instance when somebody writes a problem report, and what does not.
 */
class BeaconReportServiceTest {

    private final DiscoveryHttpClient httpClient = mock(DiscoveryHttpClient.class);

    private BeaconReportService serviceThatForwards(boolean forwardReports) {
        var config = mock(BeaconSettings.class);
        when(config.forwardReports()).thenReturn(forwardReports);
        when(config.url()).thenReturn("https://beacon.test");
        when(config.contactName()).thenReturn("");
        when(config.contactMail()).thenReturn("");
        return new BeaconReportService(config, httpClient);
    }

    /**
     * A report goes nowhere until the operator has said it may.
     *
     * <p>Asked here rather than at the caller. The switch existed, was stored, was drawn in the
     * settings screen and was read by nothing, so agreeing to it changed as little as refusing it.
     */
    @Test
    void aReportIsNotForwardedUntilTheOperatorAgrees() {
        assertFalse(
                serviceThatForwards(false).sendReport(aReport("/station/members"), "1.0.0"),
                "nothing is queued while forwarding is off");
    }

    /** With the switch on, a report is queued rather than sent on the thread that wrote it. */
    @Test
    void aReportIsQueuedOnceTheOperatorAgrees() {
        assertTrue(
                serviceThatForwards(true).sendReport(aReport("/station/members"), "1.0.0"), "the report is on its way");
    }

    /**
     * A button is not the switch.
     *
     * <p>The switch governs what leaves on its own; pressing send is an operator deciding about the
     * one report in front of them. Without this, a report written before the switch was turned on
     * could never be passed on at all, which is the state every report was in until now.
     */
    @Test
    void aReportSentByHandGoesWhateverTheSwitchSays() {
        assertTrue(
                serviceThatForwards(false).sendReportNow(aReport("/station/members"), "1.0.0"),
                "the operator asked for this one");
    }

    /**
     * The query string is dropped before anything leaves.
     *
     * <p>A query carries what somebody searched for and sometimes who they looked at, none of which
     * says which page went wrong. The screen already sends the path alone, so this is the guarantee
     * rather than the hope: what leaves the instance is decided here, not in the browser.
     */
    @Test
    void theQueryStringNeverLeavesWithTheReport() {
        var payload = serviceThatForwards(true).reportPayloadFor(aReport("/station/members?q=Nora%20F"), "1.0.0");
        assertEquals("/station/members", payload.page(), "the page is kept and the query is not");
    }

    /**
     * What the screen was, which is what makes a sentence about it worth reading.
     *
     * <p>"Der Knopf tut nichts" names no defect on its own. The browser it happened in, how large
     * the window was and what the reader was allowed to do are what tell a fault everybody meets
     * from one only a guardian on a phone meets, and none of it says who they were.
     */
    @Test
    void theScreenTravelsWithTheReportAndThePersonDoesNot() {
        var payload = serviceThatForwards(true).reportPayloadFor(aReport("/station/members"), "1.0.0");

        assertEquals("Firefox/141.0", payload.browser());
        assertEquals("1920x1080", payload.screenSize());
        assertEquals("LOGIN, USER", payload.roles());
        assertTrue(payload.recentRequests().contains("/api/v1/events"), "the calls before it went");

        String whole = payload.toString();
        assertFalse(whole.contains("Nora Fülling"), "who wrote it stays at the station: " + whole);
    }

    /** The calls are stripped of their queries the same way the page is, and for the same reason. */
    @Test
    void theQueriesOfTheCallsBeforeItAreDroppedToo() {
        var payload = serviceThatForwards(true).reportPayloadFor(aReport("/station/members"), "1.0.0");

        assertTrue(payload.recentRequests().contains("\"url\":\"/api/v1/members\""), payload.recentRequests());
        assertFalse(payload.recentRequests().contains("q=Nora"), payload.recentRequests());
    }

    /**
     * A warning without an exception is nothing but its words.
     *
     * <p>No class, no stacktrace, so no frames: a beacon was told a logger name, a level and a count.
     * One class logs several different failures, so they arrived as one row that named none of them.
     */
    @Test
    void whatAFaultWasLoggedWithTravelsWithIt() {
        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0");

        assertTrue(payload.message().contains("HTTP 409"), payload.message());
        assertTrue(payload.message().contains("HTTP 500"), "the wordings are told apart: " + payload.message());
    }

    /**
     * A mail address is the one thing in a message that is a person and never a diagnosis.
     *
     * <p>Everything else is kept as it was written. An identifier, a path inside the product and a
     * status code are what make a fault findable again, and taking those out would leave the sentence
     * as useless as the logger name was.
     */
    @Test
    void aMailAddressIsTakenOutOfWhatTravels() {
        var payload = serviceThatForwards(true).payloadFor(aWarning(), "1.0.0");

        assertFalse(payload.message().contains("nora@example.com"), payload.message());
        assertTrue(payload.message().contains("/remote/registrations/dd396799"), "the path is kept");
    }

    /** A fault as the log holds one: no exception, and the same call failing two different ways. */
    private static ProblemLogAppender.Snapshot aWarning() {
        return new ProblemLogAppender.Snapshot(
                1,
                "WARN",
                "dev.chojo.ember.feature.federation.service.FederationHttpClient",
                null,
                null,
                null,
                Instant.now().minusSeconds(600),
                Instant.now(),
                2,
                false,
                List.of(
                        "Signed GET list /remote/registrations/dd396799 failed: HTTP 409",
                        "Signed GET list /remote/registrations/dd396799 failed: HTTP 500",
                        "Mail an nora@example.com ging nicht"));
    }

    /**
     * A report as a station holds one, with a name on it that must not travel and a call carrying a
     * query that must not travel either.
     */
    private static ProblemReport aReport(String page) {
        return new ProblemReport(
                1,
                7,
                3,
                "Nora Fülling",
                "Das ging schief",
                page,
                "LOGIN, USER",
                "[{\"url\":\"/api/v1/events\",\"status\":500},{\"url\":\"/api/v1/members?q=Nora\",\"status\":200}]",
                "Firefox/141.0",
                "1920x1080",
                null,
                false,
                null,
                null,
                Instant.now());
    }
}
