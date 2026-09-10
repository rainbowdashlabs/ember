/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mailimport.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSnifferTest {

    private static byte[] pdf() {
        return "%PDF-1.7\n1 0 obj".getBytes(StandardCharsets.US_ASCII);
    }

    private static byte[] png() {
        return new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00};
    }

    private static byte[] jpeg() {
        return new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10};
    }

    @Test
    void theThreeKindsAreRecognisedByTheirFirstBytes() {
        assertEquals("application/pdf", ContentSniffer.sniff(pdf()));
        assertEquals("image/png", ContentSniffer.sniff(png()));
        assertEquals("image/jpeg", ContentSniffer.sniff(jpeg()));
    }

    @Test
    void bytesThatSayNothingKnownAnswerNothing() {
        assertNull(ContentSniffer.sniff("just some text".getBytes(StandardCharsets.UTF_8)));
        assertNull(ContentSniffer.sniff(new byte[0]));
        assertNull(ContentSniffer.sniff(null));
        assertNull(ContentSniffer.sniff(new byte[] {'%', 'P'}));
    }

    /**
     * The guard this exists for. An executable named after a document is exactly what a store keeping
     * other people's paperwork must not accept on the strength of its name.
     */
    @Test
    void aFileWhoseNameContradictsItsBytesIsRefused() {
        assertFalse(ContentSniffer.nameAgrees("invoice.pdf", ContentSniffer.sniff(png())));
        assertFalse(ContentSniffer.nameAgrees("scan.png", ContentSniffer.sniff(pdf())));
        assertFalse(ContentSniffer.nameAgrees("photo.jpg", ContentSniffer.sniff(png())));
    }

    @Test
    void aFileWhoseNameAgreesWithItsBytesPasses() {
        assertTrue(ContentSniffer.nameAgrees("bescheinigung.pdf", ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.nameAgrees("Bescheinigung.PDF", ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.nameAgrees("scan.png", ContentSniffer.sniff(png())));
        assertTrue(ContentSniffer.nameAgrees("scan.jpeg", ContentSniffer.sniff(jpeg())));
        assertTrue(ContentSniffer.nameAgrees("scan.jpg", ContentSniffer.sniff(jpeg())));
    }

    /** Nothing to contradict, so nothing is refused for it. */
    @Test
    void aNameWithNothingToSayAgreesWithAnything() {
        assertTrue(ContentSniffer.nameAgrees("scan", ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.nameAgrees("scan.", ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.nameAgrees(null, ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.nameAgrees("", ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.nameAgrees("anhang.dat", ContentSniffer.sniff(pdf())));
    }

    @Test
    void bytesNobodyCouldTypeAgreeWithNoName() {
        assertFalse(ContentSniffer.nameAgrees("bescheinigung.pdf", null));
        assertFalse(ContentSniffer.nameAgrees("scan", null));
    }

    @Test
    void everySupportedTypeIsOneThisCanActuallyAnswer() {
        assertEquals(3, ContentSniffer.SUPPORTED_TYPES.size());
        assertTrue(ContentSniffer.SUPPORTED_TYPES.contains(ContentSniffer.sniff(pdf())));
        assertTrue(ContentSniffer.SUPPORTED_TYPES.contains(ContentSniffer.sniff(png())));
        assertTrue(ContentSniffer.SUPPORTED_TYPES.contains(ContentSniffer.sniff(jpeg())));
    }
}
