/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.TestProtocolRefusal;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunExaminer;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolExaminerRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The examiners of a test run, planned per section, and what each of them may grade.
 *
 * <p>An examiner named on a section examines it and everything under it; a subsection may name
 * examiners of its own on top. A run nobody was planned for is graded by every tester, as runs always
 * were. Once a run has examiners, only they open its grading, each changing the points of their own
 * sections and of the sections nobody was named for. Protocol managers are held to the plan like
 * everybody else: one standing in for an examiner who cannot come names themselves on the planning
 * page, which also records who examined what.
 */
@Singleton
public class TestProtocolExaminerService {
    private static final Logger log = LoggerFactory.getLogger(TestProtocolExaminerService.class);

    private final TestProtocolExaminerRepository repository;
    private final TestProtocolService protocols;
    private final StationMemberService members;

    @Inject
    public TestProtocolExaminerService(
            TestProtocolExaminerRepository repository, TestProtocolService protocols, StationMemberService members) {
        this.repository = repository;
        this.protocols = protocols;
        this.members = members;
    }

    /** The examiners of each section of a run that has any, in section order. */
    public List<SectionExaminers> examinersOf(int runId) {
        return group(repository.findByRun(runId));
    }

    /** Who may be named an examiner at a station: everybody allowed to grade protocols. */
    public List<StationMember> candidates(int stationId) {
        return members.findMembersWithPermission(stationId, StationPermission.PROTOCOL_TESTER);
    }

    /**
     * Replaces every examiner of a run.
     *
     * @param run         the run, already checked to belong to the caller's station
     * @param assignments the examiners per section; a section left out has none of its own
     */
    public void plan(TestProtocolRun run, List<SectionExaminers> assignments) {
        var sectionIds = protocols.findSections(run.protocolId()).stream()
                .map(TestProtocolSection::id)
                .collect(Collectors.toSet());
        var testerIds =
                candidates(run.stationId()).stream().map(StationMember::id).collect(Collectors.toSet());
        for (var assignment : assignments) {
            if (!sectionIds.contains(assignment.sectionId())) {
                throw TestProtocolRefusal.PROTOCOL_EXAMINER_SECTION_ELSEWHERE.raise();
            }
            if (!testerIds.containsAll(assignment.memberIds())) {
                throw TestProtocolRefusal.PROTOCOL_EXAMINER_NOT_A_TESTER.raise();
            }
        }
        Transactions.run(() -> {
            repository.deleteByRun(run.id());
            for (var assignment : assignments) {
                repository.replaceForSection(
                        run.id(), assignment.sectionId(), new LinkedHashSet<>(assignment.memberIds()));
            }
        });
        log.info("Planned {} sections with examiners for test run {}", assignments.size(), run.id());
    }

    /**
     * The examiners of the last planned run of the same protocol, kept to the sections and testers
     * that are still there, for a recurring examination planned like the one before.
     */
    public List<SectionExaminers> previousPlan(TestProtocolRun run) {
        var sectionIds = protocols.findSections(run.protocolId()).stream()
                .map(TestProtocolSection::id)
                .collect(Collectors.toSet());
        var testerIds =
                candidates(run.stationId()).stream().map(StationMember::id).collect(Collectors.toSet());
        return repository
                .findPreviousPlannedRun(run.protocolId(), run.id())
                .map(previous -> examinersOf(previous).stream()
                        .filter(assignment -> sectionIds.contains(assignment.sectionId()))
                        .map(assignment -> new SectionExaminers(
                                assignment.sectionId(),
                                assignment.memberIds().stream()
                                        .filter(testerIds::contains)
                                        .toList()))
                        .filter(assignment -> !assignment.memberIds().isEmpty())
                        .toList())
                .orElse(List.of());
    }

    /**
     * What one member may grade in a run.
     *
     * @param run      the run
     * @param memberId the station member asking
     */
    public GradingScope scopeOf(TestProtocolRun run, int memberId) {
        var assignments = repository.findByRun(run.id());
        var sections = protocols.findSections(run.protocolId());
        var all = sections.stream().map(TestProtocolSection::id).collect(Collectors.toSet());
        if (assignments.isEmpty()) return new GradingScope(true, false, all);
        boolean examiner = assignments.stream().anyMatch(assignment -> assignment.memberId() == memberId);
        if (!examiner) return new GradingScope(false, true, Set.of());
        var effective = effectiveExaminers(sections, assignments);
        var gradable = sections.stream()
                .map(TestProtocolSection::id)
                .filter(id -> effective.get(id).isEmpty() || effective.get(id).contains(memberId))
                .collect(Collectors.toSet());
        return new GradingScope(true, true, gradable);
    }

