/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageIdentityTest {

    private static final Instant ARRIVED = Instant.parse("2026-09-10T08:15:00Z");

    @Test
    void aMessageWithAnIdentifierOfItsOwnKeepsIt() {
        var identity = MessageIdentity.of("<abc@musterstadt.de>", "post@musterstadt.de", "Attest", ARRIVED);

        assertEquals("<abc@musterstadt.de>", identity.id());
        assertFalse(identity.derived());
    }

    @Test
    void aMessageWithoutOneGetsAnIdentifierMadeFromItsEnvelope() {
        var identity = MessageIdentity.of(null, "post@musterstadt.de", "Attest", ARRIVED);

        assertTrue(identity.derived());
        assertTrue(identity.id().startsWith("derived:"));
    }

    @Test
    void theSameEnvelopeIsRecognisedAsTheSameMessage() {
        var first = MessageIdentity.of(null, "post@musterstadt.de", "Attest", ARRIVED);
        var again = MessageIdentity.of("   ", "post@musterstadt.de", "Attest", ARRIVED);

        assertEquals(first.id(), again.id());
    }

    /**
     * Two different mails must not collide into one, or the second would look handled and be dropped
     * without anybody being told.
     */
    @Test
    void differentEnvelopesAreDifferentMessages() {
        var attest = MessageIdentity.of(null, "post@musterstadt.de", "Attest", ARRIVED);
        var invoice = MessageIdentity.of(null, "post@musterstadt.de", "Rechnung", ARRIVED);
        var elsewhere = MessageIdentity.of(null, "andere@example.com", "Attest", ARRIVED);
        var later = MessageIdentity.of(null, "post@musterstadt.de", "Attest", ARRIVED.plusSeconds(60));

        assertNotEquals(attest.id(), invoice.id());
        assertNotEquals(attest.id(), elsewhere.id());
        assertNotEquals(attest.id(), later.id());
    }

    @Test
    void anEnvelopeWithNothingInItStillYieldsAnIdentity() {
        var identity = MessageIdentity.of(null, null, null, null);

        assertTrue(identity.derived());
        assertTrue(identity.id().startsWith("derived:"));
    }

    @Test
    void theSameBytesHashTheSameWayAndDifferentBytesDoNot() {
        byte[] scan = "Bescheinigung".getBytes(StandardCharsets.UTF_8);

        assertEquals(MessageIdentity.hashOf(scan), MessageIdentity.hashOf("Bescheinigung".getBytes(StandardCharsets.UTF_8)));
        assertNotEquals(MessageIdentity.hashOf(scan), MessageIdentity.hashOf("Rechnung".getBytes(StandardCharsets.UTF_8)));
        assertEquals(64, MessageIdentity.hashOf(scan).length());
    }
}
