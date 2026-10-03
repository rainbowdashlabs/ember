/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.ReachableFonts;
import dev.chojo.ember.feature.generator.service.font.BundledFont;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The fonts a letter is set in, as Typst is handed them: the files next to the document, the directories
 * it searches besides, and for the body, the header and the footer the family names to ask for, in order.
 *
 * <p>Every list ends in Liberation Sans, whose file always goes along, so a letter looks the same on every
 * installation and a character a font lacks is still printed. A part that names no family, or one the
 * station no longer reaches or whose file is gone, prints in the {@link DefaultFont} before that, where
 * the instance has one; its directory is searched for it. Typst finds the uploaded files by the family
 * names they carry themselves, which is why those, and not the names the uploader gave, are what the
 * letter asks for.
 *
 * <p>Typst prints a style a family lacks in another style of that family rather than moving on to the
 * next one. A default font without an italic is therefore named in {@code uprightFamilies}, and the
 * letter leaves it out of the list for italic text, which then prints in Liberation Sans italic.
 *
 * @param families        the family names for {@code body}, {@code header} and {@code footer}
 * @param files           the font files by the name they are written under
 * @param directories     the directories of further font files
 * @param uprightFamilies the families whose italic text prints in the next family of the list instead
 */
public record LetterFonts(
        Map<String, List<String>> families,
        Map<String, byte[]> files,
        List<Path> directories,
        List<String> uprightFamilies) {

    /**
     * Picks the fonts of a letter.
     *
     * @param reachable   the families the station reaches
     * @param page        the page, which names a family for the body, the header and the footer
     * @param read        reads a font file, empty where it is gone
     * @param defaultFont the font a part naming none prints in
     * @return the fonts
     */
    public static LetterFonts of(
            ReachableFonts reachable,
            LetterPage page,
            Function<DocumentFont, Optional<byte[]>> read,
            DefaultFont defaultFont) {
        var files = new LinkedHashMap<String, byte[]>();
        files.put(BundledFont.FILE_NAME, BundledFont.data());
        var families = new LinkedHashMap<String, List<String>>();
        families.put("body", namesOf(reachable, page.bodyFont(), read, files, defaultFont));
        families.put("header", namesOf(reachable, page.headerFont(), read, files, defaultFont));
        families.put("footer", namesOf(reachable, page.footerFont(), read, files, defaultFont));
        var upright = defaultFont
                .family()
                .filter(family -> !defaultFont.styles().contains(FontStyle.ITALIC))
                .map(List::of)
                .orElse(List.of());
        return new LetterFonts(families, files, defaultFont.directory().stream().toList(), upright);
    }

    private static List<String> namesOf(
            ReachableFonts reachable,
            @Nullable String family,
            Function<DocumentFont, Optional<byte[]>> read,
            Map<String, byte[]> files,
            DefaultFont defaultFont) {
        var names = new ArrayList<String>();
        reachable.find(family).ifPresent(found -> {
            for (var font : found.files().values()) {
                String name = fileName(font);
                if (!files.containsKey(name)) {
                    var data = read.apply(font);
                    if (data.isEmpty()) continue;
                    files.put(name, data.get());
                }
                if (!names.contains(font.internalFamily())) names.add(font.internalFamily());
            }
        });
        if (names.isEmpty()) defaultFont.family().ifPresent(names::add);
        names.add(BundledFont.FAMILY);
        return List.copyOf(names);
    }

    private static String fileName(DocumentFont font) {
        return "font-" + font.id() + (font.outline() == FontOutline.CFF ? ".otf" : ".ttf");
    }
}
