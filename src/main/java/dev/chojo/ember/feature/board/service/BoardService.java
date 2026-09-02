/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardField;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.LaneData;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketLabelMapping;
import dev.chojo.ember.feature.board.repository.BoardRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class BoardService {
    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final BoardRepository repository;
    private final StationMemberService memberService;
    private final MemberGroupService groupService;
    private final UserTagService tagService;

    @Inject
    public BoardService(
            BoardRepository repository,
            StationMemberService memberService,
            MemberGroupService groupService,
            UserTagService tagService) {
        this.repository = repository;
        this.memberService = memberService;
        this.groupService = groupService;
        this.tagService = tagService;
    }

    // -- Board CRUD --

    public List<Board> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public List<Board> findVisibleBoards(int stationId, int memberId) {
        return findVisibleBoards(stationId, memberId, false);
    }

    public List<Board> findVisibleBoards(int stationId, int memberId, boolean isBoardManager) {
        if (isBoardManager) return repository.findByStation(stationId);
        return repository.findByStation(stationId).stream()
                .filter(b -> canView(b.id(), memberId))
                .toList();
    }

    public Optional<Board> findById(int id) {
        return repository.findById(id);
    }

    public Optional<Board> findByShortKey(int stationId, String shortKey) {
        return repository.findByShortKey(stationId, shortKey);
    }

    public Board create(int stationId, String name, String description, String shortKey) {
        var board = repository.create(stationId, name, description, shortKey);
        log.info("Created board {} ({}) for station {}", board.id(), shortKey, stationId);
        return board;
    }

    public Board createWithPreset(int stationId, String name, String description, String shortKey, LanePreset preset) {
        var board = repository.create(stationId, name, description, shortKey);
        var laneNames = preset.laneNames();
        for (int i = 0; i < laneNames.size(); i++) {
            repository.createLane(board.id(), laneNames.get(i), null, i);
        }
        log.info(
                "Created board {} ({}) for station {} with preset {} lanes",
                board.id(),
                shortKey,
                stationId,
                laneNames.size());
        return board;
    }

    public boolean update(int id, String name, String description, int hideDoneAfterDays) {
        boolean updated = repository.update(id, name, description, hideDoneAfterDays);
        if (updated) {
            log.info("Updated board {}", id);
        } else {
            log.warn("Update for board {} affected zero rows", id);
        }
        return updated;
    }

    public boolean delete(int id) {
        boolean deleted = repository.delete(id);
        if (deleted) {
            log.info("Deleted board {}", id);
        } else {
            log.warn("Delete for board {} affected zero rows", id);
        }
        return deleted;
    }

    // -- Lanes --

    public List<BoardLane> findLanes(int boardId) {
        return repository.findLanes(boardId);
    }

    public void replaceLanes(int boardId, List<LaneData> lanes) {
        log.info("Replacing lanes on board {} with {} incoming lanes", boardId, lanes.size());
        var board = repository.findById(boardId).orElseThrow();
        var existingLanes = repository.findLanes(boardId);
        var incomingIds = lanes.stream()
                .map(LaneData::id)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());

        // Find the first lane in the new set to use as a fallback for orphaned tickets
        Integer fallbackLaneId = null;

        // Update existing lanes and create new ones
        for (int i = 0; i < lanes.size(); i++) {
            var l = lanes.get(i);
            if (l.id() != null && l.id() > 0) {
                // Update existing lane
                repository.updateLane(l.id(), l.name(), l.color(), i);
                if (fallbackLaneId == null) fallbackLaneId = l.id();
            } else {
                // Create new lane
                var created = repository.createLane(boardId, l.name(), l.color(), i);
                if (fallbackLaneId == null) fallbackLaneId = created.id();
            }
        }

        // Delete lanes that are no longer in the list, moving their tickets first
        for (var existing : existingLanes) {
            if (board.backlogLaneId() != null && existing.id() == board.backlogLaneId()) continue;
            if (!incomingIds.contains(existing.id())) {
                if (fallbackLaneId != null) {
                    repository.moveTicketsFromLane(existing.id(), fallbackLaneId);
                }
                repository.deleteLane(existing.id());
            }
        }
    }

    public BoardLane enableBacklog(int boardId) {
        var lane = repository.enableBacklog(boardId);
        log.info("Enabled backlog lane {} on board {}", lane.id(), boardId);
        return lane;
    }

    public void disableBacklog(int boardId) {
        repository.disableBacklog(boardId);
        log.info("Disabled backlog on board {}", boardId);
    }

    // -- Fields --

    public List<BoardField> findFields(int boardId) {
        return repository.findFields(boardId);
    }

    public void replaceFields(int boardId, List<BoardField> fields) {
        repository.deleteAllFields(boardId);
        for (int i = 0; i < fields.size(); i++) {
            var f = fields.get(i);
            repository.createField(boardId, f.name(), f.fieldType(), f.config(), i);
        }
        log.info("Replaced fields on board {} with {} fields", boardId, fields.size());
    }

    // -- Labels --

    public List<BoardLabel> findLabels(int boardId) {
        return repository.findLabels(boardId);
    }

    public BoardLabel createLabel(int boardId, String name, String color) {
        var label = repository.createLabel(boardId, name, color);
        log.info("Created label {} on board {}", label.id(), boardId);
        return label;
    }

    public boolean updateLabel(int id, String name, String color) {
        boolean updated = repository.updateLabel(id, name, color);
        if (updated) {
            log.info("Updated label {}", id);
        } else {
            log.warn("Update for label {} affected zero rows", id);
        }
        return updated;
    }

    public boolean deleteLabel(int id) {
        boolean deleted = repository.deleteLabel(id);
        if (deleted) {
            log.info("Deleted label {}", id);
        } else {
            log.warn("Delete for label {} affected zero rows", id);
        }
        return deleted;
    }

    public List<BoardLabel> findLabelsForTicket(int ticketId) {
        return repository.findLabelsForTicket(ticketId);
    }

    public void addLabelToTicket(int ticketId, int labelId) {
        repository.addLabelToTicket(ticketId, labelId);
        log.info("Added label {} to ticket {}", labelId, ticketId);
    }

    public boolean removeLabelFromTicket(int ticketId, int labelId) {
        boolean removed = repository.removeLabelFromTicket(ticketId, labelId);
        if (removed) {
            log.info("Removed label {} from ticket {}", labelId, ticketId);
        } else {
            log.warn("Remove of label {} from ticket {} affected zero rows", labelId, ticketId);
        }
        return removed;
    }

    public List<TicketLabelMapping> findAllTicketLabels(int boardId) {
        return repository.findAllTicketLabels(boardId);
    }

    // -- Access control --

    public boolean canView(int boardId, int memberId) {
        return canView(boardId, memberId, false);
    }

    public boolean canView(int boardId, int memberId, boolean isBoardManager) {
        if (isBoardManager) return true;
        if (!repository.hasViewRestrictions(boardId)) return true;
        return matchesAccess(
                memberId,
                repository.findViewAccessUserTypes(boardId),
                repository.findViewAccessGroupIds(boardId),
                repository.findViewAccessTagIds(boardId));
    }

    public boolean canEdit(int boardId, int memberId) {
        return canEdit(boardId, memberId, false);
    }

    public boolean canEdit(int boardId, int memberId, boolean isBoardManager) {
        if (isBoardManager) return true;
        if (!repository.hasEditRestrictions(boardId)) return canView(boardId, memberId, false);
        return matchesAccess(
                memberId,
                repository.findEditAccessUserTypes(boardId),
                repository.findEditAccessGroupIds(boardId),
                repository.findEditAccessTagIds(boardId));
    }

    /**
     * The members of a station who may write on a board, answered for all of them at once.
     *
     * <p>Asks exactly what {@link #canEdit(int, int, boolean)} asks, from the other end: that one
     * takes a member and looks up their type, groups and tags, which is three round trips a name.
     * A picker offering a station its own members would pay that for every line, so here the
     * board's own lists are read once and the members behind them collected.
     *
     * @param boardId   the board
     * @param stationId the station the board belongs to
     * @return the ids of the members who may write on it
     */
    public Set<Integer> findMembersWhoMayEdit(int boardId, int stationId) {
        var allowed = memberService.findMembersWithPermission(stationId, StationPermission.BOARD_MANAGER).stream()
                .map(StationMember::id)
                .collect(Collectors.toCollection(HashSet::new));
        if (repository.hasEditRestrictions(boardId)) {
            addMembersMatching(allowed, stationId, getEditAccess(boardId));
        } else if (repository.hasViewRestrictions(boardId)) {
            addMembersMatching(allowed, stationId, getViewAccess(boardId));
        } else {
            memberService.findByStation(stationId).forEach(member -> allowed.add(member.id()));
        }
        return allowed;
    }

    private void addMembersMatching(Set<Integer> allowed, int stationId, AccessData access) {
        if (!access.userTypes().isEmpty()) {
            memberService.findByStation(stationId).stream()
                    .filter(member -> access.userTypes().contains(member.userType()))
                    .forEach(member -> allowed.add(member.id()));
        }
        for (int groupId : access.groupIds()) {
            groupService.findMembers(groupId).forEach(member -> allowed.add(member.id()));
        }
        for (int tagId : access.tagIds()) {
            tagService.findMembers(tagId).forEach(member -> allowed.add(member.id()));
        }
    }

    public AccessData getViewAccess(int boardId) {
        return new AccessData(
                repository.findViewAccessUserTypes(boardId),
                repository.findViewAccessGroupIds(boardId),
                repository.findViewAccessTagIds(boardId));
    }

    public AccessData getEditAccess(int boardId) {
        return new AccessData(
                repository.findEditAccessUserTypes(boardId),
                repository.findEditAccessGroupIds(boardId),
                repository.findEditAccessTagIds(boardId));
    }

    public void setViewAccess(
            int boardId, List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        repository.setViewAccess(boardId, userTypes, groupIds, tagIds);
        log.info(
                "Updated view access for board {} ({} user types, {} groups, {} tags)",
                boardId,
                userTypes.size(),
                groupIds.size(),
                tagIds.size());
    }

    public void setEditAccess(
            int boardId, List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        repository.setEditAccess(boardId, userTypes, groupIds, tagIds);
        log.info(
                "Updated edit access for board {} ({} user types, {} groups, {} tags)",
                boardId,
                userTypes.size(),
                groupIds.size(),
                tagIds.size());
    }

    private boolean matchesAccess(
            int memberId, List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        if (!userTypes.isEmpty()) {
            var member = memberService.findById(memberId);
            if (member.isPresent() && userTypes.contains(member.get().userType())) return true;
        }
        if (!groupIds.isEmpty()) {
            var memberGroupIds = groupService.findGroupsForMember(memberId).stream()
                    .map(MemberGroup::id)
                    .toList();
            if (memberGroupIds.stream().anyMatch(groupIds::contains)) return true;
        }
        if (!tagIds.isEmpty()) {
            var memberTagIds = tagService.findTagsForMember(memberId).stream()
                    .map(UserTag::id)
                    .toList();
            return memberTagIds.stream().anyMatch(tagIds::contains);
        }
        return false;
    }
}
