/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MemberAnswersChanged;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.members.entity.ProfileFieldConfig;
import dev.chojo.ember.feature.members.entity.RuleChange;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOperator;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.entity.RuleSubject;
import dev.chojo.ember.feature.members.entity.RuleValue;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.handler.AnswersRuleHandler;
import dev.chojo.ember.feature.members.handler.AudienceRuleHandler;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.repository.RepositoryTestBase;
import dev.chojo.ember.util.sql.Transactions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Running the rules of a station into its groups and tags. */
class MemberRuleServiceTest extends RepositoryTestBase {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 17);

    private final List<Account> accounts = new ArrayList<>();
    private Station station;
    private DomainEventBus eventBus;
    private TaskScheduler scheduler;
    private SerialLane lane;
    private MemberRuleService service;
    private int juleica;
    private StationMember anna;
    private StationMember ben;
    private StationMember clara;

    @BeforeEach
    void setUp() {
        station = stationRepo.create("Rule Service Station");
        eventBus = mock(DomainEventBus.class);
        scheduler = mock(TaskScheduler.class);
        lane = mock(SerialLane.class);
        when(scheduler.lane(anyString())).thenReturn(lane);
        service = new MemberRuleService(memberRuleRepo, memberGroupRepo, stationRepo, scheduler, eventBus);
        juleica = profileFieldRepo
                .create(station.id(), "JuLeiCa", FieldType.BOOLEAN, ProfileFieldConfig.empty(), false, false, null)
                .id();
        anna = member("Anna", StationUserType.TEAM);
        ben = member("Ben", StationUserType.MEMBER);
        clara = member("Clara", StationUserType.MEMBER);
        profileFieldRepo.setValue(anna.id(), juleica, JsonNodeFactory.instance.booleanNode(true));
    }

    @AfterEach
    void tearDown() {
        stationRepo.delete(station.id());
        accounts.forEach(account -> accountRepo.delete(account.id()));
    }

    private StationMember member(String name, StationUserType type) {
        var account = accountRepo.create(name.toLowerCase() + "@rule-service.test", name, "Rule");
        accounts.add(account);
        var member = stationMemberRepo.create(station.id(), account.id());
        stationMemberRepo.setUserType(member.id(), type);
        return member;
    }

    private RuleOwner groupWithRule(String name, RuleNode rule) {
        var owner = RuleOwner.group(memberGroupRepo.create(station.id(), name).id());
        memberRuleRepo.replace(station.id(), owner, rule);
        return owner;
    }

    private RuleOwner tagWithRule(String name, RuleNode rule) {
        var owner = RuleOwner.tag(userTagRepo.create(station.id(), name).id());
        memberRuleRepo.replace(station.id(), owner, rule);
        return owner;
    }

    private RuleNode withJuleica() {
        return RuleNode.all(RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty()));
    }

    private List<Integer> membersOf(RuleOwner owner) {
        var members = owner.kind() == RuleOwner.Kind.GROUP
                ? memberGroupRepo.findMembers(owner.id())
                : userTagRepo.findMembers(owner.id());
        return members.stream().map(StationMember::id).sorted().toList();
    }

    /** A tag asking about a group kept by a rule is filled in the same run, after the group. */
    @Test
    void oneRunSettlesRulesThatAskAboutEachOther() {
        var group = RuleOwner.group(
                memberGroupRepo.create(station.id(), "Mit JuLeiCa").id());
        var tag = tagWithRule("Leitung", RuleNode.all(RuleNode.group(RuleOperator.IN, group.id())));
        memberRuleRepo.replace(station.id(), group, withJuleica());

        var changes = service.refresh(station.id(), TODAY);

        assertEquals(
                List.of(
                        new RuleChange(group, List.of(anna.id()), List.of()),
                        new RuleChange(tag, List.of(anna.id()), List.of())),
                changes);
        assertEquals(List.of(anna.id()), membersOf(tag));
        verify(eventBus).publish(StationAudienceChanged.at(station.id()));
    }

    @Test
    void aRunThatMovesNobodySaysNothing() {
        groupWithRule("Mit JuLeiCa", withJuleica());
        service.refresh(station.id(), TODAY);

        assertTrue(service.refresh(station.id(), TODAY).isEmpty());

        verify(eventBus, times(1)).publish(any(StationAudienceChanged.class));
    }

    /** Somebody put in by hand who does not match is taken out; somebody who now matches comes in. */
    @Test
    void theRuleAloneDecides() {
        var group = groupWithRule("Mit JuLeiCa", withJuleica());
        memberGroupRepo.addMember(group.id(), clara.id());
        profileFieldRepo.setValue(ben.id(), juleica, JsonNodeFactory.instance.booleanNode(true));

        var changes = service.refresh(station.id(), TODAY);

        assertEquals(List.of(new RuleChange(group, List.of(anna.id(), ben.id()), List.of(clara.id()))), changes);
        assertEquals(List.of(anna.id(), ben.id()), membersOf(group));
    }

    /**
     * A rule asking about something deleted underneath it cannot decide who belongs, so it is left alone and keeps its
     * members, even where the lost check sits in a block next to checks that still work.
     */
    @Test
    void aRuleThatLostATargetKeepsItsMembers() {
        var group = groupWithRule("Mit JuLeiCa", withJuleica());
        service.refresh(station.id(), TODAY);
        var lost = RuleNode.check(RuleSubject.TAG, RuleOperator.IN, null, null, null, null, null, null);
        memberRuleRepo.replace(
                station.id(),
                group,
                RuleNode.any(lost, RuleNode.field(juleica, RuleOperator.IS_YES, RuleValue.empty())));
        memberGroupRepo.addMember(group.id(), clara.id());

        assertTrue(service.refresh(station.id(), TODAY).isEmpty());
        assertEquals(List.of(anna.id(), clara.id()), membersOf(group));
    }

    @Test
    void aBoundGroupTakesOnlyItsTypes() {
        var group = groupWithRule("Alle", RuleNode.all());
        memberGroupRepo.replaceUserTypes(group.id(), List.of(StationUserType.MEMBER));

        service.refresh(station.id(), TODAY);

        assertEquals(List.of(ben.id(), clara.id()), membersOf(group));
    }

    /** Rules asking about each other in a circle are left alone; the others still run. */
    @Test
    void aCircleIsLeftAlone() {
        var first = groupWithRule("Eins", RuleNode.all());
        var second = groupWithRule("Zwei", RuleNode.all(RuleNode.group(RuleOperator.IN, first.id())));
        memberRuleRepo.replace(station.id(), first, RuleNode.all(RuleNode.group(RuleOperator.IN, second.id())));
        memberGroupRepo.addMember(first.id(), clara.id());
        var sane = tagWithRule("Mit JuLeiCa", withJuleica());

        var changes = service.refresh(station.id(), TODAY);

        assertEquals(List.of(new RuleChange(sane, List.of(anna.id()), List.of())), changes);
        assertEquals(List.of(clara.id()), membersOf(first));
    }

    @Test
    void aStationWithoutRulesRunsNothing() {
        assertTrue(service.refresh(station.id()).isEmpty());
        verify(eventBus, never()).publish(any());
    }

    /** The current day comes from the station's clock when the run names none. */
    @Test
    void aRunWithoutADayCountsFromToday() {
        var group = groupWithRule("Mit JuLeiCa", withJuleica());

        service.refresh(station.id());

        assertEquals(List.of(anna.id()), membersOf(group));
    }

    /** Several changes in a row lead to one run, a few seconds later, through the lane. */
    @Test
    void changesSettleIntoOneRun() {
        var group = groupWithRule("Mit JuLeiCa", withJuleica());
        var later = ArgumentCaptor.forClass(Runnable.class);

        service.stationChanged(station.id());
        service.stationChanged(station.id());

        verify(scheduler, times(1)).later(eq("member-rules-settle"), eq(Duration.ofSeconds(3)), later.capture());
        later.getValue().run();
        var queued = ArgumentCaptor.forClass(Runnable.class);
        verify(lane).submit(queued.capture());
        queued.getValue().run();
        assertEquals(List.of(anna.id()), membersOf(group));

        service.stationChanged(station.id());
        verify(scheduler, times(2)).later(eq("member-rules-settle"), any(), any());
    }

    @Test
    void theSweepQueuesEveryStationWithRules() {
        var group = groupWithRule("Mit JuLeiCa", withJuleica());
        var task = service.scheduledTasks().getFirst();
        assertEquals("member-rule-sweep", task.name());
        assertEquals(Schedule.fixedDelay(Duration.ofMinutes(2), Duration.ofMinutes(30)), task.schedule());

        task.work().run();

        var queued = ArgumentCaptor.forClass(Runnable.class);
        verify(lane, atLeastOnce()).submit(queued.capture());
        queued.getAllValues().forEach(Runnable::run);
        assertEquals(List.of(anna.id()), membersOf(group));
    }

    /** A rule that fails leaves the others running, and what they moved is still announced. */
    @Test
    void aFailingRuleDoesNotStopTheOthers() {
        var broken = groupWithRule("Kaputt", withJuleica());
        var working = tagWithRule("Mit JuLeiCa", withJuleica());
        var repository = spy(memberRuleRepo);
        doThrow(new IllegalStateException("broken")).when(repository).apply(eq(broken), any());
        var guarded = new MemberRuleService(repository, memberGroupRepo, stationRepo, scheduler, eventBus);

        var changes = guarded.refresh(station.id(), TODAY);

        assertEquals(List.of(new RuleChange(working, List.of(anna.id()), List.of())), changes);
        verify(eventBus).publish(StationAudienceChanged.at(station.id()));
    }

    /** A change inside a transaction lets the rules run only once it has landed, and never after a rollback. */
    @Test
    void handlersWaitForTheChangeToLand() {
        var rules = mock(MemberRuleService.class);
        var audience = new AudienceRuleHandler(() -> rules);
        var answers = new AnswersRuleHandler(() -> rules);

        Transactions.run(() -> {
            audience.handle(StationAudienceChanged.at(station.id()));
            answers.handle(new MemberAnswersChanged(station.id(), anna.id()));
            verifyNoInteractions(rules);
        });
        verify(rules, times(2)).stationChanged(station.id());

        var other = mock(MemberRuleService.class);
        assertThrows(
                IllegalStateException.class,
                () -> Transactions.run(() -> {
                    new AudienceRuleHandler(() -> other).handle(StationAudienceChanged.at(station.id()));
                    throw new IllegalStateException("rolled back");
                }));
        verifyNoInteractions(other);
    }

    /** A sweep does not queue a station whose run is already waiting. */
    @Test
    void theSweepSkipsAStationAlreadyWaiting() {
        groupWithRule("Mit JuLeiCa", withJuleica());
        service.stationChanged(station.id());

        service.sweep();
        service.sweep();

        verify(lane, never()).submit(any());
    }
}
