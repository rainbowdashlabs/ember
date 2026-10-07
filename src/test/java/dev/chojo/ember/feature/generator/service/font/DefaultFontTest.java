/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.FontStyle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which default font a directory holds: a family with a regular style, or none at all. */
class DefaultFontTest {

    private static void assertAbsent(DefaultFont font) {
        assertFalse(font.present());
        assertEquals(Optional.empty(), font.family());
        assertEquals(Optional.empty(), font.directory());
        assertEquals(BundledFont.FAMILY, font.printedFamily());
        assertTrue(font.pdfFile(FontStyle.REGULAR).isEmpty());
    }

    /** The family is the one the regular file carries, and its file is what a PDF draws in. */
    @Test
    void aRegularFileMakesTheDefaultFont(@TempDir Path directory) {
        var font = TestFonts.defaultFontIn(directory);

        assertTrue(font.present());
        assertEquals(Optional.of(TestFonts.LISU_FAMILY), font.family());
        assertEquals(TestFonts.LISU_FAMILY, font.printedFamily());
        assertEquals(Optional.of(directory.toAbsolutePath()), font.directory());
        assertEquals(List.of(FontStyle.REGULAR), font.styles());
        assertArrayEquals(TestFonts.lisu(), font.pdfFile(FontStyle.REGULAR).orElseThrow());
    }

    /** A style the font lacks is not stood in for by another of its styles. */
    @Test
    void aMissingStyleHasNoFile(@TempDir Path directory) {
        var font = TestFonts.defaultFontIn(directory);

        assertTrue(font.pdfFile(FontStyle.ITALIC).isEmpty());
        assertTrue(font.pdfFile(FontStyle.BOLD).isEmpty());
    }

    /** Every file names its own style, and a file of another family is no style of this one. */
    @Test
    void theStylesAreReadFromTheFiles(@TempDir Path directory) throws IOException {
        Files.write(directory.resolve("a-bold.ttf"), TestFonts.withFsSelection(TestFonts.lisu(), 0x20));
        Files.write(directory.resolve("b-regular.ttf"), TestFonts.lisu());
        Files.write(directory.resolve("c-italic.otf"), TestFonts.withFsSelection(TestFonts.lycian(), 0x01));

        var font = DefaultFont.readFrom(directory);

        assertEquals(Optional.of(TestFonts.LISU_FAMILY), font.family());
        assertEquals(List.of(FontStyle.REGULAR, FontStyle.BOLD), font.styles());
        assertTrue(font.pdfFile(FontStyle.BOLD).isPresent());
    }

    @Test
    void aMissingDirectoryHoldsNoDefaultFont(@TempDir Path directory) {
        assertAbsent(DefaultFont.readFrom(directory.resolve("missing")));
    }

    @Test
    void anEmptyDirectoryHoldsNoDefaultFont(@TempDir Path directory) {
        assertAbsent(DefaultFont.readFrom(directory));
    }

    @Test
    void noneAtAll() {
        assertAbsent(DefaultFont.absent());
    }

    /** A broken file is left out; with nothing else there, there is no default font. */
    @Test
    void aBrokenFileIsLeftOut(@TempDir Path directory) throws IOException {
        Files.writeString(directory.resolve("broken.ttf"), "no font at all");

        assertAbsent(DefaultFont.readFrom(directory));

        Files.write(directory.resolve("good.ttf"), TestFonts.lisu());
        assertEquals(
                Optional.of(TestFonts.LISU_FAMILY),
                DefaultFont.readFrom(directory).family());
    }

    /** A font whose licence forbids embedding it is no default font either. */
    @Test
    void aFontThatMayNotBeEmbeddedIsLeftOut(@TempDir Path directory) throws IOException {
        Files.write(directory.resolve("restricted.ttf"), TestFonts.withFsType(TestFonts.lisu(), 0x0002));

        assertAbsent(DefaultFont.readFrom(directory));
    }

    /** Only font files count; the checksums the container keeps beside them are no candidates. */
    @Test
    void otherFilesAreIgnored(@TempDir Path directory) throws IOException {
        Files.write(directory.resolve(".hidden.ttf"), TestFonts.lisu());
        Files.write(directory.resolve("font.bin"), TestFonts.lisu());

        assertAbsent(DefaultFont.readFrom(directory));
    }

    /** A font with PostScript outlines prints in letters, but a field on an uploaded PDF cannot embed it. */
    @Test
    void aPostScriptFontHasNoFileForAPdf(@TempDir Path directory) throws IOException {
        Files.write(directory.resolve("lycian.otf"), TestFonts.lycian());

        var font = DefaultFont.readFrom(directory);

        assertEquals(Optional.of("Noto Sans Lycian"), font.family());
        assertTrue(font.pdfFile(FontStyle.REGULAR).isEmpty());
    }
}
