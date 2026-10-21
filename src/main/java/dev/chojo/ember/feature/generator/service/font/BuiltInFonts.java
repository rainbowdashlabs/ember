/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.BuiltInFace;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontOrigin;
import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The font families every installation offers its templates, whatever was uploaded.
 *
 * <p>Liberation Sans, Serif and Mono, Carlito, Caladea and Open Sans ship with the application in all four
 * styles ({@code fonts/*.ttf}, each under the SIL Open Font License in {@code fonts/<Family>-LICENSE.txt}),
 * so letters and fields on an uploaded PDF print in them alike. Liberation takes the measure of Arial, Times
 * New Roman and Courier New, Carlito that of Calibri and Caladea that of Cambria, so a document written in
 * one of those keeps its lines; Open Sans stands in for humanist fonts such as Frutiger, and also ships its
 * Light cut, which a Word form set in a light weight is drawn in. Libertinus Serif, New Computer Modern and DejaVu Sans Mono
 * are the families Typst carries inside itself: letters print in them without a file, and since there
 * is no file to embed, fields on an uploaded PDF are not offered them.
 *
 * <p>A family an owner uploads under one of these names takes its place, the way a nearer owner's
 * family takes the place of a farther one's.
 */
public final class BuiltInFonts {
    private static final List<FontFamily> FAMILIES = List.of(
            bundled("Liberation Sans", "LiberationSans"),
            bundled("Liberation Serif", "LiberationSerif"),
            bundled("Liberation Mono", "LiberationMono"),
            bundled("Carlito", "Carlito"),
            bundled("Caladea", "Caladea"),
            bundled("Open Sans", "OpenSans"),
            carriedByTypst("Libertinus Serif", FontOutline.CFF),
            carriedByTypst("New Computer Modern", FontOutline.CFF),
            carriedByTypst("DejaVu Sans Mono", FontOutline.TRUETYPE));

    /**
     * The light cuts of built-in families, which a Word form in a light weight is drawn in and its fields print
     * in. Letters are not offered them: Typst knows the light file only by the name of its family. Bold text of
     * a light cut is the bold face of the family itself, since light cuts ship none of their own.
     */
    private static final Map<String, FontFamily> LIGHT_CUTS = Map.of("Open Sans", lightCut("Open Sans", "OpenSans"));

    /**
     * DejaVu Sans, which no template names but every letter falls back to for the symbols Liberation Sans
     * lacks ({@link BundledFont}).
     */
    private static final BuiltInFace SYMBOLS = new BuiltInFace(
            BundledFont.SYMBOL_FAMILY, FontStyle.REGULAR, FontOutline.TRUETYPE, BundledFont.SYMBOL_FILE_NAME);

    private static final Map<String, byte[]> FILES = Stream.concat(
                    Stream.concat(FAMILIES.stream(), LIGHT_CUTS.values().stream())
                            .flatMap(family -> family.files().values().stream())
                            .map(BuiltInFace.class::cast),
                    Stream.of(SYMBOLS))
            .filter(BuiltInFace::bundled)
            .map(BuiltInFace::bundledFile)
            .distinct()
            .collect(Collectors.toUnmodifiableMap(Function.identity(), BundledFont::resource));

    private BuiltInFonts() {}

    /** @return the built-in families, by name, the light cuts that only PDF fields print in included */
    public static List<FontFamily> families() {
        return Stream.concat(FAMILIES.stream(), LIGHT_CUTS.values().stream()).toList();
    }

    /**
     * @param family the name of a built-in family
     * @return its light cut, where it ships one
     */
    public static Optional<FontFamily> lightCut(String family) {
        return Optional.ofNullable(LIGHT_CUTS.get(family));
    }

    /** @return the names of the built-in families that ship a light cut */
    public static List<String> withLightCut() {
        return LIGHT_CUTS.keySet().stream().sorted().toList();
    }

    /** @return Liberation Sans, which a text naming no family prints in where there is no default font */
    public static FontFamily fallback() {
        return FAMILIES.stream()
                .filter(family -> family.name().equals(BundledFont.FAMILY))
                .findFirst()
                .orElseThrow();
    }

    /** @return the face of DejaVu Sans, which a letter falls back to for the symbols Liberation Sans lacks */
    public static BuiltInFace symbolFallback() {
        return SYMBOLS;
    }

    /**
     * @param face a built-in face
     * @return its file, a copy the caller may keep, or empty for a face Typst carries itself
     */
    public static Optional<byte[]> data(BuiltInFace face) {
        String file = face.bundledFile();
        if (file == null) return Optional.empty();
        return Optional.ofNullable(FILES.get(file)).map(byte[]::clone);
    }

    /**
     * @param fileName the name of a file a built-in family ships with
     * @return the file, a copy the caller may keep
     */
    static byte[] bundledFile(String fileName) {
        return Objects.requireNonNull(FILES.get(fileName), "No built-in family ships " + fileName)
                .clone();
    }

    private static FontFamily bundled(String family, String filePrefix) {
        return family(
                family,
                style -> new BuiltInFace(
                        family, style, FontOutline.TRUETYPE, filePrefix + "-" + style.fileSuffix() + ".ttf"));
    }

    /**
     * The light cut of a family, named after it with "Light". Its faces carry the family's own name, which
     * is the one Typst finds the light file by.
     */
    private static FontFamily lightCut(String family, String filePrefix) {
        return family(
                family + " Light",
                style -> new BuiltInFace(
                        family, style, FontOutline.TRUETYPE, filePrefix + "-" + lightFileSuffix(style) + ".ttf", true));
    }

    private static String lightFileSuffix(FontStyle style) {
        return switch (style) {
            case REGULAR -> "Light";
            case ITALIC -> "LightItalic";
            case BOLD, BOLD_ITALIC -> style.fileSuffix();
        };
    }

    private static FontFamily carriedByTypst(String family, FontOutline outline) {
        return family(family, style -> new BuiltInFace(family, style, outline, null));
    }

    private static FontFamily family(String family, Function<FontStyle, FontFace> face) {
        var faces = new EnumMap<FontStyle, FontFace>(FontStyle.class);
        for (var style : FontStyle.values()) faces.put(style, face.apply(style));
        return new FontFamily(family, FontOrigin.BUILT_IN, faces);
    }
}
