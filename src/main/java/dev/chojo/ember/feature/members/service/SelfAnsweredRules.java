/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.feature.members.entity.FieldOrigin;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.RuleNode;
import dev.chojo.ember.feature.members.entity.RuleOwner;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.MemberRuleRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService.FieldKey;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
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
 * field is put to anybody without being read only to them. An association's question counts as well whatever its
 * locks: the association writes the answers past every lock and holds none of the station's permissions. The rule
 * is followed through every rule-kept group and tag it asks about, since a tag filled from such an answer carries
 * the same choice one step further.
 *
 * <p>The management passes the read-only lock, so a question only it writes may decide such a group. Changing one
 * of those answers moves people into or out of the group the way a change by hand does, and needs what a change by
 * hand needs: the permissions the group grants.
 */
@Singleton
public class SelfAnsweredRules {
    private final ProfileFieldRepository fieldRepository;
    private final MemberGroupRepository groupRepository;
    private final MemberRuleRepository ruleRepository;

    @Inject
    public SelfAnsweredRules(
            ProfileFieldRepository fieldRepository,
            MemberGroupRepository groupRepository,
            MemberRuleRepository ruleRepository) {
        this.fieldRepository = fieldRepository;
        this.groupRepository = groupRepository;
        this.ruleRepository = ruleRepository;
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
     * Refuses when any group of the station granting permissions takes its members, directly or through other
     * rules, from an answer its members write themselves, naming the first such group. Called after a question was
     * changed or put to somebody else, inside the transaction of that change, which the refusal then undoes.
     *
     * @param stationId the station whose questions changed
     */
    public void requireNoneAt(int stationId) {
        var rules = ruleRepository.findByStation(stationId);
        for (var owner : rules.keySet()) {
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
        if (rule != null && readsSelfAnswer(rule, rules, new HashSet<>())) {
            throw MemberRefusal.RULE_PERMISSIONS_FROM_OWN_ANSWER.raise(group.name());
        }
    }

    /**
     * Refuses a change of answers by the management that moves people into or out of a group granting permissions
     * the writer does not hold, because the group's rule reads one of the answers, directly or through other rules,
     * naming that group.
     *
     * @param stationId the station of the member whose answers changed
     * @param changed   the questions whose answers actually changed
     * @param held      the writer's station permissions, expanded
     */
    public void requireMayChangeAnswers(int stationId, Collection<FieldKey> changed, Set<StationPermission> held) {
        var rules = ruleRepository.findByStation(stationId);
        for (var owner : rules.keySet()) {
            if (owner.kind() != RuleOwner.Kind.GROUP) continue;
            var group = groupRepository.findById(owner.id()).orElse(null);
            if (group == null) continue;
            var granted = groupRepository.findGroupPermissions(group.id());
            if (granted.isEmpty()
                    || PermissionValidation.firstNotHeld(granted, held).isEmpty()) continue;
            if (readsAnyOf(rules.get(owner), rules, changed, new HashSet<>())) {
                throw MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_THROUGH_RULE.raise(group.name());
            }
        }
    }

    private boolean readsAnyOf(
            RuleNode rule, Map<RuleOwner, RuleNode> rules, Collection<FieldKey> changed, Set<RuleOwner> seen) {
        for (var check : rule.checks()) {
            Integer field = check.fieldId();
            Integer clusterField = check.clusterFieldId();
            if (field != null && changed.contains(new FieldKey(FieldOrigin.STATION, field))) return true;
            if (clusterField != null && changed.contains(new FieldKey(FieldOrigin.CLUSTER, clusterField))) return true;
        }
        for (var asked : RuleDependencies.dependsOn(rule, rules)) {
            if (seen.add(asked) && readsAnyOf(rules.get(asked), rules, changed, seen)) return true;
        }
        return false;
    }

    private boolean readsSelfAnswer(RuleNode rule, Map<RuleOwner, RuleNode> rules, Set<RuleOwner> seen) {
        for (var check : rule.checks()) {
            Integer field = check.fieldId();
            Integer clusterField = check.clusterFieldId();
            if (field != null && membersWrite(field)) return true;
            if (clusterField != null) return true;
        }
        for (var asked : RuleDependencies.dependsOn(rule, rules)) {
            if (seen.add(asked) && readsSelfAnswer(rules.get(asked), rules, seen)) return true;
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
}
