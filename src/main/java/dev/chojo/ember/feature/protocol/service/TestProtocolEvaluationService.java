/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The evaluation of a test run: how many points each tested member reached in every section,
 * against how many the section offers.
 */
@Singleton
public class TestProtocolEvaluationService {
    private final TestProtocolService protocols;

    @Inject
    public TestProtocolEvaluationService(TestProtocolService protocols) {
        this.protocols = protocols;
    }

    /**
     * Evaluates a run the caller already holds.
     *
     * @param run the run, already checked to belong to the caller's station
     */
    public EvaluationResponse evaluate(TestProtocolRun run) {
        var protocol = protocols
                .findProtocol(run.protocolId())
                .orElseThrow(Refusal.PROTOCOL_NOT_HERE_BEHIND_RUN_TO_EVALUATE::raise);
        var sections = protocols.findSections(run.protocolId());
        Map<Integer, List<TestProtocolItem>> itemsBySection =
                protocols.findAllItemsByProtocol(run.protocolId()).stream()
                        .collect(Collectors.groupingBy(TestProtocolItem::sectionId));
        var members = protocols.findRunMembers(run.id()).stream()
                .map(member -> score(run.id(), member, sections, itemsBySection))
                .toList();
        var sectionMaxPoints = new HashMap<Integer, Double>();
        for (var section : sections) {
            sectionMaxPoints.put(section.id(), points(itemsBySection.getOrDefault(section.id(), List.of())));
        }
        return new EvaluationResponse(
                protocol.name(), run.testDate(), sections, sectionMaxPoints, members, protocol.passThreshold());
    }

    private EvalMemberData score(
            int runId,
            TestProtocolRunMember member,
            List<TestProtocolSection> sections,
            Map<Integer, List<TestProtocolItem>> itemsBySection) {
        var checked = protocols.findChecks(runId, member.memberId()).stream()
                .filter(TestProtocolRunCheck::checked)
                .map(TestProtocolRunCheck::itemId)
                .collect(Collectors.toSet());
        var sectionScores = new HashMap<Integer, Double>();
        for (var section : sections) {
            sectionScores.put(
                    section.id(),
                    points(itemsBySection.getOrDefault(section.id(), List.of()).stream()
                            .filter(item -> checked.contains(item.id()))
                            .toList()));
        }
        return new EvalMemberData(member.memberId(), member.totalScore(), sectionScores);
    }

    private static double points(List<TestProtocolItem> items) {
        return items.stream().map(TestProtocolItem::points).reduce(0.0, Double::sum);
    }

    public record EvalMemberData(int memberId, Double totalScore, Map<Integer, Double> sectionScores) {}

    public record EvaluationResponse(
            String protocolName,
            LocalDate testDate,
            List<TestProtocolSection> sections,
            Map<Integer, Double> sectionMaxPoints,
            List<EvalMemberData> members,
            Integer passThreshold) {}
}
