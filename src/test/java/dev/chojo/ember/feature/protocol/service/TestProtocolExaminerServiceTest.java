/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.repository.TestProtocolExaminerRepository;
import dev.chojo.ember.feature.protocol.service.TestProtocolExaminerService.SectionExaminers;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Who examines which section of a run, and what each of them may grade.
 *
 * <p>The protocol is a tree of three top-level sections: knots with a subsection on loops, first aid,
 * and radio. Anna examines knots, Ben examines only the loops inside it, nobody is named for first aid,
 * and Clara is a tester left out of the plan.
 */
class TestProtocolExaminerServiceTest extends RepositoryTestBase {
    private TestProtocolExaminerService examiners;
    private TestProtocolRun run;
    private StationMember anna;
    private StationMember ben;
    private StationMember clara;
    private int knots;
    private int loops;
    private int firstAid;
    private int radio;
    private int loopItem;
    private int firstAidItem;
    private int radioItem;

    @BeforeEach
    void setup() {
        var station = stationRepo.create("Examiners " + UUID.randomUUID());
        anna = member(station.id(), "anna");
        ben = member(station.id(), "ben");
        clara = member(station.id(), "clara");

        var protocol = testProtocolRepo.createProtocol(station.id(), "Leistungsabzeichen", "", null);
        knots = testProtocolRepo
                .createSection(protocol.id(), null, "Knoten", "", null, null, 0)
                .id();
        loops = testProtocolRepo
                .createSection(protocol.id(), knots, "Schlaufen", "", null, null, 0)
                .id();
        firstAid = testProtocolRepo
                .createSection(protocol.id(), null, "Erste Hilfe", "", null, null, 1)
                .id();
        radio = testProtocolRepo
                .createSection(protocol.id(), null, "Funk", "", null, null, 2)
                .id();
        loopItem = testProtocolRepo.createItem(loops, "Palstek", "", 1, 0).id();
        firstAidItem =
                testProtocolRepo.createItem(firstAid, "Seitenlage", "", 1, 0).id();
        radioItem = testProtocolRepo.createItem(radio, "Funkspruch", "", 1, 0).id();
        run = testProtocolRepo.createRun(protocol.id(), station.id(), "Herbst", LocalDate.of(2026, 10, 1), anna.id());

        var protocols = mock(TestProtocolService.class);
        when(protocols.findSections(protocol.id())).thenAnswer(_ -> testProtocolRepo.findSections(protocol.id()));
        var members = mock(StationMemberService.class);
        when(members.findMembersWithPermission(
                        anyInt(), org.mockito.ArgumentMatchers.eq(StationPermission.PROTOCOL_TESTER)))
                .thenReturn(List.of(anna, ben, clara));
        examiners = new TestProtocolExaminerService(new TestProtocolExaminerRepository(), protocols, members);
    }

    private StationMember member(int stationId, String name) {
        var account = accountRepo.create(name + "-" + UUID.randomUUID() + "@test.com", name, "Prüfer");
        return stationMemberRepo.create(stationId, account.id());
    }

    private void planTheExam() {
        examiners.plan(
                run,
                List.of(
                        new SectionExaminers(knots, List.of(anna.id())),
                        new SectionExaminers(loops, List.of(ben.id())),
                        new SectionExaminers(radio, List.of(anna.id(), ben.id()))));
    }

    @Test
    void aRunWithoutExaminersIsGradedByEveryTester() {
        var scope = examiners.scopeOf(run, clara.id());

        assertTrue(scope.mayGrade());
        assertFalse(scope.restricted());
        assertEquals(Set.of(knots, loops, firstAid, radio), scope.sectionIds());
    }

    @Test
    void anExaminerGradesTheirSectionsAndTheOnesNobodyWasNamedFor() {
        planTheExam();

        assertEquals(
                Set.of(knots, loops, firstAid, radio),
                examiners.scopeOf(run, anna.id()).sectionIds());
        assertEquals(
                Set.of(loops, firstAid, radio), examiners.scopeOf(run, ben.id()).sectionIds());
    }

    @Test
    void aTesterLeftOutOfThePlanMayNotGrade() {
        planTheExam();

        assertFalse(examiners.scopeOf(run, clara.id()).mayGrade());
        var refused = assertThrows(RefusalResponse.class, () -> examiners.requireGrader(run, clara.id()));
        assertEquals(TestProtocolRefusal.PROTOCOL_RUN_GRADED_BY_ITS_EXAMINERS, refused.refusal());
    }

    /** A sheet carrying another examiner's points as they were keeps that examiner's work. */
    @Test
    void ticksOutsideTheScopeAreLeftAsStored() {
        planTheExam();
        var protocolItems = testProtocolRepo.findAllItemsByProtocol(run.protocolId());
        var scope = examiners.scopeOf(run, ben.id());

        var allowed = examiners.allowedChecks(
                scope, Map.of(loopItem, true, firstAidItem, true, radioItem, false), protocolItems);

        assertEquals(Map.of(loopItem, true, firstAidItem, true, radioItem, false), allowed);
        planOnlyKnotsForAnna();
        var narrowed = examiners.allowedChecks(
                examiners.scopeOf(run, ben.id()), Map.of(loopItem, true, radioItem, true), protocolItems);
        assertFalse(narrowed.containsKey(loopItem));
    }

    private void planOnlyKnotsForAnna() {
        examiners.plan(
                run,
                List.of(
                        new SectionExaminers(knots, List.of(anna.id())),
                        new SectionExaminers(radio, List.of(ben.id()))));
    }

    @Test
    void aSectionWithNothingOfTheirsCannotBeFinishedByThem() {
        planOnlyKnotsForAnna();
        var bens = examiners.scopeOf(run, ben.id());

        var refused = assertThrows(RefusalResponse.class, () -> examiners.requireFinishable(run, bens, knots));
        assertEquals(TestProtocolRefusal.PROTOCOL_SECTION_NOT_YOURS_TO_FINISH, refused.refusal());
        examiners.requireFinishable(run, bens, firstAid);
        examiners.requireFinishable(run, bens, radio);
    }

    @Test
    void aPlanNamesOnlySectionsOfTheProtocolAndTesters() {
        var stranger =
                member(stationRepo.create("Elsewhere " + UUID.randomUUID()).id(), "fremd");

        var foreignSection = assertThrows(
                RefusalResponse.class,
                () -> examiners.plan(run, List.of(new SectionExaminers(-1, List.of(anna.id())))));
        assertEquals(TestProtocolRefusal.PROTOCOL_EXAMINER_SECTION_ELSEWHERE, foreignSection.refusal());
        var notATester = assertThrows(
                RefusalResponse.class,
                () -> examiners.plan(run, List.of(new SectionExaminers(knots, List.of(stranger.id())))));
        assertEquals(TestProtocolRefusal.PROTOCOL_EXAMINER_NOT_A_TESTER, notATester.refusal());
        assertTrue(examiners.examinersOf(run.id()).isEmpty());
    }

    @Test
    void theNextRunOfTheProtocolCanBePlannedLikeTheLast() {
        planTheExam();
        var next = testProtocolRepo.createRun(
                run.protocolId(), run.stationId(), "Frühjahr", LocalDate.of(2027, 4, 1), anna.id());

        assertEquals(examiners.examinersOf(run.id()), examiners.previousPlan(next));
        assertTrue(examiners.previousPlan(run).isEmpty());
    }
}
