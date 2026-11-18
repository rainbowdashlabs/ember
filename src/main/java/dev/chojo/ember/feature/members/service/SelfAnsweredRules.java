/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.cluster.service.ClusterProfileFieldService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.ProfileFieldScope;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keeps members from choosing their own permissions through a rule.
 *
 * <p>A group that grants permissions may take its members from a rule, but not from an answer the members or their
 * guardians write themselves: whoever changed that answer would put themselves into the group and receive what it
 * grants, which a change by hand is never allowed to do. An answer counts as written by the member wherever the
 * field is put to anybody without being read only to them. The rule is followed through every rule-kept group and
 * tag it asks about, since a tag filled from such an answer carries the same choice one step further.
 */
@Singleton
public class SelfAnsweredRules {
    private final ProfileFieldRepository fieldRepository;
    private final Provider<ClusterProfileFieldService> clusterFields;
    private final MemberGroupRepository groupRepository;

    @Inject
    public SelfAnsweredRules(
            ProfileFieldRepository fieldRepository,
            Provider<ClusterProfileFieldService> clusterFields,
            MemberGroupRepository groupRepository) {
        this.fieldRepository = fieldRepository;
        this.clusterFields = clusterFields;
        this.groupRepository = groupRepository;
    }

    /**
     * Refuses when a group granting permissions would take its members, directly or through other rules, from an
     * answer its members write themselves once this rule is saved, naming the first such group. Only the saved
     * group or tag and the rules asking about it are looked at, since no other rule changes.
     *
     * @param saved the group or tag whose rule is saved
     * @param rules every rule of the station, as it would be after the save
     */
    public void requireNone(RuleOwner saved, Map<RuleOwner, RuleNode> rules) {
        var affected = new LinkedHashSet<RuleOwner>();
        affected.add(saved);
        affected.addAll(RuleDependencies.dependents(saved, rules));
        for (var owner : affected) {
            if (owner.kind() != RuleOwner.Kind.GROUP) continue;
            groupRepository
                    .findById(owner.id())
                    .filter(group ->
                            !groupRepository.findGroupPermissions(group.id()).isEmpty())
                    .ifPresent(group -> requireNoneFor(group, rules));
        }
    }

    /**
     * Refuses when this group takes its members, directly or through other rules, from an answer its members write
     * themselves, whether or not it grants anything yet, naming it.
     *
     * @param group the group, about to grant permissions or already granting them
     * @param rules every rule of its station
     */
    public void requireNoneFor(MemberGroup group, Map<RuleOwner, RuleNode> rules) {
        var rule = rules.get(RuleOwner.group(group.id()));
        if (rule != null && readsSelfAnswer(group.stationId(), rule, rules, new HashSet<>())) {
            throw MemberRefusal.RULE_PERMISSIONS_FROM_OWN_ANSWER.raise(group.name());
        }
    }

    private boolean readsSelfAnswer(int stationId, RuleNode rule, Map<RuleOwner, RuleNode> rules, Set<RuleOwner> seen) {
        for (var check : rule.checks()) {
            Integer field = check.fieldId();
            Integer clusterField = check.clusterFieldId();
            if (field != null && membersWrite(field)) return true;
            if (clusterField != null && membersWriteClusterField(stationId, clusterField)) return true;
        }
        for (var asked : RuleDependencies.dependsOn(rule, rules)) {
            if (seen.add(asked) && readsSelfAnswer(stationId, rules.get(asked), rules, seen)) return true;
        }
        return false;
    }

    private boolean membersWrite(int fieldId) {
        return fieldRepository
                .findById(fieldId)
                .map(field -> fieldRepository.findAssignments(fieldId).stream()
                        .anyMatch(assignment -> !assignment.readonly(field.readonly())))
                .orElse(false);
    }

    private boolean membersWriteClusterField(int stationId, int fieldId) {
        for (var scope : ProfileFieldScope.values()) {
            boolean written = clusterFields.get().findForStation(stationId, scope).stream()
                    .filter(assigned -> assigned.field().id() == fieldId)
                    .anyMatch(assigned -> !assigned.readonly());
            if (written) return true;
        }
        return false;
    }
}
