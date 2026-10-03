/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.LetterCell;
import dev.chojo.ember.feature.generator.entity.LetterCellKind;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a letter template and the values of one member into a PDF/A-3b.
 *
 * <p>Member values never pass through markdown. A name with a {@code *}, an {@code _} or a {@code #} in
 * it would become emphasis or a heading there, and converting the body anew for every member would run
 * Pandoc once per document. Instead the body is converted to Typst once per state of the template, with
 * every placeholder left as a call to {@code ph("key")}, and the values travel in {@code data.json},
 * where Typst reads them as strings and prints them as they are. The conversion is kept by template
 * and version, so a new version is converted on its first use and the old one falls out of the cache.
 *
 * <p>The letterhead is drawn by {@code letter.typ} from the same data: its texts with their placeholders
 * filled in here, its pictures (from the media library, or the station logo) placed next to the
 * document as files. A picture that cannot be read leaves its cell empty rather than stopping the
 * document.
 */
@Singleton
public class LetterRenderer {
    private static final Logger log = LoggerFactory.getLogger(LetterRenderer.class);

    /** Wide enough to print a letterhead picture sharply, small enough to keep the PDF light. */
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
    private final StationRepository stations;
    private final Cache<BodyKey, PreparedBody> bodies =
            Caffeine.newBuilder().maximumSize(128).build();

    @Inject
    public LetterRenderer(
            KbPdfPictures pictures,
            MediaLibraryService mediaLibrary,
            StationLogoService logos,
            StationRepository stations) {
        this.pictures = pictures;
        this.mediaLibrary = mediaLibrary;
        this.logos = logos;
        this.stations = stations;
    }

    /**
     * What a letter is rendered from.
     *
     * @param stationId  the station whose pictures and language the letter uses
     * @param title      the title the PDF carries
     * @param letter     the letter
     * @param cacheKey   the template and version the body belongs to, or null for a draft that is not saved
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
            Map<String, String> values,
            Map<String, String> labels,
            boolean showLabels,
            LocalDate date) {}

    /**
     * Which state of a template a body belongs to.
     *
     * @param templateId the template
     * @param version    its version
     */
    public record BodyKey(int templateId, int version) {}

    /**
     * The body as Typst, with the pictures it shows.
     *
     * @param typst    the markup, placeholders written as {@code #ph("key");}
     * @param pictures the picture files by the name the markup uses
     */
    record PreparedBody(String typst, Map<String, byte[]> pictures) {}

    /**
     * Renders a letter.
     *
     * @param job what to render
     * @return the PDF/A-3b
     */
    public byte[] render(LetterJob job) {
        var body = body(job);
        var files = new HashMap<>(body.pictures());
        var data = new LinkedHashMap<String, Object>();
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
        data.put("header", cells(job, job.letter().letterhead().header(), "h", files));
        data.put("footer", cells(job, job.letter().letterhead().footer(), "f", files));
        data.put("values", job.values());
        data.put("labels", job.labels());
        data.put("showLabels", job.showLabels());
        String language =
                StationFormat.languageOf(stations.findById(job.stationId()).orElse(null));
        try {
            return TypstCompiler.compileTemplate(
                    data,
                    language + "/letter.typ",
                    null,
                    Map.of("body.typ", body.typst()),
                    files,
                    TypstCompiler.Output.PDF_A_3B);
        } catch (IOException e) {
            log.error("A letter of station {} could not be rendered", job.stationId(), e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }

    private PreparedBody body(LetterJob job) {
        var key = job.cacheKey();
        if (key == null) return prepare(job.stationId(), job.letter().bodyMarkdown());
        return bodies.get(key, ignored -> prepare(job.stationId(), job.letter().bodyMarkdown()));
    }

    /**
     * Converts a body to Typst with its placeholders as lookups.
     *
     * <p>Each placeholder is swapped for a plain word of letters and digits before the conversion, which
     * Pandoc passes through untouched wherever it stands, and swapped for the lookup afterwards.
     */
    PreparedBody prepare(int stationId, String markdown) {
        var placed = pictures.place(stationId, markdown);
        var keys = new ArrayList<String>();
        String marked = PlaceholderTokens.replace(placed.markdown(), key -> {
            keys.add(key);
            return marker(keys.size() - 1);
        });
        String typst;
        try {
            typst = PandocConverter.markdownToTypst(marked);
        } catch (IOException e) {
            log.error("A letter body of station {} could not be converted", stationId, e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
        for (int index = 0; index < keys.size(); index++) {
            typst = typst.replace(marker(index), "#ph(\"" + keys.get(index) + "\");");
        }
        return new PreparedBody(typst, placed.pictures());
    }

    private static String marker(int index) {
        return "EMBERPLACEHOLDER" + index + "MARK";
    }

    private List<Map<String, Object>> cells(
            LetterJob job, List<LetterCell> row, String side, Map<String, byte[]> files) {
        var out = new ArrayList<Map<String, Object>>();
        for (int index = 0; index < row.size(); index++) {
            out.add(cell(job, row.get(index), side + index, files));
        }
        return out;
    }

    private Map<String, Object> cell(LetterJob job, LetterCell cell, String name, Map<String, byte[]> files) {
        var drawn = new LinkedHashMap<String, Object>();
        drawn.put("align", cell.align().name().toLowerCase(Locale.ROOT));
        drawn.put("heightMm", cell.imageHeightMm());
        drawn.put("lines", List.of());
        drawn.put("kind", "empty");
        switch (cell.kind()) {
            case TEXT -> {
                drawn.put("kind", "text");
                drawn.put("lines", lines(job, cell.text()));
            }
            case IMAGE, LOGO ->
                picture(job.stationId(), cell).ifPresent(picture -> {
                    String extension = EXTENSIONS.get(picture.contentType());
                    if (extension == null) return;
                    String file = "letterhead-" + name + "." + extension;
                    files.put(file, picture.data());
                    drawn.put("kind", "image");
                    drawn.put("file", file);
                });
            case EMPTY -> {}
        }
        return drawn;
    }

    private Optional<MediaContent> picture(int stationId, LetterCell cell) {
        try {
            if (cell.kind() == LetterCellKind.LOGO) {
                return logos.original(stationId);
            }
            String hash = cell.mediaHash();
            if (hash == null) return Optional.empty();
            return mediaLibrary.readVariant(stationId, hash, PICTURE_WIDTH, "image/webp");
        } catch (RuntimeException e) {
            log.warn("A letterhead picture of station {} could not be read", stationId, e);
            return Optional.empty();
        }
    }

    private static List<String> lines(LetterJob job, @Nullable String text) {
        if (text == null || text.isEmpty()) return List.of();
        String filled = PlaceholderTokens.replace(text, key -> {
            String value = job.values().get(key);
            if (value != null) return value;
            return job.showLabels() ? "[" + job.labels().getOrDefault(key, key) + "]" : "";
        });
        return List.of(filled.replace("\r\n", "\n").split("\n", -1));
    }
}
