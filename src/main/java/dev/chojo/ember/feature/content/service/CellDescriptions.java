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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

/**
 * What a picture is called, where the cell showing it has not said.
 *
 * <p>A file is described once, where it lives: its alt text for whoever cannot see it, and the line
 * that goes under it. A cell that says nothing about either should show what the file says rather
 * than nothing at all, which is how a picture ended up on a public page with no alt text even though
 * somebody had written one.
 *
 * <p>A cell that does say something keeps saying it. The same picture means different things in
 * different places, and the nearer word wins. Blank counts as unsaid: a field somebody opened and
 * left empty means they had nothing to add, not that the picture should go unnamed.
 *
 * <p>Every reader of blocks goes through this, pages, wiki articles and news alike, and so does every
 * copy made of them: the stored text, the search index and the PDF. The editor alone reads the raw
 * cells, because saving what it shows would write the file's words into the cell for good.
 */
@Singleton
public class CellDescriptions {
    private final MediaLibraryService mediaLibrary;
    private final PageAddressing pageAddressing;

    @Inject
    public CellDescriptions(MediaLibraryService mediaLibrary, PageAddressing pageAddressing) {
        this.mediaLibrary = mediaLibrary;
        this.pageAddressing = pageAddressing;
    }

    /**
     * Where a page is reached, for a card that links to one.
     *
     * <p>An interface because pages are drawn by the feature that owns them, while a card linking to
     * a page also sits in a news entry and in a wiki article. Resolving in the page service would
     * leave those two pointing at an address that has since changed, which is the whole of what this
     * prevents. The arrow keeps pointing the way it already does: blocks know nothing of pages, and
     * the page feature answers the question.
     */
    public interface PageAddressing {

        /**
         * @param stationId the station the card sits in, or {@code null} for instance content
         * @param pageUid   the public uid of the page the card names
         * @return where that page is reached and what it is called, or empty where no page answers
         */
        Optional<PageAddress> addressOf(Integer stationId, String pageUid);
    }

    /**
     * A page's current name and address.
     *
     * @param title what the page is called now
     * @param href  where it is reached now, which is its slug path when it is public and its share
     *              link when it is only reachable by one
     */
    public record PageAddress(String title, String href) {}

    /**
     * Describes every cell of the given rows.
     *
     * @param stationId the station whose media library the pictures live in, or {@code null} for
     *                  the instance's, which is where the pictures of a system news entry live
     * @param rows      the rows as stored
     * @return the rows with blank picture texts filled from their files
     */
    public List<ContentRow> describe(Integer stationId, List<ContentRow> rows) {
        return rows.stream()
                .map(row -> row.withCells(row.cells().stream()
                        .map(cell -> describe(stationId, cell))
                        .toList()))
                .toList();
    }

    /**
     * Describes a single cell. Cells without a picture of the media library come back unchanged.
     *
     * @param stationId the station whose media library the pictures live in, or {@code null} for
     *                  the instance's
     * @param cell      the cell as stored
     * @return the cell with blank picture texts filled from their files
     */
    public ContentCell describe(Integer stationId, ContentCell cell) {
        return switch (cell.config()) {
            case CellConfig.ImageConfig image -> cell.withConfig(describedImage(stationId, cell.content(), image));
            case CellConfig.ImageGalleryConfig gallery -> cell.withConfig(describedGallery(stationId, gallery));
            case CellConfig.PageLinkConfig link -> cell.withConfig(addressedLink(stationId, link));
            case CellConfig.NestedRowsConfig nested -> cell.withConfig(describedNested(stationId, nested));
            case null, default -> cell;
        };
    }

    private CellConfig.PageLinkConfig addressedLink(Integer stationId, CellConfig.PageLinkConfig link) {
        if (link.pageUid() == null || link.pageUid().isBlank()) return link;
        return pageAddressing
                .addressOf(stationId, link.pageUid().trim())
                .map(address -> link.resolvedAs(address.title(), address.href()))
                .orElse(link);
    }

    /**
     * Rows inside a cell are kept as the JSON they arrived as, so they are walked as JSON rather
     * than read into rows: which record a cell's settings are depends on the content type standing
     * beside them, which no binder can work out on its own.
     *
     * <p>Without this a picture in a nested row goes unnamed and a card in one points nowhere, both
     * of which read as the cell being at fault rather than the row it happens to sit in.
     */
    private CellConfig.NestedRowsConfig describedNested(Integer stationId, CellConfig.NestedRowsConfig nested) {
        var rows = nested.rows();
        if (rows == null || !rows.isArray() || rows.isEmpty()) return nested;
        var describedRows = CellConfig.MAPPER.createArrayNode();
        for (var row : rows) {
            describedRows.add(describedNestedRow(stationId, row));
        }
        return new CellConfig.NestedRowsConfig(describedRows);
    }

    private JsonNode describedNestedRow(Integer stationId, JsonNode row) {
        var cells = row.path("cells");
        if (!cells.isArray() || cells.isEmpty()) return row;
        var describedCells = CellConfig.MAPPER.createArrayNode();
        for (var cell : cells) {
            describedCells.add(describedNestedCell(stationId, cell));
        }
        return ((ObjectNode) row.deepCopy()).set("cells", describedCells);
    }

    private JsonNode describedNestedCell(Integer stationId, JsonNode cell) {
        CellContentType type;
        try {
            type = CellContentType.valueOf(cell.path("contentType").asString());
        } catch (IllegalArgumentException e) {
            return cell;
        }
        var config = CellConfig.parse(type, cell.path("config"));
        var described = describe(
                stationId,
                new ContentCell(0, 0, 0, 100, type, cell.path("content").asString(""), config));
        if (described.config() == config) return cell;
        return ((ObjectNode) cell.deepCopy()).set("config", CellConfig.MAPPER.valueToTree(described.config()));
    }

    private CellConfig.ImageConfig describedImage(Integer stationId, String imageHash, CellConfig.ImageConfig image) {
        var file = fileFor(stationId, imageHash);
        if (file == null) return image;
        return new CellConfig.ImageConfig(
                image.imageFit(),
                spokenFor(image.altText(), file.defaultAltText()),
                image.maxHeight(),
                spokenFor(image.description(), file.defaultDescription()),
                image.cropTop(),
                image.cropRight(),
                image.cropBottom(),
                image.cropLeft(),
                image.borderRadiusPercent(),
                image.borderWidthPx(),
                image.borderColor());
    }

    private CellConfig.ImageGalleryConfig describedGallery(Integer stationId, CellConfig.ImageGalleryConfig gallery) {
        if (gallery.items() == null) return gallery;
        var described = gallery.items().stream()
                .map(item -> {
                    var file = fileFor(stationId, item.imageHash());
                    if (file == null) return item;
                    return new CellConfig.GalleryItem(
                            item.imageHash(),
                            spokenFor(item.altText(), file.defaultAltText()),
                            spokenFor(item.subtext(), file.defaultDescription()));
                })
                .toList();
        return new CellConfig.ImageGalleryConfig(
                described, gallery.columns(), gallery.aspectMode(), gallery.maxItemHeightPx());
    }

    private StationFile fileFor(Integer stationId, String imageHash) {
        if (imageHash == null || imageHash.isBlank()) return null;
        return mediaLibrary.findByHash(stationId, imageHash.trim()).orElse(null);
    }

    private static String spokenFor(String ownWord, String fileWord) {
        return ownWord != null && !ownWord.isBlank() ? ownWord : fileWord;
    }
}
