/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The font families a Word form names, and the form with some of them replaced.
 *
 * <p>A family is used where text is set in it (the body, the styles, the lists, the headers and footers)
 * and in the theme, which is where a form leaves the fonts it takes from Word's defaults. The list of
 * fonts the document carries also names fonts no text is set in, so it says nothing about what the form
 * uses. Replacing a family rewrites every name of it, that list included, so LibreOffice draws the form in
 * the family chosen instead of one of its own choosing.
 */
final class FormFonts {
    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";

    /** The parts that name fonts. */
    private static final Pattern NAMING = Pattern.compile(
            "word/(document|styles|numbering|fontTable|header\\d*|footer\\d*)\\.xml|word/theme/theme\\d*\\.xml");

    /** The part that lists fonts whether or not any text is set in them. */
    private static final String FONT_LIST = "word/fontTable.xml";

    /** The attributes of a run's fonts that name a family. */
    private static final List<String> RUN_FONTS = List.of("ascii", "hAnsi", "cs", "eastAsia");

    /** The elements of a theme that name a family in their typeface. */
    private static final Set<String> THEME_FONTS = Set.of("latin", "ea", "cs");

    private FormFonts() {}

    /**
     * @param docx a Word form
     * @return the families its text is set in, each once, in the order the parts name them
     * @throws IOException where a part cannot be read
     */
    static List<String> named(DocxPackage docx) throws IOException {
        var families = new LinkedHashSet<String>();
        for (var part : naming(docx).entrySet()) {
            if (part.getKey().equals(FONT_LIST)) continue;
            for (var name : names(part.getValue())) {
                String family = name.getValue().strip();
                if (!family.isEmpty() && !family.startsWith("+")) families.add(family);
            }
        }
        return List.copyOf(families);
    }

    /**
     * Writes another family wherever the form names one that is replaced.
     *
     * @param docx         a Word form
     * @param replacements the family put in place of each replaced one, by the family replaced
     * @throws IOException where a part cannot be read or written
     */
    static void replace(DocxPackage docx, Map<String, String> replacements) throws IOException {
        if (replacements.isEmpty()) return;
        for (var part : naming(docx).entrySet()) {
            boolean changed = false;
            for (var name : names(part.getValue())) {
                String replacement = replacements.get(name.getValue().strip());
                if (replacement == null) continue;
                name.setValue(replacement);
                changed = true;
            }
            if (changed) docx.replace(part.getKey(), part.getValue());
        }
    }

    private static Map<String, Document> naming(DocxPackage docx) throws IOException {
        var parts = new LinkedHashMap<String, Document>();
        for (String name : docx.names()) {
            if (!NAMING.matcher(name).matches()) continue;
            var document = docx.xml(name);
            if (document.isPresent()) parts.put(name, document.get());
        }
        return parts;
    }

    /** Every attribute of a part that holds the name of a family. */
    private static List<Attr> names(Document document) {
        var out = new ArrayList<Attr>();
        var runs = document.getElementsByTagNameNS(WordFormScanner.W, "rFonts");
        for (int index = 0; index < runs.getLength(); index++) {
            var element = (Element) runs.item(index);
            for (String attribute : RUN_FONTS) {
                var named = element.getAttributeNodeNS(WordFormScanner.W, attribute);
                if (named != null) out.add(named);
            }
        }
        var listed = document.getElementsByTagNameNS(WordFormScanner.W, "font");
        for (int index = 0; index < listed.getLength(); index++) {
            var named = ((Element) listed.item(index)).getAttributeNodeNS(WordFormScanner.W, "name");
            if (named != null) out.add(named);
        }
        var theme = document.getElementsByTagNameNS(A, "*");
        for (int index = 0; index < theme.getLength(); index++) {
            var element = (Element) theme.item(index);
            if (!THEME_FONTS.contains(element.getLocalName())) continue;
            var named = element.getAttributeNode("typeface");
            if (named != null) out.add(named);
        }
        return out;
    }
}
