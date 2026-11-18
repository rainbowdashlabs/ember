/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.StationAudienceChanged;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RuleChange;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.MemberRuleRepository;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.ScheduledTask;
import dev.chojo.ember.lifecycle.SerialLane;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskSource;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the groups and tags that take their members from a rule up to date.
 *
 * <p>A station's rules run again a few seconds after its people change (somebody joins, leaves, changes type,
 * changes groups or tags, or has profile answers changed), so a burst of changes leads to a single run. Every
 * station with rules also runs every half hour, which carries a rule past midnight, when a date check starts
 * counting from a new day, and catches the writers that change member data without saying so. All runs go
 * through one lane, so two runs never meet, and a run already waiting for a station takes the place of another.
 * A rule that fails is logged and the station's other rules still run. A run that moves anybody tells the station
 * that its people changed, as a hand edit would; nobody is notified about joining or leaving by a rule.
 */
@Singleton
public class MemberRuleService implements TaskSource {
    private static final Logger log = LoggerFactory.getLogger(MemberRuleService.class);
    private static final Duration SETTLE = Duration.ofSeconds(3);
    private static final Duration SWEEP = Duration.ofMinutes(30);

    private final MemberRuleRepository ruleRepository;
    private final MemberGroupRepository groupRepository;
    private final StationRepository stationRepository;
    private final TaskScheduler scheduler;
    private final DomainEventBus eventBus;
    private final SerialLane lane;
    private final Set<Integer> pending = ConcurrentHashMap.newKeySet();
    /** The rules already reported as asking about something deleted, so the sweep does not repeat it. */
    private final Set<RuleOwner> resting = ConcurrentHashMap.newKeySet();

    @Inject
    public MemberRuleService(
            MemberRuleRepository ruleRepository,
            MemberGroupRepository groupRepository,
            StationRepository stationRepository,
            TaskScheduler scheduler,
            DomainEventBus eventBus) {
        this.ruleRepository = ruleRepository;
        this.groupRepository = groupRepository;
        this.stationRepository = stationRepository;
        this.scheduler = scheduler;
        this.eventBus = eventBus;
        this.lane = scheduler.lane("member-rules");
    }

    /**
     * Says that the people of a station may have changed, so its rules run again once the changes settle.
     *
     * @param stationId the station
     */
    public void stationChanged(int stationId) {
        if (!pending.add(stationId)) return;
        scheduler.later(
                "member-rules-settle",
                SETTLE,
                () -> lane.submit(() -> {
                    pending.remove(stationId);
                    refresh(stationId);
                }));
    }

    /**
     * Runs every rule of a station on its current day.
     *
     * @param stationId the station
     * @return the changes the run made, one per group or tag that anybody joined or left
     */
    public List<RuleChange> refresh(int stationId) {
        return refresh(stationId, today(stationId));
    }

    /**
     * Runs every rule of a station as on the given day.
     *
     * @param stationId the station
     * @param today     the day the rules count from
     * @return the changes the run made, one per group or tag that anybody joined or left
     */
    public List<RuleChange> refresh(int stationId, LocalDate today) {
        var rules = ruleRepository.findByStation(stationId);
        if (rules.isEmpty()) return List.of();
        var ordered = RuleDependencies.order(rules);
        if (ordered.size() < rules.size()) {
            log.warn("Rules of station {} ask about each other in a circle and are left alone", stationId);
        }
        var changes = new ArrayList<RuleChange>();
        for (var owner : ordered) {
            try {
                var change = run(stationId, owner, rules.get(owner), today);
                if (!change.isEmpty()) changes.add(change);
            } catch (RuntimeException e) {
                log.error("The rule of {} at station {} could not run; the other rules go on", owner, stationId, e);
            }
        }
        if (!changes.isEmpty()) {
            log.info("Rules of station {} moved members: {}", stationId, changes);
            eventBus.publish(StationAudienceChanged.at(stationId));
        }
        return changes;
    }

    private RuleChange run(int stationId, RuleOwner owner, RuleNode rule, LocalDate today) {
        if (rule.lostATarget()) {
            if (resting.add(owner)) {
                log.warn(
                        "The rule of {} at station {} asks about something deleted and keeps its members",
                        owner,
                        stationId);
            }
            return RuleChange.none(owner);
        }
        resting.remove(owner);
        var bound = owner.kind() == RuleOwner.Kind.GROUP
                ? groupRepository
                        .findById(owner.id())
                        .map(MemberGroup::userTypes)
                        .orElse(List.of())
                : List.<StationUserType>of();
        var matched = ruleRepository.matching(stationId, rule, today, bound);
        return Transactions.call(() -> ruleRepository.apply(owner, matched));
    }

    /** Queues a run of every station that keeps a group or tag by a rule, unless one is already waiting. */
    public void sweep() {
        for (int stationId : ruleRepository.stationsWithRules()) {
            if (!pending.add(stationId)) continue;
            lane.submit(() -> {
                pending.remove(stationId);
                refresh(stationId);
            });
        }
    }

    private LocalDate today(int stationId) {
        return LocalDate.now(
                StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null)));
    }

    @Override
    public List<ScheduledTask> scheduledTasks() {
        return List.of(
                new ScheduledTask("member-rule-sweep", Schedule.fixedDelay(Duration.ofMinutes(2), SWEEP), this::sweep));
    }
}
