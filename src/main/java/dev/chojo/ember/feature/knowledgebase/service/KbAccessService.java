/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessRestriction;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

/**
 * Who may see a knowledge-base folder or file, along both axes the knowledge base has: the
 * member-facing access restrictions inside a station, and the visibility of an item on the
 * station's public knowledge base.
 *
 * <p>Both axes inherit downwards. A restricted or hidden folder hides everything below it, no
 * matter what the child itself says.
 */
@Singleton
public class KbAccessService {
    private final KnowledgeBaseRepository repository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public KbAccessService(
            KnowledgeBaseRepository repository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository) {
        this.repository = repository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
    }

    private static RestrictionSet toRestrictionSet(List<KbAccessRestriction> kbRestrictions, RestrictionMode mode) {
        var restrictions = kbRestrictions.stream()
                .map(r -> new Restriction(r.id(), r.userType(), r.groupId(), r.tagId(), r.memberId()))
                .toList();
        return new RestrictionSet(restrictions, mode);
    }

    /**
     * Lists the access restrictions set directly on a folder or file.
     *
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return the restrictions
     */
    public List<KbAccessRestriction> findRestrictions(Integer folderId, Integer fileId) {
        return repository.findRestrictions(folderId, fileId);
    }

    /**
     * Replaces the access restrictions of a folder or file with a new selection.
     *
     * @param folderId  the folder, or {@code null} when setting them on a file
     * @param fileId    the file, or {@code null} when setting them on a folder
     * @param selection the user types, groups, tags and members that may see the item
     */
    public void setRestrictions(Integer folderId, Integer fileId, RestrictionSelection selection) {
        repository.clearRestrictions(folderId, fileId);
        for (StationUserType userType : selection.userTypes()) {
            repository.addRestriction(folderId, fileId, userType, null, null, null);
        }
        for (Integer groupId : selection.groupIds()) {
            repository.addRestriction(folderId, fileId, null, groupId, null, null);
        }
        for (Integer tagId : selection.tagIds()) {
            repository.addRestriction(folderId, fileId, null, null, tagId, null);
        }
        for (Integer memberId : selection.memberIds()) {
            repository.addRestriction(folderId, fileId, null, null, null, memberId);
        }
    }

    /**
     * Reads the group and user-tag memberships an access check needs, so a whole listing can be
     * filtered against {@link #canAccess(MemberAccess, Integer, Integer)} without re-reading them
     * per row.
     *
     * @param memberId the member whose access is evaluated
     * @param userType the member's station user type
     * @return the member's access context
     */
    public MemberAccess memberAccess(int memberId, StationUserType userType) {
        var groupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        var tagIds = userTagRepository.findTagsForMember(memberId).stream()
                .map(UserTag::id)
                .toList();
        return new MemberAccess(memberId, userType, groupIds, tagIds);
    }

    /**
     * Evaluates the access restrictions on a folder or file against a pre-read access context.
     *
     * @param access   the member's memberships
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return {@code true} when the member may see the item
     */
    public boolean canAccess(MemberAccess access, Integer folderId, Integer fileId) {
        return canAccess(access.memberId(), folderId, fileId, access.userType(), access.groupIds(), access.tagIds());
    }

    /**
     * Evaluates the access restrictions on a folder or file, including the ones inherited from
     * every folder above it.
     *
     * @param memberId       the member whose access is evaluated
     * @param folderId       the folder, or {@code null} when asking about a file
     * @param fileId         the file, or {@code null} when asking about a folder
     * @param memberUserType the member's station user type
     * @param memberGroupIds the groups the member belongs to
     * @param memberTagIds   the user tags the member carries
     * @return {@code true} when the member may see the item
     */
    public boolean canAccess(
            int memberId,
            Integer folderId,
            Integer fileId,
            StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        var rawRestrictions = repository.findRestrictions(folderId, fileId);
        if (!rawRestrictions.isEmpty()) {
            var restrictions = toRestrictionSet(rawRestrictions, restrictionMode(folderId, fileId));
            if (!restrictions.matches(memberUserType, memberGroupIds, memberTagIds, memberId)) return false;
        }

        if (fileId != null) {
            var file = repository.findFileById(fileId);
            if (file.isPresent() && file.get().folderId() != null) {
                return canAccessFolder(memberId, file.get().folderId(), memberUserType, memberGroupIds, memberTagIds);
            }
        }

        if (folderId != null) {
            return canAccessFolder(memberId, folderId, memberUserType, memberGroupIds, memberTagIds);
        }

        return true;
    }

