/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.first;

/**
 * Draws the text of some families of a Word form larger or smaller, so a replacement that sets letters
 * wider than the original can keep the lines of the form.
 *
 * <p>Every run and paragraph mark of the body, the headers and the footers whose family ({@link RunFonts})
 * is scaled gets its size written onto itself. Word keeps sizes in half points, so a size is rounded to
 * the nearest half point.
 */
final class FormFontSizes {
    /** The parts that hold text. */
    private static final Pattern TEXT = Pattern.compile("word/(document|header\\d*|footer\\d*)\\.xml");

    private FormFontSizes() {}

    /**
     * @param docx    a Word form, its families not yet replaced
     * @param fonts   the fonts of its styles, read from the same form
     * @param choices how each replaced family is drawn, by the family the form names
     * @throws IOException where a part cannot be read or written
     */
    static void scale(DocxPackage docx, RunFonts fonts, Map<String, FontChoice> choices) throws IOException {
        if (choices.values().stream().allMatch(choice -> choice.size() == FontChoice.SAME_SIZE)) return;
        for (String name : docx.names()) {
            if (!TEXT.matcher(name).matches()) continue;
            var part = docx.xml(name);
            if (part.isEmpty()) continue;
            if (scale(part.get(), fonts, choices)) docx.replace(name, part.get());
        }
    }

    private static boolean scale(Document part, RunFonts fonts, Map<String, FontChoice> choices) {
        var resized = new ArrayList<Resize>();
        for (var holder : holders(part)) {
            var font = fonts.of(holder);
            var choice = font.family() == null ? null : choices.get(font.family());
            if (choice == null || choice.size() == FontChoice.SAME_SIZE) continue;
            long halfPoints = Math.max(1, Math.round(font.size() * 2 * choice.size() / 100.0));
            resized.add(new Resize(holder, String.valueOf(halfPoints)));
        }
        resized.forEach(resize -> resize.apply(part));
        return !resized.isEmpty();
    }

    /** Every run, and every paragraph standing for its mark. */
    private static List<Element> holders(Document part) {
        var out = new ArrayList<Element>();
        for (String name : List.of("r", "p")) {
            var all = part.getElementsByTagNameNS(W, name);
            for (int index = 0; index < all.getLength(); index++) out.add((Element) all.item(index));
        }
        return out;
    }

    /**
     * A size to write, worked out before any is written so that no size read depends on one already written.
     *
     * @param holder     a run, or a paragraph for its mark
     * @param halfPoints the size in half points
     */
    private record Resize(Element holder, String halfPoints) {
        void apply(Document part) {
            var properties = properties(part);
            for (String name : List.of("sz", "szCs")) {
                var size = first(properties, W, name);
                if (size == null) {
                    size = part.createElementNS(W, "w:" + name);
                    properties.appendChild(size);
                }
                size.setAttributeNS(W, "w:val", halfPoints);
            }
        }

        private Element properties(Document part) {
            var parent = "p".equals(holder.getLocalName()) ? paragraphProperties(part) : holder;
            var properties = first(parent, W, "rPr");
            if (properties != null) return properties;
            properties = part.createElementNS(W, "w:rPr");
            if (parent == holder) parent.insertBefore(properties, parent.getFirstChild());
            else parent.appendChild(properties);
            return properties;
        }

        private Element paragraphProperties(Document part) {
            var properties = first(holder, W, "pPr");
            if (properties != null) return properties;
            properties = part.createElementNS(W, "w:pPr");
            holder.insertBefore(properties, holder.getFirstChild());
            return properties;
        }
    }
}
