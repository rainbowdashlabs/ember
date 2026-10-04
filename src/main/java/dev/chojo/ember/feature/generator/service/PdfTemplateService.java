/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentIntake;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateKind;
import dev.chojo.ember.feature.generator.entity.PdfInspection;
import dev.chojo.ember.feature.generator.entity.PdfOriginal;
import dev.chojo.ember.feature.generator.repository.DocumentTemplateRepository;
import dev.chojo.ember.feature.generator.repository.PdfTemplateRepository;
import dev.chojo.ember.feature.generator.service.DocumentTemplateService.DocumentTemplateResponse;
import dev.chojo.ember.feature.generator.service.pdf.PdfFiles;
import dev.chojo.ember.feature.generator.service.pdf.PdfInspector;
import dev.chojo.ember.feature.generator.service.store.OwnerStores;
import dev.chojo.ember.feature.storage.entity.StorageCategory;
import dev.chojo.ember.feature.storage.entity.StorageScope;
import dev.chojo.ember.feature.storage.service.StorageQuotaService;
import dev.chojo.ember.feature.storage.service.StorageService;
import dev.chojo.ember.owner.Owner;
import dev.chojo.ember.util.Sha256;
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
 *
 * <p>The file is kept by the template's owner: a station's in its own storage and room, an association's
 * in the association scope of its home station, counted against the room it was given there.
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
    private final OwnerStores stores;

    @Inject
    public PdfTemplateService(
            DocumentTemplateService templateService,
            DocumentTemplateRepository templates,
            PdfTemplateRepository pdfTemplates,
            DocumentIntake intake,
            StorageService storage,
            OwnerStores stores) {
        this.templateService = templateService;
        this.templates = templates;
        this.pdfTemplates = pdfTemplates;
        this.intake = intake;
        this.storage = storage;
        this.stores = stores;
    }

    /**
     * Takes in a new version of a PDF template's PDF.
     *
     * @param owner      the station or the association that keeps the template
     * @param templateId the template
     * @param file       the upload, or null where the request carried none
     * @param authorId   the account that uploads it
     * @return the template as it now stands
     */
    public DocumentTemplateResponse upload(Owner owner, int templateId, @Nullable UploadedFile file, int authorId) {
        var template = templateService.requireOwned(owner, templateId);
        if (template.kind() != DocumentTemplateKind.PDF) throw DocumentRefusal.DOCUMENT_TEMPLATE_NOT_PDF.raise();
        int room = roomOf(owner);
        var refusals = DocumentDoor.STATION.intake();
        var upload = intake.read(room, file, refusals);
        String type = intake.take(room, categoryOf(owner), upload, refusals);
        if (!PDF.equals(type)) throw DocumentRefusal.DOCUMENT_TEMPLATE_PDF_NOT_A_PDF.raise();
        var inspection = inspect(upload.data());
        var original = Transactions.call(() -> {
            var written = keep(owner, templateId, upload, inspection, authorId);
            templates.countVersion(templateId, authorId);
            return written;
        });
        log.info("PDF {} uploaded for document template {} of {}", original.id(), templateId, owner);
        return templateService.detail(owner, templateId);
    }

    /**
     * Gives a copy of a PDF template the PDF the template fills now, as a stored file of its own in the
     * storage of the copy's owner and counted against that owner's room, so neither template's file
     * depends on the other's. Nothing happens where the template has no PDF yet.
     *
     * @param source   the template copied
     * @param owner    the station or the association that keeps the copy
     * @param copyId   the copy
     * @param authorId the account that makes the copy
     */
    void copyCurrent(DocumentTemplate source, Owner owner, int copyId, int authorId) {
        var original = pdfTemplates.findCurrentOriginal(source.id()).orElse(null);
        if (original == null) return;
        byte[] data =
                read(source.owner(), original).orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_COPY_PDF_GONE::raise);
        var upload = new DocumentIntake.Upload(original.fileName(), PDF, data);
        intake.take(roomOf(owner), categoryOf(owner), upload, DocumentDoor.STATION.intake());
        var written = keep(owner, copyId, upload, original.inspection(), authorId);
        log.info(
                "PDF {} of document template {} copied as PDF {} for {}",
                original.id(),
                source.id(),
                written.id(),
                owner);
    }

    /** Writes a PDF as the one a template fills now and stores its bytes with the template's owner. */
    private PdfOriginal keep(
            Owner owner, int templateId, DocumentIntake.Upload upload, PdfInspection inspection, int authorId) {
        var written = pdfTemplates.addOriginal(
                templateId, upload.fileName(), upload.data().length, Sha256.hex(upload.data()), inspection, authorId);
        storage.store(scope(owner), categoryOf(owner), key(written), upload.data(), PDF);
        return written;
    }

    /** The station whose room the owner's files are counted against. */
    private int roomOf(Owner owner) {
        return StorageQuotaService.roomOf(scope(owner)).orElseThrow(DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE::raise);
    }

    /**
     * The PDF a PDF template fills now, as it was uploaded.
     *
     * @param owner      the station or the association that keeps the template
     * @param templateId the template
     * @return the original and its bytes, or empty where none was uploaded
     */
    public Optional<Download> current(Owner owner, int templateId) {
        templateService.requireOwned(owner, templateId);
        return pdfTemplates.findCurrentOriginal(templateId).flatMap(original -> read(owner, original)
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
     * @param owner    the station or the association that keeps it
     * @param original an uploaded version of a template's PDF
     * @return its bytes, or empty where the stored file is gone
     */
    public Optional<byte[]> read(Owner owner, PdfOriginal original) {
        return storage.readAllBytes(scope(owner), categoryOf(owner), key(original));
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

    private StorageScope scope(Owner owner) {
        return stores.scopeOf(owner, DocumentRefusal.DOCUMENT_TEMPLATE_NOT_HERE);
    }

    /**
     * @param owner the station or the association that keeps the template
     * @return what its PDFs are kept as
     */
    static StorageCategory categoryOf(Owner owner) {
        return owner instanceof Owner.Association
                ? StorageCategory.ASSOCIATION_DOCUMENT_TEMPLATES
                : StorageCategory.DOCUMENT_TEMPLATES;
    }

    private static String key(PdfOriginal original) {
        return original.id() + "/original";
    }
}
