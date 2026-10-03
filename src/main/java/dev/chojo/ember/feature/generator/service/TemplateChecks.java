/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterCell;
import dev.chojo.ember.feature.generator.entity.LetterCellAlign;
import dev.chojo.ember.feature.generator.entity.LetterCellKind;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Letterhead;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
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
 * placeholder the catalogue does not know would only ever print as a gap.
 */
@Singleton
public class TemplateChecks {
    /** The longest name a template may have. */
    static final int MAX_NAME = 120;

    /** The longest title or file name pattern. */
    static final int MAX_PATTERN = 240;

    /** The longest text a letterhead cell may hold. */
    static final int MAX_CELL_TEXT = 600;

    /** The longest body, which is generous for a letter of several pages. */
    static final int MAX_BODY = 200_000;

    /** The most tags a template files a document with. */
    static final int MAX_TAGS = 20;

    /** The longest tag. */
    static final int MAX_TAG = 60;

    /** The wait between two self service documents a new template starts with. */
    static final int DEFAULT_COOLDOWN_DAYS = 30;

    private final DocumentTemplateRepository templates;
    private final MediaLibraryService mediaLibrary;
    private final ProfileFieldRepository profileFields;
    private final PlaceholderCatalogue catalogue;

    @Inject
    public TemplateChecks(
            DocumentTemplateRepository templates,
            MediaLibraryService mediaLibrary,
            ProfileFieldRepository profileFields,
            PlaceholderCatalogue catalogue) {
        this.templates = templates;
        this.mediaLibrary = mediaLibrary;
        this.profileFields = profileFields;
        this.catalogue = catalogue;
    }

    /**
     * Checks a template and fills in what was left out.
     *
     * @param stationId the station that keeps the template
     * @param request   what the editor sent
     * @param exceptId  the template being changed, whose own name is not taken by itself, or null
     * @return the template as it is to be written
     */
    public DocumentTemplateDraft draft(int stationId, DocumentTemplateRequest request, @Nullable Integer exceptId) {
        return build(stationId, request, requireName(stationId, request.name(), exceptId));
    }

    /**
     * Checks a template the editor wants to look at before it is saved. Its name is not held to
     * anything, since a draft may still share it with the template it is a new version of.
     *
     * @param stationId the station that keeps the template
     * @param request   what the editor holds
     * @return the template as it would be written
     */
    public DocumentTemplateDraft preview(int stationId, DocumentTemplateRequest request) {
        String name = request.name();
        return build(stationId, request, name == null || name.isBlank() ? "Vorschau" : name.strip());
    }

    private DocumentTemplateDraft build(int stationId, DocumentTemplateRequest request, String name) {
        String titlePattern = pattern(request.titlePattern(), name + " {{today}}");
        String fileNamePattern = pattern(request.fileNamePattern(), name + " {{member.lastName}} {{today}}");
        var letter = letter(stationId, request);
        var pronouns = pronouns(stationId, request.pronounSource());
        int cooldown = Objects.requireNonNullElse(request.cooldownDays(), DEFAULT_COOLDOWN_DAYS);
        if (cooldown < 0) throw DocumentRefusal.DOCUMENT_TEMPLATE_COOLDOWN_NEGATIVE.raise();
        var audience = Objects.requireNonNullElse(request.audience(), RestrictionAudience.empty());
        var draft = new DocumentTemplateDraft(
                name,
                titlePattern,
                fileNamePattern,
                tags(request.tags()),
                request.hidden(),
                Objects.requireNonNullElse(request.keepOnArchive(), request.legal()),
                request.legal(),
                request.selfService(),
                cooldown,
                audience.mode(),
                pronouns,
                letter);
        catalogue.requireKnown(stationId, draft);
        return draft;
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

    private LetterContent letter(int stationId, DocumentTemplateRequest request) {
        var page = Objects.requireNonNullElse(request.page(), LetterPage.defaults());
        if (!page.withinBounds()) throw DocumentRefusal.DOCUMENT_TEMPLATE_PAGE_OUT_OF_BOUNDS.raise();
        String body = Objects.requireNonNullElse(request.bodyMarkdown(), "");
        requireLength(body, MAX_BODY);
        var letterhead = Objects.requireNonNullElse(request.letterhead(), Letterhead.empty());
        return new LetterContent(
                new Letterhead(cells(stationId, letterhead.header()), cells(stationId, letterhead.footer())),
                body,
                page);
    }

    private List<LetterCell> cells(int stationId, List<LetterCell> row) {
        if (row.size() > Letterhead.MAX_CELLS) throw DocumentRefusal.DOCUMENT_TEMPLATE_TOO_MANY_CELLS.raise();
        return row.stream().map(cell -> cell(stationId, cell)).toList();
    }

    /**
     * A cell as it is kept: only what its kind reads, with a picture that is really an image of the
     * station's library.
     */
    private LetterCell cell(int stationId, @Nullable LetterCell cell) {
        if (cell == null || cell.kind() == null) return LetterCell.empty();
        var align = Objects.requireNonNullElse(cell.align(), LetterCellAlign.LEFT);
        int height = cell.imageHeightMm() > 0 ? cell.imageHeightMm() : LetterCell.DEFAULT_IMAGE_HEIGHT_MM;
        return switch (cell.kind()) {
            case EMPTY -> new LetterCell(LetterCellKind.EMPTY, null, null, align, height);
            case LOGO -> new LetterCell(LetterCellKind.LOGO, null, null, align, height);
            case IMAGE ->
                new LetterCell(LetterCellKind.IMAGE, requirePicture(stationId, cell.mediaHash()), null, align, height);
            case TEXT -> {
                String text = Objects.requireNonNullElse(cell.text(), "");
                requireLength(text, MAX_CELL_TEXT);
                yield new LetterCell(LetterCellKind.TEXT, null, text, align, height);
            }
        };
    }

    private String requirePicture(int stationId, @Nullable String hash) {
        if (hash == null || hash.isBlank()) throw DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE.raise();
        boolean image = mediaLibrary
                .findByHash(stationId, hash)
                .map(file -> file.mimeType().startsWith("image/"))
                .orElse(false);
        if (!image) throw DocumentRefusal.DOCUMENT_TEMPLATE_PICTURE_NOT_HERE.raise();
        return hash;
    }

    private @Nullable PronounSource pronouns(int stationId, @Nullable PronounSource source) {
        if (source == null) return null;
        boolean choice = profileFields
                .findById(source.fieldId())
                .filter(field -> field.stationId() == stationId)
                .map(field -> field.fieldType() == FieldType.CHOICE)
                .orElse(false);
        if (!choice) throw DocumentRefusal.DOCUMENT_TEMPLATE_PRONOUN_FIELD_NOT_CHOICE.raise();
        return source;
    }

    private static void requireLength(String text, int max) {
        if (text.length() > max) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_TEXT_TOO_LONG.raise(RefusalDetail.count(max));
        }
    }
}
