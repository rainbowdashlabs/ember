/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.BuiltInFace;
import dev.chojo.ember.feature.generator.entity.DocumentFont;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontOutline;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * How a face reaches Typst: as a file written beside the document under a name of its own, or not at
 * all for a face Typst carries itself. Typst then finds it by the family name the face carries.
 */
public final class TypstFaces {
    private TypstFaces() {}

    /**
     * Puts the file of a face among the files Typst is handed, where it needs one and it is not there
     * yet.
     *
     * @param face  the face
     * @param read  reads the file of a face, empty where it is gone
     * @param files the files Typst is handed, by name, which this adds to
     * @return whether Typst can print in the face: it carries the face itself or its file is there
     */
    public static boolean supply(FontFace face, Function<FontFace, Optional<byte[]>> read, Map<String, byte[]> files) {
        if (face instanceof BuiltInFace builtIn && !builtIn.bundled()) return true;
        String name = fileName(face);
        if (files.containsKey(name)) return true;
        var data = read.apply(face);
        data.ifPresent(bytes -> files.put(name, bytes));
        return data.isPresent();
    }

    /**
     * Puts the kept file of a face among the files Typst is handed, where it needs one.
     *
     * @param face     the face
     * @param keptFile the kept file of a face, empty where it is gone
     * @param files    the files Typst is handed, which this adds to
     * @return whether Typst can print in the face: it carries the face itself or its file is there
     */
    public static boolean supplyKept(
            FontFace face, Function<FontFace, Optional<Path>> keptFile, Collection<Path> files) {
        if (face instanceof BuiltInFace builtIn && !builtIn.bundled()) return true;
        var file = keptFile.apply(face);
        file.filter(path -> !files.contains(path)).ifPresent(files::add);
        return file.isPresent();
    }

    private static String fileName(FontFace face) {
        return switch (face) {
            case DocumentFont font -> "font-" + font.id() + (font.outline() == FontOutline.CFF ? ".otf" : ".ttf");
            case BuiltInFace builtIn ->
                Objects.requireNonNull(builtIn.bundledFile(), "only a bundled face is written out");
        };
    }
}
