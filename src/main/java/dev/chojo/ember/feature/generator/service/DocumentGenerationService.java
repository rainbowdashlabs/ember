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
import dev.chojo.ember.feature.generator.entity.MissingValue;
import dev.chojo.ember.feature.generator.repository.DocumentGenerationRepository;
import dev.chojo.ember.feature.generator.service.DocumentGeneratorService.PreviewResponse;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
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
 * file and everybody whose data went into it, which is what a signature later binds to.
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
    private final Clock clock;

    @Inject
    public DocumentGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentService documents,
            DocumentIntake intake,
            DocumentGenerationRepository generations,
            TemplateChecks checks) {
        this(templates, generator, documents, intake, generations, checks, Clock.systemUTC());
    }

    /**
     * @param clock when a document is logged as generated, which a test moves
     */
    public DocumentGenerationService(
            DocumentTemplateService templates,
            DocumentGeneratorService generator,
            DocumentService documents,
            DocumentIntake intake,
            DocumentGenerationRepository generations,
            TemplateChecks checks,
            Clock clock) {
        this.templates = templates;
        this.generator = generator;
        this.documents = documents;
        this.intake = intake;
        this.generations = generations;
        this.checks = checks;
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
     * The templates in use a manager can generate documents from.
     *
     * @param owner the owner
     * @return the templates by name
     */
    public List<DocumentTemplateService.DocumentTemplateSummary> usable(Owner.Station owner) {
        return templates.list(owner, false);
    }

    /**
     * Draws a saved template for a member of the station, for a look before it is generated.
     *
     * @param session    the manager
     * @param templateId the template
     * @param memberId   the member, already checked to be of the station
     * @return the document and what is missing
     */
    public PreviewResponse preview(StationSession session, int templateId, int memberId) {
        var template = templates.requireInUse(session.owner(), templateId);
        return generator.preview(
                generator.sourceOf(template),
                memberId,
                GenerationContext.by(session.member().id()));
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
        var saved = templateId == null ? null : templates.requireOwned(session.owner(), templateId);
        var draft = checks.preview(session.stationId(), request, saved);
        return generator.preview(
                DocumentGeneratorService.sourceOf(session.stationId(), draft),
                memberId,
                GenerationContext.by(session.member().id()));
    }

    /**
     * Generates a document for a member of the station and files it, as a manager does.
     *
     * @param session    the manager
     * @param templateId the template
     * @param memberId   the member, already checked to be of the station
     * @return the filed document and what was missing
     */
    public GeneratedDocumentResponse generate(StationSession session, int templateId, int memberId) {
        var template = templates.requireInUse(session.owner(), templateId);
        var prepared = generator.prepare(
                generator.sourceOf(template),
                memberId,
                GenerationContext.by(session.member().id()));
        return file(template, memberId, session.member().id(), false, prepared);
    }

    /**
     * Draws a document and files it, with its entry in the generation log.
     *
     * @param template    the template
     * @param memberId    the member it is about
     * @param generatedBy the member who generates it
     * @param selfService whether the member or their guardian generates it
     * @param prepared    the values it is drawn from
     * @return the filed document
     */
    GeneratedDocumentResponse file(
            DocumentTemplate template,
            int memberId,
            int generatedBy,
            boolean selfService,
            DocumentGeneratorService.Prepared prepared) {
        int stationId = template.stationId();
        documents.requireKept(stationId, DocumentDoor.STATION);
        var rendered = generator.render(prepared);
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
                template.hidden() && !selfService,
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
                        selfService,
                        document.id(),
                        sha256(rendered.pdf()),
                        prepared.source().pdfOriginalId()),
                rendered.resolved().subjects());
        log.info(
                "Document {} generated from template {} (version {}) for member {}",
                document.id(),
                template.id(),
                template.version(),
                memberId);
        return new GeneratedDocumentResponse(document.id(), entry.id(), rendered.title(), generator.missing(prepared));
    }

    /**
     * @param data the bytes
     * @return their SHA-256 as lowercase hex
     */
    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime carries SHA-256", e);
        }
    }
}
