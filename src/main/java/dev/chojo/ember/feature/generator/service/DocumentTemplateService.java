/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.content.entity.ContentRow;
import dev.chojo.ember.feature.generator.entity.DateFormat;
import dev.chojo.ember.feature.generator.entity.DateKind;
import dev.chojo.ember.feature.generator.entity.DatePreset;
import dev.chojo.ember.feature.generator.entity.DateToken;
import dev.chojo.ember.feature.generator.entity.DocumentLanguage;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.FormBinding;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.feature.generator.entity.LetterPage;
import dev.chojo.ember.feature.generator.entity.PdfContent;
import dev.chojo.ember.feature.generator.entity.PdfField;
import dev.chojo.ember.feature.generator.entity.PdfLayout;
import dev.chojo.ember.feature.generator.entity.PdfOriginal;
import dev.chojo.ember.feature.generator.entity.Placeholder;
import dev.chojo.ember.feature.generator.entity.TemplateContent;
import dev.chojo.ember.feature.generator.entity.TemplateStationUse;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.repository.TemplateStationUseRepository;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The document templates of a station or an association: listing, writing and archiving them, and which
 * of them a station may use.
 *
 * <p>A template is never deleted. Documents generated from it name it in the generation log, so one its
 * owner no longer wants is archived: it leaves the lists, generates nothing more, and can be taken back
 * into use. Every change counts its version up, so the log says which state a document came from.
 *
 * <p>Only the owner writes a template. A station uses the templates of its association in use next to
 * its own and lists them, marked as the association's, but a change to one is refused with words of its
 * own. Whether the station's members generate one of them through self service, the station decides
 * ({@link TemplateStationUseService}); an association only offers a template for it and sets no audience.
 */
@Singleton
public class DocumentTemplateService {
    private static final Logger log = LoggerFactory.getLogger(DocumentTemplateService.class);

    private final DocumentTemplateRepository templates;
    private final PdfTemplateRepository pdfTemplates;
    private final TemplateStationUseRepository uses;
    private final TemplateChecks checks;
    private final RestrictionService restrictions;
    private final PlaceholderCatalogue catalogue;
    private final OwnerStores stores;

    @Inject
    public DocumentTemplateService(
            DocumentTemplateRepository templates,
            PdfTemplateRepository pdfTemplates,
            TemplateStationUseRepository uses,
            TemplateChecks checks,
            RestrictionService restrictions,
            PlaceholderCatalogue catalogue,
            OwnerStores stores) {
        this.templates = templates;
        this.pdfTemplates = pdfTemplates;
        this.uses = uses;
        this.checks = checks;
        this.restrictions = restrictions;
        this.catalogue = catalogue;
        this.stores = stores;
    }

    /**
     * The templates of an owner by name. A station's list in use also holds the templates of its
     * association in use, marked as such, each showing whether the station offers it for self service.
     * Each says when it was last generated from: at the station for a station, at any of its stations for
     * an association.
     *
     * @param owner    the station or the association
     * @param archived whether to list the owner's archived ones instead of those in use
     * @return the templates
     */
    public List<DocumentTemplateSummary> list(Owner owner, boolean archived) {
        var lastUsed = templates.lastUsedAt(owner);
        var own = templates.findByOwner(owner, archived).stream()
                .map(template ->
                        DocumentTemplateSummary.of(template, template.selfService(), lastUsed.get(template.id())));
        if (archived || !(owner instanceof Owner.Station station)) return own.toList();
        Map<Integer, TemplateStationUse> used = uses.findByStation(station.stationId()).stream()
                .collect(Collectors.toMap(TemplateStationUse::templateId, Function.identity()));
        var association = templates.findOfAssociationOf(station.stationId()).stream()
                .map(template -> DocumentTemplateSummary.of(
                        template,
                        template.selfService() && offeredAt(used.get(template.id())),
                        lastUsed.get(template.id())));
        return Stream.concat(own, association).toList();
    }

    private static boolean offeredAt(@Nullable TemplateStationUse use) {
        return use != null && use.selfService();
    }

