/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountPreferenceRepository;
import dev.chojo.ember.feature.accountlink.entity.LinkAnswer;
import dev.chojo.ember.feature.accountlink.entity.LinkOrigin;
import dev.chojo.ember.feature.accountlink.repository.AccountLinkRepository;
import dev.chojo.ember.feature.members.entity.FindableBecause;
import dev.chojo.ember.feature.members.entity.InviteCandidate;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A manager finds by name the people they share a station or an association with, and anybody who chose
 * to be findable; nobody else, and never somebody who left or the manager themselves.
 */
class InviteCandidateRepositoryTest extends RepositoryTestBase {
    private final InviteCandidateRepository candidates = new InviteCandidateRepository();
    private final AccountPreferenceRepository preferences = new AccountPreferenceRepository();
    private final AccountLinkRepository links = new AccountLinkRepository();

    private String surname;
    private Account manager;
    private Station home;
    private Station inviting;

    @BeforeEach
    void setUp() {
        surname = "Suchname" + System.nanoTime();
        manager = person("Manuela");
        home = stationRepo.create("Heimat" + System.nanoTime());
        inviting = stationRepo.create("Einladend" + System.nanoTime());
        stationMemberRepo.create(home.id(), manager.id());
        stationMemberRepo.create(inviting.id(), manager.id());
    }

    private Account person(String firstName) {
        return accountRepo.create(firstName.toLowerCase() + System.nanoTime() + "@test.com", firstName, surname);
    }

    private List<InviteCandidate> search(String... words) {
        return candidates.find(manager.id(), inviting.id(), List.of(words), 10);
    }

    private List<String> firstNames(List<InviteCandidate> found) {
        return found.stream().map(InviteCandidate::firstName).toList();
    }

    @Test
    void somebodyAtAStationOfTheManagerIsFoundThroughIt() {
        var colleague = person("Kollege");
        stationMemberRepo.create(home.id(), colleague.id());

        var found = search(surname);

        assertEquals(List.of("Kollege"), firstNames(found), "the manager does not find themselves");
        assertEquals(FindableBecause.SHARED_STATION, found.getFirst().because());
        assertEquals(home.name(), found.getFirst().through());
        assertEquals(colleague.uid(), found.getFirst().accountUid());
    }

    @Test
    void somebodyAtAStationOfTheSameAssociationIsFoundThroughIt() {
        var association = clusterRepo.create("Verband" + System.nanoTime(), null, home.id());
        var neighbour = stationRepo.create("Nachbar" + System.nanoTime());
        stationRepo.setCluster(home.id(), association.id());
        stationRepo.setCluster(neighbour.id(), association.id());
        var stranger = person("Nachbarin");
        stationMemberRepo.create(neighbour.id(), stranger.id());

        var found = search(surname);

        assertEquals(List.of("Nachbarin"), firstNames(found));
        assertEquals(FindableBecause.SHARED_ASSOCIATION, found.getFirst().because());
        assertEquals(association.name(), found.getFirst().through());
    }

    @Test
    void somebodyElsewhereIsFoundOnlyOnceTheyChoseToBe() {
        var elsewhere = stationRepo.create("Fern" + System.nanoTime());
        var stranger = person("Fremde");
        stationMemberRepo.create(elsewhere.id(), stranger.id());

        assertTrue(search(surname).isEmpty(), "an account is not told about by default");

        preferences.setFindableByName(stranger.id(), true);
        var found = search(surname);

        assertEquals(List.of("Fremde"), firstNames(found));
        assertEquals(FindableBecause.OPTED_IN, found.getFirst().because());
        assertNull(found.getFirst().through());
    }

    @Test
    void theClosestTieIsShownForSomebodyReachedSeveralWays() {
        var colleague = person("Kollege");
        stationMemberRepo.create(home.id(), colleague.id());
        preferences.setFindableByName(colleague.id(), true);

        var found = search(surname);

        assertEquals(1, found.size());
        assertEquals(FindableBecause.SHARED_STATION, found.getFirst().because());
    }

