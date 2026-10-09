/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.signing.entity.ManagedSignatureRequest;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import dev.chojo.ember.feature.signing.entity.StoredEvidence;
import dev.chojo.ember.feature.signing.repository.SignatureRequestRepository;
import dev.chojo.ember.feature.signing.repository.SigningEvidenceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the manager of a document does with the signatures asked for on it: look at each field, confirm one
 * on paper, waive or withdraw it, withdraw the whole request, or ask anew on a corrected document.
 *
 * <p>Every action needs the request to be the caller's station's and the right to change the document
 * ({@link SigningGuards#requireMayManage}), and each answers the request as it stands afterwards. A field nobody can sign, such as a guardian place nobody
 * holds, stays open until a manager settles it here. A settled field is sealed into the document's next
 * version by {@link SigningStateSweeper}, as every manager's action is.
 */
@Singleton
public class SignatureManagementService {
    /** How many corrected documents the view offers at most. */
    private static final int CORRECTIONS_SHOWN = 20;

    private final SignatureRequestService requestService;
    private final SignatureFieldService fieldService;
    private final SignatureRequestRepository requests;
    private final SigningEvidenceRepository evidence;
    private final DocumentRepository documents;
    private final SigningGuards guards;

    @Inject
    public SignatureManagementService(
            SignatureRequestService requestService,
            SignatureFieldService fieldService,
            SignatureRequestRepository requests,
            SigningEvidenceRepository evidence,
            DocumentRepository documents,
            SigningGuards guards) {
        this.requestService = requestService;
        this.fieldService = fieldService;
        this.requests = requests;
        this.evidence = evidence;
        this.documents = documents;
        this.guards = guards;
    }

    /**
     * A request with every field, who is asked to sign it, how it stands and the act that signed it.
     *
     * @param session    the manager, who has to be allowed to change the document
     * @param requestUid the request
     * @return the request as it stands
     */
    public ManagedSignatureRequest requireOwnedView(StationSession session, UUID requestUid) {
        var request = requestService.requestAt(session, requestUid);
        guards.requireMayManage(session, request.documentId());
        return managed(request);
    }

    /**
     * Records that a field was signed on paper.
     *
     * @param session    the manager
     * @param requestUid the request
     * @param fieldName  the field
     * @return the request as it now stands
     */
    public ManagedSignatureRequest requireOwnedThenConfirmOnPaper(
            StationSession session, UUID requestUid, String fieldName) {
        fieldService.confirmOnPaper(session, requestUid, fieldName);
        return requireOwnedView(session, requestUid);
    }

    /**
     * Lets a field go without a signature.
     *
     * @param session    the manager
     * @param requestUid the request
     * @param fieldName  the field
     * @return the request as it now stands
     */
    public ManagedSignatureRequest requireOwnedThenWaive(StationSession session, UUID requestUid, String fieldName) {
        fieldService.waive(session, requestUid, fieldName);
        return requireOwnedView(session, requestUid);
    }

    /**
     * Stops asking for one field's signature.
     *
     * @param session    the manager
     * @param requestUid the request
     * @param fieldName  the field
     * @return the request as it now stands
     */
    public ManagedSignatureRequest requireOwnedThenWithdrawField(
            StationSession session, UUID requestUid, String fieldName) {
        fieldService.withdraw(session, requestUid, fieldName);
        return requireOwnedView(session, requestUid);
    }

    /**
     * Stops asking for every signature the request still waits for; what was signed stays.
     *
     * @param session    the manager
     * @param requestUid the request
     * @return the request as it now stands
     */
    public ManagedSignatureRequest requireOwnedThenWithdraw(StationSession session, UUID requestUid) {
        requestService.withdraw(session, requestUid);
        return requireOwnedView(session, requestUid);
    }

    /**
     * Asks the request's signatures anew on a document generated again for the same member after a change.
     * The old request is replaced and its open fields withdrawn; what was signed on it stays.
     *
     * @param session      the manager, who has to be allowed to change both documents
     * @param requestUid   the request to replace
     * @param generationId the generation log entry of the corrected document, or null where none was named
     * @return the new request as it stands
     */
    public ManagedSignatureRequest requireOwnedThenRectify(
            StationSession session, UUID requestUid, @Nullable Integer generationId) {
        if (generationId == null) throw DocumentRefusal.SIGNING_CORRECTION_NOT_NAMED.raise();
        var replacement = requestService.rectify(session, requestUid, generationId);
        return requireOwnedView(session, replacement.uid());
    }

    private ManagedSignatureRequest managed(SignatureRequest request) {
        var nobodyCanSign = requests.fieldsNobodyCanSign(request.id());
        Map<Integer, StoredEvidence> acts = evidence.evidenceOf(request.id()).stream()
                .collect(Collectors.toMap(StoredEvidence::fieldId, Function.identity()));
        var fields = requests.fieldsOf(request.id()).stream()
                .map(field -> new ManagedSignatureRequest.ManagedField(
                        field, nobodyCanSign.contains(field.id()), acts.get(field.id())))
                .toList();
        return new ManagedSignatureRequest(
                request, titleOf(request), supersededBy(request), fields, correctionsOf(request));
    }

    private @Nullable String titleOf(SignatureRequest request) {
        Integer documentId = request.documentId();
        if (documentId == null) return null;
        return documents.findById(documentId).map(Document::title).orElse(null);
    }

    private @Nullable UUID supersededBy(SignatureRequest request) {
        Integer replacement = request.supersededBy();
        if (replacement == null) return null;
        return requests.findById(replacement).map(SignatureRequest::uid).orElse(null);
    }

    private List<ManagedSignatureRequest.Correction> correctionsOf(SignatureRequest request) {
        return switch (request.state()) {
            case OPEN, COMPLETE -> requests.corrections(request, CORRECTIONS_SHOWN);
            case WITHDRAWN, SUPERSEDED, REVOKED -> List.of();
        };
    }
}
