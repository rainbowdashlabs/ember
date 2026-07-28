/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.quiz.entity.QuizTest;
import dev.chojo.ember.feature.quiz.entity.TestStatus;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

class QuizTestAccessServiceTest extends RepositoryTestBase {
    private static QuizTestAccessService service;
    private static Station station;
    private static Account account;
    private static StationMember member;

    @BeforeAll
    static void setup() {
        service = new QuizTestAccessService(quizTestRepo, restrictionService);
        station = stationRepo.create("QuizAccessSvcStation");
        account = accountRepo.create("quiz-access-svc@test.com", "Quiz", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        for (var test : quizTestRepo.findByStation(station.id())) {
            quizTestRepo.delete(test.id());
        }
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static QuizTest activeTest(String title) {
        var test = quizTestRepo.create(station.id(), title, "", null, false, false, member.id());
        quizTestRepo.updateStatus(test.id(), TestStatus.ACTIVE);
        return quizTestRepo.findById(test.id()).orElseThrow();
    }

    @Test
    void draftTestIsNotAccessible() {
        var test = quizTestRepo.create(station.id(), "Draft", "", null, false, false, member.id());
        assertFalse(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    @Test
    void activeTestIsAccessible() {
        var test = activeTest("Active");
        assertTrue(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    @Test
    void testWithFutureStartIsNotAccessible() {
        var test = activeTest("Future");
        quizTestRepo.update(
                test.id(), "Future", "", null, false, false, Instant.now().plusSeconds(86400), null);
        var updated = quizTestRepo.findById(test.id()).orElseThrow();
        assertFalse(service.isTestAccessible(updated, member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    @Test
    void testWithPastEndIsNotAccessible() {
        var test = activeTest("Ended");
        quizTestRepo.update(
                test.id(),
                "Ended",
                "",
                null,
                false,
                false,
                Instant.now().minusSeconds(86400),
                Instant.now().minusSeconds(3600));
        var updated = quizTestRepo.findById(test.id()).orElseThrow();
        assertFalse(service.isTestAccessible(updated, member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    @Test
    void grantedMemberAccessOpensTheTest() {
        var test = activeTest("Granted");
        service.grantMemberAccess(test.id(), member.id(), null);
        assertTrue(service.isTestAccessible(test, member.id(), EnumSet.noneOf(StationPermission.class)));
        service.revokeMemberAccess(test.id(), member.id());
    }

    @Test
    void restrictionsRoundTrip() {
        var test = activeTest("Restricted");
        service.setRestrictions(test.id(), RestrictionSelection.empty());
        var restrictions = service.findRestrictions(test.id());
        assertNotNull(restrictions);
        assertFalse(restrictions.hasRestrictions());

        service.updateRestrictionMode(test.id(), RestrictionMode.AND);
        assertEquals(
                RestrictionMode.AND,
                quizTestRepo.findById(test.id()).orElseThrow().restrictionMode());
        assertEquals(RestrictionMode.AND, service.findRestrictions(test.id()).mode());

        assertTrue(service.canMemberAccess(test.id(), member.id(), EnumSet.noneOf(StationPermission.class)));
    }

    @Test
    void restrictionsOfUnknownTestFallBackToOrMode() {
        assertEquals(RestrictionMode.OR, service.findRestrictions(99999).mode());
    }
}