    /**
     * A template of the owner, refusing one that is not theirs. A station asking for a template of its
     * association is told that only the association changes it.
     *
     * @param owner      the station or the association
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplate requireOwned(Owner owner, int templateId) {
        var template = templates.findById(templateId).orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
        if (template.owner().equals(owner)) return template;
        if (owner instanceof Owner.Station station && keptForStation(template, station.stationId())) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_KEPT_BY_ASSOCIATION.raise();
        }
        throw DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE.raise();
    }

    /**
     * A template a station may generate documents from: its own, or one of its association.
     *
     * @param stationId  the station
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplate requireUsable(int stationId, int templateId) {
        return templates
                .findById(templateId)
                .filter(template ->
                        template.owner().equals(new Owner.Station(stationId)) || keptForStation(template, stationId))
                .orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
    }

    /**
     * A template a station may generate documents from that is in use, refusing an archived one.
     *
     * @param stationId  the station
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplate requireInUse(int stationId, int templateId) {
        var template = requireUsable(stationId, templateId);
        if (template.archived()) throw DocumentRefusal.DOCUMENT_TEMPLATE_ARCHIVED.raise();
        return template;
    }

    /**
     * A template the owner may copy: one of its own, archived or in use, or for a station one of its
     * association in use, which the station's list shows.
     *
     * @param owner      the station or the association the copy is for
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplate requireCopyable(Owner owner, int templateId) {
        var template = templates.findById(templateId).orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
        if (template.owner().equals(owner)) return template;
        if (owner instanceof Owner.Station station
                && !template.archived()
                && keptForStation(template, station.stationId())) {
            return template;
        }
        throw DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE.raise();
    }

    /** Whether a template is one of the association a station belongs to. */
    private boolean keptForStation(DocumentTemplate template, int stationId) {
        return template.ofAssociation()
                && stores.associationOf(stationId)
                        .map(association -> association.equals(template.owner()))
                        .orElse(false);
    }

    /**
     * What a template is made of: what its letter says, an empty one where none was written, or the PDF
     * it fills now with what is laid over it.
     *
     * @param template the template
     * @return the content
     */
    public TemplateContent contentOf(DocumentTemplate template) {
        return switch (template.kind()) {
            case LETTER -> templates.findLetter(template.id()).orElseGet(LetterContent::blank);
            case PDF ->
                new PdfContent(
                        pdfTemplates.findCurrentOriginal(template.id()).orElse(null),
                        pdfTemplates.findLayout(template.id()));
        };
    }

    /**
     * A template with everything the editor shows of it.
     *
     * @param owner      the station or the association
     * @param templateId the template
     * @return the template
     */
    public DocumentTemplateResponse detail(Owner owner, int templateId) {
        return response(requireOwned(owner, templateId));
    }

    /**
     * Creates a template.
     *
     * @param owner    the station or the association
     * @param request  what the editor sent
     * @param authorId the account that creates it
     * @return the template as written
     */
    public DocumentTemplateResponse create(Owner owner, DocumentTemplateRequest request, int authorId) {
        requireAudienceAllowed(owner, request);
        var draft = checks.draft(owner, request, null);
        var template = Transactions.call(() -> write(owner, draft, request.audience(), authorId));
        log.info("Document template {} created for {}", template.id(), owner);
        return response(template);
    }

    /**
     * Writes a new template with its content and its self service audience, as it stands, for a caller
     * that has checked it and holds the transaction.
     *
     * @param owner    the station or the association that keeps it
     * @param draft    the template
     * @param audience who may generate it through self service, everybody where null
     * @param authorId the account that creates it
     * @return the template as written
     */
    DocumentTemplate write(
            Owner owner, DocumentTemplateDraft draft, @Nullable RestrictionAudience audience, int authorId) {
        var written = templates.create(owner, draft, authorId);
        writeContent(written.id(), draft.content());
        writeAudience(written.id(), audience);
        return written;
    }

    /**
     * @param template a template
     * @return who may generate it through self service, as its owner chose
     */
    RestrictionAudience audienceOf(DocumentTemplate template) {
        return RestrictionAudience.of(restrictions.findRestrictionSet(
                RestrictionType.DOCUMENT_TEMPLATE, template.id(), template.restrictionMode()));
    }