    /**
     * Decides whether a folder or file is visible on the station's public knowledge base. Items
     * carrying access restrictions are never public; the rest follow the station mode unless an
     * explicit override says otherwise. Folder visibility is inherited by everything below it.
     *
     * @param mode     the station's public knowledge-base mode
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return {@code true} when the item is public
     */
    public boolean isPubliclyVisible(PublicKbMode mode, Integer folderId, Integer fileId) {
        if (mode == PublicKbMode.OFF) return false;
        if (repository.hasRestrictions(folderId, fileId)) return false;

        if (fileId != null) {
            var file = repository.findFileById(fileId).orElse(null);
            if (file != null && file.folderId() != null && !isPubliclyVisible(mode, file.folderId(), null)) {
                return false;
            }
        }

        if (folderId != null) {
            var folder = repository.findFolderById(folderId).orElse(null);
            if (folder != null && folder.parentId() != null && !isPubliclyVisible(mode, folder.parentId(), null)) {
                return false;
            }
        }

        return repository.findPublicVisibility(folderId, fileId).orElseGet(() -> mode == PublicKbMode.ALLOW_ALL);
    }

    /**
     * Opts a folder or file in or out of the station's public knowledge base.
     *
     * @param folderId the folder, or {@code null} when setting it on a file
     * @param fileId   the file, or {@code null} when setting it on a folder
     * @param visible  whether the item is public
     */
    public void setPublicVisibility(Integer folderId, Integer fileId, boolean visible) {
        repository.setPublicVisibility(folderId, fileId, visible);
    }

    /**
     * Drops the explicit public-visibility override of a folder or file, so it follows the station
     * mode and its parent folder again.
     *
     * @param folderId the folder, or {@code null} when removing it from a file
     * @param fileId   the file, or {@code null} when removing it from a folder
     */
    public void removePublicVisibility(Integer folderId, Integer fileId) {
        repository.removePublicVisibility(folderId, fileId);
    }

    /**
     * Reads the explicit public-visibility override of a folder or file.
     *
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return the override, or empty when the item has none
     */
    public Optional<Boolean> findPublicVisibility(Integer folderId, Integer fileId) {
        return repository.findPublicVisibility(folderId, fileId);
    }

    private RestrictionMode restrictionMode(Integer folderId, Integer fileId) {
        if (fileId != null) {
            var file = repository.findFileById(fileId);
            if (file.isPresent() && file.get().restrictionMode() != null)
                return file.get().restrictionMode();
        } else if (folderId != null) {
            var folder = repository.findFolderById(folderId);
            if (folder.isPresent() && folder.get().restrictionMode() != null)
                return folder.get().restrictionMode();
        }
        return RestrictionMode.AND;
    }

    private boolean canAccessFolder(
            int memberId,
            int folderId,
            StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        var folder = repository.findFolderById(folderId);
        if (folder.isEmpty()) return true;

        var rawRestrictions = repository.findRestrictions(folderId, null);
        if (!rawRestrictions.isEmpty()) {
            RestrictionMode mode =
                    folder.get().restrictionMode() != null ? folder.get().restrictionMode() : RestrictionMode.AND;
            var restrictions = toRestrictionSet(rawRestrictions, mode);
            if (!restrictions.matches(memberUserType, memberGroupIds, memberTagIds, memberId)) return false;
        }

        if (folder.get().parentId() != null) {
            return canAccessFolder(memberId, folder.get().parentId(), memberUserType, memberGroupIds, memberTagIds);
        }

        return true;
    }

    /**
     * The memberships an access restriction is evaluated against, read once per request.
     */
    public record MemberAccess(int memberId, StationUserType userType, List<Integer> groupIds, List<Integer> tagIds) {}
}
