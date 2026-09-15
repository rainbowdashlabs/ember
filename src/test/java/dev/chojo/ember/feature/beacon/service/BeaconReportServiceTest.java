/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import dev.chojo.ember.feature.discovery.service.DiscoveryHttpClient;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                serviceThatForwards(false).sendReport("Das ging schief", "/station/members", Instant.now(), "1.0.0"),
                "nothing is queued while forwarding is off");
    }

    /** With the switch on, a report is queued rather than sent on the thread that wrote it. */
    @Test
    void aReportIsQueuedOnceTheOperatorAgrees() {
        assertTrue(
                serviceThatForwards(true).sendReport("Das ging schief", "/station/members", Instant.now(), "1.0.0"),
                "the report is on its way");
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
                serviceThatForwards(false).sendReportNow("Das ging schief", "/station/members", Instant.now(), "1.0.0"),
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
        var payload = serviceThatForwards(true)
                .reportPayloadFor("Das ging schief", "/station/members?q=Nora%20F", Instant.now(), "1.0.0");
        assertEquals("/station/members", payload.page(), "the page is kept and the query is not");
    }
}
