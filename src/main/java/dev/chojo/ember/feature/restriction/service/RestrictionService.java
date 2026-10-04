/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.restriction.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.knowledgebase.service.KbAccessService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.members.service.MemberPermissionResolver;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMember;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.repository.RestrictionRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Restriction business logic: resolves the member identity a restriction is evaluated against and
 * applies the manager bypass, which is handled in Java rather than SQL. All persistence goes
 * through {@link RestrictionRepository}.
 */
@Singleton
public class RestrictionService {
    private static final Logger log = LoggerFactory.getLogger(RestrictionService.class);

    private final RestrictionRepository restrictionRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final KbAccessService kbAccessService;
    private final MemberPermissionResolver permissionResolver;

    @Inject
    public RestrictionService(
            RestrictionRepository restrictionRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            KbAccessService kbAccessService,
            MemberPermissionResolver permissionResolver) {
        this.restrictionRepository = restrictionRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.kbAccessService = kbAccessService;
        this.permissionResolver = permissionResolver;
    }

    /**
     * Whether a member manages the given entity type and so passes every restriction on it, decided
     * from the permissions they hold through every source.
     *
     * <p>For members who are not the one making the request, such as the people a guardian speaks
     * for. The requesting member's answer is already in their session.
     *
     * @param type     the restricted entity type
     * @param memberId the member
     * @return {@code true} where the member holds the type's manager permission
     */
    public boolean manages(RestrictionType type, int memberId) {
        return permissionResolver.resolve(memberId).contains(type.managerPermission());
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
        log.info(
                "Restrictions of {} {} replaced: {} user type(s), {} group(s), {} tag(s), {} member(s), mode {}",
                type,
                entityId,
                selection.userTypes().size(),
                selection.groupIds().size(),
                selection.tagIds().size(),
                selection.memberIds().size(),
                selection.mode());
    }

    /**
     * Whether an entity carries any restriction at all.
     */
    public boolean hasRestrictions(RestrictionType type, int entityId) {
        return restrictionRepository.hasRestrictions(type, entityId);
    }

    /**
     * The current members of a station who may open an entity: those its restrictions take in, plus
     * the members holding the manager permission of the entity type, who bypass restrictions.
     *
     * <p>Each member is decided by the rule the access check itself applies, so nobody is told about
     * an entity they cannot open. Knowledge base items follow the knowledge base's own walk down the
     * folder tree; everything else goes through the {@code check_restriction} database function.
     *
     * @return the members, or empty where the entity carries no restriction and every member passes
     */
    public Optional<Set<Integer>> findMembersPassingRestriction(RestrictionType type, int entityId, int stationId) {
        var managerIds = stationMemberRepository.findMembersWithPermission(stationId, type.managerPermission()).stream()
                .map(StationMember::id)
                .collect(Collectors.toSet());

        return switch (type) {
            case KB_FOLDER -> Optional.of(kbAccessService.readers(kbAccessOf(stationId, managerIds), entityId, null));
            case KB_FILE -> Optional.of(kbAccessService.readers(kbAccessOf(stationId, managerIds), null, entityId));
            default -> {
                if (!restrictionRepository.hasRestrictions(type, entityId)) yield Optional.empty();
                var memberIds = new HashSet<>(restrictionRepository.findMatchingMembers(
                        type, entityId, restrictionRepository.findMode(type, entityId), stationId));
                memberIds.addAll(managerIds);
                yield Optional.of(memberIds);
            }
        };
    }

    /**
     * The knowledge base access context of every current member of a station. Only the manage right
     * is carried, because the station-wide edit right changes what a member may do with an item,
     * never whether they may read it.
     */
    private List<KbAccessService.MemberAccess> kbAccessOf(int stationId, Set<Integer> managerIds) {
        return restrictionRepository.findStationMembers(stationId).stream()
                .map(member -> new KbAccessService.MemberAccess(
                        member.memberId(),
                        member.userType(),
                        member.groupIds(),
                        member.tagIds(),
                        false,
                        managerIds.contains(member.memberId())))
                .toList();
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
        return includes(type, entityId, memberId);
    }

    /**
     * Whether an entity's restrictions take in a member, with no manager bypass.
     *
     * <p>This is whom an entity is meant for rather than who may look at it. A manager may open
     * every restricted form, which does not make them one of the people it asks to answer, so
     * anything that asks somebody to act reads this and not {@link #checkRestriction}.
     *
     * @param type     the restricted entity type
     * @param entityId the entity
     * @param memberId the member
     * @return {@code true} where the entity has no restrictions or the member passes them
     */
    public boolean includes(RestrictionType type, int entityId, int memberId) {
        var member = memberOf(memberId).orElse(null);
        if (member == null) return false;
        RestrictionMode mode = restrictionRepository.findMode(type, entityId);
        return restrictionRepository.matches(type, entityId, mode, member);
    }

    /**
     * The current members of a station an audience takes in, with no manager bypass.
     *
     * @param stationId the station
     * @param audience  the audience; one naming nobody takes in every current member
     * @return the members' ids
     */
    public List<Integer> membersIn(int stationId, RestrictionAudience audience) {
        return restrictionRepository.findStationMembers(stationId).stream()
                .filter(audience::includes)
                .map(RestrictionMember::memberId)
                .toList();
    }

    /**
     * What a restriction can name of several members, as {@link #memberOf(int)} reads it for one, in one
     * read.
     *
     * @param memberIds the members
     * @return the members as restrictions see them, by member; one that does not exist is absent
     */
    public Map<Integer, RestrictionMember> membersOf(Collection<Integer> memberIds) {
        var members = new HashMap<Integer, RestrictionMember>();
        restrictionRepository.findMembers(memberIds).forEach(member -> members.put(member.memberId(), member));
        return members;
    }

    /**
     * What a restriction can name of a member: their user type, groups and tags.
     *
     * @param memberId the member
     * @return the member as restrictions see them, or empty where there is no such member
     */
    public Optional<RestrictionMember> memberOf(int memberId) {
        return stationMemberRepository
                .findById(memberId)
                .map(member -> new RestrictionMember(
                        memberId,
                        member.userType(),
                        memberGroupRepository.findGroupsForMember(memberId).stream()
                                .map(MemberGroup::id)
                                .toList(),
                        userTagRepository.findTagsForMember(memberId).stream()
                                .map(UserTag::id)
                                .toList()));
    }
}
