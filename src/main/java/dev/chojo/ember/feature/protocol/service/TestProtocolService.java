/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.protocol.service;

import dev.chojo.ember.feature.protocol.entity.TestProtocol;
import dev.chojo.ember.feature.protocol.entity.TestProtocolItem;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRun;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunCheck;
import dev.chojo.ember.feature.protocol.entity.TestProtocolRunMember;
import dev.chojo.ember.feature.protocol.entity.TestProtocolSection;
import dev.chojo.ember.feature.protocol.repository.TestProtocolRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class TestProtocolService {

    private final TestProtocolRepository repository;

    @Inject
    public TestProtocolService(TestProtocolRepository repository) {
        this.repository = repository;
    }

    // -- Protocols --

    public List<TestProtocol> findProtocols(int stationId) {
        return repository.findProtocols(stationId);
    }

    public List<TestProtocol> searchProtocols(int stationId, String query) {
        return repository.searchProtocols(stationId, query);
    }

    public Optional<TestProtocol> findProtocol(int id) {
        return repository.findProtocolById(id);
    }

    public TestProtocol createProtocol(int stationId, String name, String description, Integer passThreshold) {
        return repository.createProtocol(stationId, name, description, passThreshold);
    }

    public boolean updateProtocol(int id, String name, String description, Integer passThreshold) {
        return repository.updateProtocol(id, name, description, passThreshold);
    }

    public boolean deleteProtocol(int id) {
        return repository.deleteProtocol(id);
    }

    // -- Sections --

    public List<TestProtocolSection> findSections(int protocolId) {
        return repository.findSections(protocolId);
    }

    public TestProtocolSection createSection(
            int protocolId,
            Integer parentId,
            String name,
            String description,
            Integer maxPoints,
            Integer passThreshold,
            int position) {
        return repository.createSection(protocolId, parentId, name, description, maxPoints, passThreshold, position);
    }

    public boolean updateSection(
            int id, String name, String description, Integer maxPoints, Integer passThreshold, int position) {
        return repository.updateSection(id, name, description, maxPoints, passThreshold, position);
    }

    public boolean deleteSection(int id) {
        return repository.deleteSection(id);
    }

    // -- Items --

    public List<TestProtocolItem> findItems(int sectionId) {
        return repository.findItems(sectionId);
    }

    public List<TestProtocolItem> findAllItemsByProtocol(int protocolId) {
        return repository.findAllItemsByProtocol(protocolId);
    }

    public TestProtocolItem createItem(int sectionId, String label, String description, double points, int position) {
        return repository.createItem(sectionId, label, description, points, position);
    }

    public boolean updateItem(int id, String label, String description, double points, int position) {
        return repository.updateItem(id, label, description, points, position);
    }

    public boolean deleteItem(int id) {
        return repository.deleteItem(id);
    }

    // -- Runs --

    public List<TestProtocolRun> findRuns(int stationId) {
        return repository.findRuns(stationId);
    }

    public Optional<TestProtocolRun> findRun(int id) {
        return repository.findRunById(id);
    }

    public TestProtocolRun createRun(int protocolId, int stationId, String name, LocalDate testDate, int createdBy) {
        return repository.createRun(protocolId, stationId, name, testDate, createdBy);
    }

    public boolean updateRun(int id, String name, LocalDate testDate) {
        return repository.updateRun(id, name, testDate);
    }

    public boolean closeRun(int id) {
        return repository.closeRun(id);
    }

    public boolean deleteRun(int id) {
        return repository.deleteRun(id);
    }

    // -- Run Members --

    public List<TestProtocolRunMember> findRunMembers(int runId) {
        return repository.findRunMembers(runId);
    }

    public Optional<TestProtocolRunMember> findRunMember(int runId, int memberId) {
        return repository.findRunMember(runId, memberId);
    }

    public TestProtocolRunMember addRunMember(int runId, int memberId) {
        return repository.addRunMember(runId, memberId);
    }

    public void addRunMembers(int runId, List<Integer> memberIds) {
        for (int memberId : memberIds) {
            repository.addRunMember(runId, memberId);
        }
    }

    public boolean lockMember(int runId, int memberId, int lockedBy) {
        var rm = repository.findRunMember(runId, memberId);
        return rm.filter(testProtocolRunMember -> repository.lockMember(testProtocolRunMember.id(), lockedBy))
                .isPresent();
    }

    public boolean unlockMember(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        return rm.filter(testProtocolRunMember -> repository.unlockMember(testProtocolRunMember.id()))
                .isPresent();
    }

    public void saveChecks(int runId, int memberId, Map<Integer, Boolean> checks, int checkedBy, int protocolId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return;
        int runMemberId = rm.get().id();
        for (var entry : checks.entrySet()) {
            repository.upsertCheck(runMemberId, entry.getKey(), entry.getValue(), checkedBy);
        }
        // Recalculate and update score
        var allChecks = repository.findChecks(runMemberId);
        var allItems = repository.findAllItemsByProtocol(protocolId);
        var itemPoints = allItems.stream().collect(Collectors.toMap(TestProtocolItem::id, TestProtocolItem::points));
        double score = 0;
        for (var c : allChecks) {
            if (c.checked() && itemPoints.containsKey(c.itemId())) {
                score += itemPoints.get(c.itemId());
            }
        }
        repository.updateScore(runMemberId, score);
    }

    // -- Section Done --

    public List<Integer> findDoneSections(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return List.of();
        return repository.findDoneSections(rm.get().id());
    }

    public void toggleSectionDone(int runId, int memberId, int sectionId, int doneBy) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return;
        int runMemberId = rm.get().id();
        var done = repository.findDoneSections(runMemberId);
        if (done.contains(sectionId)) {
            repository.unmarkSectionDone(runMemberId, sectionId);
        } else {
            repository.markSectionDone(runMemberId, sectionId, doneBy);
        }
    }

    public int countDoneSections(int runMemberId) {
        return repository.countDoneSections(runMemberId);
    }

    public List<TestProtocolRunCheck> findChecks(int runId, int memberId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return List.of();
        return repository.findChecks(rm.get().id());
    }

    public boolean completeMember(int runId, int memberId, int protocolId) {
        var rm = repository.findRunMember(runId, memberId);
        if (rm.isEmpty()) return false;
        int runMemberId = rm.get().id();

        // Calculate total score from checked items
        var checks = repository.findChecks(runMemberId);
        var allItems = repository.findAllItemsByProtocol(protocolId);
        var itemPoints = allItems.stream().collect(Collectors.toMap(TestProtocolItem::id, TestProtocolItem::points));

        double totalScore = 0;
        for (var check : checks) {
            if (check.checked() && itemPoints.containsKey(check.itemId())) {
                totalScore += itemPoints.get(check.itemId());
            }
        }

        return repository.completeMember(runMemberId, totalScore);
    }
}
