/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.documents.service.SealedDocumentService;
import dev.chojo.ember.feature.signing.entity.RecordTimeBasis;
import dev.chojo.ember.feature.signing.entity.SealLevel;
import dev.chojo.ember.feature.signing.entity.SealedDocument;
import dev.chojo.ember.feature.signing.entity.SealingKey;
import dev.chojo.ember.feature.signing.entity.SignatureRequestView;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import dev.chojo.ember.util.Sha256;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Seals the state a request for signatures has reached into its member document, once its acts are
 * recorded.
 *
 * <p>Each state is a document of its own: the request's frozen content, the record page of every act so
 * far and their evidence attached ({@link SigningStateAssembler}), sealed once with the station's key at
 * {@code BASELINE-LT}, or as far towards it as the timestamp services allow ({@link PdfSealer}). It is filed
 * as the current version of the request's member document ({@link SealedDocumentService#fileVersion}), which
 * is locked from its first version on and keeps every earlier version as it was. The content always comes
 * from the file the document was filed with, never from a version sealed before, so every version carries
 * the same content and each one is valid on its own.
 *
 * <p>The record page says where the document's times come from before the seal is made. A document
 * assembled expecting a timestamp whose seal came back without one is assembled again saying that no
 * service answered, and sealed without asking the services a second time.
 *
 * <p>Sealing runs after the act is recorded and outside its transaction, since it may wait for the
 * timestamp services, and it is safe to run again at any time. A version is filed only while some act on
 * the request is carried by no version yet, and only when the fields still stand as they did when it was
 * assembled; otherwise it is dropped, and the newer state is sealed by whoever changed it or by the next
 * run. The acts a version carries for the first time keep its SHA-256. An act whose sealing failed is
 * sealed with the next act on the request or by {@link SigningStateSweeper}.
 */
@Singleton
public class SigningStateSealer {
    private static final Logger log = LoggerFactory.getLogger(SigningStateSealer.class);

    private final SignatureRequestRepository requests;
    private final SigningEvidenceRepository evidence;
    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final SealedDocumentService sealedDocuments;
    private final StationSigningKeys keys;
    private final SigningStateAssembler assembler;
    private final PdfSealer sealer;

    @Inject
    public SigningStateSealer(
            SignatureRequestRepository requests,
            SigningEvidenceRepository evidence,
            DocumentRepository documents,
            DocumentService documentService,
            SealedDocumentService sealedDocuments,
            StationSigningKeys keys,
            SigningStateAssembler assembler,
            PdfSealer sealer) {
        this.requests = requests;
        this.evidence = evidence;
        this.documents = documents;
        this.documentService = documentService;
        this.sealedDocuments = sealedDocuments;
        this.keys = keys;
        this.assembler = assembler;
        this.sealer = sealer;
    }

    /**
     * Seals the state a request stands at into its document, when an act on it is not sealed yet.
     *
     * @param requestId the request
     * @return whether a version was filed
     * @throws IllegalStateException    when the request's document or the file it was filed with is gone
     * @throws IllegalArgumentException when that file is no longer the request's frozen content
     * @throws SigningKeyWrapException  when the station's key does not open
     */
    public boolean sealLatest(int requestId) {
        var state = unsealedState(requestId);
        if (state.isEmpty()) return false;
        return file(state.get(), seal(state.get()));
    }

    /**
     * The request as it stands, with its document and the content to seal, read together while the request
     * is held, so fields and evidence agree.
     *
     * @return the state, or empty when the request is gone or every act on it is sealed
     */
    Optional<UnsealedState> unsealedState(int requestId) {
        var view = Transactions.call(() -> requests.lockRequest(requestId)
                .map(request -> new SignatureRequestView(
                        request, requests.fieldsOf(requestId), evidence.evidenceOf(requestId))));
        if (view.isEmpty() || allSealed(view.get().evidence())) return Optional.empty();
        var request = view.get().request();
        Integer documentId = request.documentId();
        if (documentId == null) {
            throw new IllegalStateException("The document of signing request " + request.uid() + " is gone");
        }
        Document document = documents
                .findById(documentId)
                .orElseThrow(() -> new IllegalStateException("No document " + documentId));
        byte[] content = documentService
                .readUploaded(document)
                .orElseThrow(
                        () -> new IllegalStateException("The file document " + documentId + " was filed with is gone"));
        return Optional.of(new UnsealedState(view.get(), document, content));
    }

    /** Assembles the state and seals it, again without a timestamp where none came back. */
    SealedDocument seal(UnsealedState state) {
        SealingKey key = keys.forStation(state.view().request().stationId());
        RecordTimeBasis expected = assembler.expectedTimeBasis();
        var assembled = assembler.assemble(state.view(), state.content(), key.authority(), expected);
        var sealed = sealer.seal(assembled.pdf(), key.privateKey(), key.chain());
        if (expected != RecordTimeBasis.TIMESTAMP_SERVICE || sealed.level() != SealLevel.BASELINE_B) return sealed;
        var unstamped =
                assembler.assemble(state.view(), state.content(), key.authority(), RecordTimeBasis.NO_SERVICE_ANSWERED);
        return sealer.sealWithoutTimestamp(unstamped.pdf(), key.privateKey(), key.chain());
    }

    /**
     * Files a sealed state as the document's current version, unless the request moved on since it was
     * read or a version already carries every act.
     *
     * @return whether it was filed
     */
    boolean file(UnsealedState state, SealedDocument sealed) {
        var request = state.view().request();
        return Transactions.call(() -> {
            if (requests.lockRequest(request.id()).isEmpty()) return false;
            if (!requests.fieldsOf(request.id()).equals(state.view().fields())) {
                log.info(
                        "Signing request {} changed while its state was sealed; the newer state is sealed instead",
                        request.uid());
                return false;
            }
            if (allSealed(evidence.evidenceOf(request.id()))) return false;
            sealedDocuments.fileVersion(state.document(), sealed);
            int carried = evidence.markSealed(request.id(), Sha256.hex(sealed.pdf()));
            log.info(
                    "Sealed {} new acts of signing request {} into document {} ({})",
                    carried,
                    request.uid(),
                    state.document().id(),
                    sealed.level());
            return true;
        });
    }

    private static boolean allSealed(List<StoredEvidence> acts) {
        return acts.stream().map(StoredEvidence::sealedSha256).allMatch(Objects::nonNull);
    }

    /**
     * A request with an act no version carries yet, as read for sealing.
     *
     * <p>The array is handed over as it is, without a copy.
     *
     * @param view     the request, its fields and the evidence of every act on them
     * @param document the member document the request is on
     * @param content  the file the document was filed with, the request's frozen content
     */
    record UnsealedState(SignatureRequestView view, Document document, byte[] content) {}
}
