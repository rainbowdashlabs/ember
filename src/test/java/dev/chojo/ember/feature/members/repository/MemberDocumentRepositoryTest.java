/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.MemberDocument;
import dev.chojo.ember.feature.members.entity.MemberDocumentTag;
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
class MemberDocumentRepositoryTest extends RepositoryTestBase {
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

    private static MemberDocument write(String title, boolean hidden, boolean keep, List<Integer> members) {
        return memberDocumentRepo.create(
                station.id(), title, title + ".pdf", "application/pdf", 12, hidden, keep, memberId, members);
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
        assertEquals(1, memberDocumentRepo.findByMember(otherMemberId, false).size());
    }

    /** Hiding a document hides it from the members it belongs to, which is the whole point. */
    @Test
    @Order(3)
    void aHiddenDocumentIsKeptFromTheMemberItBelongsTo() {
        var hidden = write("Vermerk", true, false, List.of(memberId));

        assertTrue(memberDocumentRepo.findByMember(memberId, false).stream()
                .noneMatch(document -> document.id() == hidden.id()));
        assertTrue(memberDocumentRepo.findByMember(memberId, true).stream()
                .anyMatch(document -> document.id() == hidden.id()));
    }

    @Test
    @Order(4)
    void tagsAreWrittenAsTheyAreUsed() {
        memberDocumentRepo.setTags(documentId, station.id(), List.of("Vertrag", " Wichtig "));

        assertEquals(
                List.of("Vertrag", "Wichtig"),
                memberDocumentRepo.findTags(documentId).stream()
                        .map(MemberDocumentTag::name)
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
                        .map(MemberDocumentTag::name)
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

    private static List<MemberDocument> byStation(String search) {
        return memberDocumentRepo.findByStation(station.id(), List.of(), search, true, "simple", 50, 0);
    }

    @Test
    @Order(7)
    void theStoreIsNarrowedToOneMember() {
        var mine = memberDocumentRepo.findByStation(station.id(), List.of(memberId), null, true, "simple", 50, 0);

        assertTrue(mine.stream().allMatch(document -> memberDocumentRepo.isBoundTo(document.id(), memberId)));
        assertEquals(
                mine.size(), memberDocumentRepo.countByStation(station.id(), List.of(memberId), null, true, "simple"));
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

    @Test
    @Order(10)
    void aDocumentIsRemoved() {
        assertTrue(memberDocumentRepo.delete(documentId));
        assertTrue(memberDocumentRepo.findById(documentId).isEmpty());
    }
}
