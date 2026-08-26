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

    @BeforeAll
    static void setup() {
        repository = new OnboardingTaskRepository();
        station = stationRepo.create("Onboarding Repository Station");
        account = accountRepo.create("onboarding-repo@test.com", "Ole", "Onboarding");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void clear() {
        repository.clearForMember(member.id(), "member.bookmark");
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
