/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.content.entity.CellConfig;
import dev.chojo.ember.feature.content.entity.ContentCell;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.util.PandocConverter;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a letter template and the values of one member into a PDF/A-3b.
 *
 * <p>Member values never pass through markdown. A name with a {@code *}, an {@code _} or a {@code #} in
 * it would become emphasis or a heading there, and converting the texts anew for every member would run
 * Pandoc once per text and document. Instead every text block of the letter is converted to Typst once
 * per state of the template, with every placeholder left as a call to {@code ph("key")}, and the values
 * travel in {@code data.json}, where Typst reads them as strings and prints them as they are. The
 * conversion is kept by template and version, so a new version is converted on its first use and the old
 * one falls out of the cache.
 *
 * <p>The rows of the header, the footer and the body are laid out for the member by {@link LetterLayout}:
 * a block the member is not meant to see is left out. {@code letter.typ} draws the rows as grids from
 * {@code data.json}, the texts from their converted files and the pictures (from the media library, or
 * the station logo) placed next to the document as files. A picture that cannot be read is left out
 * rather than stopping the document. The template's language picks the {@code letter.typ} it is set in.
 *
 * <p>A signature block is drawn by {@code letter.typ} as an empty box on a line for each of its fields,
 * with its short text below, and {@link SignatureFields#replaceMarkers} turns each box into a real,
 * empty PDF signature field afterwards.
 */
@Singleton
public class LetterRenderer {
    private static final Logger log = LoggerFactory.getLogger(LetterRenderer.class);

    /** Wide enough to print a letter's picture sharply, small enough to keep the PDF light. */
    private static final int PICTURE_WIDTH = 1024;

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/gif", "gif",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final KbPdfPictures pictures;
    private final MediaLibraryService mediaLibrary;
    private final StationLogoService logos;
    private final Cache<BodyKey, PreparedTexts> converted =
            Caffeine.newBuilder().maximumSize(128).build();

    @Inject
    public LetterRenderer(KbPdfPictures pictures, MediaLibraryService mediaLibrary, StationLogoService logos) {
        this.pictures = pictures;
        this.mediaLibrary = mediaLibrary;
        this.logos = logos;
    }

    /**
     * What a letter is rendered from.
     *
     * @param stationId  the station whose pictures the letter uses
     * @param title      the title the PDF carries
     * @param letter     the letter
     * @param cacheKey   the template and version the texts belong to, or null for a draft that is not saved
     * @param view       what of the letter the member it is for sees
     * @param language   the language the letter is set in
     * @param values     the value of every placeholder that has one
     * @param labels     the words for every placeholder, shown in place of a value where labels are asked for
     * @param showLabels whether a placeholder without a value shows its label rather than a line to fill in
     * @param date       the day the document is dated
     */
    public record LetterJob(
            int stationId,
            String title,
            LetterContent letter,
            @Nullable BodyKey cacheKey,
            MemberView view,
            DocumentLanguage language,
            Map<String, String> values,
            Map<String, String> labels,
            boolean showLabels,
            LocalDate date) {}

    /**
     * Which state of a template the converted texts belong to.
     *
     * @param templateId the template
     * @param version    its version
     */
    public record BodyKey(int templateId, int version) {}

    /**
     * Every text of a letter as Typst, by its number, with the pictures they show.
     *
     * @param typst    the markup of each text, placeholders written as {@code #ph("key");}
     * @param pictures the picture files by the name the markup uses
     */
    record PreparedTexts(Map<Integer, String> typst, Map<String, byte[]> pictures) {}

    /**
     * One text as Typst, with the pictures it shows.
     *
     * @param typst    the markup
     * @param pictures the picture files by the name the markup uses
     */
    record Converted(String typst, Map<String, byte[]> pictures) {}

    /**
     * Renders a letter.
     *
     * @param job what to render
     * @return the PDF/A-3b
     */
    public byte[] render(LetterJob job) {
        var texts = texts(job);
        var files = new HashMap<>(texts.pictures());
        var resources = new HashMap<String, String>();
        var layout = new LetterLayout(job.view(), new LetterLayout.Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        return Map.of("kind", "text", "file", textFile(index));
                    }

                    @Override
                    public @Nullable Map<String, Object> image(ContentCell cell) {
                        return picture(job.stationId(), cell, files);
                    }

                    @Override
                    public Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
                        return Map.of("kind", "signature", "file", textFile(index), "fields", fields);
                    }

                    private String textFile(int index) {
                        String file = "block-" + index + ".typ";
                        resources.put(file, texts.typst().getOrDefault(index, ""));
                        return file;
                    }
                })
                .letter(job.letter());
        String marker = SignatureFields.newMarker();
        var data = new LinkedHashMap<String, Object>(layout);
        data.put("signatureMarker", marker);
        data.put("title", job.title());
        data.put(
                "date",
                Map.of(
                        "year",
                        job.date().getYear(),
                        "month",
                        job.date().getMonthValue(),
                        "day",
                        job.date().getDayOfMonth()));
        data.put("page", job.letter().page());
        data.put("values", job.values());
        data.put("labels", job.labels());
        data.put("showLabels", job.showLabels());
        try {
            return SignatureFields.replaceMarkers(
                    TypstCompiler.compileTemplate(
                            data,
                            job.language().code() + "/letter.typ",
                            null,
                            resources,
                            files,
                            TypstCompiler.Output.PDF_A_3B),
                    marker);
        } catch (IOException e) {
            log.error("A letter of station {} could not be rendered", job.stationId(), e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }

    private PreparedTexts texts(LetterJob job) {
        var key = job.cacheKey();
        if (key == null) return prepare(job.stationId(), job.letter());
        return converted.get(key, ignored -> prepare(job.stationId(), job.letter()));
    }

    /**
     * Converts every text of a letter, whoever sees it, numbered the way {@link LetterLayout} numbers them.
     */
    PreparedTexts prepare(int stationId, LetterContent letter) {
        var typst = new HashMap<Integer, String>();
        var files = new HashMap<String, byte[]>();
        new LetterLayout(MemberView.EVERYBODY, new LetterLayout.Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        return prepared(index, cell);
                    }

                    @Override
                    public Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
                        return prepared(index, cell);
                    }

                    private Map<String, Object> prepared(int index, ContentCell cell) {
                        var text = convert(stationId, cell.content(), "b" + index + "-");
                        typst.put(index, text.typst());
                        files.putAll(text.pictures());
                        return Map.of();
                    }
                })
                .letter(letter);
        return new PreparedTexts(typst, files);
    }

    /**
     * Converts one text to Typst with its placeholders as lookups.
     *
     * <p>Each placeholder is swapped for a plain word of letters and digits before the conversion, which
     * Pandoc passes through untouched wherever it stands, and swapped for the lookup afterwards.
     */
    Converted convert(int stationId, String markdown, String picturePrefix) {
        var placed = pictures.place(stationId, markdown, picturePrefix);
        var keys = new ArrayList<String>();
        String marked = PlaceholderTokens.replace(placed.markdown(), key -> {
            keys.add(key);
            return marker(keys.size() - 1);
        });
        String typst;
        try {
            typst = PandocConverter.markdownToTypst(marked);
        } catch (IOException e) {
            log.error("A text of a letter of station {} could not be converted", stationId, e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
        for (int index = 0; index < keys.size(); index++) {
            typst = typst.replace(marker(index), "#ph(\"" + keys.get(index) + "\");");
        }
        return new Converted(typst, placed.pictures());
    }

    private static String marker(int index) {
        return "EMBERPLACEHOLDER" + index + "MARK";
    }

    /**
     * Places a picture block's picture next to the document, or nothing where it cannot be read.
     */
    private @Nullable Map<String, Object> picture(int stationId, ContentCell cell, Map<String, byte[]> files) {
        var picture = read(stationId, cell.content()).orElse(null);
        if (picture == null) return null;
        String extension = EXTENSIONS.get(picture.contentType());
        if (extension == null) return null;
        String file = "image-" + files.size() + "." + extension;
        files.put(file, picture.data());
        var drawn = new LinkedHashMap<String, Object>();
        drawn.put("kind", "image");
        drawn.put("file", file);
        Integer maxHeight = cell.config() instanceof CellConfig.ImageConfig image ? image.maxHeight() : null;
        if (maxHeight != null && maxHeight > 0) drawn.put("maxHeightMm", maxHeight * LetterLayout.MM_PER_PIXEL);
        return drawn;
    }

    private Optional<MediaContent> read(int stationId, String content) {
        try {
            if (ContentCell.STATION_LOGO.equals(content)) return logos.original(stationId);
            if (content.isBlank()) return Optional.empty();
            return mediaLibrary.readVariant(stationId, content, PICTURE_WIDTH, "image/webp");
        } catch (RuntimeException e) {
            log.warn("A picture of a letter of station {} could not be read", stationId, e);
            return Optional.empty();
        }
    }
}
