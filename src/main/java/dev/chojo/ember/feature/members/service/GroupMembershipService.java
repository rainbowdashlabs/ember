/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MembersAddedToGroup;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GroupRuleRefused.GroupConflict;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;

/**
 * The one way into and out of a group, so the rules of a group hold whoever writes a membership.
 *
 * <p>Three rules apply. A group bound to user types takes only members of those types. A group in a
 * set takes nobody who is already in another group of that set. And joining a group that grants
 * permissions is granting those permissions: a person may only put somebody into such a group when
 * they hold everything it grants, and only after proving themselves recently, the same as for a
 * direct grant.
 *
 * <p>People change memberships through {@link #replaceGroupsOfMember} and {@link #setMembers}, which
 * refuse a change that breaks a rule. Registration, invitations, the waiting list and the import put
 * members into groups on their own through {@link #joinAutomatically}, which leaves out a group that
 * does not fit instead of failing somebody's registration. The set rule is also held by the database,
 * so a writer that bypasses this class still cannot put a member into two groups of one set.
 */
@Singleton
public class GroupMembershipService {
    private static final Logger log = LoggerFactory.getLogger(GroupMembershipService.class);
    private final MemberGroupRepository groupRepository;
    private final StationMemberRepository memberRepository;
    private final StepUpGuard stepUpGuard;
    private final Provider<MemberNameResolver> nameResolver;
    private final DomainEventBus eventBus;

