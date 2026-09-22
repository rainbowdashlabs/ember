/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.content.service;

import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.media.entity.StationFile;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

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

    @Inject
    public CellDescriptions(MediaLibraryService mediaLibrary) {
        this.mediaLibrary = mediaLibrary;
    }

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
            case null, default -> cell;
        };
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