    /**
     * Changes a template, counting its version up.
     *
     * @param owner      the station or the association
     * @param templateId the template
     * @param request    what the editor sent
     * @param authorId   the account that changes it
     * @return the template as written
     */
    public DocumentTemplateResponse update(Owner owner, int templateId, DocumentTemplateRequest request, int authorId) {
        var existing = requireOwned(owner, templateId);
        requireAudienceAllowed(owner, request);
        var draft = checks.draft(owner, request, existing);
        var template = Transactions.call(() -> {
            var written = templates
                    .update(templateId, draft, authorId)
                    .orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
            writeContent(templateId, draft.content());
            writeAudience(templateId, request.audience());
            return written;
        });
        log.info("Document template {} changed to version {}", templateId, template.version());
        return response(template);
    }

    /**
     * Archives a template, or takes an archived one back into use.
     *
     * @param owner      the station or the association
     * @param templateId the template
     * @param archived   whether it is archived from now on
     * @param authorId   the account that does it
     * @return the template as it now stands
     */
    public DocumentTemplateResponse setArchived(Owner owner, int templateId, boolean archived, int authorId) {
        var template = requireOwned(owner, templateId);
        if (!archived) checks.requireNameFree(owner, template.name(), templateId);
        templates.setArchived(templateId, archived, authorId);
        log.info("Document template {} {}", templateId, archived ? "archived" : "taken back into use");
        return detail(owner, templateId);
    }

    /**
     * The placeholders an owner's templates can name.
     *
     * @param owner the station or the association
     * @return the catalogue
     */
    public PlaceholderCatalogueResponse catalogue(Owner owner) {
        return new PlaceholderCatalogueResponse(
                catalogue.forOwner(owner),
                Arrays.stream(DatePreset.values()).map(DateFormatOption::of).toList(),
                Arrays.stream(DateToken.values()).map(DateTokenOption::of).toList(),
                catalogue.language(owner));
    }

    /**
     * Refuses a self service audience on a template of an association, which offers a template and leaves
     * the audience to each station.
     */
    private static void requireAudienceAllowed(Owner owner, DocumentTemplateRequest request) {
        var audience = request.audience();
        if (owner instanceof Owner.Association && audience != null && !audience.namesNobody()) {
            throw DocumentRefusal.DOCUMENT_ASSOCIATION_TEMPLATE_AUDIENCE.raise();
        }
    }

    private void writeContent(int templateId, TemplateContent content) {
        switch (content) {
            case LetterContent letter -> templates.writeLetter(templateId, letter);
            case PdfContent pdf -> pdfTemplates.writeLayout(templateId, pdf.layout());
        }
    }

    private void writeAudience(int templateId, @Nullable RestrictionAudience audience) {
        var chosen = Objects.requireNonNullElse(audience, RestrictionAudience.empty());
        restrictions.setRestrictions(RestrictionType.DOCUMENT_TEMPLATE, templateId, chosen.toSelection());
    }

