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
     * what was granted to them by hand, what their groups carry, whether they look after anybody, and
     * everything those imply.
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

        if (looksAfterSomebody(member, permissions)) {
            permissions.add(StationPermission.MEMBER_GUARDIAN);
        }

        return StationPermission.expand(permissions);
    }

    /**
     * Whether acting for somebody else is part of what this member does, which the four other sources
     * cannot answer between them.
     *
     * <p>Speaking for a member follows from being put in charge of one, and not from being of the type
     * that usually is. A team member or a station manager who is handed a child keeps their own type,
     * so nothing they hold ever grows the right to answer for that child, and the picker of grants does
     * not offer it either: the grant belongs to nobody to hand out. Reading it off the relation is what
     * closes that, and it closes it in the one place every caller already asks through.
     *
     * <p>It follows that the right ends when the last person in their care does, without anybody taking
     * it away, and that a member of the guardian type keeps it either way, because their type carries it
     * whether or not somebody is currently assigned to them.
     */
    private boolean looksAfterSomebody(StationMember member, Set<StationPermission> alreadyHeld) {
        return !alreadyHeld.contains(StationPermission.MEMBER_GUARDIAN)
                && stationMemberRepository.managesAnybody(member.id());
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
