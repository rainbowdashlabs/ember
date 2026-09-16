/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whose paperwork a guardian may read.
 *
 * <p>The forms a station holds for a child are the forms the person answering for that child needs:
 * the consent, the medical note, the certificate. The rule is the same one the screens ask, written
 * here against the relation itself so it cannot drift from what a route decides.
 */
class GuardianDocumentAccessTest extends RepositoryTestBase {

    private static Station station;
    private static Account guardianAccount;
    private static Account childAccount;
    private static Account strangerAccount;
    private static int guardianId;
    private static int childId;
    private static int strangerId;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Guardian Document Station");
        guardianAccount = accountRepo.create("guardian-doc@test.com", "Guardian", "Reader");
        childAccount = accountRepo.create("child-doc@test.com", "Child", "Reader");
        strangerAccount = accountRepo.create("stranger-doc@test.com", "Stranger", "Reader");
        guardianId =
                stationMemberRepo.create(station.id(), guardianAccount.id()).id();
        childId = stationMemberRepo.create(station.id(), childAccount.id()).id();
        strangerId =
                stationMemberRepo.create(station.id(), strangerAccount.id()).id();
        stationMemberRepo.addManager(guardianId, childId);
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(guardianAccount.id());
        accountRepo.delete(childAccount.id());
        accountRepo.delete(strangerAccount.id());
    }

    /** What a reader may reach: their own member and everybody they look after. */
    private static Set<Integer> ownAndManaged(int memberId) {
        var reachable = new HashSet<Integer>();
        reachable.add(memberId);
        stationMemberRepo.findManaged(memberId).forEach(managed -> reachable.add(managed.id()));
        return reachable;
    }

    private static Document documentFor(String title, int memberId, boolean hidden) {
        return memberDocumentRepo.create(
                station.id(), title, title + ".pdf", "application/pdf", 12, hidden, false, memberId, List.of(memberId));
    }

    @Test
    void aGuardianReachesTheMemberTheyAnswerFor() {
        assertTrue(ownAndManaged(guardianId).contains(childId), "the child is theirs to answer for");
        assertTrue(ownAndManaged(guardianId).contains(guardianId), "and so are they");
    }

    /** Answering for one member says nothing about anybody else's paperwork. */
    @Test
    void aGuardianReachesNobodyElse() {
        assertFalse(ownAndManaged(guardianId).contains(strangerId));
        assertFalse(ownAndManaged(childId).contains(guardianId), "it does not run the other way");
    }

    @Test
    void aDocumentOfTheirChildIsBoundToSomebodyTheGuardianReaches() {
        var document = documentFor("Einverständnis", childId, false);

        assertTrue(
                ownAndManaged(guardianId).stream()
                        .anyMatch(member -> memberDocumentRepo.isBoundTo(document.id(), member)),
                "the consent for the child they answer for");
        assertFalse(
                ownAndManaged(strangerId).stream()
                        .anyMatch(member -> memberDocumentRepo.isBoundTo(document.id(), member)),
                "and nobody else's business");
    }

    /**
     * Hidden means hidden from the member it belongs to, and a guardian reads what that member
     * reads. It is refused before the relation is ever consulted, so this only pins that it is still
     * bound to the child and not to them.
     */
    @Test
    void aHiddenDocumentIsStillOnlyTheChildsToBeHiddenFrom() {
        var document = documentFor("Vermerk", childId, true);

        assertTrue(memberDocumentRepo.isBoundTo(document.id(), childId));
        assertFalse(memberDocumentRepo.isBoundTo(document.id(), guardianId));
    }
}
