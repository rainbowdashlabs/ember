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
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Draws the pictures of every stored PDF again when pages are drawn at another resolution than the
 * one they were drawn at: member documents, the media libraries (event attachments included) and wiki
 * files. A picture is drawn once, when its file is stored, so without this every page stored before
 * would keep looking as coarse as it was drawn then.
 *
 * <p>The resolution the pictures were last drawn at is kept, and a start that finds another one draws
 * them again. An instance that has never recorded one draws them once, since it cannot tell at which
 * resolution its pictures were drawn. The resolution is recorded only once everything went through,
 * so a pass that fails halfway is tried again on the next start. Template pictures need none of this:
 * their key names the resolution, so a coarser one is replaced the next time it is asked for.
 */
@Singleton
public class PagePictureRedrawService {
    private static final Logger log = LoggerFactory.getLogger(PagePictureRedrawService.class);

    /** The setting holding the resolution the pictures of PDF pages were last drawn at. */
    static final String DRAWN_AT_KEY = "page_pictures_dpi";

    private final ApplicationSettingRepository settings;
    private final DocumentService documents;
    private final MediaLibraryService media;
    private final KbFilePictureService wikiFiles;

    @Inject
    public PagePictureRedrawService(
            ApplicationSettingRepository settings,
            DocumentService documents,
            MediaLibraryService media,
            KbFilePictureService wikiFiles) {
        this.settings = settings;
        this.documents = documents;
        this.media = media;
        this.wikiFiles = wikiFiles;
    }

    /**
     * Draws the pictures again if they were drawn at another resolution, or at none recorded.
     *
     * @return whether a pass ran and finished
     */
    public boolean redrawIfNeeded() {
        String current = String.valueOf(FilePicture.PAGE_DPI);
        Optional<String> drawnAt = settings.get(DRAWN_AT_KEY);
        if (drawnAt.filter(current::equals).isPresent()) return false;

        log.info(
                "Pictures of PDF pages were drawn at {}, pages are now drawn at {} dpi; drawing them again",
                drawnAt.map(dpi -> dpi + " dpi").orElse("an unknown resolution"),
                current);
        int memberDocuments = documents.redrawPages();
        int mediaFiles = media.redrawPages();
        int wikiPages = wikiFiles.redrawPages();
        settings.set(DRAWN_AT_KEY, current);
        log.info(
                "Pictures of PDF pages drawn again: {} document(s), {} media file(s), {} wiki file(s)",
                memberDocuments,
                mediaFiles,
                wikiPages);
        return true;
    }

    /**
     * The same, for a background thread: a failure is written down rather than thrown, and the next
     * start tries again because nothing was recorded.
     */
    public void redrawInBackground() {
        try {
            redrawIfNeeded();
        } catch (Exception e) {
            log.warn("The pictures of PDF pages could not be drawn again; the next start tries again", e);
        }
    }
}
