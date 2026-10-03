/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.font;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.FontFace;
import dev.chojo.ember.feature.generator.entity.FontFamily;
import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.service.font.FontSampleRenderer.SampleFonts;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pictures of a line of sample text in each font a template can print in, so a picker shows what a
 * family looks like without the browser loading its files, which only the template editor does.
 *
 * <p>An owner sees samples only of the families its templates reach, looked up the way a template
 * looks them up, and of the default font. A sample is drawn as a document prints the family
 * ({@link FontSampleRenderer}): in the face the style is drawn from, Liberation Sans after it for what
 * the face lacks, and in Liberation Sans alone where the face's file is gone.
 *
 * <p>Drawn samples are kept in memory, at most {@link #MAX_SAMPLES} of them, by the {@link #versionOf
 * version} of what they show and the style. The version follows the files themselves, so two owners
 * with the same file share a sample, and a family whose files change gets a new one. The pickers put
 * the version into the address they ask, which lets a browser keep a sample for as long as it likes.
 */
@Singleton
public class FontSampleService {
    /** The most samples kept, a few hundred pictures of some kilobytes each. */
    static final int MAX_SAMPLES = 512;

    private final FontLibrary library;
    private final FontSampleRenderer renderer;
    private final Cache<SampleKey, byte[]> samples =
            Caffeine.newBuilder().maximumSize(MAX_SAMPLES).build();

    /**
     * What a kept sample shows.
     *
     * @param version the version of the faces or the default font drawn
     * @param style   the style drawn
     */
    private record SampleKey(String version, FontStyle style) {}

    @Inject
    public FontSampleService(FontLibrary library, FontSampleRenderer renderer) {
        this.library = library;
        this.renderer = renderer;
    }

    /**
     * A sample of a family an owner reaches, or of the default font.
     *
     * @param owner  the owner whose templates the family is looked up for
     * @param family the family name, or null or blank for the default font
     * @param style  the style to draw
     * @return the PNG picture
     */
    public byte[] sample(Owner owner, @Nullable String family, FontStyle style) {
        if (family == null || family.isBlank()) return standard(style);
        var found = library.familyAt(owner, family).orElseThrow(DocumentRefusal.DOCUMENT_FONT_SAMPLE_UNKNOWN::raise);
        return samples.get(
                new SampleKey(versionOf(found), style), key -> renderer.render(fontsOf(found, style), style));
    }

    /**
     * The version of a family's sample, which changes whenever one of its files does.
     *
     * @param family the family
     * @return a short hexadecimal text
     */
    public static String versionOf(FontFamily family) {
        String faces = family.files().values().stream()
                .map(FontFace::identity)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining("\n"));
        return Sha256.hexPrefix(faces, 16);
    }

    private byte[] standard(FontStyle style) {
        var defaultFont = library.defaultFont();
        String version = Sha256.hexPrefix("default:" + defaultFont.printedFamily(), 16);
        return samples.get(new SampleKey(version, style), key -> renderer.render(standardFonts(style), style));
    }

    private SampleFonts standardFonts(FontStyle style) {
        var defaultFont = library.defaultFont();
        var families = new ArrayList<String>();
        defaultFont
                .family()
                .filter(family -> defaultFont.styles().contains(style))
                .ifPresent(families::add);
        families.add(BundledFont.FAMILY);
        return new SampleFonts(
                families, fallbackFiles(), defaultFont.directory().stream().toList());
    }

    private SampleFonts fontsOf(FontFamily family, FontStyle style) {
        var files = fallbackFiles();
        var face = family.file(style);
        var families = new ArrayList<String>();
        if (TypstFaces.supply(face, library::read, files)) families.add(face.internalFamily());
        if (!families.contains(BundledFont.FAMILY)) families.add(BundledFont.FAMILY);
        return new SampleFonts(families, files, List.of());
    }

    private static LinkedHashMap<String, byte[]> fallbackFiles() {
        var files = new LinkedHashMap<String, byte[]>();
        files.put(BundledFont.FILE_NAME, BundledFont.data());
        return files;
    }
}
