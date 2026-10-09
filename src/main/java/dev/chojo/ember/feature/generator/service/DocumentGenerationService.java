/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.GenerationContext;
import dev.chojo.ember.feature.generator.entity.GenerationOrigin;
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.generator.service.DocumentIssuerService.IssuerChoice;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.Sha256;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;

/**
 * Generates a document from a template and files it in the documents of the member it is about.
 *
 * <p>The file takes the same way in as an upload: the station has to keep documents, and the intake
 * decides whether there is room and what the file is. It is filed under the template's title and file
 * name, with its tags, kept past the membership where the template says so, and the person who
 * generated it is its uploader. A document a member or their guardian generates is never hidden from
 * the member; one a manager generates follows the template.
 *
 * <p>Every document is written to the generation log with the template's version, the SHA-256 of the
 * file and everybody whose data went into it, which is what a signature later binds to. The log also
 * names the issuer the document names, whether that is the template's own issuer and whether the
 * document carries the issuer's signature field, so whatever signs it later knows who signs there.
 *
 * <p>A manager may pick another issuer for one document ({@link DocumentIssuerService}); every other way
 * of generating takes the template's.
 *
 * <p>Before a document is filed, {@link IssuerSigning} may sign it for its issuer, where the issuer agreed
 * to that; what is filed and logged is then the signed file.
 *
 * <p>A manager may generate a document whose values are not all there; the gaps print as lines to fill
 * in by hand on a letter and stay empty on a filled-in PDF, and the screen has warned before. Self service refuses that instead
 * ({@link SelfServiceDocumentService}).
 */
