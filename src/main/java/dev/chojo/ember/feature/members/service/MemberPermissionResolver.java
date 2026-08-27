/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.Permission;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Everything a member of a station may do, from all four places it can come from.
 *
 * <p>Held apart from the access manager that guards the API, because the question is also asked away
 * from a request: whether the person who changed something is themselves one of the people such a
 * change would be put in front of cannot be answered from the request, and answering it a second way
 * would be answering it differently.
 */
@Singleton
public class MemberPermissionResolver {
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;

    @Inject
    public MemberPermissionResolver(
            StationMemberRepository stationMemberRepository, MemberGroupRepository memberGroupRepository) {
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
    }

    /**
     * What a member may do: what their type grants everywhere, what their station grants that type,
     * what was granted to them by hand, what their groups carry, and everything those imply.
     *
     * @param member the member
     * @return the permissions they hold
     */
    public Set<StationPermission> resolve(StationMember member) {
        Set<StationPermission> permissions = EnumSet.noneOf(StationPermission.class);

        permissions.addAll(Arrays.asList(member.userType().defaultPermissions()));

        stationMemberRepository.findUserTypePermissions(member.stationId(), member.userType()).stream()
                .map(Permission::permission)
                .forEach(permissions::add);

        stationMemberRepository.findPermissions(member.id()).stream()
                .map(Permission::permission)
                .forEach(permissions::add);

        memberGroupRepository.findPermissionsForMemberViaGroups(member.id()).stream()
                .map(Permission::permission)
                .forEach(permissions::add);

        return StationPermission.expand(permissions);
    }

    /**
     * What a member may do, looked up by their row.
     *
     * @param memberId the member
     * @return the permissions they hold, none where there is no such member
     */
    public Set<StationPermission> resolve(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(this::resolve)
                .orElse(EnumSet.noneOf(StationPermission.class));
    }
}
