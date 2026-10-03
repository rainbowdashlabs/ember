/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.util.Json;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Draws a line of sample text in a font as a PNG picture, with Typst, which prints the letters too and
 * so draws every family the way a letter prints it, the ones Typst carries itself included.
 *
 * <p>The glyphs are black on a transparent background, so a screen can show the picture on a light
 * background as it is and invert it on a dark one. The family names and the text reach Typst as data,
 * never as markup, since a family name is whatever an uploaded file says it is.
 */
@Singleton
public class FontSampleRenderer {
    private static final Logger log = LoggerFactory.getLogger(FontSampleRenderer.class);

    /** The line every sample shows: a name, a place, digits and the letters German adds. */
    public static final String TEXT = "Bescheinigung für Lena Muster, 12345 Musterstadt · ÄÖÜ äöüß 67890";

    /** Twice the 72 of a point, so the picture stays sharp on a screen that draws two pixels per point. */
    private static final int PPI = 144;

    private static final String SOURCE = """
            #let sample = json("sample.json")
            #set page(width: auto, height: auto, margin: (x: 1pt, y: 2pt), fill: none)
            #set text(
              font: sample.families,
              size: 14pt,
              weight: sample.weight,
              style: sample.style,
              top-edge: "ascender",
              bottom-edge: "descender",
              fill: black,
            )
            #sample.text
            """;

    /**
     * The fonts a sample is drawn in.
     *
     * @param families    the family names to ask for, in order
     * @param files       the font files Typst is handed, by name
     * @param directories further directories of fonts
     */
    public record SampleFonts(List<String> families, Map<String, byte[]> files, List<Path> directories) {}

    /**
     * Draws the sample line.
     *
     * @param fonts the fonts
     * @param style the style to draw it in
     * @return the PNG picture
     */
    public byte[] render(SampleFonts fonts, FontStyle style) {
        try {
            return TypstCompiler.compilePng(
                    SOURCE, Map.of("sample.json", data(fonts, style)), fonts.files(), fonts.directories(), PPI);
        } catch (IOException e) {
            log.error("A sample of {} could not be drawn", fonts.families(), e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }

    private static String data(SampleFonts fonts, FontStyle style) {
        return Json.MAPPER.writeValueAsString(new Sample(
                fonts.families(), style.bold() ? "bold" : "regular", style.italic() ? "italic" : "normal", TEXT));
    }

    /**
     * What the source reads.
     *
     * @param families the family names, in order
     * @param weight   Typst's name of the weight
     * @param style    Typst's name of the slant
     * @param text     the line
     */
    private record Sample(List<String> families, String weight, String style, String text) {}
}
