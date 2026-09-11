/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a request path is reduced to the endpoint it belongs to.
 *
 * <p>Every request to one endpoint has to reduce to the same string, or the monitoring page counts one
 * endpoint per identifier and the detail page for any of them is empty. Both halves of that are worth
 * pinning: that identifiers do collapse, and that nothing else does.
 */
class ApiRequestLoggerPathTest {

    @Test
    void anIdentifierSegmentBecomesAPlaceholder() {
        assertEquals("/api/v1/events/{id}/register", ApiRequestLogger.normalizePath("/api/v1/events/42/register"));
        assertEquals("/api/v1/members/{id}", ApiRequestLogger.normalizePath("/api/v1/members/7"));
        assertEquals("/api/v1/a/{id}/b/{id}", ApiRequestLogger.normalizePath("/api/v1/a/1/b/2"));
    }

    /**
     * A segment is taken whole or not at all. Rewriting the digits inside one recorded the endpoint under
     * a path nobody could ask for, and its detail page was then permanently empty.
     */
    @Test
    void aSegmentThatMerelyBeginsWithDigitsIsLeftAlone() {
        assertEquals("/api/v1/stations/2024-abc", ApiRequestLogger.normalizePath("/api/v1/stations/2024-abc"));
        assertEquals("/api/v1/things/1a", ApiRequestLogger.normalizePath("/api/v1/things/1a"));
        assertEquals("/api/v1/things/a1", ApiRequestLogger.normalizePath("/api/v1/things/a1"));
    }

    /**
     * A feed token is the whole of the credential, so leaving it recorded writes the key to somebody's
     * calendar into the statistics and gives every subscriber a row of their own.
     */
    @Test
    void aFeedTokenIsTakenForWhatItIs() {
        assertEquals(
                "/api/v1/public/feed/{id}/lost-and-found/{id}/image",
                ApiRequestLogger.normalizePath(
                        "/api/v1/public/feed/9hgEV4EC3ojBNSEGb9et9HRGPqkHlKCnKtm0n7f5lA/lost-and-found/7/image"));
        assertEquals(
                "/api/v1/public/feed/{id}/events.ics",
                ApiRequestLogger.normalizePath(
                        "/api/v1/public/feed/Uot1QDzlW8qWjl8MzNtbQONeBezFSF3gO0fBLVSyL-w/events.ics"));
    }

    /**
     * A station's name is not a token, however long it runs. It is words, so it carries neither a capital
     * nor a digit, and the requests to one station's page are worth seeing as their own line.
     */
    @Test
    void aLongNameIsStillAName() {
        assertEquals(
                "/api/v1/stations/freiwillige-feuerwehr-musterstadt-nord",
                ApiRequestLogger.normalizePath("/api/v1/stations/freiwillige-feuerwehr-musterstadt-nord"));
    }

    /** A slug is not an identifier, so it stays as it is and the endpoint is counted per station. */
    @Test
    void aSlugStaysAsItIs() {
        assertEquals(
                "/api/v1/public/station/jugendfeuerwehr-nordstadt/info",
                ApiRequestLogger.normalizePath("/api/v1/public/station/jugendfeuerwehr-nordstadt/info"));
    }

    /**
     * A uuid names one particular thing just as a number does, so it has to collapse too. Left alone, the
     * monitoring page grows a line for every page anybody has ever opened.
     */
    @Test
    void aUuidSegmentBecomesAPlaceholderToo() {
        assertEquals(
                "/api/v1/public/pages/{id}",
                ApiRequestLogger.normalizePath("/api/v1/public/pages/00000000-0000-4000-a000-000000000003"));
        assertEquals(
                "/api/v1/members/{id}/{id}/avatar",
                ApiRequestLogger.normalizePath(
                        "/api/v1/members/00000000-0000-4000-a000-000000000001/25b145ac-2404-32a4-ab8e-2b1f63d6cb82/avatar"));
    }

    @Test
    void somethingMerelyUuidShapedIsNotOne() {
        assertEquals(
                "/api/v1/things/00000000-0000-4000-a000-00000000000",
                ApiRequestLogger.normalizePath("/api/v1/things/00000000-0000-4000-a000-00000000000"));
        assertEquals(
                "/api/v1/things/zzzzzzzz-0000-4000-a000-000000000003",
                ApiRequestLogger.normalizePath("/api/v1/things/zzzzzzzz-0000-4000-a000-000000000003"));
        assertEquals(
                "/api/v1/things/000000000-000-4000-a000-000000000003",
                ApiRequestLogger.normalizePath("/api/v1/things/000000000-000-4000-a000-000000000003"));
    }

    @Test
    void aPathAlreadyCarryingAPlaceholderIsUnchanged() {
        assertEquals("/api/v1/events/{id}/register", ApiRequestLogger.normalizePath("/api/v1/events/{id}/register"));
    }

    @Test
    void nothingUnusualBreaksIt() {
        assertEquals("", ApiRequestLogger.normalizePath(""));
        assertEquals("/", ApiRequestLogger.normalizePath("/"));
        assertEquals("/{id}", ApiRequestLogger.normalizePath("/1"));
        assertEquals("/api/v1/a//b", ApiRequestLogger.normalizePath("/api/v1/a//b"));
        assertEquals("/api/v1/events/{id}/", ApiRequestLogger.normalizePath("/api/v1/events/42/"));
        assertEquals(null, ApiRequestLogger.normalizePath(null));
    }
}