@Singleton
public class DocumentGenerationService {
    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);
    private static final String PDF = "application/pdf";

    private final DocumentTemplateService templates;
    private final DocumentGeneratorService generator;
    private final DocumentService documents;
    private final DocumentIntake intake;
    private final DocumentGenerationRepository generations;
    private final TemplateChecks checks;
    private final DocumentIssuerService issuers;
    private final IssuerSigning issuerSigning;
    private final Clock clock;

    @Inject
    public DocumentGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentService documents,
            DocumentIntake intake,
            DocumentGenerationRepository generations,
            TemplateChecks checks,
            DocumentIssuerService issuers,
            IssuerSigning issuerSigning) {
        this(templates, generator, documents, intake, generations, checks, issuers, issuerSigning, Clock.systemUTC());
    }

    /** Files every letter as it was drawn, signing none for its issuer. */
    public DocumentGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentService documents,
            DocumentIntake intake,
            DocumentGenerationRepository generations,
            TemplateChecks checks,
            DocumentIssuerService issuers) {
        this(templates, generator, documents, intake, generations, checks, issuers, IssuerSigning.NONE);
    }

    /**
     * Files every letter as it was drawn, signing none for its issuer.
     *
     * @param clock when a document is logged as generated, which a test moves
     */
    public DocumentGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentService documents,
            DocumentIntake intake,
            DocumentGenerationRepository generations,
            TemplateChecks checks,
            DocumentIssuerService issuers,
            Clock clock) {
        this(templates, generator, documents, intake, generations, checks, issuers, IssuerSigning.NONE, clock);
    }

    /**
     * @param issuerSigning signs a letter for its issuer before it is filed, where the issuer agreed to it
     * @param clock         when a document is logged as generated, which a test moves
     */
    public DocumentGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentService documents,
            DocumentIntake intake,
            DocumentGenerationRepository generations,
            TemplateChecks checks,
            DocumentIssuerService issuers,
            IssuerSigning issuerSigning,
            Clock clock) {
        this.templates = templates;
        this.generator = generator;
        this.documents = documents;
        this.intake = intake;
        this.generations = generations;
        this.checks = checks;
        this.issuers = issuers;
        this.issuerSigning = issuerSigning;
        this.clock = clock;
    }

    /**
     * A document that was generated and filed.
     *
     * @param documentId   the member document it was filed as
     * @param generationId its entry in the generation log
     * @param title        the title it was filed under
     * @param missing      the placeholders that had no value and print as lines to fill in
     */
    public record GeneratedDocumentResponse(
            int documentId, int generationId, String title, List<MissingValue> missing) {}

    /**
     * The templates in use a manager can generate documents from: the station's own and those of its
     * association.
     *
     * @param owner the station
     * @param query the search, order and page
     * @return the page of templates asked for
     */
    public TemplateQuery.TemplatePage usable(Owner.Station owner, TemplateQuery query) {
        return query.pageOf(templates.list(owner, false));
    }

    /**
     * Draws a saved template for a member of the station, for a look before it is generated.
     *
     * @param session    the manager
     * @param templateId the template, the station's own or one of its association
     * @param memberId   the member, already checked to be of the station
     * @param issuer     the member the manager picked to issue it, or null for the template's issuer
     * @return the document and what is missing
     */
    public PreviewResponse preview(
            StationSession session, int templateId, int memberId, @Nullable IssuerChoice issuer) {
        var template = templates.requireInUse(session.stationId(), templateId);
        return generator.preview(generator.sourceOf(template), memberId, managerContext(session, template, issuer));
    }

    private GenerationContext managerContext(
            StationSession session, DocumentTemplate template, @Nullable IssuerChoice issuer) {
        return GenerationContext.by(session.member().id(), issuers.forManager(template, session.stationId(), issuer));
    }

    /**
     * Draws a template still in the editor, for a member or without one.
     *
     * @param session    the editor of templates
     * @param request    the template as the editor holds it
     * @param templateId the saved template the draft changes, whose kind it keeps and whose PDF a PDF
     *                   template fills, or null for one not saved yet
     * @param memberId   the member to draw it for, already checked to be of the station, or null
     * @return the document and what is missing
     */
    public PreviewResponse previewDraft(
            StationSession session,
            DocumentTemplateRequest request,
            @Nullable Integer templateId,
            @Nullable Integer memberId) {
        if (memberId != null && !session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) {
            throw DocumentRefusal.DOCUMENT_GENERATE_NOT_YOURS.raise();
        }
        return previewDraft(
                session.owner(),
                request,
                templateId,
                memberId,
                GenerationContext.by(
                        session.member().id(),
                        DocumentIssuerService.drafted(request.issuerId(), request.issuerFunction())));
    }

    /**
     * Draws a template of an association still in its editor. The association has no members of its own,
     * so it is drawn without one, its placeholders shown by their labels.
     *
     * @param owner      the association
     * @param request    the template as the editor holds it
     * @param templateId the saved template the draft changes, or null for one not saved yet
     * @param memberId   a member asked for, which an association's preview refuses
     * @return the document
     */
    public PreviewResponse previewAssociationDraft(
            Owner.Association owner,
            DocumentTemplateRequest request,
            @Nullable Integer templateId,
            @Nullable Integer memberId) {
        if (memberId != null) throw DocumentRefusal.DOCUMENT_ASSOCIATION_PREVIEW_WITH_MEMBER.raise();
        return previewDraft(owner, request, templateId, null, GenerationContext.NOBODY);
    }

    private PreviewResponse previewDraft(
            Owner owner,
            DocumentTemplateRequest request,
            @Nullable Integer templateId,
            @Nullable Integer memberId,
            GenerationContext context) {
        var saved = templateId == null ? null : templates.requireOwned(owner, templateId);
        var draft = checks.preview(owner, request, saved);
        return generator.preview(DocumentGeneratorService.sourceOf(owner, draft), memberId, context);
    }

    /**
     * Generates a document for a member of the station and files it, as a manager does.
     *
     * @param session    the manager
     * @param templateId the template, the station's own or one of its association
     * @param memberId   the member, already checked to be of the station
     * @param issuer     the member the manager picked to issue it, or null for the template's issuer
     * @return the filed document and what was missing
     */
    public GeneratedDocumentResponse generate(
            StationSession session, int templateId, int memberId, @Nullable IssuerChoice issuer) {
        var template = templates.requireInUse(session.stationId(), templateId);
        var prepared = generator.prepare(template, memberId, managerContext(session, template, issuer));
        return file(template, memberId, session.member().id(), GenerationOrigin.MANAGER, prepared);
    }

    /**
     * Draws a document and files it, with its entry in the generation log, at the station of the member it
     * is about, whoever keeps the template.
     *
     * @param template    the template
     * @param memberId    the member it is about
     * @param generatedBy the member who generates it
     * @param origin      in which role it is generated, and for which appointment
     * @param prepared    the values it is drawn from
     * @return the filed document
     */
    GeneratedDocumentResponse file(
            DocumentTemplate template,
            int memberId,
            int generatedBy,
            GenerationOrigin origin,
            DocumentGeneratorService.Prepared prepared) {
        requireKept(prepared);
        return file(
                template, memberId, generatedBy, origin, prepared, signForIssuer(prepared, generator.render(prepared)));
    }

    /**
     * Signs a drawn letter for its issuer where they agreed to it, before it is filed. It may wait for
     * outside services, so a caller filing inside a transaction calls this first.
     *
     * @param prepared what the letter was drawn from
     * @param rendered the letter as drawn
     * @return the letter to file
     */
    IssuerSigning.Signed signForIssuer(
            DocumentGeneratorService.Prepared prepared, DocumentGeneratorService.Rendered rendered) {
        return issuerSigning.sign(prepared, rendered);
    }

    /**
     * Refuses a document of a station that keeps no documents, before it is drawn.
     *
     * @param prepared the values a document is about to be drawn from
     */
    void requireKept(DocumentGeneratorService.Prepared prepared) {
        documents.requireKept(prepared.stationId(), DocumentDoor.STATION);
    }

    /**
     * Files a document drawn before, with its entry in the generation log, at the station of the member it
     * is about. Nothing here draws, so a caller holding a transaction around it holds it only while the
     * file is stored and logged.
     *
     * @param template    the template
     * @param memberId    the member it is about
     * @param generatedBy the member who generates it
     * @param origin      in which role it is generated, and for which appointment
     * @param prepared    the values it was drawn from
     * @param signed      the document drawn from them, signed for its issuer where that applies
     * @return the filed document
     */
    GeneratedDocumentResponse file(
            DocumentTemplate template,
            int memberId,
            int generatedBy,
            GenerationOrigin origin,
            DocumentGeneratorService.Prepared prepared,
            IssuerSigning.Signed signed) {
        var rendered = signed.rendered();
        int stationId = prepared.stationId();
        var issuer = prepared.issuer();
        var upload = new DocumentIntake.Upload(rendered.fileName(), PDF, rendered.pdf());
        String mimeType =
                intake.take(stationId, StorageCategory.MEMBER_DOCUMENTS, upload, DocumentDoor.STATION.intake());
        var document = documents.store(
                stationId,
                List.of(memberId),
                rendered.title(),
                rendered.fileName(),
                mimeType,
                rendered.pdf(),
                template.hidden() && origin.byManager(),
                template.keepOnArchive(),
                Uploader.member(generatedBy),
                template.tags());
        var entry = generations.log(
                new DocumentGeneration(
                        0,
                        stationId,
                        template.id(),
                        template.version(),
                        memberId,
                        generatedBy,
                        clock.instant(),
                        origin.selfService(),
                        document.id(),
                        Sha256.hex(rendered.pdf()),
                        prepared.source().pdfOriginalId(),
                        origin.eventId(),
                        origin.eventDate(),
                        issuer.memberOfRecord(),
                        issuer.named() ? issuer.issuer().function() : null,
                        issuer.issuer().fixed(),
                        issuer.signs()),
                rendered.resolved().subjects());
        signed.record().accept(entry.id());
        log.info(
                "Document {} generated from template {} (version {}) for member {}",
                document.id(),
                template.id(),
                template.version(),
                memberId);
        return new GeneratedDocumentResponse(document.id(), entry.id(), rendered.title(), prepared.missing());
    }
}
