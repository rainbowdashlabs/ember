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

/**
 * The font families every installation offers its templates, whatever was uploaded.
 *
 * <p>Liberation Sans, Serif and Mono ship with the application in all four styles
 * ({@code fonts/Liberation*.ttf}, under the SIL Open Font License in {@code fonts/Liberation-LICENSE.txt}),
 * so letters and fields
 * on an uploaded PDF print in them alike. Libertinus Serif, New Computer Modern and DejaVu Sans Mono
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
            carriedByTypst("Libertinus Serif", FontOutline.CFF),
            carriedByTypst("New Computer Modern", FontOutline.CFF),
            carriedByTypst("DejaVu Sans Mono", FontOutline.TRUETYPE));

    private static final Map<String, byte[]> FILES = FAMILIES.stream()
            .flatMap(family -> family.files().values().stream())
            .map(BuiltInFace.class::cast)
            .filter(BuiltInFace::bundled)
            .map(BuiltInFace::bundledFile)
            .collect(Collectors.toUnmodifiableMap(Function.identity(), BundledFont::resource));

    private BuiltInFonts() {}

    /** @return the built-in families, by name */
    public static List<FontFamily> families() {
        return FAMILIES;
    }

    /** @return Liberation Sans, which a text naming no family prints in where there is no default font */
    public static FontFamily fallback() {
        return FAMILIES.stream()
                .filter(family -> family.name().equals(BundledFont.FAMILY))
                .findFirst()
                .orElseThrow();
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

    private static FontFamily carriedByTypst(String family, FontOutline outline) {
        return family(family, style -> new BuiltInFace(family, style, outline, null));
    }

    private static FontFamily family(String family, Function<FontStyle, FontFace> face) {
        var faces = new EnumMap<FontStyle, FontFace>(FontStyle.class);
        for (var style : FontStyle.values()) faces.put(style, face.apply(style));
        return new FontFamily(family, FontOrigin.BUILT_IN, faces);
    }
}
