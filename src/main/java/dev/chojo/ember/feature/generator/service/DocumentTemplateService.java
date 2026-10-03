/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.Letterhead;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.PronounSource;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The document templates of an owner: listing, writing and archiving them.
 *
 * <p>A template is never deleted. Documents generated from it name it in the generation log, so one a
 * station no longer wants is archived: it leaves the lists, generates nothing more, and can be taken
 * back into use. Every change counts its version up, so the log says which state a document came from.
 *
 * <p>Templates belong to a station today. The owner is passed as an {@link Owner.Station} so that the
 * association's templates can join without the callers changing shape.
 */
@Singleton
public class DocumentTemplateService {
    private static final Logger log = LoggerFactory.getLogger(DocumentTemplateService.class);

    private final DocumentTemplateRepository templates;
    private final TemplateChecks checks;
    private final RestrictionService restrictions;
    private final PlaceholderCatalogue catalogue;

    @Inject
    public DocumentTemplateService(
            DocumentTemplateRepository templates,
            TemplateChecks checks,
            RestrictionService restrictions,
            PlaceholderCatalogue catalogue) {
        this.templates = templates;
        this.checks = checks;
        this.restrictions = restrictions;
        this.catalogue = catalogue;
    }

    /**
     * The templates of an owner by name.
     *
     * @param owner    the owner
     * @param archived whether to list the archived ones instead of those in use
     * @return the templates
     */
    public List<DocumentTemplateSummary> list(Owner.Station owner, boolean archived) {
        return templates.findByStation(owner.stationId(), archived).stream()
                .map(DocumentTemplateSummary::of)
                .toList();
    }

    /**
     * A template of the owner, refusing one that is not theirs.
     *
     * @param owner      the owner
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplate requireOwned(Owner.Station owner, int templateId) {
        return templates
                .findById(templateId)
                .filter(template -> template.stationId() == owner.stationId())
                .orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
    }

    /**
     * A template of the owner that is in use, refusing an archived one.
     *
     * @param owner      the owner
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplate requireInUse(Owner.Station owner, int templateId) {
        var template = requireOwned(owner, templateId);
        if (template.archived()) throw DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED.raise();
        return template;
    }

    /**
     * What a template's letter says, an empty one where none was written.
     *
     * @param template the template
     * @return the letter
     */
    public LetterContent letterOf(DocumentTemplate template) {
        return templates.findLetter(template.id()).orElseGet(LetterContent::blank);
    }

    /**
     * A template with everything the editor shows of it.
     *
     * @param owner      the owner
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplateResponse detail(Owner.Station owner, int templateId) {
        return response(requireOwned(owner, templateId));
    }

    /**
     * Creates a template.
     *
     * @param owner    the owner
     * @param request  what the editor sent
     * @param authorId the member who creates it
     * @return the template as written
     */
    public DocumentTemplateResponse create(Owner.Station owner, DocumentTemplateRequest request, int authorId) {
        var draft = checks.draft(owner.stationId(), request, null);
        var template = Transactions.call(() -> {
            var written = templates.create(owner.stationId(), draft, authorId);
            writeAudience(written.id(), request.audience());
            return written;
        });
        log.info("Document template {} created at station {}", template.id(), owner.stationId());
        return response(template);
    }

    /**
     * Changes a template, counting its version up.
     *
     * @param owner      the owner
     * @param templateId the template
     * @param request    what the editor sent
     * @param authorId   the member who changes it
     * @return the template as written
     */
    public DocumentTemplateResponse update(
            Owner.Station owner, int templateId, DocumentTemplateRequest request, int authorId) {
        requireOwned(owner, templateId);
        var draft = checks.draft(owner.stationId(), request, templateId);
        var template = Transactions.call(() -> {
            var written = templates
                    .update(templateId, draft, authorId)
                    .orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
            writeAudience(templateId, request.audience());
            return written;
        });
        log.info("Document template {} changed to version {}", templateId, template.version());
        return response(template);
    }

    /**
     * Archives a template, or takes an archived one back into use.
     *
     * @param owner      the owner
     * @param templateId the template
     * @param archived   whether it is archived from now on
     * @param authorId   the member who does it
     * @return the template as it now stands
     */
    public DocumentTemplateResponse setArchived(Owner.Station owner, int templateId, boolean archived, int authorId) {
        var template = requireOwned(owner, templateId);
        if (!archived) checks.requireNameFree(owner.stationId(), template.name(), templateId);
        templates.setArchived(templateId, archived, authorId);
        log.info("Document template {} {}", templateId, archived ? "archived" : "taken back into use");
        return detail(owner, templateId);
    }

