/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.CellContentType;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a picture is called where its cell has not said, for every reader of blocks alike.
 */
class CellDescriptionsTest {
    private static final int STATION = 3;

    private CellDescriptions descriptions;

    @BeforeEach
    void setup() {
        var media = mock(MediaLibraryService.class);
        when(media.findByHash(anyInt(), anyString())).thenReturn(Optional.empty());
        when(media.findByHash(STATION, "known")).thenReturn(Optional.of(file("Die Halle", "Von außen")));
        descriptions = new CellDescriptions(media);
    }

    @Test
    void everyPictureOfAGalleryCarriesTheWordsOfItsOwnFile() {
        var gallery = new CellConfig.ImageGalleryConfig(
                List.of(
                        new CellConfig.GalleryItem("known", null, ""),
                        new CellConfig.GalleryItem("known", "Eigener Text", "Eigene Zeile"),
                        new CellConfig.GalleryItem("unknown", null, null)),
                3,
                null,
                null);

        var described = (CellConfig.ImageGalleryConfig) descriptions
                .describe(STATION, cell(CellContentType.IMAGE_GALLERY, "", gallery))
                .config();

        assertEquals(
                new CellConfig.GalleryItem("known", "Die Halle", "Von außen"),
                described.items().get(0));
        assertEquals(
                new CellConfig.GalleryItem("known", "Eigener Text", "Eigene Zeile"),
                described.items().get(1));
        assertEquals(
                new CellConfig.GalleryItem("unknown", null, null),
                described.items().get(2));
        assertEquals(3, described.columns());
    }

    @Test
    void aGalleryWithoutPicturesIsLeftAlone() {
        var empty = new CellConfig.ImageGalleryConfig(null, null, null, null);
        var cell = cell(CellContentType.IMAGE_GALLERY, "", empty);

        assertSame(empty, descriptions.describe(STATION, cell).config());
    }

    @Test
    void aCellWithoutAPictureIsLeftAlone() {
        var markdown = cell(CellContentType.MARKDOWN, "Text", CellConfig.EMPTY);
        var unconfigured = cell(CellContentType.IMAGE, "known", null);

        assertSame(markdown, descriptions.describe(STATION, markdown));
        assertSame(unconfigured, descriptions.describe(STATION, unconfigured));
    }

    @Test
    void everyCellOfEveryRowIsDescribed() {
        var image = new CellConfig.ImageConfig(null, null, null, null, null, null, null, null, null, null, null);
        var rows = List.of(new ContentRow(1, 1, 0, List.of(cell(CellContentType.IMAGE, "known", image))));

        var described = (CellConfig.ImageConfig) descriptions
                .describe(STATION, rows)
                .getFirst()
                .cells()
                .getFirst()
                .config();

        assertEquals("Die Halle", described.altText());
        assertEquals("Von außen", described.description());
    }

    private static ContentCell cell(CellContentType type, String content, CellConfig config) {
        return new ContentCell(1, 1, 0, 100.0, type, content, config);
    }

    private static StationFile file(String alt, String description) {
        return new StationFile(
                1, 0, STATION, "known", "halle.png", "image/png", 64, Instant.now(), alt, description, null);
    }
}
