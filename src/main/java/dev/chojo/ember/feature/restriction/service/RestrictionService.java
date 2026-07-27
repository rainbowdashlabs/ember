/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.repository.RestrictionRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Restriction business logic: resolves the member identity a restriction is evaluated against and
 * applies the manager bypass, which is handled in Java rather than SQL. All persistence goes
 * through {@link RestrictionRepository}.
 */
@Singleton
public class RestrictionService {

    private final RestrictionRepository restrictionRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public RestrictionService(
            RestrictionRepository restrictionRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository) {
        this.restrictionRepository = restrictionRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
    }

    /**
     * Loads the restrictions of an entity combined with the mode the owning entity stores.
     */
    public RestrictionSet findRestrictionSet(RestrictionType type, int entityId, RestrictionMode mode) {
        return new RestrictionSet(restrictionRepository.findRestrictions(type, entityId), mode);
    }

    /**
     * Replaces all restrictions of an entity.
     */
    public void setRestrictions(RestrictionType type, int entityId, RestrictionSelection selection) {
        restrictionRepository.setRestrictions(type, entityId, selection);
    }

    /**
     * Returns the IDs of the station's members that pass the restrictions of an entity, including
     * the members holding the manager permission of the entity type, who bypass restrictions.
     * An unrestricted entity yields an empty set.
     */
    public Set<Integer> findMembersPassingRestriction(RestrictionType type, int entityId, int stationId) {
        var restrictions = restrictionRepository.findRestrictions(type, entityId);
        if (restrictions.isEmpty()) return Set.of();

        var groupIds = nonNullValues(restrictions, Restriction::groupId);
        var tagIds = nonNullValues(restrictions, Restriction::tagId);
        var userTypes = nonNullValues(restrictions, Restriction::userType);
        var memberIds = restrictions.stream()
                .map(Restriction::memberId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        for (int groupId : groupIds) {
            memberGroupRepository.findMembers(groupId).forEach(member -> memberIds.add(member.id()));
        }
        for (int tagId : tagIds) {
            userTagRepository.findMembers(tagId).forEach(member -> memberIds.add(member.id()));
        }
        if (!userTypes.isEmpty()) {
            stationMemberRepository.findByStation(stationId, false).stream()
                    .filter(member -> userTypes.contains(member.userType()))
                    .forEach(member -> memberIds.add(member.id()));
        }
        stationMemberRepository
                .findMembersWithPermission(stationId, type.managerPermission())
                .forEach(member -> memberIds.add(member.id()));

        return memberIds;
    }

    /**
     * Checks whether a member passes the restrictions of an entity. The manager permission is
     * checked first and bypasses restrictions entirely; everything else is resolved by the
     * database function.
     *
     * @param memberPermissions the permissions the member holds in the entity's station
     */
    public boolean checkRestriction(
            RestrictionType type, int entityId, int memberId, Set<StationPermission> memberPermissions) {
        if (memberPermissions.contains(type.managerPermission())) {
            return true;
        }

        var member = stationMemberRepository.findById(memberId).orElse(null);
        if (member == null) return false;

        List<Integer> groupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        List<Integer> tagIds = userTagRepository.findTagsForMember(memberId).stream()
                .map(UserTag::id)
                .toList();
        RestrictionMode mode = restrictionRepository.findMode(type, entityId);

        return restrictionRepository.matches(
                type, entityId, mode, new RestrictionMember(memberId, member.userType(), groupIds, tagIds));
    }

    /**
     * Collects the non-null results of applying an extractor to each restriction.
     *
     * @param extractor selects a nullable value from a restriction
     */
    private static <R> List<R> nonNullValues(List<Restriction> restrictions, Function<Restriction, R> extractor) {
        return restrictions.stream().map(extractor).filter(Objects::nonNull).toList();
    }
}