    @Test
    void aFormerMemberIsNoTieInEitherDirection() {
        var left = person("Gegangen");
        var leftMember = stationMemberRepo.create(home.id(), left.id());
        stationMemberRepo.setFormer(leftMember.id(), true);
        var other = stationRepo.create("Ehemals" + System.nanoTime());
        var formerColleague = person("Ehemalige");
        stationMemberRepo.create(other.id(), formerColleague.id());
        var managerThere = stationMemberRepo.create(other.id(), manager.id());
        stationMemberRepo.setFormer(managerThere.id(), true);

        assertTrue(search(surname).isEmpty());
    }

    /**
     * A guardian and the person they look after share a station, which is how they find each other. The
     * other guardian of a child at a station the manager is not at is only a relation of a relation.
     */
    @Test
    void aRelationOfARelationIsNotFound() {
        var child = person("Kind");
        var childAtHome = stationMemberRepo.create(home.id(), child.id());
        var managerAtHome = stationMemberRepo
                .findByStationAndAccount(home.id(), manager.id())
                .orElseThrow();
        stationMemberRepo.addManager(managerAtHome.id(), childAtHome.id());
        var school = stationRepo.create("Schule" + System.nanoTime());
        var childAtSchool = stationMemberRepo.create(school.id(), child.id());
        var otherParent = person("Elternteil");
        var otherParentAtSchool = stationMemberRepo.create(school.id(), otherParent.id());
        stationMemberRepo.addManager(otherParentAtSchool.id(), childAtSchool.id());

        assertEquals(List.of("Kind"), firstNames(search(surname)));
    }

    @Test
    void everyWordHasToBeInTheNameAndWildcardsAreLetters() {
        var colleague = person("Kollege");
        stationMemberRepo.create(home.id(), colleague.id());

        assertEquals(1, search("kOLLe", surname.toUpperCase()).size(), "case does not matter");
        assertTrue(search("Kollege", "Fremd").isEmpty(), "every word counts");
        assertTrue(search("%").isEmpty(), "a percent sign is a letter, not every name");
        assertTrue(search("_").isEmpty(), "and so is an underscore");
    }

    @Test
    void somebodyHereOrAskedIsMarkedAsSuch() {
        var here = person("Hier");
        var asked = person("Gefragt");
        stationMemberRepo.create(home.id(), here.id());
        stationMemberRepo.create(home.id(), asked.id());
        stationMemberRepo.create(inviting.id(), here.id());
        var waiting = stationMemberRepo.createWithoutAccount(inviting.id(), "Gefragt " + surname);
        links.create(
                inviting.id(),
                waiting.id(),
                asked.id(),
                LinkOrigin.INVITE,
                null,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS));

        var found = search(surname);

