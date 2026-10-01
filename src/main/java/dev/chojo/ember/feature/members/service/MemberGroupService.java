/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A station's groups: reading them, naming and ordering them, what they grant, and turning one into
 * a tag. Who is in a group is written through {@link GroupMembershipService}, and which types and set
 * a group takes through {@link GroupRulesService}.
 */
@Singleton
public class MemberGroupService {
    private static final Logger log = LoggerFactory.getLogger(MemberGroupService.class);
    private final MemberGroupRepository groupRepository;
    private final StationMemberRepository memberRepository;
    private final UserTagRepository tagRepository;

    @Inject
    public MemberGroupService(
            MemberGroupRepository groupRepository,
            StationMemberRepository memberRepository,
            UserTagRepository tagRepository) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.tagRepository = tagRepository;
    }

    public List<MemberGroup> findByStation(int stationId) {
        return groupRepository.findByStation(stationId);
    }

    public Optional<MemberGroup> findById(int id) {
        return groupRepository.findById(id);
    }

    /**
     * Removes a group, unless anything is still limited to it.
     *
     * <p>A limit that names a group goes with the group, and something limited to nothing else is
     * limited to nobody: it would open to the whole station the moment its last group was removed.
     * So the group stays until whoever manages that content has said who should see it instead.
     *
     * @param id the group
     * @return whether a group was removed
     * @throws dev.chojo.ember.api.refusal.RefusalResponse
     *         {@link MemberRefusal#GROUP_STILL_LIMITS_CONTENT_ON_DELETE} naming how many things are limited to it
     */
    public boolean delete(int id) {
        requireNothingLimitedTo(id, MemberRefusal.GROUP_STILL_LIMITS_CONTENT_ON_DELETE);
        log.info("Group deleted: id={}", id);
        return groupRepository.delete(id);
    }

    private void requireNothingLimitedTo(int groupId, MemberRefusal refusal) {
        int limited = groupRepository.countContentLimitedTo(groupId);
        if (limited > 0) throw refusal.raise(String.valueOf(limited));
    }

    public List<StationMember> findMembers(int groupId) {
        return groupRepository.findMembers(groupId);
    }

    public List<MemberGroup> findGroupsForMember(int memberId) {
        return groupRepository.findGroupsForMember(memberId);
    }

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

    /**
     * Turns a group into a tag with the same people in it, unless anything is still limited to the group.
     *
     * <p>The group goes, and with it every limit that names it, for the same reason as {@link #delete(int)}.
     *
     * @param groupId the group
     * @throws dev.chojo.ember.api.refusal.RefusalResponse
     *         {@link MemberRefusal#GROUP_STILL_LIMITS_CONTENT_ON_CONVERT} naming how many things are limited to it
     */
    public void convertToTag(int groupId) {
        requireNothingLimitedTo(groupId, MemberRefusal.GROUP_STILL_LIMITS_CONTENT_ON_CONVERT);
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
