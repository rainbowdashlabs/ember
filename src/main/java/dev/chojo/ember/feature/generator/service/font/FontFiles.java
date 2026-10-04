/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.util.ByteSignature;
import org.apache.fontbox.ttf.NameRecord;
import org.apache.fontbox.ttf.OS2WindowsMetricsTable;
import org.apache.fontbox.ttf.OTFParser;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Comparator;

/**
 * Reads what an uploaded font file is, refusing one that is no font Ember can print with.
 *
 * <p>A file is taken as a TrueType font ({@code .ttf}, or an {@code .otf} with TrueType outlines) or an
 * OpenType font with PostScript outlines, told apart by the first four bytes, and parsed in full.
 * Collections, web fonts and anything else are refused, as is a font without the OS/2 table that says
 * what its licence allows.
 *
 * <p>The licence bits of that table ({@code fsType}) are read the way PDFBox reads them: a font marked
 * for restricted licence embedding, or for bitmap embedding only, may not be put into a document, and a
 * document is all Ember would put it into. Such a font is refused when it is uploaded rather than when
 * the first document fails.
 */
public final class FontFiles {
    /** The bits of {@code fsType} that say a font may be embedded only where its licence allows. */
    private static final int USAGE_PERMISSIONS = 0x000F;

    /** The name table id of the typographic family, which groups more than four styles. */
    private static final int TYPOGRAPHIC_FAMILY = 16;

    /** The name table id of the family. */
    private static final int FAMILY = 1;

    /** What a font with TrueType outlines starts with, besides the older {@code true}. */
    private static final byte[] TRUETYPE = {0, 1, 0, 0};

    private FontFiles() {}

    /** The bit of {@code fsSelection} that marks an italic style. */
    private static final int SELECTION_ITALIC = 0x0001;

    /** The bit of {@code fsSelection} that marks a bold style. */
    private static final int SELECTION_BOLD = 0x0020;

    /**
     * What a font file is.
     *
     * @param outline        how its glyphs are drawn
     * @param internalFamily the family name it carries itself
     * @param subsettable    whether its licence lets a document embed only the glyphs it uses
     * @param style          the style of its family it says it is
     */
    public record Inspection(FontOutline outline, String internalFamily, boolean subsettable, FontStyle style) {}

    /**
     * Reads a font file.
     *
     * @param data the file
     * @return what it is
     * @throws RefusalResponse {@code DOCUMENT_FONT_NOT_A_FONT} for a file that is no font Ember reads,
     *                         {@code DOCUMENT_FONT_EMBEDDING_FORBIDDEN} for one whose licence forbids embedding
     */
    public static Inspection inspect(byte[] data) {
        var outline = outlineOf(data);
        var read = read(data, outline);
        if (read == null) throw DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT.raise();
        if (!embeddable(read.fsType())) throw DocumentRefusal.DOCUMENT_FONT_EMBEDDING_FORBIDDEN.raise();
        boolean subsettable = (read.fsType() & OS2WindowsMetricsTable.FSTYPE_NO_SUBSETTING) == 0;
        return new Inspection(outline, read.family(), subsettable, styleOf(read.fsSelection()));
    }

    /**
     * What the parser found in a file.
     *
     * @param fsType      the licence bits of its OS/2 table
     * @param fsSelection the style bits of its OS/2 table
     * @param family      the family name it carries
     */
    private record Read(int fsType, int fsSelection, String family) {}

    /**
     * Parses a file in full. The bytes come from whoever uploaded them, so any failure of the parser,
     * checked or not, means the file is no font Ember can print with.
     *
     * @return what it holds, or null where it cannot be read or lacks what printing needs
     */
    private static @Nullable Read read(byte[] data, FontOutline outline) {
        try (var font = parse(data, outline)) {
            var os2 = font.getOS2Windows();
            String family = familyOf(font);
            if (os2 == null || family == null || font.getNumberOfGlyphs() == 0) return null;
            return new Read(os2.getFsType(), os2.getFsSelection(), family);
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }

    /**
     * Whether a font's licence lets a document embed only the glyphs it uses, for a file that was
     * inspected when it was uploaded.
     *
     * @param data the file
     * @return whether a subset may be embedded; false where the file cannot be read
     */
    public static boolean subsettable(byte[] data) {
        try {
            return inspect(data).subsettable();
        } catch (RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * Whether the licence bits of a font let a document embed it.
     *
     * @param fsType the {@code fsType} of its OS/2 table
     * @return whether it may be embedded
     */
    static boolean embeddable(int fsType) {
        if ((fsType & USAGE_PERMISSIONS) == OS2WindowsMetricsTable.FSTYPE_RESTRICTED) return false;
        return (fsType & OS2WindowsMetricsTable.FSTYPE_BITMAP_ONLY) == 0;
    }

    private static FontStyle styleOf(int fsSelection) {
        boolean bold = (fsSelection & SELECTION_BOLD) != 0;
        boolean italic = (fsSelection & SELECTION_ITALIC) != 0;
        if (bold) return italic ? FontStyle.BOLD_ITALIC : FontStyle.BOLD;
        return italic ? FontStyle.ITALIC : FontStyle.REGULAR;
    }

    private static FontOutline outlineOf(byte[] data) {
        if (ByteSignature.startsWith(data, TRUETYPE) || ByteSignature.startsWith(data, "true")) {
            return FontOutline.TRUETYPE;
        }
        if (ByteSignature.startsWith(data, "OTTO")) return FontOutline.CFF;
        throw DocumentRefusal.DOCUMENT_FONT_NOT_A_FONT.raise();
    }

    private static TrueTypeFont parse(byte[] data, FontOutline outline) throws IOException {
        var read = new RandomAccessReadBuffer(data);
        return outline == FontOutline.CFF ? new OTFParser().parse(read) : new TTFParser().parse(read);
    }

    private static @Nullable String familyOf(TrueTypeFont font) throws IOException {
        var naming = font.getNaming();
        if (naming == null) return null;
        String typographic = naming.getNameRecords().stream()
                .filter(record -> record.getNameId() == TYPOGRAPHIC_FAMILY)
                .sorted(Comparator.comparingInt(FontFiles::preference))
                .map(NameRecord::getString)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
        if (typographic != null) return typographic.strip();
        String family = naming.getFontFamily();
        if (family != null && !family.isBlank()) return family.strip();
        return naming.getNameRecords().stream()
                .filter(record -> record.getNameId() == FAMILY)
                .map(NameRecord::getString)
                .filter(name -> name != null && !name.isBlank())
                .map(String::strip)
                .findFirst()
                .orElse(null);
    }

    /** Windows names in English first, as the family name a renderer finds a font by. */
    private static int preference(NameRecord record) {
        if (record.getPlatformId() == NameRecord.PLATFORM_WINDOWS && record.getLanguageId() == 0x409) return 0;
        if (record.getPlatformId() == NameRecord.PLATFORM_WINDOWS) return 1;
        return 2;
    }
}
