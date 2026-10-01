/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Creates and changes a group together with its rules: the user types it is bound to and the set it
 * belongs to.
 *
 * <p>A rule set on a group that already has members can break it for some of them. Binding a group
 * to types some members are not of is refused with those members named, unless the caller asked to
 * take them out of the group. Putting a group into a set where some members are also in another
 * group of the set is always refused with the list, because there is no safe way to pick for them
 * which group they stay in.
 */
@Singleton
public class GroupRulesService {
    private static final Logger log = LoggerFactory.getLogger(GroupRulesService.class);
    private final MemberGroupRepository groupRepository;
    private final MemberGroupSetRepository setRepository;
    private final GroupMembershipService membershipService;

    @Inject
    public GroupRulesService(
            MemberGroupRepository groupRepository,
            MemberGroupSetRepository setRepository,
            GroupMembershipService membershipService) {
        this.groupRepository = groupRepository;
        this.setRepository = setRepository;
        this.membershipService = membershipService;
    }

    /**
     * Creates a group with its rules.
     *
     * @param stationId the station
     * @param name      the group's name
     * @param rules     its binding and set, or {@code null} for a group in no set that takes every type
     * @param by        who is asking
     * @return the new group
     */
    public MemberGroup create(int stationId, String name, @Nullable GroupRules rules, UserSession by) {
        var group = Transactions.call(() -> {
            var created = groupRepository.create(stationId, name);
            if (rules != null) apply(created, rules, false, by);
            return groupRepository.findById(created.id()).orElseThrow();
        });
        log.info("Group created: id={}, station={}, name='{}'", group.id(), stationId, name);
        return group;
    }

    /**
     * Changes a group's name, colour, position and, where given, its rules.
     *
     * @param group             the group, already checked to belong to the caller's station
     * @param name              its new name
     * @param color             its new colour, or {@code null} for none
     * @param position          its new position
     * @param rules             its new binding and set, or {@code null} to leave both as they are
     * @param removeNonMatching whether members the new binding does not take are to be taken out of
     *                          the group, rather than refused
     * @param by                who is asking
     * @return the group as it is afterwards
     */
    public MemberGroup update(
            MemberGroup group,
            String name,
            @Nullable String color,
            int position,
            @Nullable GroupRules rules,
            boolean removeNonMatching,
            UserSession by) {
        var updated = Transactions.call(() -> {
            if (rules != null) apply(group, rules, removeNonMatching, by);
            if (!groupRepository.update(group.id(), name, color, position)) {
                throw Refusal.GROUP_NOT_HERE_ON_CHANGE.raise();
            }
            return groupRepository.findById(group.id()).orElseThrow(Refusal.GROUP_NOT_HERE_ON_CHANGE::raise);
        });
        log.info("Group updated: id={}, name='{}'", group.id(), name);
        return updated;
    }

    private void apply(MemberGroup group, GroupRules rules, boolean removeNonMatching, UserSession by) {
        Integer setId = rules.groupSetId();
        if (setId != null) {
            setRepository
                    .findById(setId)
                    .filter(set -> set.stationId() == group.stationId())
                    .orElseThrow(Refusal.GROUP_SET_NOT_HERE_FOR_GROUP::raise);
        }
        Set<StationUserType> userTypes = rules.userTypes();
        leaveMisfits(group, userTypes, removeNonMatching, by);
        groupRepository.replaceUserTypes(group.id(), userTypes);
        if (setId != null && !setId.equals(group.groupSetId())) requireNoOverlap(group, setId);
        groupRepository.assignSet(group.id(), setId);
    }

    private void leaveMisfits(
            MemberGroup group, Set<StationUserType> userTypes, boolean removeNonMatching, UserSession by) {
        if (userTypes.isEmpty()) return;
        List<StationMember> misfits = groupRepository.findMembersNotOfTypes(group.id(), userTypes);
        if (misfits.isEmpty()) return;
        if (!removeNonMatching) {
            throw GroupRuleRefused.naming(
                    Refusal.GROUP_BINDING_EXCLUDES_MEMBERS,
                    misfits.stream()
                            .map(member -> membershipService.conflict(member.id(), List.of(group.name())))
                            .toList());
        }
        membershipService.requirePresenceFor(List.of(group), by);
        misfits.forEach(member -> groupRepository.removeMember(group.id(), member.id()));
        log.info("Group {} bound to {}: {} member(s) taken out", group.id(), userTypes, misfits.size());
    }

    private void requireNoOverlap(MemberGroup group, int setId) {
        Map<Integer, MemberGroup> setGroups = groupRepository.findByStation(group.stationId()).stream()
                .filter(candidate ->
                        candidate.id() == group.id() || Integer.valueOf(setId).equals(candidate.groupSetId()))
                .collect(Collectors.toMap(MemberGroup::id, Function.identity()));
        var overlaps = groupRepository.findOverlaps(setGroups.keySet());
        if (overlaps.isEmpty()) return;
        var conflicts = new ArrayList<GroupRuleRefused.GroupConflict>();
        overlaps.forEach((memberId, groupIds) -> conflicts.add(membershipService.conflict(
                memberId,
                groupIds.stream()
                        .map(setGroups::get)
                        .map(MemberGroup::name)
                        .sorted()
                        .toList())));
        conflicts.sort(Comparator.comparing(GroupRuleRefused.GroupConflict::memberName));
        throw GroupRuleRefused.naming(Refusal.GROUP_SET_MEMBERS_OVERLAP, conflicts);
    }

    /**
     * The rules of a group.
     *
     * @param groupSetId the set it belongs to, or {@code null} for none
     * @param userTypes  the types it takes, empty for every type
     */
    public record GroupRules(Integer groupSetId, Set<StationUserType> userTypes) {
        public GroupRules {
            userTypes = userTypes == null || userTypes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(userTypes));
        }
    }
}
