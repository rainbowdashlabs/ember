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
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
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

import java.time.LocalDate;
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
 *
 * <p>A list follows one thing at a time: either its filter rows or one occurrence of an appointment.
 * Both are resolved by {@link #resolveMembership}, and neither ever removes a row. Refresh adds, a
 * row taken off by hand stays off, and everything a read has to say about somebody who no longer
 * belongs is said by marking their row.
 */
@Singleton
public class ChecklistService {
    private static final Logger log = LoggerFactory.getLogger(ChecklistService.class);

    private final ChecklistRepository repository;
    private final StationMemberRepository memberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final UserTagRepository userTagRepository;
    private final EventRegistrationRepository registrationRepository;

    @Inject
    public ChecklistService(
            ChecklistRepository repository,
            StationMemberRepository memberRepository,
            MemberGroupRepository memberGroupRepository,
            UserTagRepository userTagRepository,
            EventRegistrationRepository registrationRepository) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.userTagRepository = userTagRepository;
        this.registrationRepository = registrationRepository;
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
        return create(stationId, name, description, mode, columns, filter, null, createdBy);
    }

    /**
     * Creates a checklist and materialises the people it starts with.
     *
     * <p>Passing an {@code occurrence} makes the list follow that evening's accepted sign-ups
     * instead of a filter, and the filter is left empty: the two never stand together, because an
     * appointment already decides who it is for.
     */
    public Checklist create(
            int stationId,
            String name,
            String description,
            RestrictionMode mode,
            List<ColumnSpec> columns,
            FilterSpec filter,
            OccurrenceSpec occurrence,
            int createdBy) {
        var checklist = repository.create(
                stationId,
                name,
                description,
                mode,
                createdBy,
                occurrence == null ? null : occurrence.eventId(),
                occurrence == null ? null : occurrence.date());
        for (int i = 0; i < columns.size(); i++) {
            var spec = columns.get(i);
            repository.createColumn(checklist.id(), i, spec.label(), spec.description());
        }
        FilterSpec effective = occurrence != null ? FilterSpec.empty() : filter;
        repository.replaceFilter(
                checklist.id(), effective.userTypes(), effective.groupIds(), effective.tagIds(), effective.memberIds());
        materialise(repository.findById(checklist.id()).orElseThrow());
        log.info(
                "Created checklist {} at station {} by member {} (followsEvent={})",
                checklist.id(),
                stationId,
                createdBy,
                occurrence != null);
        return repository.findById(checklist.id()).orElseThrow();
    }

    /**
     * Updates checklist metadata. Restriction-filter rows are NOT touched here; pass
     * {@code filter} non-null to replace them, which also stops the list following an appointment.
     * Refresh must be triggered explicitly.
     */
    public Checklist update(int id, String name, String description, RestrictionMode mode, FilterSpec filter) {
        repository.updateMetadata(id, name, description, mode);
        if (filter != null) {
            repository.replaceFilter(id, filter.userTypes(), filter.groupIds(), filter.tagIds(), filter.memberIds());
            repository.updateSourceEvent(id, null, null);
        }
        log.info("Updated checklist {} (name='{}', mode={}, filterReplaced={})", id, name, mode, filter != null);
        return repository.findById(id).orElseThrow();
    }

    /**
     * Makes the list follow one occurrence of an appointment and drops whatever filter it carried,
     * because a list follows one thing at a time. Nobody is added or removed here: the new source
     * only takes effect on the next refresh, which is the one thing that ever brings people in.
     */
    public Checklist followOccurrence(int id, OccurrenceSpec occurrence) {
        repository.replaceFilter(id, List.of(), List.of(), List.of(), List.of());
        repository.updateSourceEvent(id, occurrence.eventId(), occurrence.date());
        log.info("Checklist {} now follows appointment {} on {}", id, occurrence.eventId(), occurrence.date());
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
        var resolved = resolveMembership(checklist).memberIds();
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
     * Works out who belongs on the list right now, and whether the list follows anything at all.
     *
     * <p>Two sources, never both. An appointment occurrence resolves to the accepted sign-ups of
     * that one evening; anything else resolves the filter rows against the station's current
     * members with {@link RestrictionSet#matches}. A list that follows neither, which is what a
     * deleted appointment leaves behind, resolves to nothing and marks nobody.
     *
     * @param checklist the list to resolve
     * @return what it follows and who currently matches
     */
    public MembershipResolution resolveMembership(Checklist checklist) {
        if (checklist.followsEvent()) {
            return new MembershipResolution(true, resolveFromOccurrence(checklist));
        }
        var filterRows = repository.findFilterRows(checklist.id());
        if (filterRows.isEmpty()) return new MembershipResolution(false, Set.of());
        var restrictionSet = new RestrictionSet(filterRows, checklist.mode());
        var groups = new HashMap<Integer, List<Integer>>();
        var tags = new HashMap<Integer, List<Integer>>();
        var matching = new LinkedHashSet<Integer>();
        for (StationMember member : memberRepository.findByStation(checklist.stationId())) {
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
        return new MembershipResolution(true, matching);
    }

    /**
     * The people holding a place on the followed evening, narrowed to the members this station has
     * today.
     *
     * <p>Only a place actually taken counts, which is the set the appointment itself measures. Two
     * groups fall away and both are deliberate: partner-station guests are kept against a remote
     * member and can never become a row here, and somebody who has since left the station is no
     * longer among its members, exactly as for every other way a list is made.
     */
    private Set<Integer> resolveFromOccurrence(Checklist checklist) {
        var current = new HashSet<Integer>();
        for (StationMember member : memberRepository.findByStation(checklist.stationId())) {
            current.add(member.id());
        }
        var matching = new LinkedHashSet<Integer>();
        for (var registration :
                registrationRepository.findByEventAndDate(checklist.sourceEventId(), checklist.sourceEventDate())) {
            if (registration.status() != RegistrationStatus.ACCEPTED) continue;
            if (!current.contains(registration.memberId())) continue;
            matching.add(registration.memberId());
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

    private void materialise(Checklist checklist) {
        for (int memberId : resolveMembership(checklist).memberIds()) {
            repository.createEntry(checklist.id(), memberId);
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
     * One evening of one appointment, which is what a following list names.
     *
     * <p>The date is the whole point. Sign-ups are kept per appointment and date, so an appointment
     * on its own would resolve to the union of every Tuesday a weekly Dienst has ever had.
     *
     * @param eventId the appointment
     * @param date    the one occurrence of it whose sign-ups are followed
     */
    public record OccurrenceSpec(int eventId, LocalDate date) {}

    /**
     * What a list follows and who currently matches it.
     *
     * @param following whether the list follows anything at all; {@code false} for one whose
     *                  appointment has been deleted, and nobody on it is marked as no longer fitting
     * @param memberIds the members who match right now, in a stable order
     */
    public record MembershipResolution(boolean following, Set<Integer> memberIds) {}

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
