/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.knowledgebase.service.KbFilePictureService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.system.repository.ApplicationSettingRepository;
import dev.chojo.ember.util.FilePicture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The pictures of PDF pages are drawn again once for each new resolution, and not on every start. */
class PagePictureRedrawServiceTest {
    private static final String CURRENT = String.valueOf(FilePicture.PAGE_DPI);

    private ApplicationSettingRepository settings;
    private DocumentService documents;
    private MediaLibraryService media;
    private KbFilePictureService wikiFiles;
    private PagePictureRedrawService service;

    @BeforeEach
    void setup() {
        settings = mock(ApplicationSettingRepository.class);
        documents = mock(DocumentService.class);
        media = mock(MediaLibraryService.class);
        wikiFiles = mock(KbFilePictureService.class);
        service = new PagePictureRedrawService(settings, documents, media, wikiFiles);
    }

    /** An instance that never recorded a resolution cannot tell how its pictures were drawn. */
    @Test
    void anUnrecordedResolutionDrawsEverythingAndRecordsIt() {
        when(settings.get(PagePictureRedrawService.DRAWN_AT_KEY)).thenReturn(Optional.empty());

        assertTrue(service.redrawIfNeeded());

        verify(documents).redrawPages();
        verify(media).redrawPages();
        verify(wikiFiles).redrawPages();
        verify(settings).set(PagePictureRedrawService.DRAWN_AT_KEY, CURRENT);
    }

    @Test
    void anotherResolutionDrawsEverythingAgain() {
        when(settings.get(PagePictureRedrawService.DRAWN_AT_KEY)).thenReturn(Optional.of("96"));

        assertTrue(service.redrawIfNeeded());

        verify(settings).set(PagePictureRedrawService.DRAWN_AT_KEY, CURRENT);
    }

    @Test
    void theSameResolutionDrawsNothing() {
        when(settings.get(PagePictureRedrawService.DRAWN_AT_KEY)).thenReturn(Optional.of(CURRENT));

        assertFalse(service.redrawIfNeeded());

        verify(documents, never()).redrawPages();
        verify(media, never()).redrawPages();
        verify(wikiFiles, never()).redrawPages();
    }

    /** A pass that fails halfway records nothing, so the next start tries again. */
    @Test
    void aFailedBackgroundPassRecordsNothing() {
        when(settings.get(PagePictureRedrawService.DRAWN_AT_KEY)).thenReturn(Optional.empty());
        when(media.redrawPages()).thenThrow(new IllegalStateException("storage gone"));

        assertDoesNotThrow(service::redrawInBackground);

        verify(settings, never()).set(anyString(), anyString());
    }
}
