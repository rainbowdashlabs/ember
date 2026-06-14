/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

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
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class BoardService {
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
        return repository.create(stationId, name, description, shortKey);
    }

    public Board createWithPreset(int stationId, String name, String description, String shortKey, LanePreset preset) {
        var board = repository.create(stationId, name, description, shortKey);
        var laneNames = preset.laneNames();
        for (int i = 0; i < laneNames.size(); i++) {
            repository.createLane(board.id(), laneNames.get(i), null, i);
        }
        return board;
    }

    public boolean update(int id, String name, String description, int hideDoneAfterDays) {
        return repository.update(id, name, description, hideDoneAfterDays);
    }

    public boolean delete(int id) {
        return repository.delete(id);
    }

    // -- Lanes --

    public List<BoardLane> findLanes(int boardId) {
        return repository.findLanes(boardId);
    }

    public void replaceLanes(int boardId, List<LaneData> lanes) {
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
        return repository.enableBacklog(boardId);
    }

    public void disableBacklog(int boardId) {
        repository.disableBacklog(boardId);
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
    }

    // -- Labels --

    public List<BoardLabel> findLabels(int boardId) {
        return repository.findLabels(boardId);
    }

    public BoardLabel createLabel(int boardId, String name, String color) {
        return repository.createLabel(boardId, name, color);
    }

    public boolean updateLabel(int id, String name, String color) {
        return repository.updateLabel(id, name, color);
    }

    public boolean deleteLabel(int id) {
        return repository.deleteLabel(id);
    }

    public List<BoardLabel> findLabelsForTicket(int ticketId) {
        return repository.findLabelsForTicket(ticketId);
    }

    public void addLabelToTicket(int ticketId, int labelId) {
        repository.addLabelToTicket(ticketId, labelId);
    }

    public boolean removeLabelFromTicket(int ticketId, int labelId) {
        return repository.removeLabelFromTicket(ticketId, labelId);
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
        if (!repository.hasEditRestrictions(boardId)) return canView(boardId, memberId, isBoardManager);
        return matchesAccess(
                memberId,
                repository.findEditAccessUserTypes(boardId),
                repository.findEditAccessGroupIds(boardId),
                repository.findEditAccessTagIds(boardId));
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
    }

    public void setEditAccess(
            int boardId, List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds) {
        repository.setEditAccess(boardId, userTypes, groupIds, tagIds);
    }
}
