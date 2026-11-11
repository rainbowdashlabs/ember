/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.service;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What leaves for a beacon names the fault and not the people: mail addresses, identifiers and the
 * names of accounts are replaced, everything else stays as it was written.
 */
class BeaconRedactionTest {
    private final BeaconRedaction.Redactor redactor = new BeaconRedaction.Redactor(Set.of("nora", "fülling", "maxi"));

    @Test
    void mailAddressesIdentifiersAndNamesAreReplaced() {
        assertEquals(
                "[name] [name] <[mail]> opened [uuid]",
                redactor.redact("Nora Fülling <nora.f@example.com> opened 5F0C6D1E-2B3A-4C5D-8E9F-0A1B2C3D4E5F"));
    }

    @Test
    void aNameIsMatchedOnlyAsAWholeWord() {
        assertEquals("Maximum reached for [name]", redactor.redact("Maximum reached for maxi"));
        assertEquals("NORAH stays", redactor.redact("NORAH stays"));
    }

    @Test
    void whatNamesTheFaultStays() {
        String line = "404 on GET /api/v1/events/17: No such event (HTTP 404)";

        assertEquals(line, redactor.redact(line));
    }

    @Test
    void nothingStaysNothing() {
        assertNull(redactor.redact(null));
        assertEquals("", redactor.redact(""));
    }

    @Test
    void internationalAddressesAreReplacedWhole() {
        assertEquals("to [mail] and [mail]", redactor.redact("to jürgen@example.de and max@müller.de"));
    }

    @Test
    void ipAddressesAreReplaced() {
        assertEquals(
                "from [ip] via [ip] at 10:00:00",
                redactor.redact("from 192.168.1.20 via 2001:db8::8a2e:370:7334 at 10:00:00"));
    }

    @Test
    void secretsLoseTheirValue() {
        assertEquals("Password: [secret]", redactor.redact("Password: hunter2"));
        assertEquals(
                "https://ember.test/enroll?code=[secret]&next=1",
                redactor.redact("https://ember.test/enroll?code=abc123&next=1"));
        assertEquals("token=[secret]", redactor.redact("token=eyJhbGciOi"));
    }

    @Test
    void aNameIsNeverTakenForAReplacement() {
        var withMail = new BeaconRedaction.Redactor(Set.of("mail", "name"));

        assertEquals("[mail] and [name]", withMail.redact("a@b.de and [name]"));
    }

    @Test
    void codesThatNameAFaultStayAndABearerTokenGoesWhole() {
        assertEquals(
                "HTTP status code: 409, refusal code: B-002",
                redactor.redact("HTTP status code: 409, refusal code: B-002"));
        assertEquals("Authorization: [secret] next", redactor.redact("Authorization: Bearer abc.def next"));
    }
}
