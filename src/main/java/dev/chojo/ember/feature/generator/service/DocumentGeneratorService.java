/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.PlaceholderTokens;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.generator.entity.ResolvedValues;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns a template into the PDF of one member, without filing it.
 *
 * <p>It works in two steps so a caller can refuse before anything is drawn: {@link #prepare} reads the
 * values of every placeholder the template names and reports the ones that are missing, and
 * {@link #render} draws the document from them. Filing the result is {@link DocumentGenerationService}'s.
 *
 * <p>A legal template is checked again here, not only when it is saved: a document that names a member
 * by the name they are called by is refused, whatever state the template got into.
 */
@Singleton
public class DocumentGeneratorService {
    /** Characters a file name may not carry on any system the file is downloaded to. */
    private static final Pattern UNSAFE_IN_FILE_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private static final Pattern SPACES = Pattern.compile("\\s+");

    /** The longest file name, before its extension. */
    private static final int MAX_FILE_NAME = 150;

    private final DocumentTemplateService templates;
    private final PlaceholderResolver resolver;
    private final PlaceholderCatalogue catalogue;
    private final LetterRenderer renderer;
    private final StationRepository stations;
    private final Clock clock;

    @Inject
    public DocumentGeneratorService(
            DocumentTemplateService templates,
            PlaceholderResolver resolver,
            PlaceholderCatalogue catalogue,
            LetterRenderer renderer,
            StationRepository stations) {
        this(templates, resolver, catalogue, renderer, stations, Clock.systemUTC());
    }

    /**
     * @param clock what the day a document is dated is read from, which a test moves
     */
    public DocumentGeneratorService(
            DocumentTemplateService templates,
            PlaceholderResolver resolver,
            PlaceholderCatalogue catalogue,
            LetterRenderer renderer,
            StationRepository stations,
            Clock clock) {
        this.templates = templates;
        this.resolver = resolver;
        this.catalogue = catalogue;
        this.renderer = renderer;
        this.stations = stations;
        this.clock = clock;
    }

    /**
     * What a document is drawn from: the state of the template and the values of one member.
     *
     * @param source   the template
     * @param resolved the values and what is missing
     */
    public record Prepared(Source source, ResolvedValues resolved) {}

    /**
     * A template as the generator reads it, saved or still a draft in the editor.
     *
     * @param stationId       the station that owns it
     * @param name            what it is called, which a document without a title is called after
     * @param titlePattern    the title, with placeholders
     * @param fileNamePattern the file name, with placeholders
     * @param letter          the letter
     * @param pronouns        the field the pronouns follow, or null
     * @param legal           whether it makes a legal document
     * @param cacheKey        the template and version its body is kept under, or null for a draft
     */
    public record Source(
            int stationId,
            String name,
            String titlePattern,
            String fileNamePattern,
            LetterContent letter,
            @Nullable PronounSource pronouns,
            boolean legal,
            LetterRenderer.@Nullable BodyKey cacheKey) {

        /** The keys the template names anywhere. */
        public Set<String> keys() {
            return PlaceholderCatalogue.keysOf(titlePattern, fileNamePattern, letter);
        }
    }

    /**
     * A drawn document.
     *
     * @param pdf      the PDF/A-3b
     * @param title    the title it is filed under
     * @param fileName the file name it is filed under, ending in {@code .pdf}
     * @param resolved the values it was drawn from
     */
    public record Rendered(byte[] pdf, String title, String fileName, ResolvedValues resolved) {}

    /**
     * A document drawn for a look before it is generated.
     *
     * @param pdfBase64 the PDF, Base64 encoded
     * @param missing   the placeholders without a value
     */
    public record PreviewResponse(String pdfBase64, List<MissingValue> missing) {}

    /**
     * @param template a saved template
     * @return the template as the generator reads it
     */
    public Source sourceOf(DocumentTemplate template) {
        return new Source(
                template.stationId(),
                template.name(),
                template.titlePattern(),
                template.fileNamePattern(),
                templates.letterOf(template),
                template.pronounSource(),
                template.legal(),
                new LetterRenderer.BodyKey(template.id(), template.version()));
    }

    /**
     * @param stationId the station the editor works for
     * @param draft     a template still in the editor
     * @return the template as the generator reads it
     */
    public static Source sourceOf(int stationId, DocumentTemplateDraft draft) {
        return new Source(
                stationId,
                draft.name(),
                draft.titlePattern(),
                draft.fileNamePattern(),
                draft.letter(),
                draft.pronounSource(),
                draft.legal(),
                null);
    }

    /**
     * Reads the values of one member for a template.
     *
     * @param source   the template
     * @param memberId the member the document is about
     * @param context  who generates it and for which appointment
     * @return the values and what is missing
     */
    public Prepared prepare(Source source, int memberId, GenerationContext context) {
        var keys = source.keys();
        if (source.legal()) PlaceholderCatalogue.requireOfficial(keys);
        return new Prepared(source, resolver.resolve(source.stationId(), memberId, keys, source.pronouns(), context));
    }

    /**
     * The placeholders without a value, in the words of the station.
     *
     * @param prepared what a document is about to be drawn from
     * @return the missing values in template order
     */
    public List<MissingValue> missing(Prepared prepared) {
        var labels = catalogue.byKey(prepared.source().stationId());
        return prepared.resolved().missing().stream()
                .map(key -> new MissingValue(key, labelOf(labels, key)))
                .toList();
    }

    /**
     * Draws the document of one member. A placeholder without a value becomes a line to fill in by hand.
     *
     * @param prepared the template and the member's values
     * @return the document
     */
    public Rendered render(Prepared prepared) {
        var source = prepared.source();
        var values = prepared.resolved().values();
        String title = title(source, values);
        String fileName = fileName(source, values);
        var labels = labels(source.stationId(), prepared.resolved().missing());
        byte[] pdf = renderer.render(new LetterRenderer.LetterJob(
                source.stationId(), title, source.letter(), source.cacheKey(), values, labels, false, today(source)));
        return new Rendered(pdf, title, fileName, prepared.resolved());
    }

    /**
     * Draws a template for a look in the editor, for a member or, without one, with the placeholders
     * shown by their labels.
     *
     * @param source   the template, saved or a draft
     * @param memberId the member to draw it for, or null for no member
     * @param context  who looks at it
     * @return the document and what is missing
     */
    public PreviewResponse preview(Source source, @Nullable Integer memberId, GenerationContext context) {
        if (memberId != null) {
            var prepared = prepare(source, memberId, context);
            return new PreviewResponse(encode(render(prepared).pdf()), missing(prepared));
        }
        var values = resolver.withoutMember(source.stationId());
        var labels = new LinkedHashMap<String, String>();
        catalogue
                .forStation(source.stationId())
                .forEach(placeholder -> labels.put(placeholder.key(), placeholder.label()));
        byte[] pdf = renderer.render(new LetterRenderer.LetterJob(
                source.stationId(),
                title(source, values),
                source.letter(),
                source.cacheKey(),
                values,
                labels,
                true,
                today(source)));
        return new PreviewResponse(encode(pdf), List.of());
    }

    private static String encode(byte[] pdf) {
        return Base64.getEncoder().encodeToString(pdf);
    }

    private Map<String, String> labels(int stationId, List<String> keys) {
        var known = catalogue.byKey(stationId);
        var labels = new LinkedHashMap<String, String>();
        keys.forEach(key -> labels.put(key, labelOf(known, key)));
        return labels;
    }

    private static String labelOf(Map<String, Placeholder> known, String key) {
        var placeholder = known.get(key);
        return placeholder == null ? key : placeholder.label();
    }

    private static String title(Source source, Map<String, String> values) {
        String title = SPACES.matcher(PlaceholderTokens.fill(source.titlePattern(), values))
                .replaceAll(" ")
                .strip();
        return title.isEmpty() ? source.name() : title;
    }

    /**
     * The file name the document is filed under: the pattern filled in, stripped of what no file system
     * takes, and ending in {@code .pdf}.
     */
    static String fileName(Source source, Map<String, String> values) {
        String filled = PlaceholderTokens.fill(source.fileNamePattern(), values);
        String safe = SPACES.matcher(UNSAFE_IN_FILE_NAME.matcher(filled).replaceAll("_"))
                .replaceAll(" ")
                .strip();
        if (safe.toLowerCase(Locale.ROOT).endsWith(".pdf"))
            safe = safe.substring(0, safe.length() - 4).strip();
        if (safe.isEmpty())
            safe = UNSAFE_IN_FILE_NAME.matcher(source.name()).replaceAll("_").strip();
        if (safe.length() > MAX_FILE_NAME)
            safe = safe.substring(0, MAX_FILE_NAME).strip();
        return safe + ".pdf";
    }

    private LocalDate today(Source source) {
        var zone =
                StationFormat.timezoneOf(stations.findById(source.stationId()).orElse(null));
        return LocalDate.now(clock.withZone(zone));
    }
}