    @Inject
    public GroupMembershipService(
            MemberGroupRepository groupRepository,
            StationMemberRepository memberRepository,
            StepUpGuard stepUpGuard,
            Provider<MemberNameResolver> nameResolver,
            DomainEventBus eventBus) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.stepUpGuard = stepUpGuard;
        this.nameResolver = nameResolver;
        this.eventBus = eventBus;
    }

    /**
     * Replaces the groups one member is in, as their edit page sends it.
     *
     * <p>Leaving one group of a set and joining another in the same request is a move and allowed;
     * choosing two groups of one set is refused.
     *
     * @param member   the member, already checked to belong to the caller's station
     * @param groupIds every group they should be in afterwards
     * @param by       who is asking
     * @return the groups they are in afterwards
     */
    public List<MemberGroup> replaceGroupsOfMember(StationMember member, Collection<Integer> groupIds, UserSession by) {
        Map<Integer, MemberGroup> stationGroups = byId(groupRepository.findByStation(member.stationId()));
        Set<Integer> wanted = new LinkedHashSet<>(groupIds);
        if (!stationGroups.keySet().containsAll(wanted)) {
            throw Refusal.GROUP_NOT_HERE_FOR_MEMBER.raise();
        }
        Set<Integer> current = groupIdsOf(member.id());
        List<MemberGroup> added = pick(stationGroups, wanted, id -> !current.contains(id));
        List<MemberGroup> removed = pick(stationGroups, current, id -> !wanted.contains(id));

        for (MemberGroup group : added) {
            if (!group.admits(member.userType())) {
                throw Refusal.GROUP_WRONG_USER_TYPE_FOR_MEMBER.raise(group.name());
            }
        }
        requireOneGroupPerSet(pick(stationGroups, wanted, _ -> true));
        requireRightsFor(added, by, Refusal.GROUP_GRANTS_MORE_THAN_YOURS_FOR_MEMBER);
        requirePresenceFor(concat(added, removed), by);

        Transactions.run(() -> {
            removed.forEach(group -> groupRepository.removeMember(group.id(), member.id()));
            added.forEach(group -> groupRepository.addMember(group.id(), member.id()));
        });
        log.info(
                "Groups of member {} replaced: added={}, removed={}, by={}",
                member.id(),
                ids(added),
                ids(removed),
                by.member().id());
        added.forEach(group -> announce(group, List.of(member.id()), by.member().id()));
        return groupRepository.findGroupsForMember(member.id());
    }

    /**
     * Replaces the members of one group, as the groups page sends it.
     *
     * @param group     the group, already checked to belong to the caller's station
     * @param memberIds every member it should hold afterwards
     * @param move      whether members in another group of the same set are to be moved out of it,
     *                  rather than refused
     * @param by        who is asking
     * @return the group's members afterwards
     */
    public List<StationMember> setMembers(
            MemberGroup group, Collection<Integer> memberIds, boolean move, UserSession by) {
        Map<Integer, StationMember> stationMembers = memberRepository.findByStation(group.stationId(), true).stream()
                .collect(Collectors.toMap(StationMember::id, Function.identity()));
        Set<Integer> wanted = new LinkedHashSet<>(memberIds);
        if (!stationMembers.keySet().containsAll(wanted)) {
            throw Refusal.GROUP_MEMBER_NOT_HERE.raise();
        }
        Set<Integer> current = groupRepository.findMembers(group.id()).stream()
                .map(StationMember::id)
                .collect(Collectors.toSet());
        List<Integer> added =
                wanted.stream().filter(id -> !current.contains(id)).toList();
        List<Integer> removed =
                current.stream().filter(id -> !wanted.contains(id)).toList();

        List<StationMember> misfits = added.stream()
                .map(stationMembers::get)
                .filter(member -> !group.admits(member.userType()))
                .toList();
        if (!misfits.isEmpty()) {
            throw GroupRuleRefused.naming(
                    Refusal.GROUP_WRONG_USER_TYPE_ON_ADD,
                    misfits.stream()
                            .map(m -> conflict(m.id(), List.of(group.name())))
                            .toList());
        }

        Map<Integer, MemberGroup> movedFrom = siblingsOf(group, added);
        if (!movedFrom.isEmpty() && !move) {
            throw GroupRuleRefused.naming(
                    Refusal.GROUP_SET_ALREADY_IN,
                    movedFrom.entrySet().stream()
                            .map(entry -> conflict(
                                    entry.getKey(), List.of(entry.getValue().name())))
                            .toList());
        }

        if (!added.isEmpty()) {
            requireRightsFor(List.of(group), by, Refusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_ADD);
        }
        List<MemberGroup> touched = new ArrayList<>(movedFrom.values());
        if (!added.isEmpty() || !removed.isEmpty()) touched.add(group);
        requirePresenceFor(touched, by);

        Transactions.run(() -> {
            removed.forEach(memberId -> groupRepository.removeMember(group.id(), memberId));
            movedFrom.forEach((memberId, sibling) -> groupRepository.removeMember(sibling.id(), memberId));
            added.forEach(memberId -> groupRepository.addMember(group.id(), memberId));
        });
        log.info(
                "Group membership updated: group={}, added={}, removed={}, moved={}, by={}",
                group.id(),
                added.size(),
                removed.size(),
                movedFrom.size(),
                by.member().id());
        if (!added.isEmpty()) announce(group, added, by.member().id());
        return groupRepository.findMembers(group.id());
    }

    /**
     * Puts a member into a group on behalf of an automatic flow: registration, an invitation, the
     * waiting list or the import.
     *
     * <p>A group that does not fit is left out rather than failing the flow, because the person
     * registering cannot do anything about a group a manager set up. That happens where a group of
     * another station is named, where its binding does not take the member's type, and where the
     * member is already in another group of its set.
     *
     * @param groupId  the group
     * @param memberId the member
     * @return {@code true} where the member is in the group afterwards
     */
    public boolean joinAutomatically(int groupId, int memberId) {
        var group = groupRepository.findById(groupId).orElse(null);
        var member = memberRepository.findById(memberId).orElse(null);
        if (group == null || member == null || group.stationId() != member.stationId()) {
            log.warn("Member {} was not put into group {}: not a group of their station", memberId, groupId);
            return false;
        }
        if (groupIdsOf(memberId).contains(groupId)) return true;
        if (!group.admits(member.userType())) {
            log.info(
                    "Member {} was not put into group {}: it takes {} and they are {}",
                    memberId,
                    groupId,
                    group.userTypes(),
                    member.userType());
            return false;
        }
        if (!siblingsOf(group, List.of(memberId)).isEmpty()) {
            log.info("Member {} was not put into group {}: they are in another group of its set", memberId, groupId);
            return false;
        }
        groupRepository.addMember(groupId, memberId);
        return true;
    }

    /**
     * Takes a member out of a group on behalf of an automatic flow, such as the end of a trial.
     *
     * @param groupId  the group
     * @param memberId the member
     */
    public void leaveAutomatically(int groupId, int memberId) {
        groupRepository.removeMember(groupId, memberId);
    }

    /**
     * Refuses a group chosen for an invitation unless it is one of the station's and grants nothing
     * the inviting person does not hold. Checked before anybody is invited, so a refusal leaves
     * nothing behind.
     *
     * @param stationId the station inviting
     * @param groupId   the group chosen, or {@code null} for none
     * @param by        who is inviting
     */
    public void requireInvitableInto(int stationId, Integer groupId, UserSession by) {
        if (groupId == null) return;
        var group = groupRepository
                .findById(groupId)
                .filter(candidate -> candidate.stationId() == stationId)
                .orElseThrow(Refusal.INVITE_GROUP_NOT_HERE::raise);
        requireRightsFor(List.of(group), by, Refusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_INVITE);
    }

    /**
     * Refuses a group an automatic flow is set up to put people into unless it is one of the station's
     * and takes the type those people will have. Checked where the flow is saved, so it is the manager
     * setting it up who hears about it and not somebody registering later.
     *
     * @param stationId the station
     * @param groupId   the group, or {@code null} for none
     * @param userType  the type people will have when they join it
     * @param notHere   the refusal for a group that is not the station's
     * @param wrongType the refusal for a group that does not take the type
     */
    public void requireAdmits(
            int stationId, Integer groupId, StationUserType userType, Refusal notHere, Refusal wrongType) {
        if (groupId == null) return;
        var group = groupRepository
                .findById(groupId)
                .filter(candidate -> candidate.stationId() == stationId)
                .orElseThrow(notHere::raise);
        if (!group.admits(userType)) {
            throw wrongType.raise(group.name());
        }
    }

    /**
     * The groups a member would have to leave on becoming another type.
     *
     * @param memberId the member
     * @param userType the type they would become
     * @return the groups whose binding does not take that type
     */
    public List<MemberGroup> groupsUnfitFor(int memberId, StationUserType userType) {
        return groupRepository.findGroupsForMember(memberId).stream()
                .filter(group -> !group.admits(userType))
                .toList();
    }

    /**
     * Takes a member out of every group that does not take the type they have just become.
     *
     * @param memberId the member
     * @param userType their new type
     * @return the groups they left
     */
    public List<MemberGroup> leaveGroupsUnfitFor(int memberId, StationUserType userType) {
        List<MemberGroup> unfit = groupsUnfitFor(memberId, userType);
        unfit.forEach(group -> groupRepository.removeMember(group.id(), memberId));
        if (!unfit.isEmpty()) {
            log.info("Member {} became {} and left groups {}", memberId, userType, ids(unfit));
        }
        return unfit;
    }

    /**
     * Refuses a change that leaves or joins a group granting permissions unless the caller proved
     * themselves recently, the same step-up a direct grant asks for.
     *
     * @param groups the groups the change joins or leaves
     * @param by     who is asking
     */
    void requirePresenceFor(Collection<MemberGroup> groups, UserSession by) {
        boolean grants = groups.stream()
                .anyMatch(group ->
                        !groupRepository.findGroupPermissions(group.id()).isEmpty());
        if (grants) stepUpGuard.require(by, StepUpCategory.ROLE_CHANGE);
    }

    /**
     * One member named in a refused change, with the groups the rule is about for them.
     *
     * @param memberId the member
     * @param groups   the names of those groups
     * @return the entry
     */
    GroupConflict conflict(int memberId, List<String> groups) {
        String name = nameResolver.get().identified(memberId);
        return new GroupConflict(memberId, name != null ? name : "#" + memberId, groups);
    }

    private void requireRightsFor(Collection<MemberGroup> groups, UserSession by, Refusal refusal) {
        Set<StationPermission> held = by.permissions();
        for (MemberGroup group : groups) {
            var missing = PermissionValidation.firstNotHeld(groupRepository.findGroupPermissions(group.id()), held);
            if (missing.isPresent()) {
                throw refusal.raise(group.name());
            }
        }
    }

    private static void requireOneGroupPerSet(Collection<MemberGroup> groups) {
        Map<Integer, List<String>> bySet = new HashMap<>();
        for (MemberGroup group : groups) {
            if (group.groupSetId() == null) continue;
            bySet.computeIfAbsent(group.groupSetId(), _ -> new ArrayList<>()).add(group.name());
        }
        for (List<String> names : bySet.values()) {
            if (names.size() > 1) {
                throw Refusal.GROUP_SET_TWO_CHOSEN.raise(String.join(", ", names));
            }
        }
    }

    /** The other group of the same set each of these members is in, where they are in one. */
    private Map<Integer, MemberGroup> siblingsOf(MemberGroup group, Collection<Integer> memberIds) {
        Integer groupSetId = group.groupSetId();
        if (groupSetId == null || memberIds.isEmpty()) return Map.of();
        Map<Integer, MemberGroup> siblings = new HashMap<>();
        groupRepository.findGroupInSet(groupSetId, memberIds).forEach((memberId, groupId) -> {
            if (groupId != group.id()) {
                groupRepository.findById(groupId).ifPresent(sibling -> siblings.put(memberId, sibling));
            }
        });
        return siblings;
    }

    private void announce(MemberGroup group, List<Integer> memberIds, Integer byMemberId) {
        eventBus.publish(new MembersAddedToGroup(group.stationId(), group.name(), memberIds, byMemberId));
    }

    private Set<Integer> groupIdsOf(int memberId) {
        return groupRepository.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .collect(Collectors.toSet());
    }

    private static Map<Integer, MemberGroup> byId(List<MemberGroup> groups) {
        return groups.stream().collect(Collectors.toMap(MemberGroup::id, Function.identity()));
    }

    private static List<MemberGroup> pick(
            Map<Integer, MemberGroup> groups, Collection<Integer> ids, IntPredicate keep) {
        return ids.stream()
                .filter(keep::test)
                .map(groups::get)
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<MemberGroup> concat(List<MemberGroup> first, List<MemberGroup> second) {
        var all = new ArrayList<>(first);
        all.addAll(second);
        return all;
    }

    private static List<Integer> ids(List<MemberGroup> groups) {
        return groups.stream().map(MemberGroup::id).toList();
    }
}
