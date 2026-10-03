/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.service.font.FontLibrary;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Turns what the editor sends into a template a station may keep, refusing what it may not.
 *
 * <p>Every check names its own refusal, so the editor can say which part of the template is wrong.
 * Placeholders are checked against the catalogue of the station by {@link PlaceholderCatalogue}: a
 * placeholder the catalogue does not know would only ever print as a gap. A letter's rows are checked
 * by {@link LetterChecks}, a PDF template's fields by {@link PdfLayoutChecks}. A font family the template
 * names has to be one the station reaches ({@link FontLibrary}).
 *
 * <p>A template for appointments is a legal one whatever the request says, since its copies are handed
 * to participants to sign. It stays one for as long as an appointment or an appointment template
 * requires it.
 */
@Singleton
public class TemplateChecks {
    /** The longest name a template may have. */
    static final int MAX_NAME = 120;

    /** The longest title or file name pattern. */
    static final int MAX_PATTERN = 240;

    /** The longest text a field on a PDF may hold. */
    static final int MAX_CELL_TEXT = 600;

    /** The most tags a template files a document with. */
    static final int MAX_TAGS = 20;

    /** The longest tag. */
    static final int MAX_TAG = 60;

    /** The wait between two self service documents a new template starts with. */
    static final int DEFAULT_COOLDOWN_DAYS = 30;

    private final DocumentTemplateRepository templates;
    private final PdfTemplateRepository pdfTemplates;
    private final LetterChecks letters;
    private final StationRepository stations;
    private final PlaceholderCatalogue catalogue;
    private final FontLibrary fonts;

    @Inject
    public TemplateChecks(
            DocumentTemplateRepository templates,
            PdfTemplateRepository pdfTemplates,
            LetterChecks letters,
            StationRepository stations,
            PlaceholderCatalogue catalogue,
            FontLibrary fonts) {
        this.templates = templates;
        this.pdfTemplates = pdfTemplates;
        this.letters = letters;
        this.stations = stations;
        this.catalogue = catalogue;
        this.fonts = fonts;
    }

    /**
     * Checks a template and fills in what was left out.
     *
     * @param stationId the station that keeps the template
     * @param request   what the editor sent
     * @param existing  the template being changed, whose own name is not taken by itself and whose kind
     *                  stays, or null for a new one
     * @return the template as it is to be written
     */
    public DocumentTemplateDraft draft(
            int stationId, DocumentTemplateRequest request, @Nullable DocumentTemplate existing) {
        Integer exceptId = existing == null ? null : existing.id();
        if (existing != null && !request.forAppointments() && templates.requiredByAppointments(existing.id())) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_REQUIRED_BY_APPOINTMENTS.raise();
        }
        return build(stationId, request, requireName(stationId, request.name(), exceptId), existing);
    }

    /**
     * Checks a template the editor wants to look at before it is saved. Its name is not held to
     * anything, since a draft may still share it with the template it is a new version of.
     *
     * @param stationId the station that keeps the template
     * @param request   what the editor holds
     * @param saved     the saved template the draft is a new version of, whose PDF a PDF template fills,
     *                  or null for one not saved yet
     * @return the template as it would be written
     */
    public DocumentTemplateDraft preview(
            int stationId, DocumentTemplateRequest request, @Nullable DocumentTemplate saved) {
        String name = request.name();
        return build(stationId, request, name == null || name.isBlank() ? "Vorschau" : name.strip(), saved);
    }

    private DocumentTemplateDraft build(
            int stationId, DocumentTemplateRequest request, String name, @Nullable DocumentTemplate existing) {
        String titlePattern = pattern(request.titlePattern(), name + " {{today}}");
        String fileNamePattern = pattern(request.fileNamePattern(), name + " {{member.lastName}} {{today}}");
        var content = content(stationId, request, existing);
        int cooldown = Objects.requireNonNullElse(request.cooldownDays(), DEFAULT_COOLDOWN_DAYS);
        if (cooldown < 0) throw DocumentRefusal.DOCUMENT_TEMPLATE_COOLDOWN_NEGATIVE.raise();
        var audience = Objects.requireNonNullElse(request.audience(), RestrictionAudience.empty());
        boolean legal = request.legal() || request.forAppointments();
        var draft = new DocumentTemplateDraft(
                name,
                titlePattern,
                fileNamePattern,
                tags(request.tags()),
                request.hidden(),
                Objects.requireNonNullElse(request.keepOnArchive(), legal),
                legal,
                request.forAppointments(),
                request.selfService(),
                cooldown,
                audience.mode(),
                language(stationId, request.language()),
                content);
        catalogue.requireKnown(stationId, draft);
        fonts.requireReachable(new Owner.Station(stationId), content);
        return draft;
    }

    /** The language asked for, or the station's where none was. */
    private DocumentLanguage language(int stationId, @Nullable DocumentLanguage asked) {
        if (asked != null) return asked;
        return DocumentLanguage.of(
                StationFormat.languageOf(stations.findById(stationId).orElse(null)));
    }

    /**
     * What the template is made of, by the kind it has or, for a new one, the kind it asks for.
     */
    private TemplateContent content(
            int stationId, DocumentTemplateRequest request, @Nullable DocumentTemplate existing) {
        var kind = existing != null
                ? existing.kind()
                : Objects.requireNonNullElse(request.kind(), DocumentTemplateKind.LETTER);
        return switch (kind) {
            case LETTER -> letters.letter(stationId, request);
            case PDF -> {
                var original = existing == null
                        ? null
                        : pdfTemplates.findCurrentOriginal(existing.id()).orElse(null);
                yield PdfLayoutChecks.check(original, request.fields(), request.formBindings(), MAX_CELL_TEXT);
            }
        };
    }

    private String requireName(int stationId, @Nullable String raw, @Nullable Integer exceptId) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) throw DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING.raise();
        requireLength(name, MAX_NAME);
        requireNameFree(stationId, name, exceptId);
        return name;
    }

    /**
     * Refuses a name another template in use at the station already carries.
     *
     * @param stationId the station
     * @param name      the name
     * @param exceptId  the template that carries it itself, or null
     */
    public void requireNameFree(int stationId, String name, @Nullable Integer exceptId) {
        if (templates.nameTaken(stationId, name, exceptId)) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_NAME_TAKEN.raise(RefusalDetail.text(name));
        }
    }

    private static String pattern(@Nullable String raw, String fallback) {
        String pattern = raw == null || raw.isBlank() ? fallback : raw.strip();
        requireLength(pattern, MAX_PATTERN);
        return pattern;
    }

    private static List<String> tags(@Nullable List<String> raw) {
        if (raw == null) return List.of();
        var tags = raw.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
        if (tags.size() > MAX_TAGS) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(MAX_TAGS));
        }
        tags.forEach(tag -> requireLength(tag, MAX_TAG));
        return tags;
    }

    private static void requireLength(String text, int max) {
        if (text.length() > max) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(max));
        }
    }
}
