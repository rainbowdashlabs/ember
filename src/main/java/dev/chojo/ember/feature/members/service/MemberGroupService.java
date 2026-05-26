/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MembersAddedToGroup;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.util.RoleValidation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for member group operations including CRUD, membership management,
 * group role assignments, and group-based queries.
 */
@Singleton
public class MemberGroupService {
    private final MemberGroupRepository groupRepository;
    private final StationMemberRepository memberRepository;
    private final UserTagRepository tagRepository;
    private final DomainEventBus eventBus;

    @Inject
    public MemberGroupService(
            MemberGroupRepository groupRepository,
            StationMemberRepository memberRepository,
            UserTagRepository tagRepository,
            DomainEventBus eventBus) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.tagRepository = tagRepository;
        this.eventBus = eventBus;
    }

    public List<MemberGroup> findByStation(int stationId) {
        return groupRepository.findByStation(stationId);
    }

    public Optional<MemberGroup> findById(int id) {
        return groupRepository.findById(id);
    }

    public MemberGroup create(int stationId, String name) {
        return groupRepository.create(stationId, name);
    }

    public Optional<MemberGroup> update(int id, String name) {
        if (groupRepository.update(id, name)) {
            return groupRepository.findById(id);
        }
        return Optional.empty();
    }

    public boolean delete(int id) {
        return groupRepository.delete(id);
    }

    // -- Memberships --

    public List<StationMember> findMembers(int groupId) {
        return groupRepository.findMembers(groupId);
    }

    public List<MemberGroup> findGroupsForMember(int memberId) {
        return groupRepository.findGroupsForMember(memberId);
    }

    public List<StationMember> setMembers(int groupId, List<Integer> desiredMemberIds) {
        List<StationMember> currentMembers = groupRepository.findMembers(groupId);
        var currentMemberIdSet =
                new HashSet<>(currentMembers.stream().map(StationMember::id).toList());

        var addedMemberIds = new ArrayList<Integer>();
        for (int memberId : currentMemberIdSet) {
            if (!desiredMemberIds.contains(memberId)) {
                groupRepository.removeMember(groupId, memberId);
            }
        }
        for (int memberId : desiredMemberIds) {
            if (!currentMemberIdSet.contains(memberId)) {
                groupRepository.addMember(groupId, memberId);
                addedMemberIds.add(memberId);
            }
        }

        if (!addedMemberIds.isEmpty()) {
            findById(groupId)
                    .ifPresent(g -> eventBus.publish(new MembersAddedToGroup(g.stationId(), g.name(), addedMemberIds)));
        }

        return groupRepository.findMembers(groupId);
    }

    // -- Group Roles --

    public List<Role> findGroupRoles(int groupId) {
        return groupRepository.findGroupRoles(groupId);
    }

    public List<Role> setGroupRoles(int groupId, List<Integer> desiredRoleIds, Set<Roles> callerRoles) {
        List<Role> allRoles = memberRepository.findAllRoles();
        List<Role> currentRoles = groupRepository.findGroupRoles(groupId);
        var currentRoleIds = currentRoles.stream().map(Role::id).toList();

        RoleValidation.validateRoleChanges(currentRoles, desiredRoleIds, allRoles, callerRoles, false);

        for (int roleId : currentRoleIds) {
            if (!desiredRoleIds.contains(roleId)) {
                groupRepository.removeGroupRole(groupId, roleId);
            }
        }
        for (int roleId : desiredRoleIds) {
            if (!currentRoleIds.contains(roleId)) {
                groupRepository.addGroupRole(groupId, roleId);
            }
        }

        return groupRepository.findGroupRoles(groupId);
    }

    public void convertToTag(int groupId) {
        var group = groupRepository.findById(groupId).orElseThrow();
        var members = groupRepository.findMembers(groupId);
        var tag = tagRepository.create(group.stationId(), group.name());
        for (var member : members) {
            tagRepository.addMember(tag.id(), member.id());
        }
        groupRepository.delete(groupId);
    }
}
