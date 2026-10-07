/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.WebFont;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.stream.Collectors;

/**
 * The font files the template editor loads, so the words of a template show in the family they print
 * in while it is written.
 *
 * <p>An owner gets the files only of the families its templates reach, looked up the way a template
 * looks them up, and of the default font. A family is served as a document prints it: the style asked
 * for where the family has it, else its regular one. An uploaded style is served as its web version
 * where the owner gave it one, else as the file documents print with. The families Typst carries inside
 * itself have no file here and are refused, as is a file that is gone.
 *
 * <p>The default font is served from its web files only, never from the files documents embed; where
 * those are missing the editor names the font instead. Where there is no default font, Liberation Sans
 * stands in, as it does in print.
 */
@Singleton
public class EditorFontService {
    /** The media type of the web files of the default font. */
    static final String WOFF2 = "font/woff2";

    private final FontLibrary library;

    /**
     * One font file as the editor gets it.
     *
     * @param data      the file
     * @param mediaType its media type
     */
    public record EditorFontFile(byte[] data, String mediaType) {}

    @Inject
    public EditorFontService(FontLibrary library) {
        this.library = library;
    }

    /**
     * The file of a style of a family an owner reaches, or of the default font.
     *
     * @param owner  the owner whose templates the family is looked up for
     * @param family the family name, or null or blank for the default font
     * @param style  the style
     * @return the file
     */
    public EditorFontFile file(Owner owner, @Nullable String family, FontStyle style) {
        if (family == null || family.isBlank()) return standard(style);
        return fileOf(
                library.familyAt(owner, family).orElseThrow(DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN::raise), style);
    }

    private EditorFontFile standard(FontStyle style) {
        var defaultFont = library.defaultFont();
        if (!defaultFont.present()) return fileOf(BuiltInFonts.fallback(), style);
        return defaultFont
                .webFile(style)
                .map(data -> new EditorFontFile(data, WOFF2))
                .orElseThrow(DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN::raise);
    }

    /**
     * The version of the files the editor loads for a family, which changes whenever one of them does.
     *
     * @param family the family
     * @return a short hexadecimal text, or null where the editor cannot load the family
     */
    public static @Nullable String versionOf(FontFamily family) {
        if (!family.hasFiles()) return null;
        String files = family.files().values().stream()
                .map(EditorFontService::identityOf)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("\n"));
        return Sha256.hexPrefix(files, 16);
    }

    private static String identityOf(FontFace face) {
        if (face instanceof DocumentFont font && font.web() instanceof WebFont web) {
            return face.identity() + ":web:" + web.sha256();
        }
        return face.identity();
    }

    private EditorFontFile fileOf(FontFamily family, FontStyle style) {
        var face = family.file(style);
        if (face instanceof DocumentFont font && font.web() instanceof WebFont web) {
            return library.readWeb(font)
                    .map(data -> new EditorFontFile(data, web.format().mediaType()))
                    .orElseThrow(DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN::raise);
        }
        return library.read(face)
                .map(data -> new EditorFontFile(data, face.outline().mediaType()))
                .orElseThrow(DocumentRefusal.DOCUMENT_FONT_FILE_UNKNOWN::raise);
    }
}
