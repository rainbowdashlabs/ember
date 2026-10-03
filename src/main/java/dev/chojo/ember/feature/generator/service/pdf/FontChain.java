/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import org.apache.pdfbox.pdmodel.font.PDFont;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The fonts a text on a PDF is drawn in, in the order they are asked: for every character the first
 * font that has a glyph for it draws it.
 *
 * <p>A font without a glyph would stop the whole document, so one name nobody's font knows (one
 * "Łukasz" in a run of a hundred members) would stop them all. Here a text is cut into runs instead,
 * each in the first font that can print it, and a character no font can print is left out and named,
 * so the screen can say which ones went missing.
 */
public final class FontChain {
    private final List<PDFont> fonts;

    /**
     * @param fonts the fonts in the order they are asked, the one chosen for the text first
     */
    public FontChain(List<PDFont> fonts) {
        if (fonts.isEmpty()) throw new IllegalArgumentException("A font chain needs a font");
        this.fonts = List.copyOf(fonts);
    }

    /** @return the font asked first, whose measures place a line */
    public PDFont primary() {
        return fonts.getFirst();
    }

    /**
     * A part of a text drawn in one font.
     *
     * @param font the font
     * @param text the characters
     */
    public record Run(PDFont font, String text) {}

    /**
     * A text cut into runs.
     *
     * @param runs        the runs, in reading order
     * @param unprintable the characters no font could print, which the runs leave out
     */
    public record Shaped(List<Run> runs, Set<String> unprintable) {

        /**
         * @param size the font size in points
         * @return how wide the runs are drawn
         */
        public float width(float size) {
            float width = 0;
            for (var run : runs) width += widthOf(run, size);
            return width;
        }
    }

    /**
     * Cuts a text into runs, each in the first font that can print it.
     *
     * @param text the text, without line breaks
     * @return the runs and what could not be printed
     */
    public Shaped shape(String text) {
        var runs = new ArrayList<Run>();
        var unprintable = new LinkedHashSet<String>();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            offset += Character.charCount(codePoint);
            String character = Character.toString(codePoint);
            PDFont font = fontFor(character);
            if (font == null) {
                if (!Character.isISOControl(codePoint)) unprintable.add(character);
            } else if (!runs.isEmpty() && runs.getLast().font() == font) {
                runs.set(runs.size() - 1, new Run(font, runs.getLast().text() + character));
            } else {
                runs.add(new Run(font, character));
            }
        }
        return new Shaped(runs, unprintable);
    }

    private @Nullable PDFont fontFor(String character) {
        for (var font : fonts) {
            if (prints(font, character)) return font;
        }
        return null;
    }

    private static boolean prints(PDFont font, String character) {
        try {
            font.encode(character);
            return true;
        } catch (IllegalArgumentException | IOException noGlyph) {
            return false;
        }
    }

    private static float widthOf(Run run, float size) {
        try {
            return run.font().getStringWidth(run.text()) / 1000f * size;
        } catch (IOException | IllegalArgumentException unmeasurable) {
            return 0;
        }
    }
}
