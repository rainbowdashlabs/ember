/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessGrant;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessLevel;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository;
import dev.chojo.ember.feature.knowledgebase.repository.KnowledgeBaseRepository.FolderPathNode;
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
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
    private static final Logger log = LoggerFactory.getLogger(KbAccessService.class);

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

    private static RestrictionSet toRestrictionSet(List<KbAccessGrant> kbRestrictions, RestrictionMode mode) {
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
    public List<KbAccessGrant> findRestrictions(@Nullable Integer folderId, @Nullable Integer fileId) {
        return repository.findRestrictions(folderId, fileId);
    }

    /**
     * Replaces the access restrictions of a folder or file with a new selection.
     *
     * @param folderId  the folder, or {@code null} when setting them on a file
     * @param fileId    the file, or {@code null} when setting them on a folder
     * @param selection the user types, groups, tags and members that may see the item
     */
    public void setRestrictions(@Nullable Integer folderId, @Nullable Integer fileId, RestrictionSelection selection) {
        var grants = new ArrayList<GrantEntry>();
        for (StationUserType userType : selection.userTypes()) {
            grants.add(new GrantEntry(userType, null, null, null, null));
        }
        for (Integer groupId : selection.groupIds()) {
            grants.add(new GrantEntry(null, groupId, null, null, null));
        }
        for (Integer tagId : selection.tagIds()) {
            grants.add(new GrantEntry(null, null, tagId, null, null));
        }
        for (Integer memberId : selection.memberIds()) {
            grants.add(new GrantEntry(null, null, null, memberId, null));
        }
        setGrants(folderId, fileId, grants);
    }

    /**
     * Replaces the grants of a folder or file, each naming an audience and, when it says so, what
     * that audience may do.
     *
     * @param folderId the folder, or {@code null} when setting them on a file
     * @param fileId   the file, or {@code null} when setting them on a folder
     * @param grants   the audiences and their levels
     */
    public void setGrants(@Nullable Integer folderId, @Nullable Integer fileId, List<GrantEntry> grants) {
        repository.clearRestrictions(folderId, fileId);
        for (var grant : grants) {
            repository.addRestriction(
                    folderId,
                    fileId,
                    grant.userType(),
                    grant.groupId(),
                    grant.tagId(),
                    grant.memberId(),
                    grant.level());
        }
        log.info("Knowledge {} now carries {} grant(s)", subject(folderId, fileId), grants.size());
    }

    private String subject(@Nullable Integer folderId, @Nullable Integer fileId) {
        return folderId != null ? "folder " + folderId : "file " + fileId;
    }

    /**
     * One audience and the level it holds, as the grants editor submits it. Exactly one subject is
     * set; a null level leaves the level to the station permission the member holds.
     */
    public record GrantEntry(
            @Nullable StationUserType userType,
            @Nullable Integer groupId,
            @Nullable Integer tagId,
            @Nullable Integer memberId,
            @Nullable KbAccessLevel level) {}

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
        return memberAccess(memberId, userType, false, false);
    }

    /**
     * Reads a member's memberships together with the station-wide knowledge rights they hold.
     *
     * @param canEdit   whether the member holds the station-wide edit right
     * @param canManage whether the member holds the station-wide manage right
     */
    public MemberAccess memberAccess(int memberId, StationUserType userType, boolean canEdit, boolean canManage) {
        var groupIds = memberGroupRepository.findGroupsForMember(memberId).stream()
                .map(MemberGroup::id)
                .toList();
        var tagIds = userTagRepository.findTagsForMember(memberId).stream()
                .map(UserTag::id)
                .toList();
        return new MemberAccess(memberId, userType, groupIds, tagIds, canEdit, canManage);
    }

    /**
     * Whether a member may see a folder or file at all.
     *
     * <p>Answered from the resolved level rather than from audience matching alone, so an item a
     * member is explicitly denied stays out of every listing instead of appearing and then
     * refusing to open.
     *
     * @param access   the member's memberships and station rights
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return {@code true} when the member may see the item
     */
    public boolean canAccess(MemberAccess access, @Nullable Integer folderId, @Nullable Integer fileId) {
        return effectiveLevel(access, folderId, fileId).covers(KbAccessLevel.READ);
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
            @Nullable Integer folderId,
            @Nullable Integer fileId,
            StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        var rawRestrictions = repository.findRestrictions(folderId, fileId);
        if (!rawRestrictions.isEmpty()) {
            var restrictions = toRestrictionSet(rawRestrictions, restrictionMode(folderId, fileId));
            if (!restrictions.matches(memberUserType, memberGroupIds, memberTagIds, memberId)) return false;
        }

        if (fileId != null) {
            Integer fileFolderId =
                    repository.findAnyFileById(fileId).map(KbFile::folderId).orElse(null);
            if (fileFolderId != null) {
                return canAccessFolder(memberId, fileFolderId, memberUserType, memberGroupIds, memberTagIds);
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
    public boolean isPubliclyVisible(PublicKbMode mode, @Nullable Integer folderId, @Nullable Integer fileId) {
        if (mode == PublicKbMode.OFF) return false;
        if (repository.hasRestrictions(folderId, fileId)) return false;

        if (fileId != null) {
            var file = repository.findAnyFileById(fileId).orElse(null);
            Integer fileFolderId = file == null ? null : file.folderId();
            if (fileFolderId != null && !isPubliclyVisible(mode, fileFolderId, null)) {
                return false;
            }
        }

        if (folderId != null) {
            var folder = repository.findAnyFolderById(folderId).orElse(null);
            Integer parentId = folder == null ? null : folder.parentId();
            if (parentId != null && !isPubliclyVisible(mode, parentId, null)) {
                return false;
            }
        }

        return repository.findPublicVisibility(folderId, fileId).orElseGet(() -> mode == PublicKbMode.ALLOW_ALL);
    }

    /**
     * Whether a folder or file would stand on the public knowledge base once it sat in a target
     * folder, rather than where it sits now.
     *
     * <p>This is the question the move dialog asks. It matters in one direction in particular: a
     * station that publishes everything by default turns an entry nobody ever published into a
     * public page the moment it lands in a public folder, and the reader deserves to be told that
     * before it happens rather than after.
     *
     * @param mode           the station's public knowledge-base mode
     * @param targetFolderId the folder it would sit in, or {@code null} for the tree root
     * @param folderId       the folder being moved, or {@code null} when moving a file
     * @param fileId         the file being moved, or {@code null} when moving a folder
     * @return {@code true} when the item would be public there
     */
    public boolean isPubliclyVisibleUnder(
            PublicKbMode mode, @Nullable Integer targetFolderId, @Nullable Integer folderId, @Nullable Integer fileId) {
        if (mode == PublicKbMode.OFF) return false;
        if (repository.hasRestrictions(folderId, fileId)) return false;
        if (targetFolderId != null && !isPubliclyVisible(mode, targetFolderId, null)) return false;
        return repository.findPublicVisibility(folderId, fileId).orElseGet(() -> mode == PublicKbMode.ALLOW_ALL);
    }

    /**
     * Opts a folder or file in or out of the station's public knowledge base.
     *
     * @param folderId the folder, or {@code null} when setting it on a file
     * @param fileId   the file, or {@code null} when setting it on a folder
     * @param visible  whether the item is public
     */
    public void setPublicVisibility(@Nullable Integer folderId, @Nullable Integer fileId, boolean visible) {
        repository.setPublicVisibility(folderId, fileId, visible);
        log.info("Knowledge {} is now {} to the public", subject(folderId, fileId), visible ? "open" : "closed");
    }

    /**
     * Drops the explicit public-visibility override of a folder or file, so it follows the station
     * mode and its parent folder again.
     *
     * @param folderId the folder, or {@code null} when removing it from a file
     * @param fileId   the file, or {@code null} when removing it from a folder
     */
    public void removePublicVisibility(@Nullable Integer folderId, @Nullable Integer fileId) {
        repository.removePublicVisibility(folderId, fileId);
        log.info("Knowledge {} follows its folder and station again", subject(folderId, fileId));
    }

    /**
     * Reads the explicit public-visibility override of a folder or file.
     *
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return the override, or empty when the item has none
     */
    public Optional<Boolean> findPublicVisibility(@Nullable Integer folderId, @Nullable Integer fileId) {
        return repository.findPublicVisibility(folderId, fileId);
    }

    /**
     * Resolves a member's level together with the folder that decided it, so a reader can be told
     * why an action is missing rather than left to guess.
     *
     * <p>Silent absence is the standing support cost of permission systems: a button that is simply
     * not there reads as a bug. The source is the name of the folder whose grant set the level, or
     * empty when nothing in the tree said anything and the station permission decided.
     *
     * @param access   the member's memberships and station rights
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return the level and where it came from
     */
    public LevelExplanation explainLevel(MemberAccess access, @Nullable Integer folderId, @Nullable Integer fileId) {
        var level = effectiveLevel(access, folderId, fileId);
        if (access.canManage()) return new LevelExplanation(level, null);
        return new LevelExplanation(level, levelSource(access, folderId, fileId));
    }

    /**
     * The name of the deepest folder along the path whose grants name the member with a level. The
     * file's own grants are not a "source" worth naming - the reader is already looking at it.
     */
    private @Nullable String levelSource(MemberAccess access, @Nullable Integer folderId, @Nullable Integer fileId) {
        Integer startFolder = folderId;
        if (fileId != null) {
            var file = repository.findAnyFileById(fileId);
            if (file.isEmpty()) return null;
            startFolder = file.get().folderId();
        }
        if (startFolder == null) return null;

        var path = repository.findFolderPath(startFolder);
        var grants = repository.findRestrictionsForPath(
                path.stream().map(FolderPathNode::id).toList(), null);

        String source = null;
        for (var node : path) {
            boolean decides = grants.stream()
                    .filter(grant -> grant.isOnFolder(node.id()))
                    .filter(grant -> grant.level() != null)
                    .anyMatch(grant ->
                            grant.matches(access.memberId(), access.userType(), access.groupIds(), access.tagIds()));
            if (decides) {
                source = repository
                        .findFolderById(node.id())
                        .map(folder -> folder.name())
                        .orElse(null);
            }
        }
        return source;
    }

    /**
     * A member's level and the folder whose grant set it, or a null source when the station
     * permission decided.
     */
    public record LevelExplanation(
            KbAccessLevel level, @Nullable String source) {}

    /**
     * Resolves what a member may do with a folder or file.
     *
     * <p>One walk from the root down answers both questions the knowledge base has. Every node on
     * the way that carries grants gates: a member who matches none of its rows is out, which is the
     * rule the knowledge base has always had. Past the gate, the deepest row that names a level
     * decides it, so a folder can be read-only for a group while one file inside it is theirs to
     * write. A row without a level says nothing about it and leaves the station permission in
     * charge, which is why a station that never sets one sees no change at all.
     *
     * @param access   the member's memberships and station rights
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return the level the member holds on that item
     */
    public KbAccessLevel effectiveLevel(MemberAccess access, @Nullable Integer folderId, @Nullable Integer fileId) {
        if (access.canManage()) return KbAccessLevel.MANAGE;
        return levelWithin(access, gatesOf(folderId, fileId));
    }

    /**
     * The members of a batch who may read a folder or file, by the same walk as
     * {@link #effectiveLevel}, with the tree and its grants read once for all of them.
     *
     * @param members  the members to test, each with their memberships and station rights
     * @param folderId the folder, or {@code null} when asking about a file
     * @param fileId   the file, or {@code null} when asking about a folder
     * @return the IDs of the members who may read the item
     */
    public Set<Integer> readers(List<MemberAccess> members, @Nullable Integer folderId, @Nullable Integer fileId) {
        var gates = gatesOf(folderId, fileId);
        return members.stream()
                .filter(access ->
                        access.canManage() || levelWithin(access, gates).covers(KbAccessLevel.READ))
                .map(MemberAccess::memberId)
                .collect(Collectors.toSet());
    }

    /**
     * Reads everything that gates a folder or file: the folders above it, root first, their grants
     * and, for a file, its own grants and mode. A file that no longer exists is gated by nothing.
     */
    private Gates gatesOf(@Nullable Integer folderId, @Nullable Integer fileId) {
        Integer startFolder = folderId;
        RestrictionMode fileMode = RestrictionMode.AND;
        if (fileId != null) {
            var file = repository.findAnyFileById(fileId);
            if (file.isEmpty()) return Gates.NOTHING;
            startFolder = file.get().folderId();
            fileMode = file.get().restrictionMode();
        }

        var path = startFolder != null ? repository.findFolderPath(startFolder) : List.<FolderPathNode>of();
        var grants = repository.findRestrictionsForPath(
                path.stream().map(FolderPathNode::id).toList(), fileId);
        return new Gates(path, grants, fileId, fileMode);
    }

    /**
     * Walks the gates of one item for one member, root first and the file last.
     */
    private KbAccessLevel levelWithin(MemberAccess access, Gates gates) {
        var granted = applyPath(access, gates.path(), gates.grants());
        if (granted == KbAccessLevel.NONE) return KbAccessLevel.NONE;

        if (gates.fileId() != null) {
            var rows = gates.grants().stream()
                    .filter(grant -> grant.fileId() != null)
                    .toList();
            granted = applyNode(access, rows, gates.fileMode(), granted);
            if (granted == KbAccessLevel.NONE) return KbAccessLevel.NONE;
        }

        return granted != null ? granted : stationDefault(access);
    }

    /**
     * What stands between a member and one folder or file.
     *
     * @param path     the folders above the item, root first, including the item when it is a folder
     * @param grants   the grants of those folders and of the file
     * @param fileId   the file, or {@code null} when the item is a folder
     * @param fileMode how the file's own grants combine
     */
    private record Gates(
            List<FolderPathNode> path,
            List<KbAccessGrant> grants,
            @Nullable Integer fileId,
            RestrictionMode fileMode) {
        private static final Gates NOTHING = new Gates(List.of(), List.of(), null, RestrictionMode.AND);
    }

    /**
     * Resolves the level of every child of one folder in two queries rather than two per child.
     *
     * <p>Everything in a folder shares its ancestry, so the walk down to the folder runs once and
     * each child only has its own grants applied on top.
     *
     * @param access         the member's memberships and station rights
     * @param parentFolderId the folder being listed, or {@code null} for the root
     * @param folders        the child folders, with the mode their grants combine in
     * @param files          the child files, with the mode their grants combine in
     * @return the level per folder id and per file id, in two maps
     */
    public ChildLevels childLevels(
            MemberAccess access, @Nullable Integer parentFolderId, List<ChildNode> folders, List<ChildNode> files) {
        if (access.canManage()) {
            return new ChildLevels(constant(folders, KbAccessLevel.MANAGE), constant(files, KbAccessLevel.MANAGE));
        }

        KbAccessLevel carried = carriedLevel(access, parentFolderId);
        if (carried == KbAccessLevel.NONE) {
            return new ChildLevels(constant(folders, KbAccessLevel.NONE), constant(files, KbAccessLevel.NONE));
        }

        var grants = repository.findRestrictionsForNodes(
                folders.stream().map(ChildNode::id).toList(),
                files.stream().map(ChildNode::id).toList());

        var folderLevels = new HashMap<Integer, KbAccessLevel>();
        for (var child : folders) {
            var rows = grants.stream()
                    .filter(grant -> grant.isOnFolder(child.id()))
                    .toList();
            folderLevels.put(child.id(), resolveChild(access, rows, child.mode(), carried));
        }
        var fileLevels = new HashMap<Integer, KbAccessLevel>();
        for (var child : files) {
            var rows =
                    grants.stream().filter(grant -> grant.isOnFile(child.id())).toList();
            fileLevels.put(child.id(), resolveChild(access, rows, child.mode(), carried));
        }
        return new ChildLevels(folderLevels, fileLevels);
    }

    /**
     * Resolves the level of files spread anywhere across the tree in two queries rather than one
     * walk per file.
     *
     * <p>A search hit is reached without walking the folders above it, so nothing about the caller's
     * standing there has been resolved by the time the hit is in hand. Answering that per hit turns
     * one search into a round trip per result, which is why the ancestries and the grants on them
     * are read for the whole batch at once and applied in memory.
     *
     * @param access the member's memberships and station rights
     * @param files  the files to resolve, each with the folder it sits in
     * @return the level per file id
     */
    public Map<Integer, KbAccessLevel> fileLevels(MemberAccess access, List<FileNode> files) {
        if (files.isEmpty()) return Map.of();
        if (access.canManage()) {
            var levels = new HashMap<Integer, KbAccessLevel>();
            for (var file : files) levels.put(file.id(), KbAccessLevel.MANAGE);
            return levels;
        }

        var folderIds = files.stream()
                .map(FileNode::folderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        var paths = repository.findFolderPaths(folderIds);
        var pathFolderIds = paths.values().stream()
                .flatMap(List::stream)
                .map(FolderPathNode::id)
                .distinct()
                .toList();
        var grants = repository.findRestrictionsForNodes(
                pathFolderIds, files.stream().map(FileNode::id).toList());

        var carried = new HashMap<Integer, KbAccessLevel>();
        for (var folderId : folderIds) {
            carried.put(folderId, applyPath(access, paths.getOrDefault(folderId, List.of()), grants));
        }

        var levels = new HashMap<Integer, KbAccessLevel>();
        for (var file : files) {
            Integer fileFolderId = file.folderId();
            var inherited = fileFolderId != null ? carried.get(fileFolderId) : null;
            if (inherited == KbAccessLevel.NONE) {
                levels.put(file.id(), KbAccessLevel.NONE);
                continue;
            }
            var rows =
                    grants.stream().filter(grant -> grant.isOnFile(file.id())).toList();
            levels.put(file.id(), resolveChild(access, rows, file.mode(), inherited));
        }
        return levels;
    }

    /**
     * Whether a member may read every one of a batch of files, resolved together.
     *
     * @param access the member's memberships and station rights
     * @param files  the files to resolve, each with the folder it sits in
     * @return the ids of the files the member may see
     */
    public Set<Integer> readableFiles(MemberAccess access, List<FileNode> files) {
        return fileLevels(access, files).entrySet().stream()
                .filter(entry -> entry.getValue().covers(KbAccessLevel.READ))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * One file of a batch: its id, the folder it sits in, and the mode its own grants combine in.
     */
    public record FileNode(int id, @Nullable Integer folderId, RestrictionMode mode) {
        public static FileNode of(KbFile file) {
            return new FileNode(file.id(), file.folderId(), file.restrictionMode());
        }
    }

    /**
     * Applies the grants of a whole ancestry, root first, to arrive at the level carried down to
     * its last folder.
     */
    private @Nullable KbAccessLevel applyPath(
            MemberAccess access, List<FolderPathNode> path, List<KbAccessGrant> grants) {
        KbAccessLevel carried = null;
        for (var node : path) {
            var rows =
                    grants.stream().filter(grant -> grant.isOnFolder(node.id())).toList();
            carried = applyNode(access, rows, node.restrictionMode(), carried);
            if (carried == KbAccessLevel.NONE) return KbAccessLevel.NONE;
        }
        return carried;
    }

    private KbAccessLevel resolveChild(
            MemberAccess access, List<KbAccessGrant> rows, RestrictionMode mode, @Nullable KbAccessLevel carried) {
        var resolved = applyNode(access, rows, mode, carried);
        return resolved != null ? resolved : stationDefault(access);
    }

    private static Map<Integer, KbAccessLevel> constant(List<ChildNode> nodes, KbAccessLevel level) {
        var levels = new HashMap<Integer, KbAccessLevel>();
        for (var node : nodes) levels.put(node.id(), level);
        return levels;
    }

    /**
     * The level carried down to a folder from everything above and including it, or {@code null}
     * when nothing along the way said anything, or {@link KbAccessLevel#NONE} when the member is
     * gated out.
     */
    private @Nullable KbAccessLevel carriedLevel(MemberAccess access, @Nullable Integer folderId) {
        if (folderId == null) return null;
        var path = repository.findFolderPath(folderId);
        var grants = repository.findRestrictionsForPath(
                path.stream().map(FolderPathNode::id).toList(), null);
        return applyPath(access, path, grants);
    }

    /**
     * Resolves the level of every folder of a station in one pass down the whole tree.
     *
     * <p>A picker that offers somewhere to put an entry has to know what the reader may do in every
     * folder at once, which per folder would be two queries each. Walking the tree from the roots
     * down instead reads all grants once and carries each folder's answer into its children, the
     * way a single lookup would if it went the same way.
     *
     * @param access the member's memberships and station rights
     * @param nodes  every folder of the station, in any order
     * @return the level per folder id
     */
    public Map<Integer, KbAccessLevel> treeLevels(MemberAccess access, List<TreeNode> nodes) {
        if (access.canManage()) {
            var levels = new HashMap<Integer, KbAccessLevel>();
            for (var node : nodes) levels.put(node.id(), KbAccessLevel.MANAGE);
            return levels;
        }

        var byParent = new HashMap<Integer, List<TreeNode>>();
        for (var node : nodes) {
            byParent.computeIfAbsent(node.parentId(), key -> new ArrayList<>()).add(node);
        }
        var grants = repository.findRestrictionsForNodes(
                nodes.stream().map(TreeNode::id).toList(), List.of());

        var levels = new HashMap<Integer, KbAccessLevel>();
        var pending = new ArrayDeque<PendingNode>();
        for (var root : byParent.getOrDefault(null, List.of())) pending.add(new PendingNode(root, null));
        while (!pending.isEmpty()) {
            var current = pending.poll();
            KbAccessLevel carried;
            if (current.carried() == KbAccessLevel.NONE) {
                carried = KbAccessLevel.NONE;
            } else {
                var rows = grants.stream()
                        .filter(grant -> grant.isOnFolder(current.node().id()))
                        .toList();
                carried = applyNode(access, rows, current.node().mode(), current.carried());
            }
            levels.put(current.node().id(), carried != null ? carried : stationDefault(access));
            for (var child : byParent.getOrDefault(current.node().id(), List.of())) {
                pending.add(new PendingNode(child, carried));
            }
        }
        return levels;
    }

    private record PendingNode(TreeNode node, @Nullable KbAccessLevel carried) {}

    /**
     * One folder of a station's whole tree: its id, where it hangs, and the mode its own grants
     * combine in.
     */
    public record TreeNode(int id, @Nullable Integer parentId, RestrictionMode mode) {}

    /**
     * One child of a listed folder: its id and the mode its own grants combine in.
     */
    public record ChildNode(int id, RestrictionMode mode) {}

    /**
     * The resolved level of every child of a listed folder.
     */
    public record ChildLevels(Map<Integer, KbAccessLevel> folders, Map<Integer, KbAccessLevel> files) {}

    /**
     * Applies one node's grants to the level carried down from above.
     *
     * <p>Answers {@link KbAccessLevel#NONE} when the member is gated out or explicitly denied here,
     * which is unambiguous: a level a member is granted is never {@code NONE}.
     */
    private @Nullable KbAccessLevel applyNode(
            MemberAccess access, List<KbAccessGrant> rows, RestrictionMode mode, @Nullable KbAccessLevel carried) {
        if (rows.isEmpty()) return carried;

        var restrictions = toRestrictionSet(rows, mode);
        if (!restrictions.matches(access.userType(), access.groupIds(), access.tagIds(), access.memberId())) {
            return KbAccessLevel.NONE;
        }

        KbAccessLevel nodeLevel = null;
        for (var row : rows) {
            if (!row.matches(access.memberId(), access.userType(), access.groupIds(), access.tagIds())) continue;
            KbAccessLevel rowLevel = row.level();
            if (rowLevel == KbAccessLevel.NONE) return KbAccessLevel.NONE;
            if (rowLevel != null) nodeLevel = KbAccessLevel.max(nodeLevel, rowLevel);
        }
        return nodeLevel != null ? nodeLevel : carried;
    }

    /**
     * The level a member holds where nothing in the tree says otherwise.
     *
     * <p>The station-wide edit right maps to {@link KbAccessLevel#MANAGE} rather than
     * {@link KbAccessLevel#WRITE} because that is what it already grants: an editor deletes and
     * publishes today, and the upgrade must not take either away. The lower levels earn their
     * keep as grants, which is where they reduce what an editor may do in one place.
     */
    private KbAccessLevel stationDefault(MemberAccess access) {
        if (access.canManage() || access.canEdit()) return KbAccessLevel.MANAGE;
        return KbAccessLevel.READ;
    }

    private RestrictionMode restrictionMode(@Nullable Integer folderId, @Nullable Integer fileId) {
        if (fileId != null) {
            var file = repository.findAnyFileById(fileId);
            if (file.isPresent()) return file.get().restrictionMode();
        } else if (folderId != null) {
            var folder = repository.findAnyFolderById(folderId);
            if (folder.isPresent()) return folder.get().restrictionMode();
        }
        return RestrictionMode.AND;
    }

    private boolean canAccessFolder(
            int memberId,
            int folderId,
            StationUserType memberUserType,
            List<Integer> memberGroupIds,
            List<Integer> memberTagIds) {
        var folder = repository.findAnyFolderById(folderId);
        if (folder.isEmpty()) return true;

        var rawRestrictions = repository.findRestrictions(folderId, null);
        if (!rawRestrictions.isEmpty()) {
            var restrictions = toRestrictionSet(rawRestrictions, folder.get().restrictionMode());
            if (!restrictions.matches(memberUserType, memberGroupIds, memberTagIds, memberId)) return false;
        }

        Integer parentId = folder.get().parentId();
        if (parentId != null) {
            return canAccessFolder(memberId, parentId, memberUserType, memberGroupIds, memberTagIds);
        }

        return true;
    }

    /**
     * A member's memberships plus the station-wide knowledge rights they hold, read once so a whole
     * listing can be resolved without re-reading them per row.
     *
     * @param canEdit   whether the member holds the station-wide edit right
     * @param canManage whether the member holds the station-wide manage right, which bypasses grants
     *                  entirely so a station cannot lock itself out of its own knowledge base
     */
    public record MemberAccess(
            int memberId,
            @Nullable StationUserType userType,
            List<Integer> groupIds,
            List<Integer> tagIds,
            boolean canEdit,
            boolean canManage) {}
}