    private DocumentTemplateResponse response(DocumentTemplate template) {
        var content = contentOf(template);
        var letter = content instanceof LetterContent written ? written : LetterContent.blank();
        var pdf = content instanceof PdfContent filled ? filled : new PdfContent(null, PdfLayout.empty());
        var audience = audienceOf(template);
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
                template.forAppointments(),
                template.selfService(),
                template.cooldownDays(),
                audience,
                template.language(),
                template.issuerId(),
                template.issuerFunction(),
                letter.header(),
                letter.footer(),
                letter.body(),
                letter.page(),
                pdf.original(),
                pdf.layout().fields(),
                pdf.layout().bindings(),
                template.version(),
                template.updatedAt(),
                template.archivedAt());
    }

    /**
     * A template as a list shows it.
     *
     * @param id              the template
     * @param name            what it is called
     * @param kind            what it is made of
     * @param legal           whether it makes a legal document
     * @param forAppointments whether appointments may require it as a document to bring
     * @param selfService     whether members may generate it for themselves; for a template of the
     *                        association listed at a station, whether the station offers it to them
     * @param ofAssociation   whether the association keeps it, which a station uses but does not change
     * @param version         how often it was changed, counted from one
     * @param createdAt       when it was created
     * @param updatedAt       when it was last changed
     * @param lastUsedAt      when a document was last generated from it, or null where none ever was
     * @param archivedAt      when it was archived, or null while it is in use
     */
    public record DocumentTemplateSummary(
            int id,
            String name,
            DocumentTemplateKind kind,
            boolean legal,
            boolean forAppointments,
            boolean selfService,
            boolean ofAssociation,
            int version,
            Instant createdAt,
            Instant updatedAt,
            @Nullable Instant lastUsedAt,
            @Nullable Instant archivedAt) {

        static DocumentTemplateSummary of(
                DocumentTemplate template, boolean selfService, @Nullable Instant lastUsedAt) {
            return new DocumentTemplateSummary(
                    template.id(),
                    template.name(),
                    template.kind(),
                    template.legal(),
                    template.forAppointments(),
                    selfService,
                    template.ofAssociation(),
                    template.version(),
                    template.createdAt(),
                    template.updatedAt(),
                    lastUsedAt,
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
     * @param forAppointments whether appointments may require it as a document to bring
     * @param selfService     whether members may generate it for themselves; for an association's
     *                        template, whether it is offered to its stations for that
     * @param cooldownDays    the days between two self service documents for one member
     * @param audience        who may generate it through self service, always everybody for a template of
     *                        an association
     * @param language        the language its documents are written in
     * @param issuerId        the member of the station who issues its documents, or null where it names
     *                        nobody, for a template of an association, and once that member was deleted
     * @param issuerFunction  what the issuer does at the station, or null where nothing is said
     * @param header          the rows across the top of every page of a letter
     * @param footer          the rows across the bottom of every page of a letter
     * @param body            the rows of a letter
     * @param page            the margins and the size of the body text of a letter
     * @param pdf             the PDF a PDF template fills now, or null for a letter or before an upload
     * @param fields          the fields drawn on the pages of a PDF template
     * @param formBindings    what the form fields of a PDF template's PDF are filled with
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
            boolean forAppointments,
            boolean selfService,
            int cooldownDays,
            RestrictionAudience audience,
            DocumentLanguage language,
            @Nullable Integer issuerId,
            @Nullable String issuerFunction,
            List<ContentRow> header,
            List<ContentRow> footer,
            List<ContentRow> body,
            LetterPage page,
            @Nullable PdfOriginal pdf,
            List<PdfField> fields,
            List<FormBinding> formBindings,
            int version,
            Instant updatedAt,
            @Nullable Instant archivedAt) {}

    /**
     * What a template of the owner can name.
     *
     * @param placeholders every placeholder, in the order the picker shows them
     * @param dateFormats  the ready-made formats a date can print in, in the order the picker offers them
     * @param dateTokens   the letters an own date format is written with, which the editor shows the
     *                     dates of a template by
     * @param language     the language the labels are written in, which a template prints in until it
     *                     names its own
     */
    public record PlaceholderCatalogueResponse(
            List<Placeholder> placeholders,
            List<DateFormatOption> dateFormats,
            List<DateTokenOption> dateTokens,
            DocumentLanguage language) {}

    /**
     * A letter of an own date format with how it prints the example day in both languages, so the editor
     * shows any date of a template on that day in the language of the template.
     *
     * @param written how it is written, such as {@code TT}
     * @param clock   whether it prints a time of day
     * @param german  the example day as it prints in a German template
     * @param english the same in an English template
     */
    public record DateTokenOption(String written, boolean clock, String german, String english) {
        static DateTokenOption of(DateToken token) {
            return new DateTokenOption(
                    token.written(),
                    token.clock(),
                    token.example(DocumentLanguage.DE),
                    token.example(DocumentLanguage.EN));
        }
    }

    /**
     * A ready-made date format as the editor offers it, with its tokens in both languages, so the editor
     * shows an example of it in the language of the template.
     *
     * @param written how a key names it after the bar
     * @param kind    what a date needs to take it: a day, or a day with a time of day
     * @param german  the format in the tokens of an own format, for a German template
     * @param english the same for an English template
     */
    public record DateFormatOption(String written, DateKind kind, String german, String english) {
        static DateFormatOption of(DatePreset preset) {
            var format = DateFormat.of(preset);
            return new DateFormatOption(
                    preset.written(),
                    format.readsClock() ? DateKind.DATE_TIME : DateKind.DATE,
                    preset.pattern(DocumentLanguage.DE),
                    preset.pattern(DocumentLanguage.EN));
        }
    }
}
