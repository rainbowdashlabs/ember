/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.SealedVersion;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.repository.SealedVersionRepository;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Files sealed documents among the members' documents, locked.
 *
 * <p>A sealed document is a member document like any other for whoever reads it: it is listed, opened,
 * drawn on its tile and found by its words through the same paths, under the same read rules. What
 * differs is the lock. It is never deleted while its station exists, unless the retention of its
 * signatures is over ({@link #removeExpired}), the members it is about stay
 * those it was sealed for, it is always kept when they leave and under their name where they are
 * deleted, and its file is never replaced. A later signature files a new sealed version that becomes
 * the one the document serves; the version before stays stored, marked superseded. A document sealed
 * after it was filed keeps the file it was filed with beside its versions.
 *
 * <p>Each sealed file is stored once under its SHA-256, byte for byte as the sealer returned it, and
 * nothing that compresses or rewrites files ever reaches it. The station's quota does not refuse a
 * sealed file, since it records an act that already happened; it counts towards the store like any
 * other.
 */
@Singleton
public class SealedDocumentService {
    private static final Logger log = LoggerFactory.getLogger(SealedDocumentService.class);

    private final DocumentRepository documents;
    private final SealedVersionRepository versions;
    private final DocumentService documentService;

    @Inject
    public SealedDocumentService(
            DocumentRepository documents, SealedVersionRepository versions, DocumentService documentService) {
        this.documents = documents;
        this.versions = versions;
        this.documentService = documentService;
    }

    /**
     * Files a sealed document as a new member document, locked, with the sealed file as its first
     * version.
     *
     * @param stationId the station it belongs to, which has to keep documents
     * @param filing    what it is called and whom it is about
     * @param sealed    the sealed file
     * @return the document as filed
     */
    public Document file(int stationId, SealedFiling filing, SealedDocument sealed) {
        documentService.requireKept(stationId, DocumentDoor.STATION);
        byte[] pdf = sealed.pdf();
        String sha256 = Sha256.hex(pdf);
        var document = Transactions.call(() -> {
            var created = documents.create(
                    stationId,
                    filing.title().strip(),
                    filing.fileName(),
                    DocumentService.SEALED_TYPE,
                    pdf.length,
                    filing.hidden(),
                    true,
                    filing.uploader(),
                    filing.memberIds());
            documents.seal(created.id());
            versions.add(created.id(), sha256, pdf.length, sealed.level(), sealed.timestampedBy());
            documents.setTags(created.id(), stationId, filing.tags());
            documentService.storeSealed(stationId, sha256, pdf);
            return created;
        });
        documentService.describe(document, pdf);
        log.info(
                "Filed sealed document {} for station {} ({} at {})", document.id(), stationId, sha256, sealed.level());
        return documents.findById(document.id()).orElse(document);
    }

    /**
     * Files a sealed file as the new current version of a sealed document, superseding the one it was
     * built on. The superseded version stays stored.
     *
     * <p>Sealing again adds a signature to the file it starts from, so the new file holds every
     * signature of the version it was built on and none of a version filed since. A file built on a
     * version that is no longer current is therefore refused rather than filed over the newer one. The
     * same file offered again changes nothing.
     *
     * @param document the sealed document
     * @param basedOn  the number of the version the new file was sealed from
     * @param sealed   the new sealed file
     * @return the document as it now stands
     */
    public Document supersede(Document document, int basedOn, SealedDocument sealed) {
        documentService.requireKept(document.stationId(), DocumentDoor.STATION);
        byte[] pdf = sealed.pdf();
        String sha256 = Sha256.hex(pdf);
        boolean added = Transactions.call(() -> {
            documents.lockSealed(document.id()).orElseThrow(DocumentRefusal.DOCUMENT_NOT_SEALED::raise);
            var current = versions.current(document.id()).orElseThrow(DocumentRefusal.DOCUMENT_NOT_SEALED::raise);
            if (current.sha256().equals(sha256)) return false;
            if (current.version() != basedOn) throw DocumentRefusal.DOCUMENT_SEALED_VERSION_OUTDATED.raise();
            versions.supersedeCurrent(document.id());
            versions.add(document.id(), sha256, pdf.length, sealed.level(), sealed.timestampedBy());
            documents.setSize(document.id(), pdf.length);
            documentService.storeSealed(document.stationId(), sha256, pdf);
            return true;
        });
        if (added) {
            documentService.describe(document, pdf);
            log.info("Filed sealed version {} of document {} ({})", basedOn + 1, document.id(), sealed.level());
        }
        return documents.findById(document.id()).orElse(document);
    }

    /**
     * Files a sealed file as the current version of a document filed before, for a document whose
     * signatures are sealed into it one state after the other, each state a whole document of its own. A
     * document filed unsealed is sealed with the file as its first version and keeps the file it was filed
     * with, which its signatures bind to and every version is built from
     * ({@link DocumentService#readUploaded}); a sealed one has its current version superseded. The same
     * file offered again changes nothing.
     *
     * <p>Joins the caller's transaction where there is one, so the caller files the version together with
     * what it records about it. Neither the station's switch for documents nor its quota refuses it, since
     * it records acts that already happened.
     *
     * @param document the document
     * @param sealed   the sealed file
     * @return whether a version was added
     */
    public boolean fileVersion(Document document, SealedDocument sealed) {
        byte[] pdf = sealed.pdf();
        String sha256 = Sha256.hex(pdf);
        boolean added = Transactions.call(() -> {
            var locked = documents
                    .lock(document.id())
                    .orElseThrow(() -> new IllegalArgumentException("No document " + document.id()));
            var current = versions.current(document.id());
            if (current.isPresent() && current.get().sha256().equals(sha256)) return false;
            if (!locked.sealed()) documents.seal(document.id());
            versions.supersedeCurrent(document.id());
            var version = versions.add(document.id(), sha256, pdf.length, sealed.level(), sealed.timestampedBy());
            documents.setSize(document.id(), pdf.length);
            documentService.storeSealed(document.stationId(), sha256, pdf);
            log.info("Filed sealed version {} of document {} ({})", version.version(), document.id(), sealed.level());
            return true;
        });
        if (added) documentService.describe(document, pdf);
        return added;
    }

    /**
     * Removes a sealed document whose signatures no longer need keeping, with its versions and their files.
     *
     * <p>This is the one way a sealed document goes while its station exists, and only the retention sweep
     * of signing takes it. The database decides, not this method: it refuses the deletion unless a request
     * for signatures on the document has passed the end of its retention and none still keeps it.
     *
     * @param document the sealed document
     */
    public void removeExpired(Document document) {
        var sha256s = versions.versionsOf(document.id()).stream()
                .map(SealedVersion::sha256)
                .toList();
        documents.delete(document.id());
        documentService.deleteSealedFiles(document, sha256s);
        log.info("Removed sealed document {} of station {} after its retention", document.id(), document.stationId());
    }

    /**
     * What is said about a sealed document as it is filed.
     *
     * @param memberIds the members it is about, fixed from then on
     * @param title     what it is called
     * @param fileName  the name it is downloaded under
     * @param hidden    whether it is kept from the members it names
     * @param uploader  who files it
     * @param tags      the words it is sorted by
     */
    public record SealedFiling(
            List<Integer> memberIds,
            String title,
            String fileName,
            boolean hidden,
            Uploader uploader,
            List<String> tags) {}
}
