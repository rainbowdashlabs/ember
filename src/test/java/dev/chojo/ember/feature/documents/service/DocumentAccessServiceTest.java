/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.DocumentRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Who may list, read, add, change and remove the documents a station keeps. */
class DocumentAccessServiceTest {

    private static final int STATION_ID = 1;
    private static final UUID STATION_UID = UUID.randomUUID();
    private static final int GUARDIAN = 10;
    private static final int WARD = 11;
    private static final int STRANGER = 12;
    private static final int DOCUMENT = 100;

    private DocumentRepository documentRepository;
    private DocumentService documentService;
    private GuardianPolicy guardianPolicy;
    private DocumentAccessService access;

    @BeforeEach
    void setup() {
        documentRepository = mock(DocumentRepository.class);
        documentService = mock(DocumentService.class);
        guardianPolicy = mock(GuardianPolicy.class);
        when(guardianPolicy.mayActFor(any(), anyInt())).thenAnswer(invocation -> {
            UserSession session = invocation.getArgument(0);
            int memberId = invocation.getArgument(1);
            return session.member() != null && household(session).contains(memberId);
        });
        when(guardianPolicy.household(any())).thenAnswer(invocation -> household(invocation.getArgument(0)));
        when(documentService.mayRead(any(), anyBoolean(), anyBoolean())).thenReturn(false);
        access = new DocumentAccessService(documentRepository, documentService, guardianPolicy);
    }

    private static List<Integer> household(UserSession session) {
        if (session.member() == null) return List.of();
        int id = session.member().id();
        return id == GUARDIAN ? List.of(GUARDIAN, WARD) : List.of(id);
    }

    private static StationSession sessionOf(int memberId, StationPermission... permissions) {
        var member = new StationMember(
                memberId, STATION_ID, UUID.randomUUID(), memberId, false, null, "M", StationUserType.MEMBER, null);
        return StationSession.of(new UserSession(
                new Account(1, null, "wer@test.com", null, "Wer", "Da", true, null, "Wer Da", null, null),
                1,
                STATION_ID,
                STATION_UID,
                member,
                Set.of(permissions),
                Set.of(),
                null));
    }

    private static Document document(boolean hidden, Integer uploadedBy) {
        return new Document(
                DOCUMENT,
                STATION_ID,
                "Einverständnis",
                "e.pdf",
                "application/pdf",
                10,
                hidden,
                false,
                false,
                uploadedBy,
                null,
                Instant.now(),
                false);
    }

    private static void assertRefused(Refusal refusal, Executable call) {
        assertEquals(refusal, assertThrows(RefusalResponse.class, call).refusal());
    }

    @Test
    void aMemberAndTheirGuardianMayListTheirDocuments() {
        assertDoesNotThrow(() -> access.requireMayList(sessionOf(WARD), WARD));
        assertDoesNotThrow(() -> access.requireMayList(sessionOf(GUARDIAN), WARD));
        assertRefused(DocumentRefusal.DOCUMENT_LIST_NOT_YOURS, () -> access.requireMayList(sessionOf(STRANGER), WARD));
    }

    @Test
    void readingEveryMembersDocumentsListsAnybody() {
        var reader = sessionOf(STRANGER, StationPermission.DOCUMENT_READ_MEMBER);

        assertTrue(access.readsEveryMember(reader));
        assertFalse(access.readsEveryMember(sessionOf(GUARDIAN)));
        assertDoesNotThrow(() -> access.requireMayList(reader, WARD));
    }

