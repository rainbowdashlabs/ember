/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a report's picture is allowed to be.
 *
 * <p>The part worth pinning is the refusing, because it is what stands between a bug report and a
 * file of any shape somebody chose to call a picture. Storing is exercised where there is a place to
 * store things; these are the answers that are given before anything is stored at all.
 */
class ProblemReportScreenshotServiceTest {

    private final ProblemReportScreenshotService service = new ProblemReportScreenshotService(null);

    /** The given bytes as a picture would arrive, so a test can write the first few of a format. */
    private static String encoded(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            bytes[i] = (byte) values[i];
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** A report without a picture is the ordinary case and must not be turned into an error. */
    @Test
    void nothingSentIsNothingStored() {
        assertTrue(service.store(null, null).isEmpty());
        assertTrue(service.store("", null).isEmpty());
        assertTrue(service.store("   ", null).isEmpty());
    }

    @Test
    void whatIsNotBase64IsRefused() {
        var refused = assertThrows(RefusalResponse.class, () -> service.store("this is not a picture", null));

        assertEquals(Refusal.PROBLEM_PICTURE_NOT_READABLE, refused.refusal());
    }

    /**
     * The bytes are read rather than the type somebody wrote in the request: a sender claiming "png"
     * proves nothing about what they sent, and a beacon takes these from instances it did not write.
     */
    @Test
    void whatIsNeitherPngNorWebpIsRefused() {
        assertThrows(RefusalResponse.class, () -> service.store(encoded('G', 'I', 'F', '8', '9', 'a'), null));
        assertThrows(RefusalResponse.class, () -> service.store(encoded(0xFF, 0xD8, 0xFF), null));
        var refused = assertThrows(RefusalResponse.class, () -> service.store(encoded('<', 's', 'v', 'g'), null));

        assertEquals(Refusal.PROBLEM_PICTURE_KIND_NOT_TAKEN, refused.refusal());
    }

    /** A preamble with nothing behind it is a picture that was announced and never sent. */
    @Test
    void anEmptyPictureIsRefused() {
        var refused = assertThrows(RefusalResponse.class, () -> service.store("data:image/png;base64,", null));

        assertEquals(Refusal.PROBLEM_PICTURE_EMPTY, refused.refusal());
    }

    /** A browser writes the preamble in front of the bytes, and it is not part of them. */
    @Test
    void theDataPreambleABrowserWritesIsReadPast() {
        String withPreamble = "data:image/gif;base64," + encoded('G', 'I', 'F', '8', '9', 'a');

        var refused = assertThrows(RefusalResponse.class, () -> service.store(withPreamble, null));

        assertEquals(Refusal.PROBLEM_PICTURE_KIND_NOT_TAKEN, refused.refusal(), "it got as far as reading the bytes");
    }
}
