/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.AttendanceRecorded;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldType;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.entity.SessionSummary;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository.TemplateGroup;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.MemberAbsence;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for attendance management including templates, sessions, entries, check-in/out,
 * absences, and event synchronization.
 */
@Singleton
public class AttendanceService {
    private static final Logger log = LoggerFactory.getLogger(AttendanceService.class);
    private static final ObjectMapper JSON = JsonMapper.builder().build();
    /** Reads a value only when it is JSON all the way to its end, so a date is not read as a number. */
    private static final ObjectReader STRICT_JSON = JSON.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final AttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;
    private final EventFieldRepository eventFieldRepository;
    private final EventFieldDefaultRepository eventFieldDefaultRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final DomainEventBus eventBus;
    private final Attendance attendanceConfig;

    @Inject
    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EventRepository eventRepository,
            EventFieldRepository eventFieldRepository,
            EventFieldDefaultRepository eventFieldDefaultRepository,
            EventRegistrationRepository eventRegistrationRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            DomainEventBus eventBus,
            Attendance attendanceConfig) {
        this.attendanceRepository = attendanceRepository;
        this.eventRepository = eventRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.eventFieldDefaultRepository = eventFieldDefaultRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.eventBus = eventBus;
        this.attendanceConfig = attendanceConfig;
    }

    private static String toJsonValue(Object value) {
        if (value == null) return null;
        try {
            return JSON.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
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
        var template = attendanceRepository.createTemplate(stationId, name);
        log.info("Created attendance template {} for station {}", template.id(), stationId);
        return template;
    }

    public Optional<AttendanceTemplate> updateTemplate(int id, String name) {
        if (attendanceRepository.updateTemplate(id, name)) {
            log.info("Updated attendance template {}", id);
            return attendanceRepository.findTemplateById(id);
        }
        log.warn("Cannot update attendance template: template {} not found", id);
        return Optional.empty();
    }

    public boolean deleteTemplate(int id) {
        if (attendanceRepository.deleteTemplate(id)) {
            log.info("Deleted attendance template {}", id);
            return true;
        }
        log.warn("Cannot delete attendance template: template {} not found", id);
        return false;
    }

    // -- Template Groups --

    public List<TemplateGroup> findTemplateGroups(int templateId) {
        return attendanceRepository.findTemplateGroups(templateId);
    }

    public void setTemplateGroups(int templateId, List<TemplateGroup> groups) {
        attendanceRepository.setTemplateGroups(templateId, groups);
        log.info("Set {} template groups for attendance template {}", groups.size(), templateId);
    }

    // -- Template Fields --

    public List<AttendanceTemplateField> createTemplateField(
            int templateId, String name, AttendanceFieldType fieldType, AttendanceFieldConfig config, int position) {
        attendanceRepository.createTemplateField(templateId, name, fieldType, config, position);
        log.info("Created attendance template field for template {} (type {})", templateId, fieldType);
        return attendanceRepository.findTemplateFields(templateId);
    }

    public Optional<List<AttendanceTemplateField>> updateTemplateField(
            int templateId,
            int fieldId,
            String name,
            AttendanceFieldType fieldType,
            AttendanceFieldConfig config,
            int position) {
        if (attendanceRepository.updateTemplateField(fieldId, name, fieldType, config, position)) {
            log.info("Updated attendance template field {} for template {}", fieldId, templateId);
            return Optional.of(attendanceRepository.findTemplateFields(templateId));
        }
        log.warn("Cannot update attendance template field: field {} not found", fieldId);
        return Optional.empty();
    }

    public Optional<List<AttendanceTemplateField>> deleteTemplateField(int templateId, int fieldId) {
        if (attendanceRepository.deleteTemplateField(fieldId)) {
            log.info("Deleted attendance template field {} from template {}", fieldId, templateId);
            return Optional.of(attendanceRepository.findTemplateFields(templateId));
        }
        log.warn("Cannot delete attendance template field: field {} not found", fieldId);
        return Optional.empty();
    }

    // -- Sessions --

    public List<SessionSummary> findSessionSummaries(int stationId) {
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

    public Optional<AttendanceEntry> findEntryById(int id) {
        return attendanceRepository.findEntryById(id);
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
        log.info("Created attendance session {} for template {} (event {})", session.id(), templateId, eventId);
        // Auto-populate field defaults from template field config
        var templateFields = attendanceRepository.findTemplateFields(templateId);
        for (var field : templateFields) {
            var config = field.config();
            if (config.hasDefaultValue()) {
                String resolved = config.resolveDefaultValueJson();
                if (resolved != null) {
                    attendanceRepository.setSessionField(session.id(), field.id(), resolved);
                }
            }
        }
        // Auto-populate field defaults from the linked event (overrides template defaults)
        if (eventId != null) {
            var defaults = eventFieldDefaultRepository.findByEvent(eventId);
            if (!defaults.isEmpty()) {
                var event = eventRepository.findById(eventId).orElse(null);
                if (event != null) {
                    for (var def : defaults) {
                        String resolved =
                                switch (def.source()) {
                                    case "VALUE" -> toJsonValue(def.value());
                                    case "EVENT_NAME" -> toJsonValue(event.name());
                                    case "EVENT_DESCRIPTION" -> toJsonValue(event.description());
                                    case "EVENT_START_TIME" -> toJsonValue(event.startTime());
                                    case "EVENT_END_TIME" -> toJsonValue(event.endTime());
                                    default -> null;
                                };
                        if (resolved != null) {
                            attendanceRepository.setSessionField(session.id(), def.fieldId(), resolved);
                        }
                    }
                }
            }
        }
        // The appointment's own answers stand above the defaults the sheet and the appointment carry
        if (eventId != null) takeEventFieldValues(session.id(), eventId, false);

        enterExpectedMembers(session.id(), expectedMembers(templateId), new HashSet<>());

        return session;
    }

    /**
     * Writes what the appointment answered into the sheet fields its questions are tied to.
     *
     * <p>Taken again whenever the sheet is filled in from its appointment, because the answer is
     * often given after the sheet was opened: whoever runs the evening enters it on the appointment,
     * and until then there was nothing to carry over.
     *
     * @param sessionId        the sheet being filled
     * @param eventId          the appointment it was made from
     * @param keepWhatIsFilled leaves a field that already says something alone, which is what filling
     *                         an open sheet in wants: what stands on it was written by somebody
     *                         looking at the evening itself, and the appointment must not undo that
     */
    private void takeEventFieldValues(int sessionId, int eventId, boolean keepWhatIsFilled) {
        Set<Integer> filled = keepWhatIsFilled
                ? attendanceRepository.findSessionFields(sessionId).stream()
                        .filter(field -> !isEmptyValue(field.value()))
                        .map(AttendanceSessionField::fieldId)
                        .collect(Collectors.toSet())
                : Set.of();
        for (var field : eventFieldRepository.findByEvent(eventId)) {
            if (field.attendanceFieldId() == null) continue;
            if (field.value() == null || field.value().isBlank()) continue;
            if (filled.contains(field.attendanceFieldId())) continue;
            String value = asJsonValue(field.value());
            if (value == null) continue;
            attendanceRepository.setSessionField(sessionId, field.attendanceFieldId(), value);
        }
    }

    /** Whether a sheet field says nothing, whether it was never written or written empty. */
    private static boolean isEmptyValue(String value) {
        if (value == null || value.isBlank()) return true;
        String trimmed = value.trim();
        return trimmed.equals("\"\"") || trimmed.equals("null");
    }

    /**
     * The value as the sheet keeps it, which is JSON.
     *
     * <p>An appointment keeps its answers as plain text, and a piece of plain text is not JSON:
     * writing it straight into the sheet threw the whole request away, which is why an answer given
     * on an appointment never arrived on the sheet it was tied to.
     *
     * <p>Only a list or an object is taken as it stands, which is how the members a question holds
     * are kept. Everything else goes in as the text it is, rather than being read for a number that
     * happens to stand at its front: a date read that way is a number followed by the rest of the
     * date, and the rest is what the sheet would have choked on.
     */
    private static String asJsonValue(String raw) {
        try {
            STRICT_JSON.readTree(raw);
            return raw.trim();
        } catch (Exception e) {
            // Not JSON, or not JSON all the way to its end, so it goes in as the text it is
            return toJsonValue(raw);
        }
    }

    /**
     * Everybody the template expects: the members of the groups it names.
     *
     * <p>The template is the only thing that decides who belongs on a sheet. An event the sheet was
     * made from asks its own question of its own people, and whom it was open to says nothing about
     * who is expected at the evening itself.
     *
     * @param templateId the template the sheet was made from
     * @return the members of the template's groups, without those who have left
     */
    private Set<Integer> expectedMembers(int templateId) {
        Set<Integer> expected = new LinkedHashSet<>();
        for (var group : attendanceRepository.findTemplateGroups(templateId)) {
            for (var member : memberGroupRepository.findMembers(group.groupId())) {
                if (!member.former()) expected.add(member.id());
            }
        }
        return expected;
    }

    /**
     * Puts everybody the template expects onto the sheet, as far as they are not on it already.
     *
     * <p>Run again whenever the sheet is filled in from its event, because a group grows: somebody
     * who joined after the sheet was opened would otherwise stand on it without an entry, with
     * nothing to mark and nothing for the walk to pick up.
     *
     * <p>Somebody who is away for the day arrives declined, since that is already settled.
     *
     * @param sessionId      the sheet being filled
     * @param expected       whom the template expects
     * @param alreadyEntered who is on the sheet already, extended by everybody added here
     */
    private void enterExpectedMembers(int sessionId, Set<Integer> expected, Set<Integer> alreadyEntered) {
        for (int memberId : expected) {
            if (!alreadyEntered.add(memberId)) continue;
            if (!hadJoinedBySession(sessionId, memberId)) continue;
            attendanceRepository.createEntry(
                    sessionId,
                    memberId,
                    attendanceRepository.isAbsent(memberId)
                            ? AttendanceEntry.AttendanceStatus.DECLINED
                            : AttendanceEntry.AttendanceStatus.UNCONFIRMED,
                    AttendanceEntry.EntrySource.EXPECTED);
        }
    }

    public Optional<AttendanceSession> updateSession(int id, Instant startTime, Instant endTime, String title) {
        if (attendanceRepository.updateSession(id, startTime, endTime, title)) {
            log.info("Updated attendance session {}", id);
            return attendanceRepository.findSessionById(id);
        }
        log.warn("Cannot update attendance session: session {} not found", id);
        return Optional.empty();
    }

    public boolean deleteSession(int id) {
        if (attendanceRepository.deleteSession(id)) {
            log.info("Deleted attendance session {}", id);
            return true;
        }
        log.warn("Cannot delete attendance session: session {} not found", id);
        return false;
    }

    // -- Session Fields (batch) --

    public List<AttendanceSessionField> setSessionFields(int sessionId, List<AttendanceFieldValueEntry> fields) {
        requireSessionOpen(sessionId);
        for (var field : fields) {
            attendanceRepository.setSessionField(sessionId, field.fieldId(), field.value());
        }
        log.info("Set {} session field values for attendance session {}", fields.size(), sessionId);
        return attendanceRepository.findSessionFields(sessionId);
    }

    // -- Entries --

    /**
     * Whether the member had joined the station by the evening the sheet is about.
     *
     * <p>A member entered afterwards was not there, so recording them would invent a presence and
     * would count towards the trial evenings of somebody who had not started. A member with no join
     * date carries no restriction: that is the state of every member entered before the field
     * existed, and refusing them would empty the sheets of every station that has not filled it in.
     *
     * @param sessionId the sheet being written
     * @param memberId  the member a row is wanted for
     * @return true where the member may be recorded on this sheet
     */
    /**
     * Whether the sheet is still open for writing, by its own state and the configured span.
     *
     * @param sessionId the sheet
     * @return true where anything about it may still be written
     */
    public boolean isSessionOpen(int sessionId) {
        return attendanceRepository
                .findSessionById(sessionId)
                .map(session -> session.isOpen(Instant.now(), attendanceConfig.freezeAfterDays()))
                .orElse(true);
    }

    /**
     * Refuses every write to a frozen sheet.
     *
     * <p>Guards all four ways of writing to one, because a sheet that refuses a status but takes a
     * check-out time is not frozen, it is confusing: the late corrections are exactly the times.
     *
     * @param sessionId the sheet being written to
     */
    private void requireSessionOpen(int sessionId) {
        if (!isSessionOpen(sessionId)) {
            throw new BadRequestResponse("This attendance sheet is closed and has to be reopened first");
        }
    }

    private void requireSessionOpenForEntry(int entryId) {
        attendanceRepository.findEntryById(entryId).ifPresent(entry -> requireSessionOpen(entry.sessionId()));
    }

    /**
     * Reopens a frozen sheet for another span of the configured length, and clears a deliberate
     * closing so that reopening always means something.
     *
     * @param sessionId the sheet to reopen
     * @return the sheet as it now stands
     */
    public Optional<AttendanceSession> unlockSession(int sessionId) {
        attendanceRepository.unlockSession(
                sessionId, Instant.now().plus(Duration.ofDays(attendanceConfig.freezeAfterDays())));
        log.info("Reopened attendance session {} for {} days", sessionId, attendanceConfig.freezeAfterDays());
        return attendanceRepository.findSessionById(sessionId);
    }

    /**
     * Closes a sheet now, whatever its age and whatever reopening was running.
     *
     * @param sessionId the sheet to close
     * @return the sheet as it now stands
     */
    public Optional<AttendanceSession> lockSession(int sessionId) {
        attendanceRepository.lockSession(sessionId, Instant.now());
        log.info("Closed attendance session {}", sessionId);
        return attendanceRepository.findSessionById(sessionId);
    }

    private boolean hadJoinedBySession(int sessionId, int memberId) {
        var joinDate = stationMemberRepository
                .findById(memberId)
                .map(StationMember::joinDate)
                .orElse(null);
        if (joinDate == null) return true;
        return !joinDate.isAfter(attendanceRepository
                .findSessionById(sessionId)
                .map(session -> LocalDate.ofInstant(session.startTime(), ZoneOffset.UTC))
                .orElse(LocalDate.MAX));
    }

    public List<AttendanceEntry> createEntry(int sessionId, int memberId, AttendanceEntry.EntrySource source) {
        requireSessionOpen(sessionId);
        if (!hadJoinedBySession(sessionId, memberId)) {
            throw new BadRequestResponse("The member had not joined the station on this date");
        }
        AttendanceEntry.AttendanceStatus status;
        if (attendanceRepository.isAbsent(memberId)) {
            status = AttendanceEntry.AttendanceStatus.DECLINED;
        } else if (isDeclinedForSession(sessionId, memberId)) {
            status = AttendanceEntry.AttendanceStatus.DECLINED;
        } else {
            status = AttendanceEntry.AttendanceStatus.UNCONFIRMED;
        }
        attendanceRepository.createEntry(sessionId, memberId, status, source);
        log.info(
                "Created attendance entry for member {} in session {} (status {}, source {})",
                memberId,
                sessionId,
                status,
                source);
        return attendanceRepository.findEntries(sessionId);
    }

    /**
     * Sets what an entry says about somebody, and announces the ones that say they were there.
     *
     * <p>Announced only on the change to present, so re-saving a sheet that already said so does
     * not count the same evening twice.
     */
    public boolean updateEntryStatus(int entryId, AttendanceEntry.AttendanceStatus status) {
        requireSessionOpenForEntry(entryId);
        var before = attendanceRepository.findEntryById(entryId).orElse(null);
        if (attendanceRepository.updateEntryStatus(entryId, status)) {
            log.info("Updated attendance entry {} status to {}", entryId, status);
            if (status == AttendanceEntry.AttendanceStatus.PRESENT
                    && before != null
                    && before.status() != AttendanceEntry.AttendanceStatus.PRESENT) {
                announcePresence(before);
            }
            return true;
        }
        log.warn("Cannot update attendance entry status: entry {} not found", entryId);
        return false;
    }

    /** Tells whoever cares that somebody was there, which is what feeds a trial period's count. */
    private void announcePresence(AttendanceEntry entry) {
        stationMemberRepository
                .findById(entry.memberId())
                .ifPresent(member -> eventBus.publish(
                        new AttendanceRecorded(member.stationId(), entry.memberId(), entry.sessionId())));
    }

    public boolean resetTimes(int entryId) {
        requireSessionOpenForEntry(entryId);
        if (attendanceRepository.resetTimes(entryId)) {
            log.info("Reset check-in/out times for attendance entry {}", entryId);
            return true;
        }
        log.warn("Cannot reset attendance entry times: entry {} not found", entryId);
        return false;
    }

    /**
     * Sync attendance entries from event registrations, absence data, and autoAttend template fields.
     * - Members of the template's groups → put on the sheet if they are not on it yet
     * - Answers on the event → written into the sheet fields they are tied to, where the sheet is empty
     * - ACCEPTED registrations → PRESENT (or ABSENT if member has active absence)
     * - DECLINED registrations → DECLINED
     * - Members with active absence who already have PRESENT status → updated to ABSENT
     * - Members from autoAttend fields → added as PRESENT at the end
     *
     * <p>Only the template's groups put anybody on a sheet. An answer given to the event settles what
     * an entry says, and an answer nobody gave settles nothing, so whom the event was open to has no
     * say here at all.
     */
    public List<AttendanceEntry> syncFromEvent(int sessionId) {
        var session = attendanceRepository.findSessionById(sessionId);
        if (session.isEmpty()) {
            log.warn("Cannot sync attendance from event: session {} not found", sessionId);
            return attendanceRepository.findEntries(sessionId);
        }

        var existingEntries = attendanceRepository.findEntries(sessionId);
        var existingMemberIds =
                existingEntries.stream().map(AttendanceEntry::memberId).collect(Collectors.toCollection(HashSet::new));

        var expected = expectedMembers(session.get().templateId());
        enterExpectedMembers(sessionId, expected, existingMemberIds);

        // Sync from event registrations
        if (session.get().eventId() != null) {
            int eventId = session.get().eventId();
            takeEventFieldValues(sessionId, eventId, true);
            LocalDate today = LocalDate.now();
            var registrations = eventRegistrationRepository.findByEventAndDate(eventId, today);

            for (var reg : registrations) {
                if (!expected.contains(reg.memberId())) continue;
                var status =
                        switch (reg.status()) {
                            case ACCEPTED -> AttendanceEntry.AttendanceStatus.PRESENT;
                            case DECLINED -> AttendanceEntry.AttendanceStatus.DECLINED;
                            default -> null;
                        };
                if (status == null) continue;
                if (status == AttendanceEntry.AttendanceStatus.PRESENT
                        && attendanceRepository.isAbsent(reg.memberId())) {
                    status = AttendanceEntry.AttendanceStatus.ABSENT;
                }
                var entry = attendanceRepository
                        .findEntry(sessionId, reg.memberId())
                        .orElse(null);
                if (entry == null) {
                    attendanceRepository.createEntry(
                            sessionId, reg.memberId(), status, AttendanceEntry.EntrySource.EXPECTED);
                    existingMemberIds.add(reg.memberId());
                } else if (entry.status() == AttendanceEntry.AttendanceStatus.UNCONFIRMED) {
                    // Only what nobody has decided yet: a mark taken on the day outlives the answer
                    attendanceRepository.updateEntryStatus(entry.id(), status);
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
                .collect(Collectors.toCollection(HashSet::new));

        for (var field : templateFieldsList) {
            if (!field.config().autoAttend()) continue;
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

        log.info("Synced attendance entries from event for session {}", sessionId);
        return attendanceRepository.findEntries(sessionId);
    }

    public boolean checkIn(int entryId, Instant time) {
        requireSessionOpenForEntry(entryId);
        if (attendanceRepository.checkIn(entryId, time)) {
            log.info("Checked in attendance entry {} at {}", entryId, time);
            return true;
        }
        log.warn("Cannot check in attendance entry: entry {} not found", entryId);
        return false;
    }

    public boolean checkOut(int entryId, Instant time) {
        requireSessionOpenForEntry(entryId);
        if (attendanceRepository.checkOut(entryId, time)) {
            log.info("Checked out attendance entry {} at {}", entryId, time);
            return true;
        }
        log.warn("Cannot check out attendance entry: entry {} not found", entryId);
        return false;
    }

    public boolean deleteEntry(int id) {
        if (attendanceRepository.deleteEntry(id)) {
            log.info("Deleted attendance entry {}", id);
            return true;
        }
        log.warn("Cannot delete attendance entry: entry {} not found", id);
        return false;
    }

    public MemberAbsence createAbsence(
            int memberId, LocalDate absentFrom, LocalDate absentUntil, String reason, Integer createdBy) {
        var absence = attendanceRepository.createAbsence(memberId, absentFrom, absentUntil, reason, createdBy);
        log.info("Created absence {} for member {} ({} - {})", absence.id(), memberId, absentFrom, absentUntil);
        return absence;
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

    public List<MemberAbsence> findAbsencesByStationOnDate(int stationId, LocalDate date) {
        return attendanceRepository.findAbsencesByStationOnDate(stationId, date);
    }

    public boolean deleteAbsence(int id) {
        if (attendanceRepository.deleteAbsence(id)) {
            log.info("Deleted absence {}", id);
            return true;
        }
        log.warn("Cannot delete absence: absence {} not found", id);
        return false;
    }

    /**
     * Checks if a member has declined the event linked to this session.
     */
    private boolean isDeclinedForSession(int sessionId, int memberId) {
        var session = attendanceRepository.findSessionById(sessionId);
        if (session.isEmpty() || session.get().eventId() == null) return false;

        LocalDate today = LocalDate.now();
        return eventRegistrationRepository
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
}
