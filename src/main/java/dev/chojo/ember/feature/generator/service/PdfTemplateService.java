/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.PdfInspection;
import dev.chojo.ember.feature.generator.entity.PdfOriginal;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.generator.service.pdf.PdfInspector;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.sql.Transactions;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Optional;

/**
 * The PDFs of PDF templates: taking a new version in, and reading one back.
 *
 * <p>A PDF passes the checks every uploaded file passes (size, room, its bytes deciding what it is),
 * then has to be a PDF that opens without a password; an owner password only, which forbids changes
 * but not opening, is taken off when the document is filled. Its pages and form fields are read once
 * and kept beside it.
 *
 * <p>A new version never replaces the old one. It becomes the one the template fills, the template's
 * version counts up, and the fields stay as they were, to be checked over the new pages; documents
 * generated before keep naming the version they were filled from.
 */
@Singleton
public class PdfTemplateService {
    private static final Logger log = LoggerFactory.getLogger(PdfTemplateService.class);
    private static final String PDF = "application/pdf";

    private final DocumentTemplateService templateService;
    private final DocumentTemplateRepository templates;
    private final PdfTemplateRepository pdfTemplates;
    private final DocumentIntake intake;
    private final StorageService storage;
    private final StationRepository stations;

    @Inject
    public PdfTemplateService(
            DocumentTemplateService templateService,
            DocumentTemplateRepository templates,
            PdfTemplateRepository pdfTemplates,
            DocumentIntake intake,
            StorageService storage,
            StationRepository stations) {
        this.templateService = templateService;
        this.templates = templates;
        this.pdfTemplates = pdfTemplates;
        this.intake = intake;
        this.storage = storage;
        this.stations = stations;
    }

    /**
     * Takes in a new version of a PDF template's PDF.
     *
     * @param owner      the owner
     * @param templateId the template
     * @param file       the upload, or null where the request carried none
     * @param authorId   the member who uploads it
     * @return the template as it now stands
     */
    public DocumentTemplateResponse upload(
            Owner.Station owner, int templateId, @Nullable UploadedFile file, int authorId) {
        var template = templateService.requireOwned(owner, templateId);
        if (template.kind() != DocumentTemplateKind.PDF) throw DocumentRefusal.DOCUMENT_TEMPLATE_NOT_PDF.raise();
        int stationId = owner.stationId();
        var refusals = DocumentDoor.STATION.intake();
        var upload = intake.read(stationId, file, refusals);
        String type = intake.take(stationId, StorageCategory.DOCUMENT_TEMPLATES, upload, refusals);
        if (!PDF.equals(type)) throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_NOT_A_PDF.raise();
        var inspection = inspect(upload.data());
        var original = Transactions.call(() -> {
            var written = pdfTemplates.addOriginal(
                    templateId,
                    upload.fileName(),
                    upload.data().length,
                    DocumentGenerationService.sha256(upload.data()),
                    inspection,
                    authorId);
            templates.countVersion(templateId, authorId);
            storage.store(scope(stationId), StorageCategory.DOCUMENT_TEMPLATES, key(written), upload.data(), PDF);
            return written;
        });
        log.info("PDF {} uploaded for document template {} at station {}", original.id(), templateId, stationId);
        return templateService.detail(owner, templateId);
    }

    /**
     * The PDF a PDF template fills now, as it was uploaded.
     *
     * @param owner      the owner
     * @param templateId the template
     * @return the original and its bytes, or empty where none was uploaded
     */
    public Optional<Download> current(Owner.Station owner, int templateId) {
        templateService.requireOwned(owner, templateId);
        return pdfTemplates.findCurrentOriginal(templateId).flatMap(original -> read(owner.stationId(), original)
                .map(data -> new Download(original.fileName(), data)));
    }

    /**
     * An original PDF with its bytes.
     *
     * @param fileName the name it was uploaded under
     * @param data     its bytes
     */
    public record Download(String fileName, byte[] data) {}

    /**
     * @param stationId the station that keeps it
     * @param original  an uploaded version of a template's PDF
     * @return its bytes, or empty where the stored file is gone
     */
    public Optional<byte[]> read(int stationId, PdfOriginal original) {
        return storage.readAllBytes(scope(stationId), StorageCategory.DOCUMENT_TEMPLATES, key(original));
    }

    /**
     * Opens a PDF the way it will be filled and reads its pages and form fields, refusing one that does
     * not open, has no pages, or keeps a protection that cannot be taken off.
     */
    private static PdfInspection inspect(byte[] data) {
        try (var document = PdfFiles.open(data)) {
            var inspection = PdfInspector.inspect(document);
            if (inspection.pages().isEmpty()) throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_UNREADABLE.raise();
            if (document.isEncrypted()) PdfFiles.save(document);
            return inspection;
        } catch (IOException e) {
            throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_PROTECTED.raise();
        }
    }

    private StorageScope.Station scope(int stationId) {
        return new StorageScope.Station(stationId, stations.requireUid(stationId));
    }

    private static String key(PdfOriginal original) {
        return original.id() + "/original";
    }
}
