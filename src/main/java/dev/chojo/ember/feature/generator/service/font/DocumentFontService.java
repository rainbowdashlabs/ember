/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FontUse;
import dev.chojo.ember.feature.generator.entity.WebFontFormat;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
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

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The fonts a station, an association or the instance uploads for its documents.
 *
 * <p>A font is one file per style of a family. It is taken only with the uploader's word that the owner
 * may use it for documents and show it in the template editor to those who edit templates, since the
 * licence is the owner's to know. The file has to be a TrueType or OpenType font
 * whose licence bits allow embedding it in a document, no larger than {@link #MAX_BYTES}, and the owner
 * must have room for it: a station in its own room, an association in its home station's, the instance
 * without limit. A family has one file per style at one owner.
 *
 * <p>The screens list the families by name with a picture of sample text the server draws
 * ({@link FontSampleService}). The files reach a browser only in the template editor
 * ({@link EditorFontService}), which shows the words of a template in the family they print in. A style
 * may have a web version beside its file ({@link WebFontService}), which the editor loads instead.
 *
 * <p>The families every installation has built in ({@link BuiltInFonts}) are listed among those the
 * templates reach, but they are nobody's own: they cannot be deleted and take no room.
 *
 * <p>A font cannot be deleted while a template in use prints with it, that is, while a template that
 * reaches it names its family and finds this owner's family under that name. An archived template does
 * not hold a font: it generates nothing, and should it be restored, a family that is gone prints in the
 * default font.
 */
@Singleton
public class DocumentFontService {
    private static final Logger log = LoggerFactory.getLogger(DocumentFontService.class);

    /** The largest font file taken, generous for a font with every script of a language family. */
    static final long MAX_BYTES = 10L * 1024 * 1024;

    /** The longest family name. */
    static final int MAX_FAMILY = 60;

    private static final DocumentIntake.Refusals UPLOAD_REFUSALS = new DocumentIntake.Refusals(
            DocumentRefusal.DOCUMENT_FONT_MISSING_FILE,
            DocumentRefusal.DOCUMENT_FONT_TOO_LARGE,
            DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT,
            DocumentRefusal.DOCUMENT_FONT_NO_ROOM,
            DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT);

    private final DocumentFontRepository fonts;
    private final FontLibrary library;
    private final StorageService storage;
    private final StorageQuotaService quota;

    @Inject
    public DocumentFontService(
            DocumentFontRepository fonts, FontLibrary library, StorageService storage, StorageQuotaService quota) {
        this.fonts = fonts;
        this.library = library;
        this.storage = storage;
        this.quota = quota;
    }

    /**
     * A font as the owner's screen lists it.
     *
     * @param id         the font
     * @param family     the family name
     * @param style      the style of the family it is
     * @param fileName   the name it was uploaded under
     * @param outline    how its glyphs are drawn, which decides whether fields on a PDF can print in it
     * @param sizeBytes  the size of the file
     * @param uploadedAt when it was uploaded
     * @param web        the web version the template editor shows the style in, or null where it has none
     */
    public record DocumentFontView(
            int id,
            String family,
            FontStyle style,
            String fileName,
            FontOutline outline,
            long sizeBytes,
            Instant uploadedAt,
            @Nullable WebFontView web) {}

    /**
     * The web version of a font style as the owner's screen lists it.
     *
     * @param fileName   the name it was uploaded under
     * @param format     what it is
     * @param sizeBytes  its size
     * @param uploadedAt when it was uploaded
     */
    public record WebFontView(String fileName, WebFontFormat format, long sizeBytes, Instant uploadedAt) {}

    /**
     * A family a template of the owner can print in, as the pickers offer it.
     *
     * @param family        the family name
     * @param origin        who uploaded it, or that it is built in
     * @param styles        the styles it has
     * @param printsOnPdf   whether fields on an uploaded PDF can print in it
     * @param sample        the version of its sample picture, which changes whenever its files do
     * @param editorVersion the version of the files the template editor loads, which changes whenever one
     *                      of them does, or null where it cannot load the family, as for a family Typst
     *                      carries inside itself
     */
    public record FontFamilyOption(
            String family,
            FontOrigin origin,
            List<FontStyle> styles,
            boolean printsOnPdf,
            String sample,
            @Nullable String editorVersion) {}

    /**
     * The fonts of an owner and those its templates can print in.
     *
     * @param own           the owner's own fonts, by family and style
     * @param reachable     every family its templates can print in, its own and those it reaches
     * @param defaultFamily the family a text that names none prints in
     * @param defaultStyles the styles the template editor can load the default font in, empty where it
     *                      only names it
     */
    public record DocumentFontsResponse(
            List<DocumentFontView> own,
            List<FontFamilyOption> reachable,
            String defaultFamily,
            List<FontStyle> defaultStyles) {}

    /**
     * @param owner the owner
     * @return its fonts, the families its templates can print in and the default font
     */
    public DocumentFontsResponse list(Owner owner) {
        var own = fonts.findOwned(owner).stream().map(DocumentFontService::view).toList();
        var reachable = library.reachable(owner).families().stream()
                .map(DocumentFontService::option)
                .toList();
        var defaultFont = library.defaultFont();
        var defaultStyles = defaultFont.present()
                ? defaultFont.webStyles()
                : BuiltInFonts.fallback().styles();
        return new DocumentFontsResponse(own, reachable, defaultFont.printedFamily(), defaultStyles);
    }

    /**
     * Takes a font in.
     *
     * @param owner     who keeps it
     * @param file      the upload, or null where the request carried none
     * @param family    the family name it is picked by
     * @param style     which style of the family it is
     * @param confirmed whether the uploader confirmed that the owner may use the font
     * @param accountId the account that uploads it
     * @return the owner's fonts as they now stand
     */
    public DocumentFontsResponse upload(
            Owner owner,
            @Nullable UploadedFile file,
            @Nullable String family,
            FontStyle style,
            boolean confirmed,
            int accountId) {
        if (!confirmed) throw DocumentRefusal.DOCUMENT_FONT_LICENCE_NOT_CONFIRMED.raise();
        String name = requireFamily(family);
        byte[] data = readUpload(file);
        var inspection = FontFiles.inspect(data);
        if (fonts.exists(owner, name, style)) {
            throw DocumentRefusal.DOCUMENT_FONT_TAKEN.raise(RefusalDetail.text(name));
        }
        var scope = library.scopeOf(owner);
        var category = FontLibrary.categoryOf(owner);
        var font = new DocumentFontRepository.NewFont(
                name,
                style,
                fileNameOf(file),
                inspection.outline(),
                inspection.internalFamily(),
                data.length,
                Sha256.hex(data));
        try {
            var written = Transactions.call(() -> {
                var row = fonts.insert(owner, font, accountId);
                quota.onFileUploaded(scope, category, data.length);
                storage.store(
                        scope,
                        category,
                        FontLibrary.key(row.id()),
                        data,
                        inspection.outline().mediaType());
                return row;
            });
            log.info("Font {} ({} {}) uploaded for {}", written.id(), name, style, owner);
        } catch (StorageQuotaService.StorageQuotaExceededException e) {
            throw DocumentRefusal.DOCUMENT_FONT_NO_ROOM.raise();
        }
        return list(owner);
    }

    /**
     * Deletes a font, refusing one a template in use prints with.
     *
     * @param owner     who keeps it
     * @param fontId    the font
     * @param accountId the account that deletes it
     * @return the owner's fonts as they now stand
     */
    public DocumentFontsResponse delete(Owner owner, int fontId, int accountId) {
        var font = fonts.find(owner, fontId).orElseThrow(DocumentRefusal.DOCUMENT_FONT_NOT_HERE::raise);
        var users = usersOf(font);
        if (!users.isEmpty()) {
            String names = users.stream().map(FontUse::name).distinct().collect(Collectors.joining(", "));
            throw DocumentRefusal.DOCUMENT_FONT_IN_USE.raise(RefusalDetail.text(names));
        }
        var scope = library.scopeOf(owner);
        var category = FontLibrary.categoryOf(owner);
        Transactions.run(() -> {
            fonts.delete(font.id());
            quota.onFileDeleted(scope, category, font.storedBytes());
        });
        storage.delete(scope, category, FontLibrary.key(font.id()));
        if (font.web() != null) storage.delete(scope, category, FontLibrary.webKey(font.id()));
        log.info(
                "Font {} ({} {}) of {} deleted by account {}",
                font.id(),
                font.family(),
                font.style(),
                owner,
                accountId);
        return list(owner);
    }

    /**
     * The templates in use that print with a font: those that name its family and, from where they are
     * kept, find this owner's family under that name rather than a nearer one.
     */
    List<FontUse> usersOf(DocumentFont font) {
        var candidates = fonts.templatesNaming(font.family(), font.owner());
        Map<Owner, Optional<FontFamily>> resolved = new HashMap<>();
        return candidates.stream()
                .filter(use -> resolved.computeIfAbsent(use.owner(), owner -> library.familyAt(owner, font.family()))
                        .map(family -> family.file(font.style()) instanceof DocumentFont found
                                && found.owner().equals(font.owner()))
                        .orElse(false))
                .toList();
    }

    private static String requireFamily(@Nullable String family) {
        String name = family == null ? "" : family.strip();
        if (name.isEmpty() || name.length() > MAX_FAMILY) throw DocumentRefusal.DOCUMENT_FONT_FAMILY_INVALID.raise();
        return name;
    }

    /**
     * Reads an uploaded font file, refusing a missing one and one larger than a font may be.
     *
     * @param file the upload, or null where the request carried none
     * @return its bytes
     */
    static byte[] readUpload(@Nullable UploadedFile file) {
        return DocumentIntake.readAtMost(MAX_BYTES, file, UPLOAD_REFUSALS).data();
    }

    /**
     * @param file an upload, or null where the request carried none
     * @return the name it is kept under
     */
    static String fileNameOf(@Nullable UploadedFile file) {
        String name = file == null ? null : file.filename();
        return name == null || name.isBlank() ? "font" : name.strip();
    }

    private static DocumentFontView view(DocumentFont font) {
        var web = font.web();
        return new DocumentFontView(
                font.id(),
                font.family(),
                font.style(),
                font.fileName(),
                font.outline(),
                font.sizeBytes(),
                font.uploadedAt(),
                web == null ? null : new WebFontView(web.fileName(), web.format(), web.sizeBytes(), web.uploadedAt()));
    }

    private static FontFamilyOption option(FontFamily family) {
        return new FontFamilyOption(
                family.name(),
                family.origin(),
                family.styles(),
                family.printsOnPdf(),
                FontSampleService.versionOf(family),
                EditorFontService.versionOf(family));
    }
}
