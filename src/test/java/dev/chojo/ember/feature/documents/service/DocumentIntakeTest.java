/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.TestUploads;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.media.image.MediaTypes;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The one intake every uploaded file passes, whoever hands it over.
 *
 * <p>Manual uploads used to skip what only the mail import checked: the station's own limit for one
 * file, its room left, and what the bytes say they are.
 */
class DocumentIntakeTest {
    private static final int STATION = 3;
    private static final byte[] PDF = "%PDF-1.7 und so weiter".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
    private static final DocumentIntake.Refusals REFUSALS = DocumentDoor.STATION.intake();

    private StorageQuotaService quota;
    private DocumentIntake intake;

    @BeforeEach
    void setup() {
        quota = mock(StorageQuotaService.class);
        when(quota.perFileLimitBytes(anyInt())).thenReturn(100L);
        intake = new DocumentIntake(quota);
    }

    private static void assertRefused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    private String take(String fileName, String declared, byte[] data) {
        return intake.take(
                STATION,
                StorageCategory.MEMBER_DOCUMENTS,
                new DocumentIntake.Upload(fileName, declared, data),
                REFUSALS);
    }

    @Test
    void aFileIsReadWhenItArrivedWhole() {
        var upload = intake.read(STATION, TestUploads.of("a.pdf", "application/pdf", PDF), REFUSALS);

        assertEquals("a.pdf", upload.fileName());
        assertArrayEquals(PDF, upload.data());
    }

    /** A missing, oversized or unreadable upload is a named refusal, never a server error. */
    @Test
    void aMissingOversizedOrUnreadableUploadIsRefusedByName() {
        assertRefused(DocumentRefusal.DOCUMENT_UPLOAD_MISSING_FILE, () -> intake.read(STATION, null, REFUSALS));
        assertRefused(
                DocumentRefusal.DOCUMENT_UPLOAD_TOO_LARGE,
                () -> intake.read(STATION, TestUploads.unreadable("gross.pdf", 101), REFUSALS));
        assertRefused(
                DocumentRefusal.DOCUMENT_UPLOAD_UNREADABLE,
                () -> intake.read(STATION, TestUploads.unreadable("kaputt.pdf", 10), REFUSALS));
    }

    /** The station's own limit for one file holds for what is uploaded by hand, not only by mail. */
    @Test
    void theStationsPerFileLimitHolds() {
        assertRefused(DocumentRefusal.DOCUMENT_UPLOAD_TOO_LARGE, () -> take("gross.bin", null, new byte[101]));
    }

    /** A station out of room takes nothing more, by hand or by mail. */
    @Test
    void aStationOutOfRoomTakesNothing() {
        doThrow(new StorageQuotaService.StorageQuotaExceededException("voll"))
                .when(quota)
                .checkQuota(eq(STATION), eq(StorageCategory.MEMBER_DOCUMENTS), anyLong());

        assertRefused(DocumentRefusal.DOCUMENT_UPLOAD_NO_ROOM, () -> take("a.pdf", "application/pdf", PDF));
    }

    /** The bytes decide what a file is, wherever they can tell. */
    @Test
    void theBytesDecideWhatAFileIs() {
        assertEquals("application/pdf", take("scan", "image/png", PDF));
        assertEquals("image/png", take("bild.png", "application/octet-stream", PNG));
    }

    /** A name that claims one kind of file for bytes of another is refused, as the mail import does. */
    @Test
    void aFileCalledOneThingAndMadeOfAnotherIsRefused() {
        assertRefused(DocumentRefusal.DOCUMENT_UPLOAD_NOT_WHAT_IT_IS_CALLED, () -> take("bild.png", "image/png", PDF));
    }

    /**
     * Bytes that say nothing keep the declared type, unless that type is one the bytes would have shown:
     * then the file is kept as plain bytes and never shown inline as something it is not.
     */
    @Test
    void bytesThatSayNothingNeverPassForAKindTheyAreNot() {
        byte[] text = "<html>hallo</html>".getBytes(StandardCharsets.UTF_8);

        assertEquals("text/plain", take("notiz.txt", "text/plain", text));
        assertEquals(MediaTypes.UNTYPED, take("bild.png", "image/png", text));
        assertEquals(MediaTypes.UNTYPED, take("notiz", "application/pdf", text));
        assertEquals(MediaTypes.UNTYPED, take("notiz", null, text));
    }

    @Test
    void aTitleIsTheFileNameWhereNoneWasGiven() {
        var upload = new DocumentIntake.Upload("vertrag.pdf", null, PDF);

        assertEquals("vertrag.pdf", DocumentIntake.titleOf(null, upload));
        assertEquals("vertrag.pdf", DocumentIntake.titleOf("  ", upload));
        assertEquals("Vertrag", DocumentIntake.titleOf(" Vertrag ", upload));
    }
}
