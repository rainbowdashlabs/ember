/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import dev.chojo.ember.feature.generator.entity.FontOutline;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.util.FilePaths;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The font a document prints in where its template picks none, in place of Liberation Sans.
 *
 * <p>It is read once, from a directory of font files the instance's operator provides: the container
 * images put Berlin Type there on their first start. It is not an uploaded font: no owner keeps it, it
 * takes no room and no template names it. The family is the one the regular file carries itself, and
 * the other files of that family are its further styles. A file that is no font a document can embed is
 * left out and named in the log. Without a regular file the directory holds no default font, and
 * Liberation Sans stays the default as before; it remains the fallback for every character and style the
 * default font lacks either way.
 *
 * <p>Its font files never reach a browser: Typst reads them from the directory, the PDF stamper from
 * memory. Beside them the directory may hold the web files the font's publisher provides for web pages
 * ({@code .woff2}, the style named at the end of the file name as in {@code Family-Bold.woff2}), which
 * only the template editor loads, so the words it shows look as they print. Their family name may differ
 * from the one the font files carry; they are taken as styles of the default font all the same. Without
 * a regular web file the editor shows the default font by its name only.
 */
public final class DefaultFont {
    private static final Logger log = LoggerFactory.getLogger(DefaultFont.class);

    private final @Nullable Path directory;
    private final @Nullable String family;
    private final Map<FontStyle, Face> faces;
    private final Map<FontStyle, byte[]> webFiles;

    /**
     * One style of the default font.
     *
     * @param outline how its glyphs are drawn
     * @param data    the file
     */
    private record Face(FontOutline outline, byte[] data) {}

    private DefaultFont(
            @Nullable Path directory,
            @Nullable String family,
            Map<FontStyle, Face> faces,
            Map<FontStyle, byte[]> webFiles) {
        this.directory = directory;
        this.family = family;
        this.faces = Collections.unmodifiableMap(faces);
        this.webFiles = webFiles.containsKey(FontStyle.REGULAR) ? Collections.unmodifiableMap(webFiles) : Map.of();
    }

    /** @return no default font, where every text naming none prints in Liberation Sans */
    public static DefaultFont absent() {
        return new DefaultFont(null, null, Map.of(), Map.of());
    }

    /**
     * Reads the default font from a directory.
     *
     * @param directory the directory, which may be missing or empty
     * @return the font, absent where the directory holds no regular style of a family
     */
    public static DefaultFont readFrom(Path directory) {
        var styles = new EnumMap<FontStyle, FontFile>(FontStyle.class);
        for (var file : filesIn(directory, ".ttf", ".otf")) {
            read(file).ifPresent(read -> styles.putIfAbsent(read.inspection().style(), read));
        }
        var regular = styles.get(FontStyle.REGULAR);
        if (regular == null) {
            log.info("No default font in {}: documents print in {}", directory, BundledFont.FAMILY);
            return absent();
        }
        String family = regular.inspection().internalFamily();
        var faces = new EnumMap<FontStyle, Face>(FontStyle.class);
        styles.forEach((style, read) -> {
            if (read.inspection().internalFamily().equalsIgnoreCase(family)) {
                faces.put(style, new Face(read.inspection().outline(), read.data()));
            }
        });
        log.info("Documents print in {} ({}) by default, read from {}", family, faces.keySet(), directory);
        return new DefaultFont(directory.toAbsolutePath(), family, faces, webFilesIn(directory));
    }

    /** @return whether there is a default font in place of Liberation Sans */
    public boolean present() {
        return family != null;
    }

    /** @return the family name the font carries itself, which Typst asks for, or empty where there is none */
    public Optional<String> family() {
        return Optional.ofNullable(family);
    }

    /** @return the family a text naming none prints in: the default font, else Liberation Sans */
    public String printedFamily() {
        return Objects.requireNonNullElse(family, BundledFont.FAMILY);
    }

    /** @return the directory Typst finds the font in, or empty where there is no default font */
    public Optional<Path> directory() {
        return Optional.ofNullable(directory);
    }

    /** @return the styles the font has, in their natural order */
    public List<FontStyle> styles() {
        return List.copyOf(faces.keySet());
    }

    /**
     * The file a text on an uploaded PDF is drawn in for a style. Only the style itself counts: a style
     * the font lacks, italic above all, is drawn in Liberation Sans rather than as another style of this
     * font.
     *
     * @param style the style asked for
     * @return the TrueType file, a copy the caller may keep, or empty where the font has no such style
     *         drawn with TrueType outlines
     */
    public Optional<byte[]> pdfFile(FontStyle style) {
        var face = faces.get(style);
        if (face == null || face.outline() != FontOutline.TRUETYPE) return Optional.empty();
        return Optional.of(face.data().clone());
    }

    /** @return the styles the template editor can show the font in, regular first; empty where it has no web files */
    public List<FontStyle> webStyles() {
        return List.copyOf(webFiles.keySet());
    }

    /**
     * The web file the template editor shows a style of the font in.
     *
     * @param style the style asked for
     * @return the WOFF2 file, a copy the caller may keep, or empty where there is none of that style
     */
    public Optional<byte[]> webFile(FontStyle style) {
        return Optional.ofNullable(webFiles.get(style)).map(byte[]::clone);
    }

    /**
     * A file of the directory as it was read.
     *
     * @param inspection what the file is
     * @param data       the file
     */
    private record FontFile(FontFiles.Inspection inspection, byte[] data) {}

    private static Map<FontStyle, byte[]> webFilesIn(Path directory) {
        var files = new EnumMap<FontStyle, byte[]>(FontStyle.class);
        for (var file : filesIn(directory, ".woff2")) {
            styleNamedBy(file).ifPresent(style -> readWebFile(file).ifPresent(data -> files.putIfAbsent(style, data)));
        }
        return files;
    }

    private static List<Path> filesIn(Path directory, String... extensions) {
        if (!Files.isDirectory(directory)) return List.of();
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> namedAs(file, extensions))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("The default font directory {} could not be listed", directory, e);
            return List.of();
        }
    }

    private static boolean namedAs(Path file, String... extensions) {
        String name = FilePaths.nameOf(file).toLowerCase(Locale.ROOT);
        return !name.startsWith(".") && Arrays.stream(extensions).anyMatch(name::endsWith);
    }

    private static Optional<FontStyle> styleNamedBy(Path file) {
        String name = FilePaths.nameOf(file);
        String stem = name.substring(0, name.lastIndexOf('.'));
        String suffix = stem.substring(stem.lastIndexOf('-') + 1);
        return Arrays.stream(FontStyle.values())
                .filter(style -> style.fileSuffix().equalsIgnoreCase(suffix))
                .findFirst();
    }

    private static Optional<byte[]> readWebFile(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            if (WebFontFiles.woff2(data)) return Optional.of(data);
            log.warn("The file {} is no web font and is left out of the default font", file);
        } catch (IOException e) {
            log.warn("The web file {} of the default font could not be read", file, e);
        }
        return Optional.empty();
    }

    private static Optional<FontFile> read(Path file) {
        try {
            byte[] data = Files.readAllBytes(file);
            return Optional.of(new FontFile(FontFiles.inspect(data), data));
        } catch (IOException | RuntimeException unusable) {
            log.warn("The file {} is no font a document can embed and is left out of the default font", file);
            return Optional.empty();
        }
    }
}
