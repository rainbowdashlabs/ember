/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.media.service.MediaStorageService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The pictures of an article that make it into its PDF: the station's own, and nothing else.
 */
class KbPdfPicturesTest {
    private static final int STATION = 7;
    private static final UUID STATION_UID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final byte[] PNG = {1, 2, 3};
    private static final byte[] WEBP = {4, 5, 6};

    private KbImageService articleImages;
    private MediaLibraryService mediaLibrary;
    private KbPdfPictures pictures;

    @BeforeEach
    void setup() {
        articleImages = mock(KbImageService.class);
        mediaLibrary = mock(MediaLibraryService.class);
        var stations = mock(StationRepository.class);
        when(stations.resolveUid(STATION)).thenReturn(STATION_UID);
        pictures = new KbPdfPictures(articleImages, mediaLibrary, stations);
    }

    @Test
    void aPicturePastedIntoAnArticleIsPlaced() {
        when(articleImages.read(STATION, "pic1", 1024))
                .thenReturn(Optional.of(new ImageVariantService.ImageData(PNG, "image/png")));

        var placed = pictures.place(STATION, "Vorher\n\n![Plan](/kb/images/pic1?size=512)\n\nNachher");

        assertEquals("Vorher\n\n![Plan](img-1.png)\n\nNachher", placed.markdown());
        assertArrayEquals(PNG, placed.pictures().get("img-1.png"));
    }

    @Test
    void everyPrefixOfAnArticlePictureIsRecognised() {
        when(articleImages.read(eq(STATION), anyString(), anyInt()))
                .thenReturn(Optional.of(new ImageVariantService.ImageData(PNG, "image/png")));

        var placed = pictures.place(
                STATION,
                "![a](/api/v1/kb/images/a) ![b](/public/kb/images/b) ![c](/api/v1/public/kb/" + STATION_UID
                        + "/images/c)");

        assertEquals("![a](img-1.png) ![b](img-2.png) ![c](img-3.png)", placed.markdown());
    }

    @Test
    void aPictureOfTheMediaLibraryIsPlaced() {
        when(mediaLibrary.readVariant(STATION, "hash", 1024, "image/webp"))
                .thenReturn(Optional.of(new MediaStorageService.FileData(WEBP, "image/webp")));

        var placed = pictures.place(STATION, "![Wappen](/api/v1/public/media/" + STATION_UID + "/hash)");

        assertEquals("![Wappen](img-1.webp)", placed.markdown());
        assertArrayEquals(WEBP, placed.pictures().get("img-1.webp"));
    }

    @Test
    void aPictureGivenAWidthIsPlacedToo() {
        when(articleImages.read(STATION, "wide", 1024))
                .thenReturn(Optional.of(new ImageVariantService.ImageData(PNG, "image/png")));

        var placed = pictures.place(
                STATION, "<img src=\"/kb/images/wide\" alt=\"Plan\" width=\"300\" style=\"width: 300px\" />");

        assertEquals("<img src=\"img-1.png\" alt=\"Plan\" width=\"300\" style=\"width: 300px\" />", placed.markdown());
        assertArrayEquals(PNG, placed.pictures().get("img-1.png"));
    }

    @Test
    void theSamePictureTwiceIsPlacedOnce() {
        when(articleImages.read(STATION, "pic", 1024))
                .thenReturn(Optional.of(new ImageVariantService.ImageData(PNG, "image/png")));

        var placed = pictures.place(STATION, "![x](/kb/images/pic) ![y](/kb/images/pic)");

        assertEquals("![x](img-1.png) ![y](img-1.png)", placed.markdown());
        assertEquals(1, placed.pictures().size());
    }

    @Test
    void whatIsNotTheStationsOwnKeepsItsUrl() {
        String markdown = "![fremd](/api/v1/public/media/" + UUID.randomUUID() + "/hash) "
                + "![kaputt](/api/v1/public/media/not-a-uid/hash) "
                + "![web](https://example.com/a.png) "
                + "![fehlt](/kb/images/gone)";
        when(articleImages.read(eq(STATION), eq("gone"), anyInt())).thenReturn(Optional.empty());

        var placed = pictures.place(STATION, markdown);

        assertEquals(markdown, placed.markdown());
        assertTrue(placed.pictures().isEmpty());
    }

    @Test
    void aFileThePdfCannotShowKeepsItsUrl() {
        when(mediaLibrary.readVariant(eq(STATION), eq("doc"), any(), any()))
                .thenReturn(Optional.of(new MediaStorageService.FileData(PNG, "application/pdf")));
        String markdown = "![Handbuch](/api/v1/public/media/" + STATION_UID + "/doc)";

        assertEquals(markdown, pictures.place(STATION, markdown).markdown());
    }

    @Test
    void aPictureThatFailsToLoadDoesNotStopTheExport() {
        when(articleImages.read(STATION, "broken", 1024)).thenThrow(new IllegalStateException("storage down"));

        var placed = pictures.place(STATION, "![a](/kb/images/broken)");

        assertEquals("![a](/kb/images/broken)", placed.markdown());
    }
}