    /**
     * The scope of a member who means to grade, refused when they may not grade the run at all.
     */
    public GradingScope requireGrader(TestProtocolRun run, int memberId) {
        var scope = scopeOf(run, memberId);
        if (!scope.mayGrade()) throw TestProtocolRefusal.PROTOCOL_RUN_GRADED_BY_ITS_EXAMINERS.raise();
        return scope;
    }

    /**
     * Keeps only the ticks a scope may change. Every other point keeps what is stored, so an examiner
     * saving a sheet that still shows another examiner's section as it was a minute ago does not undo
     * that examiner's work.
     *
     * @param requested the ticks as the sheet sent them
     * @param items     every point of the protocol
     */
    public Map<Integer, Boolean> allowedChecks(
            GradingScope scope, Map<Integer, Boolean> requested, List<TestProtocolItem> items) {
        if (!scope.restricted()) return requested;
        var allowedItems = items.stream()
                .filter(item -> scope.sectionIds().contains(item.sectionId()))
                .map(TestProtocolItem::id)
                .collect(Collectors.toSet());
        var allowed = new HashMap<Integer, Boolean>();
        requested.forEach((itemId, checked) -> {
            if (allowedItems.contains(itemId)) allowed.put(itemId, checked);
        });
        return allowed;
    }

    /**
     * Refuses marking a top-level section finished when nothing in it is the member's to grade.
     *
     * @param sectionId the top-level section
     */
    public void requireFinishable(TestProtocolRun run, GradingScope scope, int sectionId) {
        if (!scope.restricted()) return;
        var subtree = TestProtocolService.subtreeOf(sectionId, protocols.findSections(run.protocolId()));
        if (subtree.stream().noneMatch(scope.sectionIds()::contains)) {
            throw TestProtocolRefusal.PROTOCOL_SECTION_NOT_YOURS_TO_FINISH.raise();
        }
    }

    /** Whether a run has examiners, which is what stops one tester from holding a member for everyone. */
    public boolean isPlanned(int runId) {
        return !repository.findByRun(runId).isEmpty();
    }

    /**
     * The examiners in charge of every section: its own and those of every section above it. A section
     * with none is open to every examiner of the run.
     */
    static Map<Integer, Set<Integer>> effectiveExaminers(
            List<TestProtocolSection> sections, List<TestProtocolRunExaminer> assignments) {
        var own = new HashMap<Integer, Set<Integer>>();
        for (var assignment : assignments) {
            own.computeIfAbsent(assignment.sectionId(), _ -> new HashSet<>()).add(assignment.memberId());
        }
        var effective = new HashMap<Integer, Set<Integer>>();
        for (var section : TestProtocolService.parentsFirst(sections)) {
            var examiners = new HashSet<Integer>(own.getOrDefault(section.id(), Set.of()));
            if (section.parentId() != null) examiners.addAll(effective.getOrDefault(section.parentId(), Set.of()));
            effective.put(section.id(), examiners);
        }
        for (var section : sections) effective.putIfAbsent(section.id(), Set.of());
        return effective;
    }

    private static List<SectionExaminers> group(List<TestProtocolRunExaminer> assignments) {
        var bySection = assignments.stream()
                .collect(Collectors.groupingBy(
                        TestProtocolRunExaminer::sectionId,
                        TreeMap::new,
                        Collectors.mapping(TestProtocolRunExaminer::memberId, Collectors.toList())));
        return bySection.entrySet().stream()
                .map(entry -> new SectionExaminers(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * The examiners named for one section.
     *
     * @param sectionId the section
     * @param memberIds the station members examining it
     */
    public record SectionExaminers(int sectionId, List<Integer> memberIds) {
        public SectionExaminers {
            memberIds = List.copyOf(Objects.requireNonNullElse(memberIds, List.of()));
        }
    }

    /**
     * What a member may grade in a run.
     *
     * @param mayGrade   whether they may open its grading at all
     * @param restricted whether the run has examiners, so that not every section is theirs
     * @param sectionIds the sections whose points they may change
     */
    public record GradingScope(boolean mayGrade, boolean restricted, Set<Integer> sectionIds) {}
}
