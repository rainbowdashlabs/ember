/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.MembersAddedToGroup;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Singleton
public class MemberGroupService {
    private static final Logger log = LoggerFactory.getLogger(MemberGroupService.class);
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
        var group = groupRepository.create(stationId, name);
        log.info("Group created: id={}, station={}, name='{}'", group.id(), stationId, name);
        return group;
    }

    public Optional<MemberGroup> update(int id, String name, String color, int position) {
        if (groupRepository.update(id, name, color, position)) {
            log.info("Group updated: id={}, name='{}'", id, name);
            return groupRepository.findById(id);
        }
        log.warn("Group update affected no rows: id={}", id);
        return Optional.empty();
    }

    public boolean delete(int id) {
        log.info("Group deleted: id={}", id);
        return groupRepository.delete(id);
    }

    // -- Memberships --

    public List<StationMember> findMembers(int groupId) {
        return groupRepository.findMembers(groupId);
    }

    public List<MemberGroup> findGroupsForMember(int memberId) {
        return groupRepository.findGroupsForMember(memberId);
    }

    public List<StationMember> setMembers(int groupId, List<Integer> desiredMemberIds, Integer addedByMemberId) {
        List<StationMember> currentMembers = groupRepository.findMembers(groupId);
        var currentMemberIdSet =
                new HashSet<>(currentMembers.stream().map(StationMember::id).toList());

        var addedMemberIds = new ArrayList<Integer>();
        int removedCount = 0;
        for (int memberId : currentMemberIdSet) {
            if (!desiredMemberIds.contains(memberId)) {
                groupRepository.removeMember(groupId, memberId);
                removedCount++;
            }
        }
        for (int memberId : desiredMemberIds) {
            if (!currentMemberIdSet.contains(memberId)) {
                groupRepository.addMember(groupId, memberId);
                addedMemberIds.add(memberId);
            }
        }

        log.info(
                "Group membership updated: group={}, added={}, removed={}, by={}",
                groupId,
                addedMemberIds.size(),
                removedCount,
                addedByMemberId);

        if (!addedMemberIds.isEmpty()) {
            findById(groupId)
                    .ifPresent(g -> eventBus.publish(
                            new MembersAddedToGroup(g.stationId(), g.name(), addedMemberIds, addedByMemberId)));
        }

        return groupRepository.findMembers(groupId);
    }

    // -- Group Permissions --

    public List<Permission> findGroupPermissions(int groupId) {
        return groupRepository.findGroupPermissions(groupId);
    }

    public List<Permission> setGroupPermissions(
            int groupId, List<Integer> desiredPermissionIds, Set<StationPermission> callerPermissions) {
        List<Permission> allPermissions = memberRepository.findAllPermissions();
        List<Permission> currentPermissions = groupRepository.findGroupPermissions(groupId);
        var currentIds = currentPermissions.stream().map(Permission::id).toList();

        PermissionValidation.validatePermissionChanges(
                currentPermissions, desiredPermissionIds, allPermissions, callerPermissions);

        for (int permId : currentIds) {
            if (!desiredPermissionIds.contains(permId)) {
                groupRepository.removeGroupPermission(groupId, permId);
            }
        }
        for (int permId : desiredPermissionIds) {
            if (!currentIds.contains(permId)) {
                groupRepository.addGroupPermission(groupId, permId);
            }
        }

        log.info("Group permissions updated: group={}, permissions={}", groupId, desiredPermissionIds);
        return groupRepository.findGroupPermissions(groupId);
    }

    public void convertToTag(int groupId) {
        var group = groupRepository.findById(groupId).orElseThrow();
        var members = groupRepository.findMembers(groupId);
        var tag = tagRepository.create(group.stationId(), group.name());
        for (var member : members) {
            tagRepository.addMember(tag.id(), member.id());
        }
        groupRepository.delete(groupId);
        log.info("Group {} converted to tag {} in station {}", groupId, tag.id(), group.stationId());
    }
}
