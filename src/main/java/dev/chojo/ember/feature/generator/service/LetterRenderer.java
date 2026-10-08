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
import dev.chojo.ember.feature.generator.entity.FillInField;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.MemberView;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.generator.service.pdf.FillInFields;
import dev.chojo.ember.feature.generator.service.pdf.SignatureFields;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.knowledgebase.service.KbPdfPictures;
import dev.chojo.ember.feature.media.entity.MediaContent;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.station.service.StationLogoService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.PandocConverter;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.TypstCompiler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns a letter template and the values of one member into a PDF/A-3b.
 *
 * <p>Member values never pass through markdown. A name with a {@code *}, an {@code _} or a {@code #} in
 * it would become emphasis or a heading there, and converting the texts anew for every member would run
 * Pandoc once per text and document. Instead every text block of the letter is converted to Typst with
 * every placeholder left as a call to {@code ph("key")}, and the values travel in {@code data.json}, where
 * Typst reads them as strings and prints them as they are. The conversion is kept by the SHA-256 of the
 * markdown Pandoc is handed, so a text is converted once whatever template, version or draft it stands in,
 * and an unchanged block of a new version or a draft is not converted again. The pictures a text shows
 * are read for every letter and never kept, so the cache holds text only and is bounded by its size.
 *
 * <p>The rows of the header, the footer and the body are laid out for the member by {@link LetterLayout}:
 * a block the member is not meant to see is left out. {@code letter.typ} draws the rows as grids from
 * {@code data.json}, the texts from their converted files and the pictures (from the media library, or
 * the station logo) placed next to the document as files. A picture that cannot be read is left out
 * rather than stopping the document. The template's language travels in {@code data.json} and sets the
 * language of the text, and its page the fonts of the body, the header and the footer ({@link LetterFonts}), whose files
 * are kept on the instance's disk and reach Typst as a directory holding just them
 * ({@link dev.chojo.ember.feature.generator.service.font.FontFileCache}); the default font of the instance is found in its own
 * directory. Words a text sets in a family of their own become calls to {@code font("Family")} in the
 * converted text, which {@code letter.typ} answers from the families the owner reaches when the letter is
 * printed, so the converted texts stay right whatever the owner reaches.
 *
 * <p>A signature block is drawn by {@code letter.typ} as an empty box on a line for each of its fields,
 * with its short text below, and {@link SignatureFields#replaceMarkers} turns each box into a real,
 * empty PDF signature field afterwards. A block to fill in at signing is drawn as its label over a boxed
 * line for each of its fields, and {@link FillInFields#replaceMarkers} turns each box into an empty text
 * field the same way.
 */
@Singleton
public class LetterRenderer {
    private static final Logger log = LoggerFactory.getLogger(LetterRenderer.class);

    /** Wide enough to print a letter's picture sharply, small enough to keep the PDF light. */
    private static final int PICTURE_WIDTH = 1024;

    /** How many characters of converted text are kept, keys and markup together. */
    private static final long CACHED_CHARACTERS = 8_000_000;

    private final KbPdfPictures pictures;
    private final MediaLibraryService mediaLibrary;
    private final StationLogoService logos;
    private final FontLibrary fonts;
    private final OwnerStores stores;
    private final Cache<String, String> converted = Caffeine.newBuilder()
            .maximumWeight(CACHED_CHARACTERS)
            .weigher((String markdownSha, String typst) -> markdownSha.length() + typst.length())
            .build();

    @Inject
    public LetterRenderer(
            KbPdfPictures pictures,
            MediaLibraryService mediaLibrary,
            StationLogoService logos,
            FontLibrary fonts,
            OwnerStores stores) {
        this.pictures = pictures;
        this.mediaLibrary = mediaLibrary;
        this.logos = logos;
        this.fonts = fonts;
        this.stores = stores;
    }

    /**
     * What a letter is rendered from.
     *
     * @param owner      the station or the association that keeps the letter, whose media library its
     *                   pictures come from and whose fonts it reaches
     * @param stationId  the station the letter is drawn at, whose logo it prints, or null for a look at an
     *                   association's letter without a member, which prints the logo of its home station
     * @param title      the title the PDF carries
     * @param letter     the letter
     * @param view       what of the letter the member it is for sees
     * @param language   the language the letter is set in
     * @param values     the value of every placeholder that has one
     * @param labels     the words for every placeholder, shown in place of a value where labels are asked for
     * @param showLabels whether a placeholder without a value shows its label rather than a line to fill in
     * @param date       the day the document is dated
     */
    public record LetterJob(
            Owner owner,
            @Nullable Integer stationId,
            String title,
            LetterContent letter,
            MemberView view,
            DocumentLanguage language,
            Map<String, String> values,
            Map<String, String> labels,
            boolean showLabels,
            LocalDate date) {}

    /**
     * One text as Typst, with the pictures it shows.
     *
     * @param typst    the markup
     * @param pictures the picture files by the name the markup uses
     */
    record Converted(String typst, Map<String, byte[]> pictures) {}

    /**
     * What every member's letter of a template is printed with, read once for as many letters as are
     * drawn from it.
     *
     * @param library         the station whose media library holds the owner's pictures
     * @param fonts           the fonts the letter is set in
     * @param fontDirectories the directories Typst searches for them, the letter's own fonts first
     */
    public record Setting(int library, LetterFonts fonts, List<Path> fontDirectories) {}

    /**
     * Reads what the letters of a template are printed with.
     *
     * @param owner  the station or the association that keeps the letter
     * @param letter the letter
     * @return the setting
     */
    public Setting setting(Owner owner, LetterContent letter) {
        int library = stores.libraryOf(owner);
        var typeset = LetterFonts.of(fonts.reachable(owner), letter, fonts::keptFile, fonts.defaultFont());
        var directories = new ArrayList<Path>();
        directories.add(fonts.directoryOf(typeset.files()));
        directories.addAll(typeset.directories());
        return new Setting(library, typeset, List.copyOf(directories));
    }

    /**
     * Renders a letter.
     *
     * @param job what to render
     * @return the PDF/A-3b
     */
    public byte[] render(LetterJob job) {
        return render(job, setting(job.owner(), job.letter()));
    }

    /**
     * Renders a letter with what was read for it before.
     *
     * @param job     what to render
     * @param setting what the letter's template is printed with
     * @return the PDF/A-3b
     */
    public byte[] render(LetterJob job, Setting setting) {
        int library = setting.library();
        int logoStation = Objects.requireNonNullElse(job.stationId(), library);
        var files = new HashMap<String, byte[]>();
        var resources = new HashMap<String, String>();
        var fillIns = new HashMap<String, FillInField>();
        var layout = new LetterLayout(job.view(), new LetterLayout.Blocks() {
                    @Override
                    public Map<String, Object> text(int index, ContentCell cell) {
                        return Map.of("kind", "text", "file", textFile(index, cell));
                    }

                    @Override
                    public @Nullable Map<String, Object> image(ContentCell cell) {
                        return picture(new PictureSource(library, logoStation), cell, files);
                    }

                    @Override
                    public Map<String, Object> signature(int index, ContentCell cell, List<String> fields) {
                        return Map.of("kind", "signature", "file", textFile(index, cell), "fields", fields);
                    }

                    @Override
                    public Map<String, Object> fillIn(ContentCell cell, List<FillInField> fields) {
                        fields.forEach(field -> fillIns.put(field.name(), field));
                        return Map.of(
                                "kind",
                                "fillIn",
                                "label",
                                fields.getFirst().label(),
                                "required",
                                fields.getFirst().required(),
                                "fields",
                                fields.stream().map(FillInField::name).toList());
                    }

                    private String textFile(int index, ContentCell cell) {
                        String file = "block-" + index + ".typ";
                        var text = convert(library, cell.content(), "b" + index + "-");
                        resources.put(file, text.typst());
                        files.putAll(text.pictures());
                        return file;
                    }
                })
                .letter(job.letter());
        String marker = SignatureFields.newMarker();
        var data = new LinkedHashMap<String, Object>(layout);
        data.put("signatureMarker", marker);
        String fillInMarker = FillInFields.newMarker();
        data.put("fillInMarker", fillInMarker);
        data.put("title", job.title());
        data.put("language", job.language().code());
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
        var typeset = setting.fonts();
        data.put("fonts", typeset.families());
        data.put("spanFonts", typeset.spans());
        data.put("uprightFamilies", typeset.uprightFamilies());
        try {
            byte[] drawn = SignatureFields.replaceMarkers(
                    TypstCompiler.compileTemplate(
                            data,
                            "letter.typ",
                            null,
                            resources,
                            files,
                            TypstCompiler.Output.PDF_A_3B,
                            Map.of(),
                            setting.fontDirectories()),
                    marker);
            return FillInFields.replaceMarkers(drawn, fillInMarker, fillIns);
        } catch (IOException e) {
            log.error("A letter of {} could not be rendered", job.owner(), e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }

    /**
     * Converts one text to Typst with its placeholders as lookups, reading the pictures it shows anew.
     *
     * <p>Each placeholder is swapped for a plain word of letters and digits before the conversion, which
     * Pandoc passes through untouched wherever it stands, and swapped for the lookup afterwards. The
     * markdown Pandoc is handed names every picture by the file it was placed as, or by its address where
     * it could not be read, so a text whose pictures come and go is converted for each state.
     */
    Converted convert(int stationId, String markdown, String picturePrefix) {
        var placed = pictures.place(stationId, markdown, picturePrefix);
        var keys = new ArrayList<String>();
        String marked = PlaceholderTokens.replace(placed.markdown(), key -> {
            keys.add(key);
            return marker(keys.size() - 1);
        });
        String typst = converted.get(Sha256.hex(marked), ignored -> toTypst(stationId, marked));
        for (int index = 0; index < keys.size(); index++) {
            typst = typst.replace(marker(index), "#ph(\"" + keys.get(index) + "\");");
        }
        return new Converted(typst, placed.pictures());
    }

    /** @return how many converted texts are kept, which says how often Pandoc ran */
    long convertedTexts() {
        converted.cleanUp();
        return converted.estimatedSize();
    }

    private static String toTypst(int stationId, String marked) {
        try {
            return PandocConverter.markdownToTypstWithFonts(marked);
        } catch (IOException e) {
            log.error("A text of a letter of station {} could not be converted", stationId, e);
            throw DocumentRefusal.DOCUMENT_RENDER_FAILED.raise();
        }
    }

    private static String marker(int index) {
        return "EMBERPLACEHOLDER" + index + "MARK";
    }

    /**
     * Where a letter's pictures come from: its owner's media library, and the logo of the station it is
     * drawn at.
     *
     * @param library     the station whose media library holds the owner's pictures
     * @param logoStation the station whose logo the letter prints
     */
    private record PictureSource(int library, int logoStation) {}

    /**
     * Places a picture block's picture next to the document, or nothing where it cannot be read.
     */
    private @Nullable Map<String, Object> picture(PictureSource source, ContentCell cell, Map<String, byte[]> files) {
        var picture = read(source, cell.content())
                .flatMap(content -> KbPdfPictures.printable(content.data(), content.contentType()))
                .orElse(null);
        if (picture == null) return null;
        String file = "image-" + files.size() + "." + picture.extension();
        files.put(file, picture.data());
        var drawn = new LinkedHashMap<String, Object>();
        drawn.put("kind", "image");
        drawn.put("file", file);
        Integer maxHeight = cell.config() instanceof CellConfig.ImageConfig image ? image.maxHeight() : null;
        if (maxHeight != null && maxHeight > 0) drawn.put("maxHeightMm", maxHeight * LetterLayout.MM_PER_PIXEL);
        return drawn;
    }

    private Optional<MediaContent> read(PictureSource source, String content) {
        try {
            if (ContentCell.STATION_LOGO.equals(content)) return logos.original(source.logoStation());
            if (content.isBlank()) return Optional.empty();
            return mediaLibrary.readVariant(source.library(), content, PICTURE_WIDTH, "image/webp");
        } catch (RuntimeException e) {
            log.warn("A picture of a letter from the library of station {} could not be read", source.library(), e);
            return Optional.empty();
        }
    }
}
