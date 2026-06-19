/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.util;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogRedactionTest {

    @Test
    void redactsTokenValueInQueryString() {
        assertEquals("a=1&token=[REDACTED]&b=2", LogRedaction.redactQueryString("a=1&token=abc&b=2"));
    }

    @Test
    void redactsStationIdValueInQueryString() {
        assertEquals(
                "stationId=[REDACTED]",
                LogRedaction.redactQueryString("stationId=00000000-0000-0000-0000-000000000001"));
    }

    @Test
    void keyMatchIsCaseInsensitiveInQueryString() {
        assertEquals("TOKEN=[REDACTED]", LogRedaction.redactQueryString("TOKEN=abc"));
        assertEquals("Token=[REDACTED]", LogRedaction.redactQueryString("Token=abc"));
    }

    @Test
    void leavesUnrelatedQueryKeysAlone() {
        assertEquals("a=1&b=2&c=3", LogRedaction.redactQueryString("a=1&b=2&c=3"));
    }

    @Test
    void handlesValuelessQueryPair() {
        assertEquals("flag&a=1", LogRedaction.redactQueryString("flag&a=1"));
    }

    @Test
    void nullAndEmptyQueryStringReturnEmpty() {
        assertEquals("", LogRedaction.redactQueryString(null));
        assertEquals("", LogRedaction.redactQueryString(""));
    }

    @Test
    void redactsAuthorizationHeaderCaseInsensitively() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer abc");
        headers.put("Content-Type", "application/json");
        var out = LogRedaction.redactHeaders(headers);
        assertEquals("[REDACTED]", out.get("Authorization"));
        assertEquals("application/json", out.get("Content-Type"));
    }

    @Test
    void redactsFederationSignatureHeader() {
        var out = LogRedaction.redactHeaders(Map.of("X-Federation-Signature", "abc=="));
        assertEquals("[REDACTED]", out.get("X-Federation-Signature"));
    }

    @Test
    void redactsCookieHeaders() {
        var out = LogRedaction.redactHeaders(Map.of("Cookie", "session=abc"));
        assertEquals("[REDACTED]", out.get("Cookie"));
    }

    @Test
    void inputMapIsNotMutated() {
        var input = new LinkedHashMap<String, String>();
        input.put("Authorization", "Bearer abc");
        LogRedaction.redactHeaders(input);
        assertEquals("Bearer abc", input.get("Authorization"));
    }

    @Test
    void nullAndEmptyHeadersReturnEmptyMap() {
        assertEquals(Map.of(), LogRedaction.redactHeaders(null));
        assertEquals(Map.of(), LogRedaction.redactHeaders(Map.of()));
    }
}
