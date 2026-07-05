/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.checklist.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.checklist.entity.Checklist;
import dev.chojo.ember.feature.checklist.entity.ChecklistCell;
import dev.chojo.ember.feature.checklist.entity.ChecklistCellNoteHistory;
import dev.chojo.ember.feature.checklist.entity.ChecklistColumn;
import dev.chojo.ember.feature.checklist.entity.ChecklistEntry;
import dev.chojo.ember.feature.checklist.entity.ChecklistSummary;
import dev.chojo.ember.feature.checklist.repository.ChecklistRepository;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.repository.UserTagRepository;
import dev.chojo.ember.feature.restriction.Restriction;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates checklist creation, refresh, manual member add/restore, cell upserts and column edits.
 * Member resolution against the materialisation filter is performed in Java with
 * {@link RestrictionSet#matches} per active station member.
 */
@Singleton
public class ChecklistService {
    private static final Logger log = LoggerFactory.getLogger(ChecklistService.class);

    private final ChecklistRepository repository;
    private final StationMemberRepository memberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;

    @Inject
    public ChecklistService(
            ChecklistRepository repository,
            StationMemberRepository memberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
    }

    public List<ChecklistSummary> findSummaries(int stationId) {
        return repository.findSummariesByStation(stationId);
    }

    public Optional<Checklist> findById(int id) {
        return repository.findById(id);
    }

    public List<ChecklistColumn> findColumns(int checklistId) {
        return repository.findColumns(checklistId);
    }

    public Optional<ChecklistColumn> findColumn(int columnId) {
        return repository.findColumn(columnId);
    }

    public List<ChecklistEntry> findEntries(int checklistId, boolean includeDeleted) {
        return repository.findEntries(checklistId, includeDeleted);
    }

    public Optional<ChecklistEntry> findEntry(int entryId) {
        return repository.findEntry(entryId);
    }

    public List<ChecklistCell> findCells(int checklistId) {
        return repository.findCellsForChecklist(checklistId);
    }

    public List<Restriction> findFilterRows(int checklistId) {
        return repository.findFilterRows(checklistId);
    }

    public RestrictionSet findRestrictionSet(Checklist checklist) {
        return new RestrictionSet(repository.findFilterRows(checklist.id()), checklist.mode());
    }

    /**
     * Creates a checklist with its columns and filter rows, then materialises an entry per
     * matching active member of the station. Returns the freshly-created checklist.
     */
    public Checklist create(
            int stationId,
            String name,
            String description,
            RestrictionMode mode,
            List<ColumnSpec> columns,
            FilterSpec filter,
            int createdBy) {
        var checklist = repository.create(stationId, name, description, mode, createdBy);
        for (int i = 0; i < columns.size(); i++) {
            var spec = columns.get(i);
            repository.createColumn(checklist.id(), i, spec.label(), spec.description());
        }
        repository.replaceFilter(
                checklist.id(), filter.userTypes(), filter.groupIds(), filter.tagIds(), filter.memberIds());
        materialise(checklist.id(), stationId, mode);
        log.info("Created checklist {} at station {} by member {}", checklist.id(), stationId, createdBy);
        return repository.findById(checklist.id()).orElseThrow();
    }

    /**
     * Updates checklist metadata. Restriction-filter rows are NOT touched here; pass
     * {@code filter} non-null to replace them. Refresh must be triggered explicitly.
     */
    public Checklist update(int id, String name, String description, RestrictionMode mode, FilterSpec filter) {
        repository.updateMetadata(id, name, description, mode);
        if (filter != null) {
            repository.replaceFilter(id, filter.userTypes(), filter.groupIds(), filter.tagIds(), filter.memberIds());
        }
        log.info("Updated checklist {} (name='{}', mode={}, filterReplaced={})", id, name, mode, filter != null);
        return repository.findById(id).orElseThrow();
    }

    public boolean delete(int id) {
        boolean deleted = repository.delete(id);
        if (deleted) log.info("Deleted checklist {}", id);
        else log.warn("Delete of checklist {} did not change any row", id);
        return deleted;
    }

    /**
     * Additive refresh: resolves the current filter against the current station members and
     * inserts an entry for every matching member that has no entry (alive or soft-deleted).
     * Returns the counts the route surfaces to the client.
     */
    public RefreshResult refresh(int checklistId) {
        var checklist = repository.findById(checklistId).orElseThrow();
        var resolved = resolveMatchingMemberIds(checklistId, checklist.stationId(), checklist.mode());
        var existing = existingMemberIds(checklistId);
        int added = 0;
        int alreadyPresent = 0;
        for (int memberId : resolved) {
            if (existing.contains(memberId)) {
                alreadyPresent++;
                continue;
            }
            repository.createEntry(checklistId, memberId);
            added++;
        }
        repository.touchRefreshed(checklistId);
        log.info("Refreshed checklist {}: added={}, alreadyPresent={}", checklistId, added, alreadyPresent);
        return new RefreshResult(added, alreadyPresent);
    }

    /**
     * Manually places the listed members on a checklist: creates a fresh entry for new
     * members, clears {@code deleted_at} on soft-deleted ones, and ignores members that
     * already have a live entry. Returns per-bucket counts.
     */
    public ManualAddResult addMembers(int checklistId, List<Integer> memberIds) {
        int added = 0;
        int restored = 0;
        int skipped = 0;
        for (int memberId : memberIds) {
            var existing = repository.findEntryByMember(checklistId, memberId);
            if (existing.isEmpty()) {
                repository.createEntry(checklistId, memberId);
                added++;
            } else if (existing.get().deletedAt() != null) {
                repository.restoreEntry(existing.get().id());
                restored++;
            } else {
                skipped++;
            }
        }
        log.info(
                "Added members to checklist {}: added={}, restored={}, skipped={}",
                checklistId,
                added,
                restored,
                skipped);
        return new ManualAddResult(added, restored, skipped);
    }

    public void softDeleteEntry(int entryId) {
        repository.softDeleteEntry(entryId);
        log.info("Soft-deleted checklist entry {}", entryId);
    }

    public ChecklistColumn addColumn(int checklistId, String label, String description, Integer position) {
        int pos = position != null ? position : repository.nextColumnPosition(checklistId);
        ChecklistColumn column = repository.createColumn(checklistId, pos, label, description);
        log.info("Added column {} to checklist {} (label='{}', position={})", column.id(), checklistId, label, pos);
        return column;
    }

    public ChecklistColumn updateColumn(int columnId, String label, String description, int position) {
        repository.updateColumn(columnId, label, description, position);
        log.info("Updated checklist column {} (label='{}', position={})", columnId, label, position);
        return repository.findColumn(columnId).orElseThrow();
    }

    /**
     * Reorders every column of {@code checklistId} to match {@code orderedIds}. The list must
     * contain every column of the checklist exactly once; any extras or missing ids are rejected
     * so a partial reorder cannot corrupt the sequence.
     */
    public void reorderColumns(int checklistId, List<Integer> orderedIds) {
        var existingIds = repository.findColumns(checklistId).stream()
                .map(ChecklistColumn::id)
                .collect(Collectors.toSet());
        if (orderedIds.size() != existingIds.size() || !new HashSet<>(orderedIds).equals(existingIds)) {
            throw new IllegalArgumentException("orderedIds must contain every column exactly once");
        }
        repository.reorderColumns(checklistId, orderedIds);
        log.info("Reordered {} columns of checklist {}", orderedIds.size(), checklistId);
    }

    public int countCheckedCellsInColumn(int columnId) {
        return repository.countCheckedCellsInColumn(columnId);
    }

    public boolean deleteColumn(int columnId) {
        boolean deleted = repository.deleteColumn(columnId);
        if (deleted) log.info("Deleted checklist column {}", columnId);
        else log.warn("Delete of checklist column {} did not change any row", columnId);
        return deleted;
    }

    /**
     * Writes the cell at (entryId, columnId) and appends a note-history row when the note
     * actually changed. Returns the new cell.
     */
    public CellWriteResult writeCell(int entryId, int columnId, boolean checked, String note, int updatedBy) {
        var previous = repository.findCell(entryId, columnId).orElse(null);
        String previousNote = previous != null ? previous.note() : null;
        String normalisedNote = note == null || note.isEmpty() ? null : note;
        var cell = repository.upsertCell(entryId, columnId, checked, normalisedNote, updatedBy);
        boolean noteChanged = !Objects.equals(previousNote, normalisedNote);
        if (noteChanged) {
            repository.appendNoteHistory(cell.id(), previousNote, normalisedNote, updatedBy);
        }
        log.info(
                "Wrote checklist cell (entry {}, column {}) checked={}, noteChanged={} by member {}",
                entryId,
                columnId,
                checked,
                noteChanged,
                updatedBy);
        return new CellWriteResult(cell, noteChanged);
    }

    public List<ChecklistCellNoteHistory> findNoteHistory(int entryId, int columnId) {
        return repository
                .findCell(entryId, columnId)
                .map(c -> repository.findNoteHistory(c.id()))
                .orElseGet(List::of);
    }

    public int bulkSetColumn(int columnId, List<Integer> entryIds, boolean checked, int updatedBy) {
        int changed = repository.bulkSetChecked(entryIds, columnId, checked, updatedBy);
        log.info(
                "Bulk set column {} checked={} for {} entries ({} rows changed) by member {}",
                columnId,
                checked,
                entryIds.size(),
                changed,
                updatedBy);
        return changed;
    }

    /**
     * Resolves the current materialisation filter against the station's current active members,
     * applying {@link RestrictionSet#matches} per member with its own group / tag context.
     */
    public Set<Integer> resolveMatchingMemberIds(int checklistId, int stationId, RestrictionMode mode) {
        var filterRows = repository.findFilterRows(checklistId);
        if (filterRows.isEmpty()) return Set.of();
        var restrictionSet = new RestrictionSet(filterRows, mode);
        var groups = new HashMap<Integer, List<Integer>>();
        var tags = new HashMap<Integer, List<Integer>>();
        var matching = new LinkedHashSet<Integer>();
        for (StationMember member : memberRepository.findByStation(stationId)) {
            var groupIds =
                    groups.computeIfAbsent(member.id(), id -> memberGroupRepository.findGroupsForMember(id).stream()
                            .map(MemberGroup::id)
                            .toList());
            var tagIds = tags.computeIfAbsent(member.id(), id -> userTagRepository.findTagsForMember(id).stream()
                    .map(UserTag::id)
                    .toList());
            StationUserType userType = member.userType();
            if (restrictionSet.matches(userType, groupIds, tagIds, member.id())) {
                matching.add(member.id());
            }
        }
        return matching;
    }

    private Set<Integer> existingMemberIds(int checklistId) {
        var set = new HashSet<Integer>();
        for (var entry : repository.findEntries(checklistId, true)) {
            set.add(entry.memberId());
        }
        return set;
    }

    private void materialise(int checklistId, int stationId, RestrictionMode mode) {
        for (int memberId : resolveMatchingMemberIds(checklistId, stationId, mode)) {
            repository.createEntry(checklistId, memberId);
        }
    }

    /**
     * Lightweight bag used by the create + update flows to pass filter selections to the service.
     */
    public record FilterSpec(
            List<StationUserType> userTypes, List<Integer> groupIds, List<Integer> tagIds, List<Integer> memberIds) {

        public static FilterSpec empty() {
            return new FilterSpec(List.of(), List.of(), List.of(), List.of());
        }
    }

    /**
     * Lightweight bag used by the create flow to pass each column's label and description.
     */
    public record ColumnSpec(String label, String description) {}

    /**
     * Counts of inserted vs. already-present rows returned by an additive refresh.
     */
    public record RefreshResult(int added, int alreadyPresent) {}

    /**
     * Per-bucket counts returned by the manual-add endpoint.
     */
    public record ManualAddResult(int added, int restored, int skipped) {}

    /**
     * Result of a single cell write, including whether the note actually changed so the
     * route can surface that to the caller.
     */
    public record CellWriteResult(ChecklistCell cell, boolean noteChanged) {}
}
