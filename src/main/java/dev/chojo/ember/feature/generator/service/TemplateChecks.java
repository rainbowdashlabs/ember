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
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
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
 * Turns what the editor sends into a template a station or an association may keep, refusing what it may
 * not.
 *
 * <p>Every check names its own refusal, so the editor can say which part of the template is wrong.
 * Placeholders are checked against the catalogue of the owner by {@link PlaceholderCatalogue}: a
 * placeholder the catalogue does not know would only ever print as a gap. A letter's rows are checked
 * by {@link LetterChecks}, its pictures against the owner's media library, a PDF template's fields by
 * {@link PdfLayoutChecks}. A font family the template names has to be one the owner reaches
 * ({@link FontLibrary}).
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
    private final OwnerStores stores;

    @Inject
    public TemplateChecks(
            DocumentTemplateRepository templates,
            PdfTemplateRepository pdfTemplates,
            LetterChecks letters,
            StationRepository stations,
            PlaceholderCatalogue catalogue,
            FontLibrary fonts,
            OwnerStores stores) {
        this.templates = templates;
        this.pdfTemplates = pdfTemplates;
        this.letters = letters;
        this.stations = stations;
        this.catalogue = catalogue;
        this.fonts = fonts;
        this.stores = stores;
    }

    /**
     * Checks a template and fills in what was left out.
     *
     * @param owner    the station or the association that keeps the template
     * @param request  what the editor sent
     * @param existing the template being changed, whose own name is not taken by itself and whose kind
     *                 stays, or null for a new one
     * @return the template as it is to be written
     */
    public DocumentTemplateDraft draft(
            Owner owner, DocumentTemplateRequest request, @Nullable DocumentTemplate existing) {
        Integer exceptId = existing == null ? null : existing.id();
        if (existing != null && !request.forAppointments() && templates.requiredByAppointments(existing.id())) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_REQUIRED_BY_APPOINTMENTS.raise();
        }
        return build(owner, request, requireName(owner, request.name(), exceptId), existing);
    }

    /**
     * Checks a template the editor wants to look at before it is saved. Its name is not held to
     * anything, since a draft may still share it with the template it is a new version of.
     *
     * @param owner   the station or the association that keeps the template
     * @param request what the editor holds
     * @param saved   the saved template the draft is a new version of, whose PDF a PDF template fills,
     *                or null for one not saved yet
     * @return the template as it would be written
     */
    public DocumentTemplateDraft preview(
            Owner owner, DocumentTemplateRequest request, @Nullable DocumentTemplate saved) {
        String name = request.name();
        return build(owner, request, name == null || name.isBlank() ? "Vorschau" : name.strip(), saved);
    }

    private DocumentTemplateDraft build(
            Owner owner, DocumentTemplateRequest request, String name, @Nullable DocumentTemplate existing) {
        String titlePattern = pattern(request.titlePattern(), name + " {{today}}");
        String fileNamePattern = pattern(request.fileNamePattern(), name + " {{member.lastName}} {{today}}");
        var content = content(owner, request, existing);
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
                language(owner, request.language()),
                content);
        catalogue.requireKnown(owner, draft);
        fonts.requireReachable(owner, content);
        return draft;
    }

    /** The language asked for, or that of the station, or of the association's home station, where none was. */
    private DocumentLanguage language(Owner owner, @Nullable DocumentLanguage asked) {
        if (asked != null) return asked;
        return DocumentLanguage.of(StationFormat.languageOf(
                stations.findById(stores.libraryOf(owner)).orElse(null)));
    }

    /**
     * What the template is made of, by the kind it has or, for a new one, the kind it asks for. A letter's
     * pictures come from its owner's media library.
     */
    private TemplateContent content(Owner owner, DocumentTemplateRequest request, @Nullable DocumentTemplate existing) {
        var kind = existing != null
                ? existing.kind()
                : Objects.requireNonNullElse(request.kind(), DocumentTemplateKind.LETTER);
        return switch (kind) {
            case LETTER -> letters.letter(stores.libraryOf(owner), request);
            case PDF -> {
                var original = existing == null
                        ? null
                        : pdfTemplates.findCurrentOriginal(existing.id()).orElse(null);
                yield PdfLayoutChecks.check(original, request.fields(), request.formBindings(), MAX_CELL_TEXT);
            }
        };
    }

    private String requireName(Owner owner, @Nullable String raw, @Nullable Integer exceptId) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) throw DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING.raise();
        requireLength(name, MAX_NAME);
        requireNameFree(owner, name, exceptId);
        return name;
    }

    /**
     * Refuses a name another template in use of the owner already carries.
     *
     * @param owner    the station or the association
     * @param name     the name
     * @param exceptId the template that carries it itself, or null
     */
    public void requireNameFree(Owner owner, String name, @Nullable Integer exceptId) {
        if (templates.nameTaken(owner, name, exceptId)) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_NAME_TAKEN.raise(RefusalDetail.text(name));
        }
    }

    /**
     * The name a copy is written under: the one asked for where no template in use of the owner carries
     * it, and otherwise that name with the first free count after it, {@code (2)}, {@code (3)} and so on.
     * A name too long for the count is shortened to make room for it.
     *
     * @param owner  the station or the association the copy is for
     * @param wanted the name the screen worded for the copy
     * @return a name no template in use of the owner carries
     */
    public String freeCopyName(Owner owner, @Nullable String wanted) {
        String base = wanted == null ? "" : wanted.strip();
        if (base.isEmpty()) throw DocumentRefusal.DOCUMENT_TEMPLATE_NAME_MISSING.raise();
        String name = fitted(base, "");
        for (int count = 2; templates.nameTaken(owner, name, null); count++) {
            name = fitted(base, " (%d)".formatted(count));
        }
        return name;
    }

    private static String fitted(String base, String suffix) {
        int room = MAX_NAME - suffix.length();
        return (base.length() > room ? base.substring(0, room).strip() : base) + suffix;
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
