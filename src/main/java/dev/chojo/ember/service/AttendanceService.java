/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.entity.AttendanceEntry;
import dev.chojo.ember.entity.AttendanceFieldConfig;
import dev.chojo.ember.entity.AttendanceSession;
import dev.chojo.ember.entity.AttendanceSessionField;
import dev.chojo.ember.entity.AttendanceTemplate;
import dev.chojo.ember.entity.AttendanceTemplateField;
import dev.chojo.ember.entity.MemberAbsence;
import dev.chojo.ember.entity.StationMember;
import dev.chojo.ember.repository.AttendanceRepository;
import dev.chojo.ember.repository.AttendanceRepository.TemplateGroup;
import dev.chojo.ember.repository.EventRepository;
import dev.chojo.ember.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EventRepository eventRepository,
            StationMemberRepository stationMemberRepository) {
        this.attendanceRepository = attendanceRepository;
        this.eventRepository = eventRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    public Set<Integer> findManagedMemberIds(int managerId) {
        return stationMemberRepository.findManaged(managerId).stream()
                .map(StationMember::id)
                .collect(Collectors.toSet());
    }

    // -- Templates --

    public List<AttendanceTemplate> findTemplatesByStation(int stationId) {
        return attendanceRepository.findTemplatesByStation(stationId);
    }

    public Optional<AttendanceTemplate> findTemplateById(int id) {
        return attendanceRepository.findTemplateById(id);
    }

    public List<AttendanceTemplateField> findTemplateFields(int templateId) {
        return attendanceRepository.findTemplateFields(templateId);
    }

    public AttendanceTemplate createTemplate(int stationId, String name) {
        return attendanceRepository.createTemplate(stationId, name);
    }

    public Optional<AttendanceTemplate> updateTemplate(int id, String name) {
        if (attendanceRepository.updateTemplate(id, name)) {
            return attendanceRepository.findTemplateById(id);
        }
        return Optional.empty();
    }

    public boolean deleteTemplate(int id) {
        return attendanceRepository.deleteTemplate(id);
    }

    // -- Template Groups --

    public List<TemplateGroup> findTemplateGroups(int templateId) {
        return attendanceRepository.findTemplateGroups(templateId);
    }

    public void setTemplateGroups(int templateId, List<TemplateGroup> groups) {
        attendanceRepository.setTemplateGroups(templateId, groups);
    }

    // -- Template Fields --

    public List<AttendanceTemplateField> createTemplateField(
            int templateId, String name, String fieldType, String config, int position) {
        attendanceRepository.createTemplateField(templateId, name, fieldType, config, position);
        return attendanceRepository.findTemplateFields(templateId);
    }

    public Optional<List<AttendanceTemplateField>> updateTemplateField(
            int templateId, int fieldId, String name, String fieldType, String config, int position) {
        if (attendanceRepository.updateTemplateField(fieldId, name, fieldType, config, position)) {
            return Optional.of(attendanceRepository.findTemplateFields(templateId));
        }
        return Optional.empty();
    }

    public Optional<List<AttendanceTemplateField>> deleteTemplateField(int templateId, int fieldId) {
        if (attendanceRepository.deleteTemplateField(fieldId)) {
            return Optional.of(attendanceRepository.findTemplateFields(templateId));
        }
        return Optional.empty();
    }

    // -- Sessions --

    public List<AttendanceRepository.SessionSummary> findSessionSummaries(int stationId) {
        return attendanceRepository.findSessionSummariesByStation(stationId);
    }

    public List<AttendanceSession> findSessionsByTemplate(int templateId) {
        return attendanceRepository.findSessionsByTemplate(templateId);
    }

    public Optional<AttendanceSession> findSessionById(int id) {
        return attendanceRepository.findSessionById(id);
    }

    public List<AttendanceSessionField> findSessionFields(int sessionId) {
        return attendanceRepository.findSessionFields(sessionId);
    }

    public List<AttendanceEntry> findEntries(int sessionId) {
        return attendanceRepository.findEntries(sessionId);
    }

    public AttendanceSession createSession(
            int templateId, Instant startTime, Instant endTime, Integer eventId, String title) {
        // Reuse existing session for this event
        if (eventId != null) {
            var existing = attendanceRepository.findSessionByEventId(eventId);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        // Determine title and default times from the linked event
        String resolvedTitle = title;
        Instant resolvedStart = startTime;
        Instant resolvedEnd = endTime;
        if (eventId != null) {
            var event = eventRepository.findById(eventId).orElse(null);
            if (event != null) {
                if (resolvedTitle == null || resolvedTitle.isBlank()) {
                    resolvedTitle = event.name();
                }
                if (resolvedStart == null && event.startTime() != null) {
                    resolvedStart = event.startTime();
                }
                if (resolvedEnd == null && event.endTime() != null) {
                    resolvedEnd = event.endTime();
                }
            }
        }
        if (resolvedTitle == null || resolvedTitle.isBlank()) {
            var template = attendanceRepository.findTemplateById(templateId);
            if (template.isPresent()) resolvedTitle = template.get().name();
        }

        // Fall back to current time when no times could be resolved
        if (resolvedStart == null) resolvedStart = Instant.now();
        if (resolvedEnd == null) resolvedEnd = Instant.now();

        var session =
                attendanceRepository.createSession(templateId, resolvedStart, resolvedEnd, eventId, resolvedTitle);
        // Auto-populate field defaults from template field config
        var templateFields = attendanceRepository.findTemplateFields(templateId);
        for (var field : templateFields) {
            var config = AttendanceFieldConfig.parse(field.config());
            if (config.hasDefaultValue()) {
                String resolved = config.resolveDefaultValueJson();
                if (resolved != null) {
                    attendanceRepository.setSessionField(session.id(), field.id(), resolved);
                }
            }
        }
        // Auto-populate field defaults from the linked event (overrides template defaults)
        if (eventId != null) {
            var defaults = eventRepository.findFieldDefaults(eventId);
            if (!defaults.isEmpty()) {
                var event = eventRepository.findById(eventId).orElse(null);
                if (event != null) {
                    for (var def : defaults) {
                        String resolved =
                                switch (def.source()) {
                                    case "VALUE" -> def.value();
                                    case "EVENT_NAME" -> event.name();
                                    case "EVENT_DESCRIPTION" -> event.description();
                                    case "EVENT_START_TIME" ->
                                        event.startTime() != null ? "\"" + event.startTime() + "\"" : null;
                                    case "EVENT_END_TIME" ->
                                        event.endTime() != null ? "\"" + event.endTime() + "\"" : null;
                                    default -> null;
                                };
                        if (resolved != null) {
                            attendanceRepository.setSessionField(session.id(), def.fieldId(), resolved);
                        }
                    }
                }
            }
        }
        return session;
    }

    public Optional<AttendanceSession> updateSession(int id, Instant startTime, Instant endTime, String title) {
        if (attendanceRepository.updateSession(id, startTime, endTime, title)) {
            return attendanceRepository.findSessionById(id);
        }
        return Optional.empty();
    }

    public boolean deleteSession(int id) {
        return attendanceRepository.deleteSession(id);
    }

    // -- Session Fields (batch) --

    public List<AttendanceSessionField> setSessionFields(int sessionId, List<FieldValueEntry> fields) {
        for (var field : fields) {
            attendanceRepository.setSessionField(sessionId, field.fieldId(), field.value());
        }
        return attendanceRepository.findSessionFields(sessionId);
    }

    // -- Entries --

    public List<AttendanceEntry> createEntry(int sessionId, int memberId, AttendanceEntry.EntrySource source) {
        AttendanceEntry.AttendanceStatus status;
        if (attendanceRepository.isAbsent(memberId)) {
            status = AttendanceEntry.AttendanceStatus.DECLINED;
        } else if (isDeclinedForSession(sessionId, memberId)) {
            status = AttendanceEntry.AttendanceStatus.DECLINED;
        } else {
            status = AttendanceEntry.AttendanceStatus.UNCONFIRMED;
        }
        attendanceRepository.createEntry(sessionId, memberId, status, source);
        return attendanceRepository.findEntries(sessionId);
    }

    public boolean updateEntryStatus(int entryId, AttendanceEntry.AttendanceStatus status) {
        return attendanceRepository.updateEntryStatus(entryId, status);
    }

    public boolean resetTimes(int entryId) {
        return attendanceRepository.resetTimes(entryId);
    }

    /**
     * Sync attendance entries from event registrations, absence data, and autoAttend template fields.
     * - ACCEPTED registrations → PRESENT (or ABSENT if member has active absence)
     * - DECLINED registrations → DECLINED
     * - Members with active absence who already have PRESENT status → updated to ABSENT
     * - Members from autoAttend fields → added as PRESENT at the end
     */
    public List<AttendanceEntry> syncFromEvent(int sessionId) {
        var session = attendanceRepository.findSessionById(sessionId);
        if (session.isEmpty()) return attendanceRepository.findEntries(sessionId);

        var existingEntries = attendanceRepository.findEntries(sessionId);
        var existingMemberIds =
                existingEntries.stream().map(AttendanceEntry::memberId).collect(Collectors.toSet());

        // Sync from event registrations
        if (session.get().eventId() != null) {
            int eventId = session.get().eventId();
            LocalDate today = LocalDate.now();
            var registrations = eventRepository.findRegistrations(eventId, today);

            for (var reg : registrations) {
                if (existingMemberIds.contains(reg.memberId())) continue;
                var status =
                        switch (reg.status()) {
                            case ACCEPTED -> AttendanceEntry.AttendanceStatus.PRESENT;
                            case DECLINED -> AttendanceEntry.AttendanceStatus.DECLINED;
                            default -> null;
                        };
                if (status != null) {
                    if (status == AttendanceEntry.AttendanceStatus.PRESENT
                            && attendanceRepository.isAbsent(reg.memberId())) {
                        status = AttendanceEntry.AttendanceStatus.ABSENT;
                    }
                    attendanceRepository.createEntry(
                            sessionId, reg.memberId(), status, AttendanceEntry.EntrySource.EXPECTED);
                    existingMemberIds.add(reg.memberId());
                }
            }
        }

        // Sync absence status for existing PRESENT/UNCONFIRMED entries
        existingEntries = attendanceRepository.findEntries(sessionId);
        for (var entry : existingEntries) {
            if ((entry.status() == AttendanceEntry.AttendanceStatus.PRESENT
                            || entry.status() == AttendanceEntry.AttendanceStatus.UNCONFIRMED)
                    && attendanceRepository.isAbsent(entry.memberId())) {
                attendanceRepository.updateEntryStatus(entry.id(), AttendanceEntry.AttendanceStatus.ABSENT);
            }
        }

        // Add members from autoAttend fields
        int templateId = session.get().templateId();
        var templateFieldsList = attendanceRepository.findTemplateFields(templateId);
        var sessionFieldValues = attendanceRepository.findSessionFields(sessionId);
        var fieldValueMap = sessionFieldValues.stream()
                .collect(Collectors.toMap(AttendanceSessionField::fieldId, f -> f.value() != null ? f.value() : ""));

        // Refresh existing member IDs
        existingMemberIds = attendanceRepository.findEntries(sessionId).stream()
                .map(AttendanceEntry::memberId)
                .collect(Collectors.toSet());

        for (var field : templateFieldsList) {
            if (!AttendanceFieldConfig.parse(field.config()).autoAttend()) continue;
            String value = fieldValueMap.getOrDefault(field.id(), "");
            if (value.isBlank()) continue;

            var memberIds = parseMemberIdsFromFieldValue(value);
            for (int memberId : memberIds) {
                if (existingMemberIds.contains(memberId)) {
                    // If already exists but not PRESENT, upgrade to PRESENT
                    attendanceRepository.findEntry(sessionId, memberId).ifPresent(entry -> {
                        if (entry.status() != AttendanceEntry.AttendanceStatus.PRESENT) {
                            attendanceRepository.updateEntryStatus(
                                    entry.id(), AttendanceEntry.AttendanceStatus.PRESENT);
                        }
                    });
                    continue;
                }
                attendanceRepository.createEntry(
                        sessionId,
                        memberId,
                        AttendanceEntry.AttendanceStatus.PRESENT,
                        AttendanceEntry.EntrySource.EXTRA);
                existingMemberIds.add(memberId);
            }
        }

        return attendanceRepository.findEntries(sessionId);
    }

    public boolean checkIn(int entryId, Instant time) {
        return attendanceRepository.checkIn(entryId, time);
    }

    public boolean checkOut(int entryId, Instant time) {
        return attendanceRepository.checkOut(entryId, time);
    }

    public boolean deleteEntry(int id) {
        return attendanceRepository.deleteEntry(id);
    }

    public MemberAbsence createAbsence(
            int memberId, LocalDate absentFrom, LocalDate absentUntil, String reason, Integer createdBy) {
        return attendanceRepository.createAbsence(memberId, absentFrom, absentUntil, reason, createdBy);
    }

    public Optional<MemberAbsence> findAbsenceById(int id) {
        return attendanceRepository.findAbsenceById(id);
    }

    // -- Absences --

    public List<MemberAbsence> findAbsencesByMember(int memberId) {
        return attendanceRepository.findAbsencesByMember(memberId);
    }

    public List<MemberAbsence> findActiveAbsencesByStation(int stationId) {
        return attendanceRepository.findActiveAbsencesByStation(stationId);
    }

    public boolean isAbsent(int memberId) {
        return attendanceRepository.isAbsent(memberId);
    }

    public boolean deleteAbsence(int id) {
        return attendanceRepository.deleteAbsence(id);
    }

    /**
     * Checks if a member has declined the event linked to this session.
     */
    private boolean isDeclinedForSession(int sessionId, int memberId) {
        var session = attendanceRepository.findSessionById(sessionId);
        if (session.isEmpty() || session.get().eventId() == null) return false;

        LocalDate today = LocalDate.now();
        return eventRepository
                .findDeclinedMemberIds(session.get().eventId(), today)
                .contains(memberId);
    }

    private List<Integer> parseMemberIdsFromFieldValue(String value) {
        var ids = new ArrayList<Integer>();
        try {
            // Try as JSON array: [1,2,3] or ["1","2"]
            if (value.startsWith("[")) {
                var cleaned = value.replaceAll("[\\[\\]\"\\s]", "");
                for (String part : cleaned.split(",")) {
                    if (!part.isBlank()) ids.add(Integer.parseInt(part.trim()));
                }
            } else {
                // Try as single number or quoted number
                var cleaned = value.replace("\"", "").trim();
                if (!cleaned.isBlank()) ids.add(Integer.parseInt(cleaned));
            }
        } catch (NumberFormatException e) {
            // ignore unparseable
        }
        return ids;
    }

    public record FieldValueEntry(int fieldId, String value) {}
}