        var hereFound = found.stream()
                .filter(c -> c.firstName().equals("Hier"))
                .findFirst()
                .orElseThrow();
        var askedFound = found.stream()
                .filter(c -> c.firstName().equals("Gefragt"))
                .findFirst()
                .orElseThrow();
        assertTrue(hereFound.alreadyHere());
        assertFalse(hereFound.alreadyAsked());
        assertFalse(askedFound.alreadyHere());
        assertTrue(askedFound.alreadyAsked());
    }

    @Test
    void anAccountIsReachableByItsUidOnlyWhereItCouldBeFound() {
        var colleague = person("Kollege");
        stationMemberRepo.create(home.id(), colleague.id());
        var stranger = person("Fremde");

        assertTrue(candidates
                .findReachable(manager.id(), inviting.id(), colleague.uid())
                .isPresent());
        assertTrue(candidates
                .findReachable(manager.id(), inviting.id(), stranger.uid())
                .isEmpty());
        assertTrue(candidates
                .findReachable(manager.id(), inviting.id(), UUID.randomUUID())
                .isEmpty());
    }

    @Test
    void anotherAssociationsMembersStayOutAndSoDoItsFormerOnes() {
        var ours = clusterRepo.create("Unser" + System.nanoTime(), null, home.id());
        var theirStation = stationRepo.create("Deren" + System.nanoTime());
        var theirs = clusterRepo.create("Deren" + System.nanoTime(), null, theirStation.id());
        var neighbour = stationRepo.create("Nachbar" + System.nanoTime());
        stationRepo.setCluster(home.id(), ours.id());
        stationRepo.setCluster(neighbour.id(), ours.id());
        stationRepo.setCluster(theirStation.id(), theirs.id());
        stationMemberRepo.create(theirStation.id(), person("Fremde").id());
        var left = stationMemberRepo.create(neighbour.id(), person("Gegangen").id());
        stationMemberRepo.setFormer(left.id(), true);

        assertTrue(search(surname).isEmpty());
    }

    @Test
    void aStationThatMovedAwayTiesNobody() {
        var moved = stationRepo.create("Umgezogen" + System.nanoTime());
        stationMemberRepo.create(moved.id(), manager.id());
        stationMemberRepo.create(moved.id(), person("Zurueckgelassen").id());
        stationRepo.markMovedAway(moved.id(), null);

        assertTrue(search(surname).isEmpty());
    }

    @Test
    void accentsAndTheSharpSDoNotMatter() {
        var accented =
                accountRepo.create("mueller" + System.nanoTime() + "@test.com", "José", "Müller-Straße" + surname);
        stationMemberRepo.create(home.id(), accented.id());

        assertEquals(1, search("jose", "muller", "strasse").size());
        assertEquals(1, search("JOSÉ", "MÜLLER", "STRASSE").size());
        var capital = accountRepo.create("oezdemir" + System.nanoTime() + "@test.com", "Élodie", "Özdemir" + surname);
        stationMemberRepo.create(home.id(), capital.id());
        assertEquals(List.of("Élodie"), firstNames(search("elodie", "ozdemir")), "a capital accent folds too");
    }

    @Test
    void anAnsweredOrExpiredRequestIsNoLongerWaitedForAndADeclineIsMarked() {
        var declined = person("Abgelehnt");
        var expired = person("Abgelaufen");
        stationMemberRepo.create(home.id(), declined.id());
        stationMemberRepo.create(home.id(), expired.id());
        var declinedMember = stationMemberRepo.createWithoutAccount(inviting.id(), "Abgelehnt " + surname);
        var expiredMember = stationMemberRepo.createWithoutAccount(inviting.id(), "Abgelaufen " + surname);
        var request = links.create(
                inviting.id(),
                declinedMember.id(),
                declined.id(),
                LinkOrigin.INVITE,
                null,
                null,
                Instant.now().plus(1, ChronoUnit.DAYS));
        links.answer(request.id(), LinkAnswer.DECLINED);
        links.create(
                inviting.id(),
                expiredMember.id(),
                expired.id(),
                LinkOrigin.INVITE,
                null,
                null,
                Instant.now().minus(1, ChronoUnit.DAYS));

        var found = search(surname);
        assertTrue(found.stream().noneMatch(InviteCandidate::alreadyAsked));
        assertTrue(found.stream()
                .filter(c -> c.firstName().equals("Abgelehnt"))
                .findFirst()
                .orElseThrow()
                .declinedLately());
        assertFalse(found.stream()
                .filter(c -> c.firstName().equals("Abgelaufen"))
                .findFirst()
                .orElseThrow()
                .declinedLately());
    }

    @Test
    void noMoreThanTheLimitIsReturned() {
        for (int i = 0; i < 3; i++)
            stationMemberRepo.create(home.id(), person("Viele").id());

        assertEquals(
                2,
                candidates
                        .find(manager.id(), inviting.id(), List.of(surname), 2)
                        .size());
    }
}
