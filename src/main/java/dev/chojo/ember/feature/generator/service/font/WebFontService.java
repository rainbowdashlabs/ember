/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.WebFont;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository.NewWebFont;
import dev.chojo.ember.feature.generator.service.font.DocumentFontService.DocumentFontsResponse;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The web versions of an owner's font styles: a file beside a style's own that the template editor
 * loads instead, such as the WOFF2 file a font's publisher offers for web pages. Documents keep printing
 * with the style's own file.
 *
 * <p>A web version is taken with the same word of the uploader as the font itself, as WOFF2, WOFF or a
 * TrueType or OpenType file ({@link WebFontFiles}), no larger than a font file may be. It is kept by the
 * owner of the style, in the same storage and against the same room, replaced by the next one uploaded,
 * and removed with the style.
 */
@Singleton
public class WebFontService {
    private static final Logger log = LoggerFactory.getLogger(WebFontService.class);

    private final DocumentFontRepository fonts;
    private final FontLibrary library;
    private final StorageService storage;
    private final StorageQuotaService quota;
    private final DocumentFontService listing;

    @Inject
    public WebFontService(
            DocumentFontRepository fonts,
            FontLibrary library,
            StorageService storage,
            StorageQuotaService quota,
            DocumentFontService listing) {
        this.fonts = fonts;
        this.library = library;
        this.storage = storage;
        this.quota = quota;
        this.listing = listing;
    }

    /**
     * Gives a font style of the owner its web version, in place of the one it had.
     *
     * @param owner     who keeps the font
     * @param fontId    the font style
     * @param file      the upload, or null where the request carried none
     * @param confirmed whether the uploader confirmed that the owner may use the font
     * @param accountId the account that uploads it
     * @return the owner's fonts as they now stand
     */
    public DocumentFontsResponse upload(
            Owner owner, int fontId, @Nullable UploadedFile file, boolean confirmed, int accountId) {
        if (!confirmed) throw DocumentRefusal.DOCUMENT_FONT_LICENCE_NOT_CONFIRMED.raise();
        var font = find(owner, fontId);
        byte[] data = DocumentFontService.readUpload(file);
        var format = WebFontFiles.inspect(data);
        var scope = library.scopeOf(owner);
        var category = FontLibrary.categoryOf(owner);
        var previous = font.web();
        var web = new NewWebFont(DocumentFontService.fileNameOf(file), format, data.length, Sha256.hex(data));
        try {
            Transactions.run(() -> {
                if (previous != null) quota.onFileDeleted(scope, category, previous.sizeBytes());
                quota.onFileUploaded(scope, category, data.length);
                fonts.setWeb(font.id(), web);
                storage.store(scope, category, FontLibrary.webKey(font.id()), data, format.mediaType());
            });
        } catch (StorageQuotaService.StorageQuotaExceededException e) {
            throw DocumentRefusal.DOCUMENT_FONT_NO_ROOM.raise();
        }
        log.info("Web version of font {} ({}) uploaded for {} by account {}", font.id(), format, owner, accountId);
        return listing.list(owner);
    }

    /**
     * Takes the web version of a font style of the owner away, so the editor loads the style's own file.
     *
     * @param owner     who keeps the font
     * @param fontId    the font style
     * @param accountId the account that removes it
     * @return the owner's fonts as they now stand
     */
    public DocumentFontsResponse remove(Owner owner, int fontId, int accountId) {
        var font = find(owner, fontId);
        WebFont web = font.web();
        if (web == null) return listing.list(owner);
        var scope = library.scopeOf(owner);
        var category = FontLibrary.categoryOf(owner);
        Transactions.run(() -> {
            fonts.clearWeb(font.id());
            quota.onFileDeleted(scope, category, web.sizeBytes());
        });
        storage.delete(scope, category, FontLibrary.webKey(font.id()));
        log.info("Web version of font {} of {} removed by account {}", font.id(), owner, accountId);
        return listing.list(owner);
    }

    private DocumentFont find(Owner owner, int fontId) {
        return fonts.find(owner, fontId).orElseThrow(DocumentRefusal.DOCUMENT_FONT_NOT_HERE::raise);
    }
}
