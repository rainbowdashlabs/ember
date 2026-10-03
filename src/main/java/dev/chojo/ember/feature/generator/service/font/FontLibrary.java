/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.BuiltInFace;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.ReachableFonts;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.repository.DocumentFontRepository;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Which fonts an owner's documents can print with, and where their files are kept.
 *
 * <p>Reach adds up: a station reaches its own fonts, those of the association it belongs to and those
 * of the instance; an association reaches its own and the instance's; the instance only its own. Where
 * a family name is found at several of them, the nearest owner's family is taken whole (station over
 * association over instance). Every owner also reaches the {@link BuiltInFonts}, farthest of all, so an
 * uploaded family of the same name takes their place. This is decided here and only here, so a template
 * of an association is served by handing in that association as the owner.
 *
 * <p>An association keeps its fonts in its home station, the way it keeps its other files there; the
 * instance keeps them in its own storage. The web version of a style lies beside its file.
 *
 * <p>A text that names no family, or one no longer reached, prints in the {@link DefaultFont}, which
 * every owner reaches alike and none keeps.
 */
@Singleton
public class FontLibrary {
    private final DocumentFontRepository fonts;
    private final OwnerStores stores;
    private final StorageService storage;
    private final DefaultFont defaultFont;

    @Inject
    public FontLibrary(
            DocumentFontRepository fonts, OwnerStores stores, StorageService storage, DefaultFont defaultFont) {
        this.fonts = fonts;
        this.stores = stores;
        this.storage = storage;
        this.defaultFont = defaultFont;
    }

    /** @return the font a text naming no family prints in */
    public DefaultFont defaultFont() {
        return defaultFont;
    }

    /**
     * @param owner the owner a template belongs to
     * @return every family it can print with, the nearest owner's where a name is found at several
     */
    public ReachableFonts reachable(Owner owner) {
        return ReachableFonts.of(
                switch (owner) {
                    case Owner.Station station ->
                        fonts.findReachable(station.stationId(), associationOf(station.stationId()));
                    case Owner.Association association -> fonts.findReachable(null, association.clusterId());
                    case Owner.Instance ignored -> fonts.findReachable(null, null);
                },
                BuiltInFonts.families());
    }

    /**
     * Refuses a template that names a family its owner cannot reach, or, on an uploaded PDF, a family
     * whose outlines a field cannot embed.
     *
     * @param owner   the owner the template belongs to
     * @param content what the template is made of
     */
    public void requireReachable(Owner owner, TemplateContent content) {
        outOfReach(owner, content).stream().findFirst().ifPresent(family -> {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_FONT_UNKNOWN.raise(RefusalDetail.text(family));
        });
    }

    /**
     * The families a template names that its owner cannot print with: one it does not reach, or, on an
     * uploaded PDF, one whose outlines a field cannot embed.
     *
     * @param owner   the owner the template belongs to
     * @param content what the template is made of
     * @return the family names, each once, in the order the content names them
     */
    public List<String> outOfReach(Owner owner, TemplateContent content) {
        var families = content.fontFamilies().distinct().toList();
        if (families.isEmpty()) return List.of();
        var reachable = reachable(owner);
        return families.stream()
                .filter(family -> !reachable
                        .find(family)
                        .map(found -> !(content instanceof PdfContent) || found.printsOnPdf())
                        .orElse(false))
                .toList();
    }

    /**
     * Which family the templates of an owner print in for a family name, which says which owner's file
     * they would read.
     *
     * @param owner  the station or the association that keeps the templates
     * @param family the family name
     * @return the family the owner reaches under that name, or empty where it reaches none
     */
    public Optional<FontFamily> familyAt(Owner owner, String family) {
        return reachable(owner).find(family);
    }

    /**
     * Reads the file of a face.
     *
     * @param face an uploaded font or a built-in face
     * @return its bytes, or empty where the stored file is gone or the face is one Typst carries itself
     */
    public Optional<byte[]> read(FontFace face) {
        return switch (face) {
            case DocumentFont font ->
                storage.readAllBytes(scopeOf(font.owner()), categoryOf(font.owner()), key(font.id()));
            case BuiltInFace builtIn -> BuiltInFonts.data(builtIn);
        };
    }

    /**
     * Reads the web version of an uploaded font style.
     *
     * @param font an uploaded font
     * @return its web version, or empty where it has none or the stored file is gone
     */
    public Optional<byte[]> readWeb(DocumentFont font) {
        if (font.web() == null) return Optional.empty();
        return storage.readAllBytes(scopeOf(font.owner()), categoryOf(font.owner()), webKey(font.id()));
    }

    /**
     * @param owner who keeps fonts
     * @return where the owner's font files are kept
     */
    public StorageScope scopeOf(Owner owner) {
        return stores.scopeOf(owner, DocumentRefusal.DOCUMENT_FONT_NOT_HERE);
    }

    /**
     * @param owner who keeps fonts
     * @return what the owner's font files are kept as
     */
    public static StorageCategory categoryOf(Owner owner) {
        return switch (owner) {
            case Owner.Station ignored -> StorageCategory.FONTS;
            case Owner.Association ignored -> StorageCategory.ASSOCIATION_FONTS;
            case Owner.Instance ignored -> StorageCategory.INSTANCE_FONTS;
        };
    }

    /**
     * @param fontId a font
     * @return the storage key of its file
     */
    static String key(int fontId) {
        return String.valueOf(fontId);
    }

    /**
     * @param fontId a font
     * @return the storage key of its web version
     */
    static String webKey(int fontId) {
        return fontId + "-web";
    }

    private @Nullable Integer associationOf(int stationId) {
        return stores.associationOf(stationId).map(Owner.Association::clusterId).orElse(null);
    }
}
