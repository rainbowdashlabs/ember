/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.first;

/**
 * Draws the bold text of a replaced family in a family that has a bold face.
 *
 * <p>A light cut such as Open Sans Light ships no bold face, so LibreOffice would thicken the light letters
 * itself and a bold label of the form would barely stand out. Every bold run of the body, the headers and
 * the footers whose family ({@link RunFonts}) is replaced by such a cut is set in the family named for its
 * bold text instead, Open Sans in that case, whose bold face is a real one.
 */
final class FormBoldFaces {
    /** The parts that hold text. */
    private static final Pattern TEXT = Pattern.compile("word/(document|header\\d*|footer\\d*)\\.xml");

    /** The attributes of a run's fonts that name the family Latin text is set in. */
    private static final List<String> LATIN = List.of("ascii", "hAnsi");

    private FormBoldFaces() {}

    /**
     * @param docx    a Word form, its families not yet replaced
     * @param fonts   the fonts of its styles, read from the same form
     * @param choices how each replaced family is drawn, by the family the form names
     * @throws IOException where a part cannot be read or written
     */
    static void split(DocxPackage docx, RunFonts fonts, Map<String, FontChoice> choices) throws IOException {
        if (choices.values().stream().allMatch(choice -> choice.boldFamily() == null)) return;
        for (String name : docx.names()) {
            if (!TEXT.matcher(name).matches()) continue;
            var part = docx.xml(name);
            if (part.isEmpty()) continue;
            var bold = new ArrayList<Element>();
            var families = new ArrayList<String>();
            var runs = part.get().getElementsByTagNameNS(W, "r");
            for (int index = 0; index < runs.getLength(); index++) {
                var run = (Element) runs.item(index);
                var family = fonts.of(run).family();
                var choice = family == null ? null : choices.get(family);
                if (choice == null || choice.boldFamily() == null || !fonts.bold(run)) continue;
                bold.add(run);
                families.add(choice.boldFamily());
            }
            for (int index = 0; index < bold.size(); index++) setFamily(bold.get(index), families.get(index));
            if (!bold.isEmpty()) docx.replace(name, part.get());
        }
    }

    private static void setFamily(Element run, String family) {
        var document = run.getOwnerDocument();
        var properties = first(run, W, "rPr");
        if (properties == null) {
            properties = document.createElementNS(W, "w:rPr");
            run.insertBefore(properties, run.getFirstChild());
        }
        var named = first(properties, W, "rFonts");
        if (named == null) {
            named = document.createElementNS(W, "w:rFonts");
            var style = first(properties, W, "rStyle");
            properties.insertBefore(named, style == null ? properties.getFirstChild() : style.getNextSibling());
        }
        for (String attribute : LATIN) {
            named.removeAttributeNS(W, attribute + "Theme");
            named.setAttributeNS(W, "w:" + attribute, family);
        }
    }
}