    /** A guardian reads the paperwork of the child in their care, and a stranger does not. */
    @Test
    void aGuardianReadsTheDocumentsOfTheirWard() {
        when(documentRepository.isBoundTo(DOCUMENT, WARD)).thenReturn(true);
        var onTheWard = document(false, null);

        assertDoesNotThrow(() -> access.requireReadable(sessionOf(WARD), onTheWard));
        assertDoesNotThrow(() -> access.requireReadable(sessionOf(GUARDIAN), onTheWard));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_READ,
                () -> access.requireReadable(sessionOf(STRANGER), onTheWard));
    }

    /** Hiding a document hides it from the member it names, and so from their guardian. */
    @Test
    void aHiddenDocumentIsHiddenFromTheHousehold() {
        when(documentRepository.isBoundTo(DOCUMENT, WARD)).thenReturn(true);
        var hidden = document(true, null);

        assertRefused(
                DocumentRefusal.DOCUMENT_HIDDEN_FROM_YOU, () -> access.requireReadable(sessionOf(GUARDIAN), hidden));
        assertRefused(DocumentRefusal.DOCUMENT_HIDDEN_FROM_YOU, () -> access.requireReadable(sessionOf(WARD), hidden));
    }

    @Test
    void thePermissionsOfTheStoreReadWhatTheyCover() {
        var hidden = document(true, null);
        when(documentService.mayRead(hidden, true, false)).thenReturn(true);

        assertDoesNotThrow(
                () -> access.requireReadable(sessionOf(STRANGER, StationPermission.DOCUMENT_READ_MEMBER), hidden));
    }

    /**
     * A member who may only put documents on themselves may not tag them, since a tag is written into the
     * station's own list, nor keep them past the membership, which is the station's decision.
     */
    @Test
    void labellingAnUploadNeedsTheRightToEditTheStore() {
        var self = sessionOf(WARD, StationPermission.MEMBER_SELF_UPLOAD);

        assertDoesNotThrow(() -> access.requireMayLabel(self, false, List.of()));
        assertRefused(
                DocumentRefusal.DOCUMENT_LABELS_NOT_YOURS_TO_SET, () -> access.requireMayLabel(self, true, List.of()));
        assertRefused(
                DocumentRefusal.DOCUMENT_LABELS_NOT_YOURS_TO_SET,
                () -> access.requireMayLabel(self, false, List.of("Neu")));
        assertDoesNotThrow(() -> access.requireMayLabel(
                sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT_MEMBER), true, List.of("Neu")));
        assertDoesNotThrow(() ->
                access.requireMayLabel(sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT), true, List.of("Neu")));
    }

    @Test
    void changingFollowsTheDocument() {
        when(documentRepository.hasNoMembers(DOCUMENT)).thenReturn(true);
        assertDoesNotThrow(() -> access.requireMayEdit(sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT), DOCUMENT));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> access.requireMayEdit(sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT_MEMBER), DOCUMENT));

        when(documentRepository.hasNoMembers(DOCUMENT)).thenReturn(false);
        assertDoesNotThrow(
                () -> access.requireMayEdit(sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT_MEMBER), DOCUMENT));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> access.requireMayEdit(sessionOf(GUARDIAN, StationPermission.DOCUMENT_EDIT), DOCUMENT));
    }

    @Test
    void whoeverPutADocumentInMayTakeItOut() {
        assertDoesNotThrow(() -> access.requireMayDelete(sessionOf(GUARDIAN), document(false, GUARDIAN)));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> access.requireMayDelete(sessionOf(GUARDIAN), document(false, STRANGER)));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_CHANGE,
                () -> access.requireMayDelete(sessionOf(GUARDIAN), document(false, null)));
    }

    @Test
    void aMemberUploadsOntoThemselvesOnlyWhereTheyMay() {
        assertDoesNotThrow(() -> access.requireMayUpload(sessionOf(WARD, StationPermission.MEMBER_SELF_UPLOAD), WARD));
        assertDoesNotThrow(
                () -> access.requireMayUpload(sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT_MEMBER), WARD));
        assertRefused(DocumentRefusal.DOCUMENT_NOT_YOURS_TO_ADD, () -> access.requireMayUpload(sessionOf(WARD), WARD));
        assertRefused(
                DocumentRefusal.DOCUMENT_NOT_YOURS_TO_ADD,
                () -> access.requireMayUpload(sessionOf(GUARDIAN, StationPermission.MEMBER_SELF_UPLOAD), WARD));
    }

    @Test
    void onlyWhoeverReadsMemberDocumentsMayHideOne() {
        assertDoesNotThrow(() -> access.requireMayHide(sessionOf(WARD), false));
        assertDoesNotThrow(
                () -> access.requireMayHide(sessionOf(STRANGER, StationPermission.DOCUMENT_EDIT_MEMBER), true));
        assertRefused(DocumentRefusal.DOCUMENT_HIDING_NOT_ALLOWED, () -> access.requireMayHide(sessionOf(WARD), true));
    }
}
