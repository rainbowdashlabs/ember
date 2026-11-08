/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.wordform;

import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static dev.chojo.ember.feature.generator.service.wordform.WordXml.W;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.attribute;
import static dev.chojo.ember.feature.generator.service.wordform.WordXml.first;

/**
 * The font a run or a paragraph mark of a Word form is drawn in, found the way Word finds it.
 *
 * <p>The family and the size are each taken from the first level that sets them: the run's own properties,
 * its character style and the styles that one is based on, the paragraph style (the default one where the
 * paragraph names none) and its bases, and last the defaults of the document. A level that names a font of
 * the theme stands for the minor or major Latin font of the theme. Where no level sets a size, text is ten
 * points, as in Word.
 *
 * <p>TODO: the fonts a table style sets for the text in its cells are not followed.
 */
final class RunFonts {
    /** The size of text no level sets one for, in points. */
    static final double DEFAULT_SIZE = 10;

    private static final String A = "http://schemas.openxmlformats.org/drawingml/2006/main";
    private static final Pattern THEME = Pattern.compile("word/theme/theme\\d*\\.xml");

    /**
     * A style as far as fonts go.
     *
     * @param basedOn       the style it is based on, or null
     * @param runProperties the run properties it sets, or null
     */
    private record Style(@Nullable String basedOn, @Nullable Element runProperties) {}

    private final Map<String, Style> styles;
    private final @Nullable String defaultParagraphStyle;
    private final @Nullable Element defaults;
    private final String minorFont;
    private final String majorFont;

    private RunFonts(
            Map<String, Style> styles,
            @Nullable String defaultParagraphStyle,
            @Nullable Element defaults,
            String minorFont,
            String majorFont) {
        this.styles = styles;
        this.defaultParagraphStyle = defaultParagraphStyle;
        this.defaults = defaults;
        this.minorFont = minorFont;
        this.majorFont = majorFont;
    }

    /**
     * @param docx a Word form, read before any of its fonts is replaced
     * @return the fonts of its styles, defaults and theme
     * @throws IOException where a part cannot be read
     */
    static RunFonts read(DocxPackage docx) throws IOException {
        var styles = new HashMap<String, Style>();
        String defaultParagraphStyle = null;
        Element defaults = null;
        var styleSheet = docx.xml(DocxPackage.STYLES);
        if (styleSheet.isPresent()) {
            var all = styleSheet.get().getElementsByTagNameNS(W, "style");
            for (int index = 0; index < all.getLength(); index++) {
                var style = (Element) all.item(index);
                String id = style.getAttributeNS(W, "styleId");
                String basedOn = attribute(first(style, W, "basedOn"), "val");
                styles.put(id, new Style(basedOn.isEmpty() ? null : basedOn, first(style, W, "rPr")));
                if ("paragraph".equals(style.getAttributeNS(W, "type")) && isOn(style.getAttributeNS(W, "default")))
                    defaultParagraphStyle = id;
            }
            var docDefaults = first(styleSheet.get().getDocumentElement(), W, "docDefaults");
            defaults = first(first(docDefaults, W, "rPrDefault"), W, "rPr");
        }
        var theme = theme(docx);
        return new RunFonts(
                Map.copyOf(styles),
                defaultParagraphStyle,
                defaults,
                themeFont(theme, "minorFont"),
                themeFont(theme, "majorFont"));
    }

    /**
     * @param element a run, or anything else inside a paragraph, which is read as that paragraph's mark
     * @return the font it is drawn in
     */
    TextFont of(Element element) {
        var levels = levelsOf(element);
        return new TextFont(familyOf(levels), sizeOf(levels), boldIn(levels));
    }

    /**
     * @param element a run, or anything else inside a paragraph, which is read as that paragraph's mark
     * @return whether it is drawn bold
     */
    boolean bold(Element element) {
        return boldIn(levelsOf(element));
    }

    private static boolean boldIn(List<Element> levels) {
        for (var level : levels) {
            var bold = first(level, W, "b");
            if (bold == null) continue;
            String value = bold.getAttributeNS(W, "val");
            return value.isEmpty() || isOn(value);
        }
        return false;
    }

    /** The run properties that decide how it is drawn, the one that decides first first. */
    private List<Element> levelsOf(Element element) {
        var levels = new ArrayList<Element>();
        var paragraph = paragraphOf(element);
        if (isRun(element)) {
            var properties = first(element, W, "rPr");
            add(levels, properties);
            styleChain(levels, attribute(first(properties, W, "rStyle"), "val"));
        } else if (paragraph != null) {
            add(levels, first(first(paragraph, W, "pPr"), W, "rPr"));
        }
        String paragraphStyle =
                paragraph == null ? "" : attribute(first(first(paragraph, W, "pPr"), W, "pStyle"), "val");
        styleChain(
                levels,
                paragraphStyle.isEmpty() && defaultParagraphStyle != null ? defaultParagraphStyle : paragraphStyle);
        add(levels, defaults);
        return levels;
    }

    private void styleChain(List<Element> levels, String id) {
        var seen = new HashSet<String>();
        String current = id;
        while (current != null && !current.isEmpty() && seen.add(current)) {
            var style = styles.get(current);
            if (style == null) return;
            add(levels, style.runProperties());
            current = style.basedOn();
        }
    }

    private @Nullable String familyOf(List<Element> levels) {
        for (var level : levels) {
            var fonts = first(level, W, "rFonts");
            if (fonts == null) continue;
            String theme = fonts.getAttributeNS(W, "asciiTheme");
            if (!theme.isEmpty()) {
                String family = theme.startsWith("major") ? majorFont : minorFont;
                return family.isEmpty() ? null : family;
            }
            String family = fonts.getAttributeNS(W, "ascii").strip();
            if (!family.isEmpty()) return family;
        }
        return null;
    }

    private static double sizeOf(List<Element> levels) {
        for (var level : levels) {
            String halfPoints = attribute(first(level, W, "sz"), "val");
            if (halfPoints.isEmpty()) continue;
            try {
                return Integer.parseInt(halfPoints) / 2.0;
            } catch (NumberFormatException unreadable) {
                return DEFAULT_SIZE;
            }
        }
        return DEFAULT_SIZE;
    }

    private static void add(List<Element> levels, @Nullable Element properties) {
        if (properties != null) levels.add(properties);
    }

    private static boolean isRun(Element element) {
        return W.equals(element.getNamespaceURI()) && "r".equals(element.getLocalName());
    }

    private static @Nullable Element paragraphOf(Element element) {
        for (Node node = element; node instanceof Element current; node = current.getParentNode()) {
            if (W.equals(current.getNamespaceURI()) && "p".equals(current.getLocalName())) return current;
        }
        return null;
    }

    private static boolean isOn(String flag) {
        return "1".equals(flag) || "true".equals(flag) || "on".equals(flag);
    }

    private static @Nullable Document theme(DocxPackage docx) throws IOException {
        for (String name : docx.names()) {
            if (THEME.matcher(name).matches()) return docx.xml(name).orElse(null);
        }
        return null;
    }

    private static String themeFont(@Nullable Document theme, String which) {
        if (theme == null) return "";
        var scheme = theme.getElementsByTagNameNS(A, which);
        if (scheme.getLength() == 0) return "";
        var latin = first((Element) scheme.item(0), A, "latin");
        return latin == null ? "" : latin.getAttribute("typeface").strip();
    }
}
