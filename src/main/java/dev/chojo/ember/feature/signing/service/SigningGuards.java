/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentAccessService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.signing.entity.SignatureRequest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Who may see a request for signatures and who may ask for, confirm, waive or withdraw signatures.
 *
 * <p>A request is part of the document it is on, and so follows the document's read rules: whoever may read
 * the member document may read what is asked of it and the evidence of every act on it, a guardian included,
 * and health data in a consent form reaches nobody beyond that. Asking for signatures and settling a field
 * is a change to a member's paperwork and needs the permission for member documents. Where the document
 * was deleted, the request falls back to the member it is about.
 */
@Singleton
public class SigningGuards {
    private final DocumentAccessService documentAccess;
    private final DocumentRepository documents;
    private final GuardianPolicy guardianPolicy;

    @Inject
    public SigningGuards(
            DocumentAccessService documentAccess, DocumentRepository documents, GuardianPolicy guardianPolicy) {
        this.documentAccess = documentAccess;
        this.documents = documents;
        this.guardianPolicy = guardianPolicy;
    }

    /**
     * Refuses a request to a reader who may not read its document.
     *
     * @param session the reader
     * @param request the request
     */
    public void requireReadable(StationSession session, SignatureRequest request) {
        Integer documentId = request.documentId();
        var document =
                documentId == null ? null : documents.findById(documentId).orElse(null);
        if (document != null) {
            documentAccess.requireReadable(session, document);
            return;
        }
        if (documentAccess.readsEveryMember(session)) return;
        Integer memberId = request.memberId();
        if (memberId == null || !guardianPolicy.mayActFor(session.user(), memberId)) {
            throw DocumentRefusal.DOCUMENT_NOT_YOURS_TO_READ.raise();
        }
    }

    /**
     * Refuses asking for, confirming, waiving or withdrawing signatures to a reader who may not change the
     * document.
     *
     * @param session    the reader
     * @param documentId the document the signatures are on, or null once it was deleted
     */
    public void requireMayManage(StationSession session, @Nullable Integer documentId) {
        if (documentId != null && documents.findById(documentId).isPresent()) {
            documentAccess.requireMayEdit(session, documentId);
            return;
        }
        if (!session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) {
            throw DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE.raise();
        }
    }
}
