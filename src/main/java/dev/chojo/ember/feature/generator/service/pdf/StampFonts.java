/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service.pdf;

import dev.chojo.ember.feature.generator.entity.FontStyle;
import dev.chojo.ember.feature.generator.service.font.BundledFont;
import dev.chojo.ember.feature.generator.service.font.DefaultFont;
import dev.chojo.ember.feature.generator.service.font.FontFiles;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The fonts text is stamped onto a PDF in.
 *
 * <p>Every chain ends in Liberation Sans ({@link BundledFont}). A text field may name a family of
 * uploaded fonts to draw in first; that file is embedded as a subset where its licence allows, and in
 * full where it allows embedding but not subsetting. Embedding is what keeps a filled PDF/A a PDF/A.
 * A field that names none, or a family whose file cannot be had, draws in the {@link DefaultFont} first
 * where it has the style asked for. Each file is loaded into a document once, however many fields draw
 * in it.
 */
@Singleton
public class StampFonts {
    private final byte[] liberationSans = BundledFont.data();
    private final DefaultFont defaultFont;

    @Inject
    public StampFonts(DefaultFont defaultFont) {
        this.defaultFont = defaultFont;
    }

    /**
     * Where the file of a family and style comes from.
     */
    @FunctionalInterface
    public interface FieldFonts {
        /** Fields that only ever print in the default font. */
        FieldFonts NONE = (family, style) -> Optional.empty();

        /**
         * @param family the family a field names
         * @param style  the style it asks for
         * @return the TrueType file to draw in, or empty where there is none to have
         */
        Optional<byte[]> file(String family, FontStyle style);
    }

    /**
     * The fonts for the text on one document, loaded into it as they are first asked for.
     */
    public final class Loaded {
        private final PDDocument document;
        private final FieldFonts source;
        private final Map<String, Optional<PDFont>> custom = new HashMap<>();
        private final Map<FontStyle, Optional<PDFont>> standard = new EnumMap<>(FontStyle.class);
        private @Nullable PDFont fallback;

        private Loaded(PDDocument document, FieldFonts source) {
            this.document = document;
            this.source = source;
        }

        /**
         * The chain a text is drawn in.
         *
         * @param family the family the text names, or null for the default font
         * @param style  the style it asks for
         * @return the chain, Liberation Sans last
         * @throws IOException where Liberation Sans cannot be embedded
         */
        public FontChain chain(@Nullable String family, FontStyle style) throws IOException {
            var fonts = new ArrayList<PDFont>();
            var chosen = family == null ? Optional.<PDFont>empty() : custom(family, style);
            chosen.or(() -> standard(style)).ifPresent(fonts::add);
            fonts.add(fallback());
            return new FontChain(fonts);
        }

        private Optional<PDFont> standard(FontStyle style) {
            return standard.computeIfAbsent(
                    style, ignored -> defaultFont.pdfFile(style).flatMap(this::load));
        }

        private Optional<PDFont> custom(String family, FontStyle style) {
            String key = family.toLowerCase(Locale.ROOT) + "/" + style;
            return custom.computeIfAbsent(
                    key, ignored -> source.file(family, style).flatMap(this::load));
        }

        /**
         * Embeds an uploaded file or one of the default font. One that PDFBox cannot embed after all is
         * left out of the chain, so its text falls back rather than stopping the document.
         */
        private Optional<PDFont> load(byte[] file) {
            try {
                return Optional.of(
                        PDType0Font.load(document, new ByteArrayInputStream(file), FontFiles.subsettable(file)));
            } catch (IOException | RuntimeException unusable) {
                return Optional.empty();
            }
        }

        private PDFont fallback() throws IOException {
            var loaded = fallback;
            if (loaded == null) {
                loaded = liberationSans(document);
                fallback = loaded;
            }
            return loaded;
        }
    }

    /**
     * The fonts for the text on one document.
     *
     * @param document the document the text is drawn into
     * @param source   where the files of uploaded families come from
     * @return the fonts, loaded as they are asked for
     */
    public Loaded load(PDDocument document, FieldFonts source) {
        return new Loaded(document, source);
    }

    /**
     * Liberation Sans, loaded into a document as a subset.
     *
     * @param document the document
     * @return the font
     * @throws IOException where it cannot be embedded
     */
    public PDType0Font liberationSans(PDDocument document) throws IOException {
        return PDType0Font.load(document, new ByteArrayInputStream(liberationSans), true);
    }
}
