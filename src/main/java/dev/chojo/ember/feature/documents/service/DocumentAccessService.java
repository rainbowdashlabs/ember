/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Who may list, read, add and change the documents a station keeps.
 *
 * <p>Two things decide. The permissions of the store say what somebody may do with the documents of
 * everybody. Beside them, a member's own paperwork is their business, and the business of whoever
 * looks after them: a guardian is handed the forms of the child in their care, the medical note, the
 * consent, the certificate the station holds. That relation is {@link GuardianPolicy}'s to answer, so
 * documents read it the same way registrations and forms do.
 *
 * <p>A hidden document is hidden from the member it names and so from their guardian too; only the
 * permission to read member documents reaches it.
 */
@Singleton
public class DocumentAccessService {
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final GuardianPolicy guardianPolicy;

    @Inject
    public DocumentAccessService(
            DocumentRepository documentRepository, DocumentService documentService, GuardianPolicy guardianPolicy) {
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.guardianPolicy = guardianPolicy;
    }

    /**
     * Whether the reader sees the documents of every member, hidden ones included, rather than only
     * the ones of their own household.
     */
    public boolean readsEveryMember(StationSession session) {
        return session.hasPermission(StationPermission.DOCUMENT_READ_MEMBER);
    }

    /**
     * Refuses the list of a member's documents to a reader who is neither allowed to read every
     * member's nor that member or their guardian.
     *
     * @param session  the reader
     * @param memberId the member whose documents are listed
     */
    public void requireMayList(StationSession session, int memberId) {
        if (readsEveryMember(session)) return;
        if (!guardianPolicy.mayActFor(session.user(), memberId)) throw DocumentRefusal.DOCUMENT_LIST_NOT_YOURS.raise();
    }

    /**
     * Refuses a document the reader may not see.
     *
     * <p>The permissions of the store come first, and which one counts follows the document: see
     * {@link DocumentService#mayRead(Document, boolean, boolean)}. Without them, a document is readable
     * where it names the reader or somebody in their care and is not hidden.
     *
     * @param session  the reader
     * @param document the document being read
     */
    public void requireReadable(StationSession session, Document document) {
        boolean byPermission = documentService.mayRead(
                document, readsEveryMember(session), session.hasPermission(StationPermission.DOCUMENT_READ));
        if (byPermission) return;
        if (document.hidden()) throw DocumentRefusal.DOCUMENT_HIDDEN_FROM_YOU.raise();
        boolean aboutTheHousehold = guardianPolicy.household(session.user()).stream()
                .anyMatch(member -> documentRepository.isBoundTo(document.id(), member));
        if (!aboutTheHousehold) throw DocumentRefusal.DOCUMENT_NOT_YOURS_TO_READ.raise();
    }

    /**
     * Refuses a change to a document the reader may not make. The station's own paperwork needs the
     * permission to edit the store, and a document that names a member needs the permission for
     * member documents.
     *
     * @param session    the reader
     * @param documentId the document being changed
     */
    public void requireMayEdit(StationSession session, int documentId) {
        var needed = documentRepository.hasNoMembers(documentId)
                ? StationPermission.DOCUMENT_EDIT
                : StationPermission.DOCUMENT_EDIT_MEMBER;
        if (!session.hasPermission(needed)) throw DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE.raise();
    }

    /**
     * Refuses removing a document the reader may not remove. Whoever put a document in may take it
     * out again; anybody else needs the permission to change it.
     *
     * @param session  the reader
     * @param document the document being removed
     */
    public void requireMayDelete(StationSession session, Document document) {
        Integer uploadedBy = document.uploadedBy();
        boolean ownUpload = uploadedBy != null && uploadedBy == session.member().id();
        if (!ownUpload) requireMayEdit(session, document.id());
    }

    /**
     * Refuses putting a document on a member to a reader who may not. The permission for member
     * documents allows it for everybody; a member allowed to upload their own may put one on
     * themselves.
     *
     * @param session  the reader
     * @param memberId the member the document is put on
     */
    public void requireMayUpload(StationSession session, int memberId) {
        if (session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) return;
        boolean self = session.member().id() == memberId;
        if (self && session.hasPermission(StationPermission.MEMBER_SELF_UPLOAD)) return;
        throw DocumentRefusal.DOCUMENT_NOT_YOURS_TO_ADD.raise();
    }

    /**
     * Refuses marking a document as hidden to a reader who could then not see it themselves.
     *
     * @param session the reader
     * @param hidden  whether the document is to be hidden
     */
    public void requireMayHide(StationSession session, boolean hidden) {
        if (hidden && !session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER)) {
            throw DocumentRefusal.DOCUMENT_HIDING_NOT_ALLOWED.raise();
        }
    }

    /**
     * Refuses tags, or keeping a document past the membership, to a reader who may only put a document
     * on themselves. A tag is written into the station's own list for everybody, and what outlasts a
     * membership is the station's decision, not the member's.
     *
     * @param session       the reader
     * @param keepOnArchive whether the document is to outlast the membership
     * @param tags          the words it is to be sorted by
     */
    public void requireMayLabel(StationSession session, boolean keepOnArchive, List<String> tags) {
        if (!keepOnArchive && tags.isEmpty()) return;
        boolean edits = session.hasPermission(StationPermission.DOCUMENT_EDIT)
                || session.hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER);
        if (!edits) throw DocumentRefusal.DOCUMENT_LABELS_NOT_YOURS_TO_SET.raise();
    }
}
