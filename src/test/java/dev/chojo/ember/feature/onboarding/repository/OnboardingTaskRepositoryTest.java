/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.onboarding.repository;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.onboarding.entity.OnboardingMark;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What somebody said about an onboarding task, and nothing that can be worked out. A second word on
 * the same task replaces the first rather than piling up, and taking a task back up removes the row
 * instead of leaving a state nobody chose.
 */
class OnboardingTaskRepositoryTest extends RepositoryTestBase {

    private static OnboardingTaskRepository repository;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static Station otherStation;
    private static StationMember otherMember;
    private static Station thirdStation;
    private static StationMember thirdMember;

    @BeforeAll
    static void setup() {
        repository = new OnboardingTaskRepository();
        station = stationRepo.create("Onboarding Repository Station");
        account = accountRepo.create("onboarding-repo@test.com", "Ole", "Onboarding");
        member = stationMemberRepo.create(station.id(), account.id());
        otherStation = stationRepo.create("Onboarding Repository Other Station");
        otherMember = stationMemberRepo.create(otherStation.id(), account.id());
        thirdStation = stationRepo.create("Onboarding Repository Third Station");
        thirdMember = stationMemberRepo.create(thirdStation.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        stationRepo.delete(thirdStation.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void clear() {
        repository.clearForEveryMembershipOf(member.id(), "member.bookmark");
        repository.clearForEveryMembershipOf(member.id(), "member.wiki");
        repository.clearForStation(station.id(), "station.memberTypes");
        repository.clearForInstance("instance.security");
    }

    @Test
    void aMemberSaysSomethingAboutTheirOwnTask() {
        repository.markForMember(member.id(), "member.bookmark", "CONFIRMED");

        OnboardingMark mark = only(repository.findByMember(member.id()), "member.bookmark");
        assertEquals("member.bookmark", mark.taskKey());
        assertTrue(mark.confirmed());
        assertNotNull(mark.changedAt());
    }

    @Test
    void sayingSomethingElseReplacesWhatWasSaid() {
        repository.markForMember(member.id(), "member.bookmark", "CONFIRMED");
        repository.markForMember(member.id(), "member.bookmark", "SKIPPED");

        assertTrue(only(repository.findByMember(member.id()), "member.bookmark").skipped());
    }

    @Test
    void takingATaskUpAgainLeavesNothingBehind() {
        repository.markForMember(member.id(), "member.bookmark", "SKIPPED");
        repository.clearForMember(member.id(), "member.bookmark");

        assertTrue(find(repository.findByMember(member.id()), "member.bookmark").isEmpty());
    }

    @Test
    void theSamePersonsMarksOnAnotherStationAreFoundAndNotTheirOwn() {
        repository.markForMember(otherMember.id(), "member.bookmark", "CONFIRMED");
        repository.markForMember(member.id(), "member.wiki", "SKIPPED");

        List<OnboardingMark> elsewhere = repository.findByOtherMembershipsOf(member.id());

        assertTrue(only(elsewhere, "member.bookmark").confirmed());
        assertTrue(find(elsewhere, "member.wiki").isEmpty());
    }

    /** A tick says the person knows the thing, which a later word on another station does not undo. */
    @Test
    void aTickOnAnotherStationOutweighsALaterWord() {
        repository.markForMember(otherMember.id(), "member.bookmark", "CONFIRMED");
        repository.markForMember(thirdMember.id(), "member.bookmark", "SKIPPED");

        assertTrue(only(repository.findByOtherMembershipsOf(member.id()), "member.bookmark")
                .confirmed());
    }

    @Test
    void withoutATickTheLatestWordOnAnotherStationStands() {
        repository.markForMember(otherMember.id(), "member.bookmark", "SKIPPED");
        repository.markForMember(thirdMember.id(), "member.bookmark", "DISMISSED");

        assertTrue(only(repository.findByOtherMembershipsOf(member.id()), "member.bookmark")
                .dismissed());
    }

    @Test
    void takingACarriedTaskUpAgainClearsItOnEveryStationOfThePerson() {
        repository.markForMember(member.id(), "member.bookmark", "SKIPPED");
        repository.markForMember(otherMember.id(), "member.bookmark", "CONFIRMED");

        repository.clearForEveryMembershipOf(member.id(), "member.bookmark");

        assertTrue(find(repository.findByMember(member.id()), "member.bookmark").isEmpty());
        assertTrue(find(repository.findByMember(otherMember.id()), "member.bookmark")
                .isEmpty());
    }

    @Test
    void aStationTaskRemembersWhoSettledIt() {
        repository.markForStation(station.id(), "station.memberTypes", "CONFIRMED", member.id());

        OnboardingMark mark = only(repository.findByStation(station.id()), "station.memberTypes");
        assertTrue(mark.confirmed());
        assertEquals(member.id(), mark.actorId());
    }

    @Test
    void aStationTaskCanBeTakenUpAgainByAnyone() {
        repository.markForStation(station.id(), "station.memberTypes", "SKIPPED", member.id());
        repository.clearForStation(station.id(), "station.memberTypes");

        assertTrue(find(repository.findByStation(station.id()), "station.memberTypes")
                .isEmpty());
    }

    @Test
    void anInstanceTaskRemembersWhichAdministratorSettledIt() {
        repository.markForInstance("instance.security", "CONFIRMED", account.id());

        OnboardingMark mark = only(repository.findForInstance(), "instance.security");
        assertTrue(mark.confirmed());
        assertEquals(account.id(), mark.actorId());

        repository.clearForInstance("instance.security");
        assertTrue(find(repository.findForInstance(), "instance.security").isEmpty());
    }

    private static Optional<OnboardingMark> find(List<OnboardingMark> marks, String key) {
        return marks.stream().filter(mark -> mark.taskKey().equals(key)).findFirst();
    }

    private static OnboardingMark only(List<OnboardingMark> marks, String key) {
        return find(marks, key).orElseThrow();
    }
}
