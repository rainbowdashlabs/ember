/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the document permissions reach, and what they deliberately do not.
 */
class DocumentPermissionTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static int memberId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Permission Station");
        account = accountRepo.create("doc-permission@test.com", "Doc", "Permission");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    /**
     * Reading the store used to mean holding the permission to read members, so the new permissions
     * are granted by implication rather than by migration. Without this a custom role holding only
     * {@code MEMBER_READ} would have lost sight of the documents it can see today, which is a
     * regression nobody asked for, and putting it right would have needed a migration over every
     * station.
     */
    @Test
    void readingMembersStillReachesTheStore() {
        assertTrue(StationPermission.MEMBER_READ.allChildren().contains(StationPermission.DOCUMENT_READ));
        assertTrue(StationPermission.MEMBER_READ.allChildren().contains(StationPermission.DOCUMENT_READ_MEMBER));
    }

    /** Editing members still files documents, member documents included, for the same reason. */
    @Test
    void editingMembersStillFilesDocuments() {
        assertTrue(StationPermission.MEMBER_EDIT.allChildren().contains(StationPermission.DOCUMENT_EDIT));
        assertTrue(StationPermission.MEMBER_EDIT.allChildren().contains(StationPermission.DOCUMENT_READ));
        assertTrue(StationPermission.MEMBER_EDIT.allChildren().contains(StationPermission.DOCUMENT_EDIT_MEMBER));
    }

    /**
     * The implication runs one way only. Letting the equipment officer at the test certificates is
     * the whole point of a permission of its own, and it must not hand them the member list. Nor
     * does the permission for the documents that name a member: a name on a document is not the
     * profile behind it.
     */
    @Test
    void readingDocumentsDoesNotReachMembers() {
        assertFalse(StationPermission.DOCUMENT_READ.allChildren().contains(StationPermission.MEMBER_READ));
        assertFalse(StationPermission.DOCUMENT_EDIT.allChildren().contains(StationPermission.MEMBER_READ));
        assertFalse(StationPermission.DOCUMENT_EDIT.allChildren().contains(StationPermission.MEMBER_EDIT));
        assertFalse(StationPermission.DOCUMENT_READ_MEMBER.allChildren().contains(StationPermission.MEMBER_READ));
        assertFalse(StationPermission.DOCUMENT_EDIT_MEMBER.allChildren().contains(StationPermission.MEMBER_READ));
        assertFalse(StationPermission.DOCUMENT_MANAGER.allChildren().contains(StationPermission.MEMBER_READ));
    }

    /** Filing implies reading, because nobody should be able to write into a store they cannot see. */
    @Test
    void filingImpliesReading() {
        assertTrue(StationPermission.DOCUMENT_EDIT.allChildren().contains(StationPermission.DOCUMENT_READ));
    }

    /**
     * Filing to a member implies reading what is filed about them, which is what closes the way
     * round the read rule. Whoever may take the members off a document could already read it, so
     * taking them off buys nothing that was not already theirs.
     */
    @Test
    void filingToAMemberImpliesReadingMemberDocuments() {
        var filing = StationPermission.DOCUMENT_EDIT_MEMBER.allChildren();

        assertTrue(filing.contains(StationPermission.DOCUMENT_READ_MEMBER));
        assertTrue(filing.contains(StationPermission.DOCUMENT_EDIT));
        assertTrue(StationPermission.DOCUMENT_MANAGER.allChildren().contains(StationPermission.DOCUMENT_READ_MEMBER));
    }

    /**
     * Setting the members of a document is gated on {@code DOCUMENT_EDIT_MEMBER}, and filing into
     * the store does not carry it. A reader who could clear the members of a document they may not
     * read would be deleting the very thing that keeps them out of it.
     */
    @Test
    void clearingTheMembersOfADocumentNeedsTheMemberPermission() {
        assertFalse(held(StationPermission.DOCUMENT_EDIT).contains(StationPermission.DOCUMENT_EDIT_MEMBER));
        assertTrue(held(StationPermission.DOCUMENT_EDIT_MEMBER).contains(StationPermission.DOCUMENT_EDIT_MEMBER));
    }

    /** The station's own paperwork is all a reader without the member permission is shown. */
    @Test
    void theStationListingHoldsBackWhatNamesAMember() {
        var bound = write("Attest", List.of(memberId));
        var unbound = write("Satzung", List.of());

        var seen = storeAsSeenBy(StationPermission.DOCUMENT_READ, null);

        assertTrue(seen.stream().anyMatch(document -> document.id() == unbound.id()), "the station's own paperwork");
        assertFalse(seen.stream().anyMatch(document -> document.id() == bound.id()), "and nothing that names anybody");
        assertTrue(storeAsSeenBy(StationPermission.DOCUMENT_READ_MEMBER, null).stream()
                .anyMatch(document -> document.id() == bound.id()));
    }

    /**
     * The search runs against what was read out of the documents, so a listing that reached a member
     * document would answer whether a word appears in it without ever handing the document over.
     */
    @Test
    void aWordOnlyAMemberDocumentSaysIsNotAnswered() {
        var bound = write("Bescheinigung", List.of(memberId));
        memberDocumentRepo.updateSearchIndex(bound.id(), "Tauglichkeit bis 2030", "simple");

        assertTrue(
                storeAsSeenBy(StationPermission.DOCUMENT_READ, "Tauglichkeit").isEmpty());
        assertTrue(storeAsSeenBy(StationPermission.DOCUMENT_READ_MEMBER, "Tauglichkeit").stream()
                .anyMatch(document -> document.id() == bound.id()));
    }

    /** What a session carries when it is granted this permission and nothing else. */
    private static Set<StationPermission> held(StationPermission granted) {
        return StationPermission.expand(Set.of(granted));
    }

    /**
     * The store as the listing hands it to a reader holding this permission and nothing else.
     * Without the permission for member documents that is the documents naming nobody, none of the
     * hidden ones, and no narrowing to a member however the request asks for one.
     */
    private static List<Document> storeAsSeenBy(StationPermission granted, String search) {
        boolean readsMemberDocuments = held(granted).contains(StationPermission.DOCUMENT_READ_MEMBER);
        return memberDocumentRepo.findByStation(
                station.id(), List.of(), search, readsMemberDocuments, !readsMemberDocuments, "simple", 50, 0);
    }

    private static Document write(String title, List<Integer> members) {
        return memberDocumentRepo.create(
                station.id(), title, title + ".pdf", "application/pdf", 12, false, false, memberId, members);
    }
}
