/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.cluster.service.ClusterService;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.FontUse;
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

import java.io.IOException;
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
 * may use it, since the licence is the owner's to know. The file has to be a TrueType or OpenType font
 * whose licence bits allow embedding it in a document, no larger than {@link #MAX_BYTES}, and the owner
 * must have room for it: a station in its own room, an association in its home station's, the instance
 * without limit. A family has one file per style at one owner.
 *
 * <p>The files are never sent to a browser. The screens list the families by name and preview text in a
 * font the browser already has; only the server draws with the real file.
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

    private final DocumentFontRepository fonts;
    private final FontLibrary library;
    private final ClusterService clusters;
    private final StorageService storage;
    private final StorageQuotaService quota;

    @Inject
    public DocumentFontService(
            DocumentFontRepository fonts,
            FontLibrary library,
            ClusterService clusters,
            StorageService storage,
            StorageQuotaService quota) {
        this.fonts = fonts;
        this.library = library;
        this.clusters = clusters;
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
     */
    public record DocumentFontView(
            int id,
            String family,
            FontStyle style,
            String fileName,
            FontOutline outline,
            long sizeBytes,
            Instant uploadedAt) {}

    /**
     * A family a template of the owner can print in, as the pickers offer it.
     *
     * @param family      the family name
     * @param origin      who uploaded it
     * @param styles      the styles it has
     * @param printsOnPdf whether fields on an uploaded PDF can print in it
     */
    public record FontFamilyOption(String family, FontOrigin origin, List<FontStyle> styles, boolean printsOnPdf) {}

    /**
     * The fonts of an owner and those its templates can print in.
     *
     * @param own       the owner's own fonts, by family and style
     * @param reachable every family its templates can print in, its own and those it reaches
     */
    public record DocumentFontsResponse(List<DocumentFontView> own, List<FontFamilyOption> reachable) {}

    /**
     * @param owner the owner
     * @return its fonts and the families its templates can print in
     */
    public DocumentFontsResponse list(Owner owner) {
        var own = fonts.findOwned(owner).stream().map(DocumentFontService::view).toList();
        var reachable = library.reachable(owner).families().stream()
                .map(DocumentFontService::option)
                .toList();
        return new DocumentFontsResponse(own, reachable);
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
        byte[] data = read(file);
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
                storage.store(scope, category, FontLibrary.key(row.id()), data, mimeOf(inspection.outline()));
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
            quota.onFileDeleted(scope, category, font.sizeBytes());
        });
        storage.delete(scope, category, FontLibrary.key(font.id()));
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
        var candidates = fonts.templatesNaming(font.family(), stationsReaching(font.owner()));
        Map<Integer, Optional<FontFamily>> resolved = new HashMap<>();
        return candidates.stream()
                .filter(use -> resolved.computeIfAbsent(
                                use.stationId(), stationId -> library.familyAt(stationId, font.family()))
                        .map(family -> family.file(font.style()).owner().equals(font.owner()))
                        .orElse(false))
                .toList();
    }

    /** The stations whose templates can reach an owner's fonts, or null for every station. */
    private @Nullable List<Integer> stationsReaching(Owner owner) {
        return switch (owner) {
            case Owner.Station station -> List.of(station.stationId());
            case Owner.Association association -> clusters.findStationIds(association.clusterId());
            case Owner.Instance ignored -> null;
        };
    }

    private static String requireFamily(@Nullable String family) {
        String name = family == null ? "" : family.strip();
        if (name.isEmpty() || name.length() > MAX_FAMILY) throw DocumentRefusal.DOCUMENT_FONT_FAMILY_INVALID.raise();
        return name;
    }

    private static byte[] read(@Nullable UploadedFile file) {
        if (file == null) throw DocumentRefusal.DOCUMENT_FONT_MISSING_FILE.raise();
        if (file.size() > MAX_BYTES) throw DocumentRefusal.DOCUMENT_FONT_TOO_LARGE.raise();
        try (var in = file.content()) {
            byte[] data = in.readAllBytes();
            if (data.length > MAX_BYTES) throw DocumentRefusal.DOCUMENT_FONT_TOO_LARGE.raise();
            return data;
        } catch (IOException e) {
            throw DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT.raise();
        }
    }

    private static String fileNameOf(@Nullable UploadedFile file) {
        String name = file == null ? null : file.filename();
        return name == null || name.isBlank() ? "font" : name.strip();
    }

    private static String mimeOf(FontOutline outline) {
        return outline == FontOutline.CFF ? "font/otf" : "font/ttf";
    }

    private static DocumentFontView view(DocumentFont font) {
        return new DocumentFontView(
                font.id(),
                font.family(),
                font.style(),
                font.fileName(),
                font.outline(),
                font.sizeBytes(),
                font.uploadedAt());
    }

    private static FontFamilyOption option(FontFamily family) {
        return new FontFamilyOption(family.name(), family.origin(), family.styles(), family.printsOnPdf());
    }
}