    /**
     * The placeholders an owner's templates can name, and the choice questions pronouns can follow.
     *
     * @param owner the owner
     * @return the catalogue
     */
    public PlaceholderCatalogueResponse catalogue(Owner.Station owner) {
        var choices = catalogue.choiceFields(owner.stationId()).stream()
                .map(field -> new ChoiceField(
                        field.id(),
                        field.name(),
                        Objects.requireNonNullElse(field.config().options(), List.of())))
                .toList();
        return new PlaceholderCatalogueResponse(catalogue.forStation(owner.stationId()), choices);
    }

    private void writeAudience(int templateId, @Nullable RestrictionAudience audience) {
        var chosen = Objects.requireNonNullElse(audience, RestrictionAudience.empty());
        restrictions.setRestrictions(RestrictionType.DOCUMENT_TEMPLATE, templateId, chosen.toSelection());
    }

    private DocumentTemplateResponse response(DocumentTemplate template) {
        var letter = letterOf(template);
        var audience = RestrictionAudience.of(restrictions.findRestrictionSet(
                RestrictionType.DOCUMENT_TEMPLATE, template.id(), template.restrictionMode()));
        return new DocumentTemplateResponse(
                template.id(),
                template.name(),
                template.kind(),
                template.titlePattern(),
                template.fileNamePattern(),
                template.tags(),
                template.hidden(),
                template.keepOnArchive(),
                template.legal(),
                template.selfService(),
                template.cooldownDays(),
                audience,
                template.pronounSource(),
                letter.letterhead(),
                letter.bodyMarkdown(),
                letter.page(),
                template.version(),
                template.updatedAt(),
                template.archivedAt());
    }

    /**
     * A template as a list shows it.
     *
     * @param id          the template
     * @param name        what it is called
     * @param kind        what it is made of
     * @param legal       whether it makes a legal document
     * @param selfService whether members may generate it for themselves
     * @param version     how often it was changed, counted from one
     * @param updatedAt   when it was last changed
     * @param archivedAt  when it was archived, or null while it is in use
     */
    public record DocumentTemplateSummary(
            int id,
            String name,
            DocumentTemplateKind kind,
            boolean legal,
            boolean selfService,
            int version,
            Instant updatedAt,
            @Nullable Instant archivedAt) {

        static DocumentTemplateSummary of(DocumentTemplate template) {
            return new DocumentTemplateSummary(
                    template.id(),
                    template.name(),
                    template.kind(),
                    template.legal(),
                    template.selfService(),
                    template.version(),
                    template.updatedAt(),
                    template.archivedAt());
        }
    }

    /**
     * A template with everything the editor shows of it.
     *
     * @param id              the template
     * @param name            what it is called
     * @param kind            what it is made of
     * @param titlePattern    the title a generated document is filed under
     * @param fileNamePattern the file name a generated document is filed under
     * @param tags            the document tags a generated document is filed with
     * @param hidden          whether a document a manager generates is hidden from the member
     * @param keepOnArchive   whether a generated document outlasts the membership
     * @param legal           whether it makes a legal document
     * @param selfService     whether members may generate it for themselves
     * @param cooldownDays    the days between two self service documents for one member
     * @param audience        who may generate it through self service
     * @param pronounSource   the choice field the pronouns follow, or null
     * @param letterhead      the header and the footer
     * @param bodyMarkdown    the body
     * @param page            the margins and the size of the body text
     * @param version         how often it was changed, counted from one
     * @param updatedAt       when it was last changed
     * @param archivedAt      when it was archived, or null while it is in use
     */
    public record DocumentTemplateResponse(
            int id,
            String name,
            DocumentTemplateKind kind,
            String titlePattern,
            String fileNamePattern,
            List<String> tags,
            boolean hidden,
            boolean keepOnArchive,
            boolean legal,
            boolean selfService,
            int cooldownDays,
            RestrictionAudience audience,
            @Nullable PronounSource pronounSource,
            Letterhead letterhead,
            String bodyMarkdown,
            LetterPage page,
            int version,
            Instant updatedAt,
            @Nullable Instant archivedAt) {}

    /**
     * What a template of the owner can name.
     *
     * @param placeholders every placeholder, in the order the picker shows them
     * @param choiceFields the choice questions pronouns can follow, with their answers
     */
    public record PlaceholderCatalogueResponse(List<Placeholder> placeholders, List<ChoiceField> choiceFields) {}

    /**
     * A choice question pronouns can follow.
     *
     * @param id      the question
     * @param name    what it is called
     * @param options its answers, as they are stored
     */
    public record ChoiceField(int id, String name, List<String> options) {}
}
