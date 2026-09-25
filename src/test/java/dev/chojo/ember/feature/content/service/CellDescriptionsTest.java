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
import static org.junit.jupiter.api.Assertions.assertNull;
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

    private static final String KNOWN_PAGE = "11111111-2222-3333-4444-555555555555";

    private CellDescriptions descriptions;

    @BeforeEach
    void setup() {
        var media = mock(MediaLibraryService.class);
        when(media.findByHash(anyInt(), anyString())).thenReturn(Optional.empty());
        when(media.findByHash(STATION, "known")).thenReturn(Optional.of(file("Die Halle", "Von außen")));
        descriptions = new CellDescriptions(
                media,
                (stationId, pageUid) -> KNOWN_PAGE.equals(pageUid)
                        ? Optional.of(new CellDescriptions.PageAddress(
                                "Die Seite", "/public/station/musterstadt/page/die-seite"))
                        : Optional.empty());
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

    @Test
    void aCardLinkingToAPageIsGivenThatPagesNameAndAddress() {
        var described = (CellConfig.PageLinkConfig) descriptions
                .describe(
                        STATION,
                        cell(CellContentType.PAGE_LINK, "", new CellConfig.PageLinkConfig(KNOWN_PAGE, "Ersatz")))
                .config();

        assertEquals("Die Seite", described.resolvedTitle());
        assertEquals("/public/station/musterstadt/page/die-seite", described.resolvedHref());
        assertEquals(KNOWN_PAGE, described.pageUid(), "what was stored is left alone");
    }

    @Test
    void aCardWhosePageIsGoneKeepsItsStandInAndGetsNoAddress() {
        var gone = (CellConfig.PageLinkConfig) descriptions
                .describe(
                        STATION,
                        cell(CellContentType.PAGE_LINK, "", new CellConfig.PageLinkConfig("nicht-da", "Ersatz")))
                .config();
        assertNull(gone.resolvedHref());
        assertEquals("Ersatz", gone.fallbackTitle());

        var unpicked = (CellConfig.PageLinkConfig) descriptions
                .describe(STATION, cell(CellContentType.PAGE_LINK, "", new CellConfig.PageLinkConfig(null, null)))
                .config();
        assertNull(unpicked.resolvedHref());
    }

    /**
     * Rows inside a cell were walked past entirely, so a picture in one went unnamed and a card in
     * one pointed nowhere. Both are the same fix and this asks for both at once.
     */
    @Test
    void whatSitsInsideANestedRowIsDescribedToo() {
        var inner = CellConfig.MAPPER.createArrayNode();
        var row = CellConfig.MAPPER.createObjectNode();
        var cells = CellConfig.MAPPER.createArrayNode();
        cells.add(CellConfig.MAPPER
                .createObjectNode()
                .put("contentType", "IMAGE")
                .put("content", "known")
                .set(
                        "config",
                        CellConfig.MAPPER.valueToTree(new CellConfig.ImageConfig(
                                null, null, null, null, null, null, null, null, null, null, null))));
        cells.add(CellConfig.MAPPER
                .createObjectNode()
                .put("contentType", "PAGE_LINK")
                .put("content", "")
                .set("config", CellConfig.MAPPER.valueToTree(new CellConfig.PageLinkConfig(KNOWN_PAGE, "Ersatz"))));
        row.set("cells", cells);
        inner.add(row);

        var described = (CellConfig.NestedRowsConfig) descriptions
                .describe(STATION, cell(CellContentType.NESTED_ROWS, "", new CellConfig.NestedRowsConfig(inner)))
                .config();

        var out = described.rows().get(0).path("cells");
        assertEquals("Die Halle", out.get(0).path("config").path("altText").asString());
        assertEquals(
                "Die Seite", out.get(1).path("config").path("resolvedTitle").asString());
    }

    @Test
    void aCellWithNothingNestedInItIsLeftAlone() {
        var empty = new CellConfig.NestedRowsConfig(CellConfig.MAPPER.createArrayNode());
        assertSame(
                empty,
                descriptions
                        .describe(STATION, cell(CellContentType.NESTED_ROWS, "", empty))
                        .config());
    }

    private static ContentCell cell(CellContentType type, String content, CellConfig config) {
        return new ContentCell(1, 1, 0, 100.0, type, content, config);
    }

    private static StationFile file(String alt, String description) {
        return new StationFile(
                1, 0, STATION, "known", "halle.png", "image/png", 64, Instant.now(), alt, description, null);
    }
}
