/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.content.service.ContentBlockService;
import dev.chojo.ember.feature.knowledgebase.entity.ConversionStatus;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KbPresentationServiceTest extends RepositoryTestBase {
    private static final String PPTX_MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation";

    private static KbPresentationService service;
    private static KbFileStorageService fileStorage;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        fileStorage = mock(KbFileStorageService.class);
        var contentService = new KbContentService(
                knowledgeBaseRepo,
                new ContentBlockService(contentContainerRepo),
                stationRepo,
                fileStorage,
                new KbSearchService(knowledgeBaseRepo, stationRepo));
        service = new KbPresentationService(knowledgeBaseRepo, fileStorage, contentService);
        station = stationRepo.create("KbPresentationStation");
        account = accountRepo.create("kb-presentation@test.com", "Kb", "PresentationTester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static int createPresentation(String name) {
        return knowledgeBaseRepo
                .createFile(station.id(), null, name, "", KbFileType.PRESENTATION, PPTX_MIME, 1, null, member.id())
                .id();
    }

    private static ConversionStatus statusOf(int fileId) {
        return knowledgeBaseRepo.findFileById(fileId).orElseThrow().conversionStatus();
    }

    /**
     * A stored conversion result marks the slide deck as ready and can be read back as the PDF a
     * reader is served.
     */
    @Test
    void aStoredConversionBecomesTheServedPdf() {
        int fileId = createPresentation("stored.pptx");
        byte[] pdf = "fake-pdf-content".getBytes(StandardCharsets.UTF_8);
        when(fileStorage.readPresentationPdf(station.id(), fileId))
                .thenReturn(Optional.of(new KbFileStorageService.FileData(pdf, "application/pdf")));

        service.storePresentationResult(station.id(), fileId, pdf);

        assertEquals(ConversionStatus.SUCCESS, statusOf(fileId));
        assertArrayEquals(pdf, service.getPresentationPdf(fileId).orElseThrow());

        knowledgeBaseRepo.purgeFile(fileId);
    }

    /**
     * A conversion whose result cannot be stored is marked as failed, so the file reports the
     * problem instead of appearing to still be converting.
     */
    @Test
    void aConversionThatCannotBeStoredIsMarkedFailed() {
        int fileId = createPresentation("unstorable.pptx");
        doThrow(new RuntimeException("disk full"))
                .when(fileStorage)
                .storePresentationPdf(eq(station.id()), eq(fileId), any());

        service.storePresentationResult(station.id(), fileId, "pdf".getBytes(StandardCharsets.UTF_8));

        assertEquals(ConversionStatus.FAILED, statusOf(fileId));
        knowledgeBaseRepo.purgeFile(fileId);
    }

    /**
     * A conversion that cannot run at all leaves the file marked as failed rather than pending
     * forever, so a reader is told the rendered version is never coming.
     */
    @Test
    void aConversionThatCannotRunIsMarkedFailed() {
        int fileId = createPresentation("broken.pptx");

        service.convert(station.id(), fileId, null, "broken.pptx");

        assertEquals(ConversionStatus.FAILED, statusOf(fileId));
        knowledgeBaseRepo.purgeFile(fileId);
    }

    /**
     * Re-uploading replaces the stored deck and puts the file back into conversion, so the served
     * PDF is rebuilt from the new slides.
     */
    @Test
    void reuploadingReplacesTheDeckAndReconverts() {
        int fileId = createPresentation("reupload.pptx");
        service.storePresentationResult(station.id(), fileId, "old".getBytes(StandardCharsets.UTF_8));
        assertEquals(ConversionStatus.SUCCESS, statusOf(fileId));

        service.reuploadPresentation(fileId, new byte[] {0x02}, "application/vnd.ms-powerpoint", "v2.ppt");

        verify(fileStorage).store(eq(station.id()), eq(fileId), any(), eq("application/vnd.ms-powerpoint"));
        assertNotNull(statusOf(fileId));
        knowledgeBaseRepo.purgeFile(fileId);
    }

    @Test
    void anUnknownFileServesNoPdf() {
        assertTrue(service.getPresentationPdf(999999).isEmpty());
    }
}
