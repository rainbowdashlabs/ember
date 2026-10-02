/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.DocumentFilter;
import dev.chojo.ember.feature.documents.entity.DocumentTag;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DocumentRepositoryTest extends RepositoryTestBase {
    private static Station station;
    private static Account account;
    private static Account otherAccount;
    private static int memberId;
    private static int otherMemberId;
    private static int documentId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Document Station");
        account = accountRepo.create("doc-owner@test.com", "Doc", "Owner");
        otherAccount = accountRepo.create("doc-other@test.com", "Doc", "Other");
        memberId = stationMemberRepo.create(station.id(), account.id()).id();
        otherMemberId =
                stationMemberRepo.create(station.id(), otherAccount.id()).id();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
        accountRepo.delete(otherAccount.id());
    }

    private static Document write(String title, boolean hidden, boolean keep, List<Integer> members) {
        return memberDocumentRepo.create(
                station.id(),
                title,
                title + ".pdf",
                "application/pdf",
                12,
                hidden,
                keep,
                Uploader.member(memberId),
                members);
    }

    @Test
    @Order(1)
    void aDocumentIsWrittenAndBoundToTheMemberItConcerns() {
        var document = write("Vertrag", false, false, List.of(memberId));
        documentId = document.id();

        assertEquals("Vertrag", document.title());
        assertEquals(List.of(memberId), memberDocumentRepo.membersOf(documentId));
        assertTrue(memberDocumentRepo.isBoundTo(documentId, memberId));
        assertFalse(memberDocumentRepo.isBoundTo(documentId, otherMemberId));
    }

    /** One agreement can be the agreement of several people, and stays one document. */
    @Test
    @Order(2)
    void aDocumentIsBoundToFurtherMembersWithoutBecomingTwo() {
        memberDocumentRepo.bind(documentId, List.of(otherMemberId));
        memberDocumentRepo.bind(documentId, List.of(otherMemberId));

        assertEquals(2, memberDocumentRepo.membersOf(documentId).size());
        assertEquals(
                1,
                memberDocumentRepo
                        .findByMember(station.id(), otherMemberId, false)
                        .size());
    }

    /** Hiding a document hides it from the members it belongs to, which is the whole point. */
    @Test
    @Order(3)
    void aHiddenDocumentIsKeptFromTheMemberItBelongsTo() {
        var hidden = write("Vermerk", true, false, List.of(memberId));

        assertTrue(memberDocumentRepo.findByMember(station.id(), memberId, false).stream()
                .noneMatch(document -> document.id() == hidden.id()));
        assertTrue(memberDocumentRepo.findByMember(station.id(), memberId, true).stream()
                .anyMatch(document -> document.id() == hidden.id()));
    }

    @Test
    @Order(4)
    void tagsAreWrittenAsTheyAreUsed() {
        memberDocumentRepo.setTags(documentId, station.id(), List.of("Vertrag", " Wichtig "));

        assertEquals(
                List.of("Vertrag", "Wichtig"),
                memberDocumentRepo.findTags(documentId).stream()
                        .map(DocumentTag::name)
                        .toList());
        assertTrue(memberDocumentRepo.findTagsByStation(station.id()).size() >= 2);
    }

    @Test
    @Order(5)
    void tagsAreReplacedRatherThanAddedTo() {
        memberDocumentRepo.setTags(documentId, station.id(), List.of("Wichtig"));

        assertEquals(
                List.of("Wichtig"),
                memberDocumentRepo.findTags(documentId).stream()
                        .map(DocumentTag::name)
                        .toList());
    }

    @Test
    @Order(6)
    void theStoreIsSearchedByTitleAndByWhatTheDocumentsSay() {
        var document = write("Dienstanweisung", false, false, List.of());
        memberDocumentRepo.updateSearchIndex(document.id(), "Der Loeschzug rueckt aus", "simple");

        assertTrue(byStation("Dienstanweisung").stream().anyMatch(found -> found.id() == document.id()));
        assertTrue(byStation("Loeschzug").stream().anyMatch(found -> found.id() == document.id()));
        assertTrue(byStation("Kommandowagen").isEmpty());
    }

    private static List<Document> byStation(String search) {
        return memberDocumentRepo.findByStation(
                station.id(), new DocumentFilter(List.of(), search, true, false, false), "simple", 50, 0);
    }

    @Test
    @Order(7)
    void theStoreIsNarrowedToOneMember() {
        var filter = new DocumentFilter(List.of(memberId), null, true, false, false);
        var mine = memberDocumentRepo.findByStation(station.id(), filter, "simple", 50, 0);

        assertTrue(mine.stream().allMatch(document -> memberDocumentRepo.isBoundTo(document.id(), memberId)));
        assertEquals(mine.size(), memberDocumentRepo.countByStation(station.id(), filter, "simple"));
        assertEquals(
                mine.stream().map(Document::id).toList(),
                memberDocumentRepo.idsByStation(station.id(), filter, "simple"));
    }

    /**
     * What is kept for the record outlasts the membership; the rest is let go of, and a document
     * that was bound to nobody but them is left for its owner to delete.
     */
    @Test
    @Order(8)
    void archivingKeepsWhatWasMarkedKeptAndReleasesTheRest() {
        var kept = write("Loeschvereinbarung", false, true, List.of(otherMemberId));
        var released = write("Notiz", false, false, List.of(otherMemberId));

        var orphaned = memberDocumentRepo.unbindMember(otherMemberId, true);

        assertTrue(memberDocumentRepo.isBoundTo(kept.id(), otherMemberId), "what is kept stays bound");
        assertFalse(memberDocumentRepo.isBoundTo(released.id(), otherMemberId), "the rest is let go of");
        assertTrue(orphaned.contains(released.id()), "and is left with nobody");
        assertFalse(orphaned.contains(documentId), "a document with another member left is not orphaned");
    }

    /** A document that never had a member is the station's own and is nobody's to lose. */
    @Test
    @Order(9)
    void aDocumentBoundToNobodyIsNotSweptUpByArchiving() {
        var stationOwned = write("Satzung", false, false, List.of());

        var orphaned = memberDocumentRepo.unbindMember(memberId, true);

        assertFalse(orphaned.contains(stationOwned.id()));
    }

    /**
     * A member deleted while a document is kept for them leaves their name on it, which keeps it their
     * paperwork rather than the station's. What was not kept is let go of as on archiving.
     */
    @Test
    @Order(10)
    void deletingAMemberLeavesTheirNameOnWhatIsKept() {
        var leaving = stationMemberRepo.create(
                station.id(),
                accountRepo.create("doc-leaving@test.com", "Lena", "Weg").id());
        var kept = write("Verpflichtung", false, true, List.of(leaving.id()));
        var shared = write("Gemeinsam", false, false, List.of(leaving.id(), memberId));

        assertEquals(1, memberDocumentRepo.keepDepartedName(leaving.id()));
        memberDocumentRepo.unbindMember(leaving.id(), false);
        stationMemberRepo.delete(leaving.id());

        assertEquals(List.of("Lena Weg"), memberDocumentRepo.departedOf(kept.id()));
        assertTrue(memberDocumentRepo.membersOf(kept.id()).isEmpty(), "nobody is bound to it any more");
        assertFalse(memberDocumentRepo.hasNoMembers(kept.id()), "and still it names somebody");
        assertEquals(List.of(memberId), memberDocumentRepo.membersOf(shared.id()));
        assertTrue(memberDocumentRepo.departedOf(shared.id()).isEmpty(), "what was not kept keeps no name");
    }

    /** Rebinding a document chooses among the members there are; a deleted member's name is not one. */
    @Test
    @Order(11)
    void rebindingADocumentKeepsTheNameOfSomebodyDeleted() {
        var kept = byStation("Verpflichtung").getFirst();

        memberDocumentRepo.setMembers(kept.id(), List.of(memberId));

        assertEquals(List.of(memberId), memberDocumentRepo.membersOf(kept.id()));
        assertEquals(List.of("Lena Weg"), memberDocumentRepo.departedOf(kept.id()));
        memberDocumentRepo.setMembers(kept.id(), List.of());
    }

    /**
     * The documents about people who have all gone: archived or deleted. A document still about somebody
     * who is here is not among them, so pruning the list never takes a current member's paperwork.
     */
    @Test
    @Order(12)
    void theStoreIsNarrowedToThePeopleWhoHaveLeft() {
        var archived = stationMemberRepo.create(
                station.id(),
                accountRepo.create("doc-archived@test.com", "Arne", "Alt").id());
        var ofArchived = write("Altakte", false, true, List.of(archived.id()));
        var ofBoth = write("Mitakte", false, true, List.of(archived.id(), memberId));
        stationMemberRepo.setFormer(archived.id(), true);

        var departed = memberDocumentRepo.idsByStation(
                station.id(), new DocumentFilter(List.of(), null, true, false, true), "simple");

        assertTrue(departed.contains(ofArchived.id()), "an archived member's document");
        assertTrue(departed.contains(byStation("Verpflichtung").getFirst().id()), "a deleted member's document");
        assertFalse(departed.contains(ofBoth.id()), "not one that is still somebody's here");
        assertFalse(departed.contains(byStation("Satzung").getFirst().id()), "nor the station's own paperwork");
    }

    /** The uploader is named by membership at the station, or by the account of an association manager. */
    @Test
    @Order(13)
    void theUploaderIsNamedEitherWay() {
        var byMember = write("Hochgeladen", false, false, List.of(memberId));
        var byManager = memberDocumentRepo.create(
                station.id(),
                "Vom Verband",
                "verband.pdf",
                "application/pdf",
                12,
                false,
                false,
                Uploader.account(otherAccount.id()),
                List.of(memberId));
        var byNobody = memberDocumentRepo.create(
                station.id(),
                "Per Post",
                "post.pdf",
                "application/pdf",
                12,
                false,
                false,
                Uploader.nobody(),
                List.of());

        assertEquals(
                "Doc Owner", memberDocumentRepo.uploaderNameOf(byMember.id()).orElseThrow());
        assertEquals(
                "Doc Other", memberDocumentRepo.uploaderNameOf(byManager.id()).orElseThrow());
        assertNull(byManager.uploadedBy(), "a manager is not named by a membership");
        assertEquals(otherAccount.id(), byManager.uploaderAccountId());
        assertTrue(memberDocumentRepo.uploaderNameOf(byNobody.id()).isEmpty());
    }

    @Test
    @Order(20)
    void aDocumentIsRemoved() {
        assertTrue(memberDocumentRepo.delete(documentId));
        assertTrue(memberDocumentRepo.findById(documentId).isEmpty());
    }
}
