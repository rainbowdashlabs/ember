/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.RefusalDetail;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.util.PermissionValidation;
import dev.chojo.ember.util.SetDiff;
import dev.chojo.ember.util.sql.Transactions;
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
    private final GroupMembershipService memberships;

    @Inject
    public MemberGroupService(
            MemberGroupRepository groupRepository,
            StationMemberRepository memberRepository,
            UserTagRepository tagRepository,
            GroupMembershipService memberships) {
        this.groupRepository = groupRepository;
        this.memberRepository = memberRepository;
        this.tagRepository = tagRepository;
        this.memberships = memberships;
    }

    public List<MemberGroup> findByStation(int stationId) {
        return groupRepository.findByStation(stationId);
    }

    public Optional<MemberGroup> findById(int id) {
        return groupRepository.findById(id);
    }

    /**
     * Removes a group, unless anything is still limited to it or the caller may not take what it grants.
     *
     * <p>A limit that names a group goes with the group, and something limited to nothing else is
     * limited to nobody: it would open to the whole station the moment its last group was removed.
     * So the group stays until whoever manages that content has said who should see it instead.
     *
     * <p>Removing a group also takes what it grants from everybody in it, which is the same as taking
     * each of them out, so the same two rules hold: the caller holds everything it grants, and proved
     * themselves recently where it grants anything.
     *
     * @param group the group, already checked to belong to the caller's station
     * @param by    who is asking
     * @return whether a group was removed
     * @throws dev.chojo.ember.api.refusal.RefusalResponse
     *         {@link MemberRefusal#GROUP_STILL_LIMITS_CONTENT_ON_DELETE} naming how many things are limited to it,
     *         {@link MemberRefusal#GROUP_GRANTS_MORE_THAN_YOURS_ON_DELETE} for a group granting more than the caller holds
     */
    public boolean delete(MemberGroup group, UserSession by) {
        requireNothingLimitedTo(group.id(), MemberRefusal.GROUP_STILL_LIMITS_CONTENT_ON_DELETE);
        memberships.requireMayDissolve(group, by, MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_DELETE);
        log.info("Group deleted: id={}", group.id());
        return groupRepository.delete(group.id());
    }

    private void requireNothingLimitedTo(int groupId, MemberRefusal refusal) {
        int limited = groupRepository.countContentLimitedTo(groupId);
        if (limited > 0) throw refusal.raise(RefusalDetail.count(limited));
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

        PermissionValidation.validatePermissionChanges(
                currentPermissions, desiredPermissionIds, allPermissions, callerPermissions);

        var change = SetDiff.of(currentPermissions.stream().map(Permission::id).toList(), desiredPermissionIds);
        Transactions.run(() -> {
            change.removed().forEach(permId -> groupRepository.removeGroupPermission(groupId, permId));
            change.added().forEach(permId -> groupRepository.addGroupPermission(groupId, permId));
        });

        log.info("Group permissions updated: group={}, permissions={}", groupId, desiredPermissionIds);
        return groupRepository.findGroupPermissions(groupId);
    }

    /**
     * Turns a group into a tag with the same people in it, unless anything is still limited to the group
     * or the caller may not take what it grants.
     *
     * <p>The group goes, and with it every limit that names it and everything it grants, for the same
     * reasons as {@link #delete(MemberGroup, UserSession)}.
     *
     * @param group the group, already checked to belong to the caller's station
     * @param by    who is asking
     * @throws dev.chojo.ember.api.refusal.RefusalResponse
     *         {@link MemberRefusal#GROUP_STILL_LIMITS_CONTENT_ON_CONVERT} naming how many things are limited to it,
     *         {@link MemberRefusal#GROUP_GRANTS_MORE_THAN_YOURS_ON_CONVERT} for a group granting more than the caller holds
     */
    public void convertToTag(MemberGroup group, UserSession by) {
        requireNothingLimitedTo(group.id(), MemberRefusal.GROUP_STILL_LIMITS_CONTENT_ON_CONVERT);
        memberships.requireMayDissolve(group, by, MemberRefusal.GROUP_GRANTS_MORE_THAN_YOURS_ON_CONVERT);
        var tag = Transactions.call(() -> {
            var created = tagRepository.create(group.stationId(), group.name());
            for (var member : groupRepository.findMembers(group.id())) {
                tagRepository.addMember(created.id(), member.id());
            }
            groupRepository.delete(group.id());
            return created;
        });
        log.info("Group {} converted to tag {} in station {}", group.id(), tag.id(), group.stationId());
    }
}
