/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.conf.file.elements.Attendance;
import dev.chojo.ember.feature.attendance.entity.AttendanceEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldConfig;
import dev.chojo.ember.feature.attendance.entity.AttendanceFieldValueEntry;
import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.AttendanceSessionField;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplate;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.entity.SessionAudience;
import dev.chojo.ember.feature.attendance.entity.SessionSummary;
import dev.chojo.ember.feature.attendance.entity.TemplateGroup;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventDateCancellationRepository;
import dev.chojo.ember.feature.events.repository.EventFieldDefaultRepository;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.MemberAbsence;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.StationFormat;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.util.Json;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private static final ObjectMapper JSON = Json.MAPPER;
    /** How long a sheet runs where nothing and nobody says otherwise. */
    private static final Duration DEFAULT_SESSION_LENGTH = Duration.ofHours(2);
    /** The longest span a sheet may cover, which is longer than any camp and shorter than a typo. */
    private static final Duration MAX_SESSION_LENGTH = Duration.ofDays(31);
    /** The most a sheet may count as, read the same way as the longest span it may cover. */
    private static final int MAX_COUNTED_MINUTES = (int) MAX_SESSION_LENGTH.toMinutes();

    private final AttendanceRepository attendanceRepository;
    private final EventRepository eventRepository;
    private final EventFieldRepository eventFieldRepository;
    private final EventFieldDefaultRepository eventFieldDefaultRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final Attendance attendanceConfig;
    private final StationRepository stationRepository;
    private final EventDateCancellationRepository cancellationRepository;
    private final AttendanceAudienceService audienceService;
    private final MemberEligibility memberEligibility;

    @Inject
    public AttendanceService(
            AttendanceRepository attendanceRepository,
            EventRepository eventRepository,
            EventFieldRepository eventFieldRepository,
            EventFieldDefaultRepository eventFieldDefaultRepository,
            EventRegistrationRepository eventRegistrationRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            Attendance attendanceConfig,
            StationRepository stationRepository,
            EventDateCancellationRepository cancellationRepository,
            AttendanceAudienceService audienceService,
            MemberEligibility memberEligibility) {
        this.memberEligibility = memberEligibility;
        this.cancellationRepository = cancellationRepository;
        this.audienceService = audienceService;
        this.attendanceRepository = attendanceRepository;
        this.eventRepository = eventRepository;
        this.eventFieldRepository = eventFieldRepository;
        this.eventFieldDefaultRepository = eventFieldDefaultRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.attendanceConfig = attendanceConfig;
        this.stationRepository = stationRepository;
    }

    /**
     * The day a sheet for an appointment is taken for: a one-time appointment's own day, the day a
     * caller named for a series, and otherwise the day of the start asked for, or of now.
     */
    private static LocalDate sheetDay(StationEvent event, LocalDate eventDate, Instant startTime, ZoneId zone) {
        if (!event.isRecurring() && event.startTime() != null) {
            return event.startTime().atZone(zone).toLocalDate();
        }
        if (eventDate != null) return eventDate;
        return (startTime != null ? startTime : Instant.now()).atZone(zone).toLocalDate();
    }

    /**
     * Refuses a sheet for a date that was called off, on its own or with its whole series: nobody is
     * coming, so there is nobody to count.
     */
    private void requireNotCancelled(StationEvent event, LocalDate day) {
        if (event.cancelled() || cancellationRepository.isCancelled(event.id(), day)) {
            throw EventRefusal.ATTENDANCE_DAY_CANCELLED.raise();
        }
    }

    public Set<Integer> findManagedMemberIds(int managerId) {
        return stationMemberRepository.findManaged(managerId).stream()
                .map(StationMember::id)
                .collect(Collectors.toSet());
    }

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

    public List<TemplateGroup> findTemplateGroups(int templateId) {
        return attendanceRepository.findTemplateGroups(templateId);
    }

    public void setTemplateGroups(int templateId, List<TemplateGroup> groups) {
        attendanceRepository.setTemplateGroups(templateId, groups);
        log.info("Set {} template groups for attendance template {}", groups.size(), templateId);
    }

    public Set<StationUserType> findTemplateUserTypes(int templateId) {
        return attendanceRepository.findTemplateUserTypes(templateId);
    }

    /**
     * Replaces the user types a template expects besides the members of its groups.
     *
     * @param templateId the template
     * @param userTypes  the user types, null or empty to expect nobody by type
     * @return the user types the template now expects
     */
    public Set<StationUserType> setTemplateUserTypes(int templateId, Collection<StationUserType> userTypes) {
        Set<StationUserType> chosen = EnumSet.noneOf(StationUserType.class);
        if (userTypes != null) chosen.addAll(userTypes);
        attendanceRepository.setTemplateUserTypes(templateId, chosen);
        log.info("Set {} template user types for attendance template {}", chosen.size(), templateId);
        return attendanceRepository.findTemplateUserTypes(templateId);
    }

    /** Whom a sheet expects, as {@link AttendanceAudienceService#audienceOf} answers it. */
    public SessionAudience audienceOf(AttendanceSession session) {
        return audienceService.audienceOf(session);
    }

    public List<AttendanceTemplateField> createTemplateField(
            int templateId, String name, FieldType fieldType, AttendanceFieldConfig config, int position) {
        requireUsable(name, fieldType, config);
        attendanceRepository.createTemplateField(templateId, name, fieldType, config, position);
        log.info("Created attendance template field for template {} (type {})", templateId, fieldType);
        return attendanceRepository.findTemplateFields(templateId);
    }

    public Optional<List<AttendanceTemplateField>> updateTemplateField(
            int templateId, int fieldId, String name, FieldType fieldType, AttendanceFieldConfig config, int position) {
        requireUsable(name, fieldType, config);
        if (attendanceRepository.updateTemplateField(fieldId, name, fieldType, config, position)) {
            log.info("Updated attendance template field {} for template {}", fieldId, templateId);
            return Optional.of(attendanceRepository.findTemplateFields(templateId));
        }
        log.warn("Cannot update attendance template field: field {} not found", fieldId);
        return Optional.empty();
    }

    /**
     * Refuses a field of a type a sheet does not offer, or one set up to start from a value it would
     * then refuse as an answer.
     *
     * @throws io.javalin.http.HttpResponseException {@link AttendanceRefusal#ATTENDANCE_FIELD_TYPE_NOT_OFFERED}
     * @throws io.javalin.http.HttpResponseException {@link AttendanceRefusal#ATTENDANCE_FIELD_DEFAULT_NOT_ACCEPTED}
     *                                               naming the field and what is wrong with its
     *                                               starting value
     */
    private void requireUsable(String name, FieldType fieldType, AttendanceFieldConfig config) {
        if (!FieldTypes.ATTENDANCE.contains(fieldType))
            throw AttendanceRefusal.ATTENDANCE_FIELD_TYPE_NOT_OFFERED.raise();
        var field = new AttendanceTemplateField(0, 0, name, fieldType, config, 0);
        QuestionCheck.defaultValue(field.question()).ifPresent(problem -> {
            throw AttendanceRefusal.ATTENDANCE_FIELD_DEFAULT_NOT_ACCEPTED.raise(problem.message());
        });
    }

    public Optional<List<AttendanceTemplateField>> deleteTemplateField(int templateId, int fieldId) {
        if (attendanceRepository.deleteTemplateField(fieldId)) {
            log.info("Deleted attendance template field {} from template {}", fieldId, templateId);
            return Optional.of(attendanceRepository.findTemplateFields(templateId));
        }
        log.warn("Cannot delete attendance template field: field {} not found", fieldId);
        return Optional.empty();
    }

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

    /**
     * The sheet an appointment has on one of its days, where somebody has already opened one.
     *
     * @param eventId the appointment
     * @param day     the day to look on, null for the station's today
     * @return the sheet, or empty where that day has none
     */
    public Optional<AttendanceSession> findSessionForEvent(int eventId, @Nullable LocalDate day) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Optional.empty();
        var zone = timezoneOf(event.stationId());
        LocalDate on = day != null ? day : LocalDate.now(zone);
        return sheetOfDay(eventId, on.atStartOfDay(zone).toInstant(), zone);
    }

    /**
     * The sheet an appointment already has on the day a new one would run.
     *
     * <p>A repeating appointment is one row that comes round again and again, so its sheets are told
     * apart by their day: asked for the appointment alone, the first date's sheet came back for
     * every date after it.
     */
    private Optional<AttendanceSession> sheetOfDay(int eventId, Instant start, ZoneId zone) {
        LocalDate day = start.atZone(zone).toLocalDate();
        return attendanceRepository.findSessionByEventOnDay(
                eventId,
                day.atStartOfDay(zone).toInstant(),
                day.plusDays(1).atStartOfDay(zone).toInstant());
    }

    /**
     * Opens a sheet, or hands back the one an appointment already has.
     *
     * <p>What the caller sends decides the time frame. Where it sends none, an appointment's own
     * times stand in, and where there is no appointment either the sheet begins now and runs for
     * {@link #DEFAULT_SESSION_LENGTH}. A sheet of no length was the older answer, and it counted
     * everybody who was there for nothing.
     *
     * @param templateId     the sheet's template
     * @param startTime      when it begins, null to let the appointment or the clock decide
     * @param endTime        when it ends, null for the same reason
     * @param eventId        the appointment behind it, null where it stands on its own
     * @param title          what it is called, null to take the appointment's or the template's name
     * @param countedMinutes what a whole presence counts as, null to let the times decide
     * @return the sheet
     * @throws io.javalin.http.HttpResponseException where the span or the counted minutes cannot be used
     */
    public AttendanceSession createSession(
            int templateId,
            Instant startTime,
            Instant endTime,
            @Nullable Integer eventId,
            @Nullable String title,
            @Nullable Integer countedMinutes) {
        return createSession(templateId, startTime, endTime, eventId, title, countedMinutes, null, null);
    }

    public AttendanceSession createSession(
            int templateId,
            Instant startTime,
            Instant endTime,
            @Nullable Integer eventId,
            @Nullable String title,
            @Nullable Integer countedMinutes,
            @Nullable SessionAudience audience) {
        return createSession(templateId, startTime, endTime, eventId, title, countedMinutes, audience, null);
    }

    /**
     * Opens a sheet. Its fields are seeded in rising precedence: the template's starting answers,
     * then the appointment's field defaults, then the appointment's own answers.
     *
     * @param audience whom to enter instead of the template's own user types and groups, kept with the
     *     sheet; null or naming nobody where the template decides
     * @param eventDate which day of a repeating appointment the sheet is for, null where the sheet
     *     stands on its own or the caller already worked the times out. A series is one row that
     *     comes round again and again, so without this a sheet taken for next Tuesday would be
     *     written for today, which is the day the caller happened to ask on
     */
    public AttendanceSession createSession(
            int templateId,
            Instant startTime,
            Instant endTime,
            @Nullable Integer eventId,
            @Nullable String title,
            @Nullable Integer countedMinutes,
            @Nullable SessionAudience audience,
            @Nullable LocalDate eventDate) {
        requireUsableSpan(startTime, endTime);
        requireUsableCountedMinutes(countedMinutes);
        String resolvedTitle = title;
        Instant resolvedStart = startTime;
        Instant resolvedEnd = endTime;
        ZoneId zone = ZoneId.systemDefault();
        if (eventId != null) {
            var event = eventRepository.findById(eventId).orElse(null);
            if (event != null) {
                zone = timezoneOf(event.stationId());
                if (resolvedTitle == null || resolvedTitle.isBlank()) {
                    resolvedTitle = event.name();
                }
                LocalDate day = sheetDay(event, eventDate, startTime, zone);
                requireNotCancelled(event, day);
                if (resolvedStart == null || resolvedEnd == null) {
                    var span = event.occurrenceOn(day);
                    if (span.isPresent()) {
                        if (resolvedStart == null) resolvedStart = span.get().start();
                        if (resolvedEnd == null) resolvedEnd = span.get().end();
                    }
                }
            }
        }
        if (resolvedTitle == null || resolvedTitle.isBlank()) {
            var template = attendanceRepository.findTemplateById(templateId);
            if (template.isPresent()) resolvedTitle = template.get().name();
        }

        if (resolvedStart == null) resolvedStart = Instant.now();
        if (resolvedEnd == null || !resolvedEnd.isAfter(resolvedStart)) {
            resolvedEnd = resolvedStart.plus(DEFAULT_SESSION_LENGTH);
        }

        if (eventId != null) {
            var existing = sheetOfDay(eventId, resolvedStart, zone);
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        var session = attendanceRepository.createSession(
                templateId, resolvedStart, resolvedEnd, eventId, resolvedTitle, countedMinutes);
        log.info("Created attendance session {} for template {} (event {})", session.id(), templateId, eventId);
        var templateFields = fieldsById(templateId);
        for (var field : templateFields.values()) {
            String starting = field.config().startingAnswer();
            if (starting != null) writeSessionField(session.id(), field, starting);
        }
        if (eventId != null) {
            var defaults = eventFieldDefaultRepository.findByEvent(eventId);
            if (!defaults.isEmpty()) {
                var event = eventRepository.findById(eventId).orElse(null);
                if (event != null) {
                    for (var def : defaults) {
                        var field = templateFields.get(def.fieldId());
                        if (field == null) continue;
                        String resolved =
                                switch (def.source()) {
                                    case "VALUE" -> def.value();
                                    case "EVENT_NAME" -> event.name();
                                    case "EVENT_DESCRIPTION" -> event.description();
                                    case "EVENT_START_TIME" -> textOf(event.startTime());
                                    case "EVENT_END_TIME" -> textOf(event.endTime());
                                    default -> null;
                                };
                        if (resolved != null) writeSessionField(session.id(), field, resolved);
                    }
                }
            }
        }
        if (eventId != null) takeEventFieldValues(session.id(), eventId, dayOf(session), false);

        keepAudience(session, audience);
        var expected = expectedFor(templateId, audience);
        enterExpectedMembers(session.id(), expected, new HashSet<>());
        if (eventId != null) applyRegistrations(session.id(), eventId, expected);
        enterMembersNamedInAutoAttendFields(session.id(), templateId);

        return session;
    }

    /**
     * Writes what the appointment answered into the sheet fields its questions are tied to.
     *
     * <p>Taken again whenever the sheet is filled in from its appointment, because the answer is
     * often given after the sheet was opened: whoever runs the appointment enters it there, and
     * until then there was nothing to carry over.
     *
     * @param sessionId        the sheet being filled
     * @param eventId          the appointment it was made from
     * @param date             the date the sheet covers, which is the date whose answers it takes
     * @param keepWhatIsFilled leaves a field that already says something alone, which is what filling
     *                         an open sheet in wants: what stands on it was written by somebody
     *                         looking at the occurrence itself, and the appointment must not undo that
     */
    private void takeEventFieldValues(int sessionId, int eventId, LocalDate date, boolean keepWhatIsFilled) {
        Set<Integer> filled = keepWhatIsFilled
                ? attendanceRepository.findSessionFields(sessionId).stream()
                        .filter(field -> !QuestionValues.read(field.value()).isEmpty())
                        .map(AttendanceSessionField::fieldId)
                        .collect(Collectors.toSet())
                : Set.of();
        var sheetFields = attendanceRepository
                .findSessionById(sessionId)
                .map(session -> fieldsById(session.templateId()))
                .orElse(Map.of());
        for (var field : eventFieldRepository.findByEventOn(eventId, date)) {
            Integer attendanceFieldId = field.attendanceFieldId();
            if (attendanceFieldId == null) continue;
            if (QuestionValues.read(field.value()).isEmpty()) continue;
            if (filled.contains(attendanceFieldId)) continue;
            var sheetField = sheetFields.get(attendanceFieldId);
            if (sheetField == null) continue;
            writeSessionField(sessionId, sheetField, QuestionValues.read(field.value()));
        }
    }

    /** The fields of a template, by id. */
    private Map<Integer, AttendanceTemplateField> fieldsById(int templateId) {
        return attendanceRepository.findTemplateFields(templateId).stream()
                .collect(Collectors.toMap(AttendanceTemplateField::id, field -> field));
    }

    /**
     * Writes one answer onto a sheet in the one shape its field type is kept in, and clears the
     * field where the answer says nothing.
     *
     * @param sessionId the sheet
     * @param field     the field answered
     * @param answer    the answer as plain text
     */
    private void writeSessionField(int sessionId, AttendanceTemplateField field, @Nullable String answer) {
        var stored = QuestionValues.write(field.fieldType(), answer);
        if (stored == null) {
            attendanceRepository.deleteSessionField(sessionId, field.id());
            return;
        }
        attendanceRepository.setSessionField(sessionId, field.id(), stored.toString());
    }

    /** A value as the plain text an answer holds, written the way the server writes it to JSON. */
    private static @Nullable String textOf(@Nullable Object value) {
        if (value == null) return null;
        try {
            return QuestionValues.read(JSON.writeValueAsString(value));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Whom a new sheet expects: what it was told, or its template's user types and groups when it was
     * told nothing.
     *
     * <p>The sheet and its template are the only things that decide who belongs on it. An event the
     * sheet was made from asks its own question of its own people, and whom it was open to says
     * nothing about who is expected at the appointment itself.
     */
    private Set<Integer> expectedFor(int templateId, SessionAudience audience) {
        var decisive =
                audience == null || audience.namesNobody() ? audienceService.templateAudience(templateId) : audience;
        return attendanceRepository
                .findTemplateById(templateId)
                .map(template -> chosenMembers(template.stationId(), decisive))
                .orElseGet(Set::of);
    }

    private Set<Integer> groupIdsOf(int stationId) {
        return memberGroupRepository.findByStation(stationId).stream()
                .map(MemberGroup::id)
                .collect(Collectors.toSet());
    }

    /**
     * Writes down whom a new sheet was told to expect, so that filling it in later, showing it and
     * printing it go by the same people. Only groups of the sheet's own station are kept, the same
     * ones {@link #chosenMembers} reads.
     *
     * @param session  the new sheet
     * @param audience what it was told, null or naming nobody where its template decides
     */
    private void keepAudience(AttendanceSession session, SessionAudience audience) {
        if (audience == null || audience.namesNobody()) return;
        var stationId =
                attendanceRepository.findTemplateById(session.templateId()).map(AttendanceTemplate::stationId);
        if (stationId.isEmpty()) return;
        Set<Integer> ofThisStation = groupIdsOf(stationId.get());
        var kept = new SessionAudience(
                audience.userTypes(),
                audience.groupIds().stream().filter(ofThisStation::contains).toList());
        if (!kept.namesNobody()) attendanceRepository.setSessionAudience(session.id(), kept);
    }

    /**
     * Whom a sheet was told to expect. The two answers add up and name nobody twice, groups first so
     * that the entries read like a template's. A group of another station is dropped rather than
     * refused, since a sheet is not the place to say what another station keeps.
     *
     * @param stationId the station the sheet belongs to, which bounds both answers
     * @param audience  the types and groups chosen for the sheet, or those of its template
     * @return the members the two answers name, without those who have left
     */
    private Set<Integer> chosenMembers(int stationId, SessionAudience audience) {
        Set<Integer> ofThisStation = groupIdsOf(stationId);
        Set<Integer> expected = new LinkedHashSet<>();
        for (int groupId : audience.groupIds()) {
            if (!ofThisStation.contains(groupId)) continue;
            for (var member : memberGroupRepository.findMembers(groupId)) {
                if (!member.former()) expected.add(member.id());
            }
        }
        if (audience.userTypes().isEmpty()) return expected;
        for (var member : stationMemberRepository.findByStation(stationId)) {
            if (!member.former() && audience.userTypes().contains(member.userType())) expected.add(member.id());
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
        var sessionDate = dateOf(sessionId);
        for (int memberId : expected) {
            if (!alreadyEntered.add(memberId)) continue;
            if (!hadJoinedBy(sessionDate, memberId)) continue;
            attendanceRepository.createEntry(
                    sessionId,
                    memberId,
                    attendanceRepository.isAbsent(memberId)
                            ? AttendanceEntry.AttendanceStatus.DECLINED
                            : AttendanceEntry.AttendanceStatus.UNCONFIRMED,
                    AttendanceEntry.EntrySource.EXPECTED);
        }
    }

    /**
     * Writes what the appointment's answers make of the sheet.
     *
     * <p>Read for the day the sheet is about rather than for today, because a repeating appointment
     * is answered per occurrence: a sheet opened for another date was filled from the answers to
     * a different one.
     *
     * <p>Only a row nobody has decided yet is written, so a mark taken during the appointment outlives
     * the answer given before it.
     *
     * @param sessionId the sheet being filled
     * @param eventId   the appointment it was made from
     * @param expected  whom the sheet expects, which bounds whose answer is read
     */
    private void applyRegistrations(int sessionId, int eventId, Set<Integer> expected) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return;
        var sessionDate = dateOf(sessionId);
        var answers = eventRegistrationRepository.findByEventAndDate(eventId, sessionDate).stream()
                .collect(Collectors.toMap(
                        EventRegistration::memberId, EventRegistration::status, (earlier, later) -> later));

        for (int memberId : expected) {
            var status = attendanceFor(answers.get(memberId), event.requiresRegistration());
            if (status == null) continue;
            if (status == AttendanceEntry.AttendanceStatus.UNCONFIRMED && attendanceRepository.isAbsent(memberId)) {
                status = AttendanceEntry.AttendanceStatus.ABSENT;
            }
            var entry = attendanceRepository.findEntry(sessionId, memberId).orElse(null);
            if (entry == null) {
                if (!hadJoinedBy(sessionDate, memberId)) continue;
                attendanceRepository.createEntry(sessionId, memberId, status, AttendanceEntry.EntrySource.EXPECTED);
            } else if (entry.status() == AttendanceEntry.AttendanceStatus.UNCONFIRMED
                    && status != AttendanceEntry.AttendanceStatus.UNCONFIRMED) {
                attendanceRepository.updateEntryStatus(entry.id(), status);
            }
        }
    }

    /**
     * What an answer to the appointment makes of a row on the sheet.
     *
     * <p>Accepting puts somebody on the sheet and leaves their row open. Saying yes beforehand is not
     * having been there, and a sheet that marks every yes present has nothing left to check.
     *
     * <p>Where the appointment demanded an answer, everybody who did not accept is declined: whether
     * they said no, took it back, were refused, are still waiting or never answered at all, the
     * appointment already knows they are not coming. Leaving those rows open is what made a prepared
     * sheet no better than an empty one, since whoever ran the appointment had to look every silence
     * up on it.
     *
     * <p>Where no answer was demanded, silence settles nothing and only what was answered is written.
     *
     * @param answer   what the member answered, null where they never did
     * @param demanded whether the appointment asked everybody to answer
     * @return the status to write, or null to leave the row as it stands
     */
    private static AttendanceEntry.@Nullable AttendanceStatus attendanceFor(
            @Nullable RegistrationStatus answer, boolean demanded) {
        if (answer == RegistrationStatus.ACCEPTED) return AttendanceEntry.AttendanceStatus.UNCONFIRMED;
        if (answer == RegistrationStatus.DECLINED || answer == RegistrationStatus.WITHDRAWN) {
            return AttendanceEntry.AttendanceStatus.DECLINED;
        }
        return demanded ? AttendanceEntry.AttendanceStatus.DECLINED : null;
    }

    /**
     * Writes a sheet's own time frame, its title and what it counts as.
     *
     * <p>The times are the sheet's own from the moment it is made: an appointment hands its times
     * down once, and correcting them afterwards is nobody's business but the sheet's.
     *
     * @param id             the sheet
     * @param startTime      when it begins
     * @param endTime        when it ends, which may be on another day
     * @param title          what it is called
     * @param countedMinutes what a whole presence counts as, null to let the times decide
     * @return the sheet as it now stands, empty where there is none
     * @throws io.javalin.http.HttpResponseException where the span or the counted minutes cannot be used
     */
    public Optional<AttendanceSession> updateSession(
            int id, Instant startTime, Instant endTime, @Nullable String title, @Nullable Integer countedMinutes) {
        requireUsableSpan(startTime, endTime);
        requireUsableCountedMinutes(countedMinutes);
        if (attendanceRepository.updateSession(id, startTime, endTime, title, countedMinutes)) {
            log.info("Updated attendance session {}", id);
            return attendanceRepository.findSessionById(id);
        }
        log.warn("Cannot update attendance session: session {} not found", id);
        return Optional.empty();
    }

    /**
     * Refuses a time frame nothing could have happened in.
     *
     * <p>A sheet may run over several days, which is what a camp is, so only the two ends that make
     * no sense are refused: one that finishes before it starts, and one so long that it is a typed
     * year rather than an occasion.
     *
     * @param startTime when the sheet begins, null where the caller left it to us
     * @param endTime   when it ends, read the same way
     * @throws io.javalin.http.HttpResponseException naming what is wrong with the span
     */
    private void requireUsableSpan(Instant startTime, Instant endTime) {
        if (startTime == null || endTime == null) return;
        if (!endTime.isAfter(startTime)) {
            throw AttendanceRefusal.ATTENDANCE_SHEET_ENDS_BEFORE_IT_STARTS.raise();
        }
        if (Duration.between(startTime, endTime).compareTo(MAX_SESSION_LENGTH) > 0) {
            throw AttendanceRefusal.ATTENDANCE_SHEET_TOO_LONG.raise(MAX_SESSION_LENGTH.toDays() + " days");
        }
    }

    /**
     * The clock a station keeps its days by.
     *
     * <p>Which day it is has to be asked of the station and not of the server: a repeating appointment
     * is placed on the day it is opened for, and a server an hour or two behind is still on yesterday
     * late in the evening. A sheet opened then was placed on the occurrence before the one everybody
     * had turned up for.
     *
     * @param stationId the station whose day is meant
     * @return its timezone, UTC where it keeps none
     */
    /**
     * The day a sheet covers, which is the day its own start falls on where the station stands.
     *
     * <p>A sheet has no date of its own, and the appointment it was made from may fall on many, so
     * the answers it takes have to be the answers of this day rather than of the series.
     */
    private LocalDate dayOf(AttendanceSession session) {
        Integer eventId = session.eventId();
        var event = eventId == null ? null : eventRepository.findById(eventId).orElse(null);
        var zone = event == null ? ZoneId.systemDefault() : timezoneOf(event.stationId());
        return session.startTime().atZone(zone).toLocalDate();
    }

    private ZoneId timezoneOf(int stationId) {
        return StationFormat.timezoneOf(stationRepository.findById(stationId).orElse(null));
    }

    /**
     * Refuses a number of counted minutes that could not be worth anybody's presence.
     *
     * @param countedMinutes what a whole presence counts as, null where the times decide
     * @throws io.javalin.http.HttpResponseException naming what is wrong with the number
     */
    private void requireUsableCountedMinutes(@Nullable Integer countedMinutes) {
        if (countedMinutes == null) return;
        if (countedMinutes < 0) {
            throw AttendanceRefusal.ATTENDANCE_COUNTED_HOURS_NEGATIVE.raise();
        }
        if (countedMinutes > MAX_COUNTED_MINUTES) {
            throw AttendanceRefusal.ATTENDANCE_COUNTED_HOURS_TOO_MANY.raise(MAX_COUNTED_MINUTES / 60 + " hours");
        }
    }

    public boolean deleteSession(int id) {
        if (attendanceRepository.deleteSession(id)) {
            log.info("Deleted attendance session {}", id);
            return true;
        }
        log.warn("Cannot delete attendance session: session {} not found", id);
        return false;
    }

    /**
     * Writes what a sheet says in its own fields.
     *
     * <p>What is written is measured against the question it answers, including the group a member
     * field is narrowed to, and what is left blank is not: a sheet is filled in through the
     * appointment and saved as it goes, so demanding every required answer at every save would refuse
     * the sheet itself. Every answer is kept in the one shape its type is kept in, and a blank one
     * clears the field.
     *
     * @throws io.javalin.http.HttpResponseException naming the field and what is wrong with the answer
     */
    public List<AttendanceSessionField> setSessionFields(int sessionId, List<AttendanceFieldValueEntry> fields) {
        requireSessionOpen(sessionId);
        var sheetFields = attendanceRepository
                .findSessionById(sessionId)
                .map(session -> fieldsById(session.templateId()))
                .orElse(Map.of());
        for (var entry : fields) {
            var field = sheetFields.get(entry.fieldId());
            if (field == null) continue;
            QuestionCheck.answerIfGiven(field.question(), QuestionValues.read(entry.value()), memberEligibility)
                    .ifPresent(problem -> {
                        throw AttendanceRefusal.ATTENDANCE_SHEET_ANSWER_NOT_ACCEPTED.raise(problem.message());
                    });
        }
        for (var entry : fields) {
            var field = sheetFields.get(entry.fieldId());
            if (field != null) writeSessionField(sessionId, field, QuestionValues.read(entry.value()));
        }
        log.info("Set {} session field values for attendance session {}", fields.size(), sessionId);
        return attendanceRepository.findSessionFields(sessionId);
    }

    /**
     * Whether the member had joined the station by the date the sheet is about.
     *
     * <p>A member entered afterwards was not there, so recording them would invent a presence and
     * would count towards the trial appointments of somebody who had not started. A member with no
     * join date carries no restriction: that is the state of every member entered before the field
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
            throw AttendanceRefusal.ATTENDANCE_SHEET_CLOSED.raise();
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

    /**
     * The day a sheet is about, as a date.
     *
     * <p>Read in the station's own timezone, which is how the report already decides which day and
     * which month a sheet belongs to. Reading it anywhere else lets the two disagree over a sheet
     * that starts near midnight, and a member left off a sheet the report still counts them on is
     * worse than either answer on its own.
     *
     * @param sessionId the sheet
     * @return its date, or the furthest date there is where the sheet is unknown, so nobody is
     *     refused on the strength of a sheet that is not there
     */
    private LocalDate dateOf(int sessionId) {
        return attendanceRepository
                .findSessionById(sessionId)
                .map(session -> session.startTime()
                        .atZone(StationFormat.timezoneOf(attendanceRepository
                                .findTemplateById(session.templateId())
                                .flatMap(template -> stationRepository.findById(template.stationId()))
                                .orElse(null)))
                        .toLocalDate())
                .orElse(LocalDate.MAX);
    }

    /**
     * Whether the member had joined the station by the given date.
     *
     * <p>Takes the date rather than the sheet so that filling a whole sheet in reads the station and
     * its timezone once instead of once a member.
     */
    private boolean hadJoinedBy(LocalDate sessionDate, int memberId) {
        var joinDate = stationMemberRepository
                .findById(memberId)
                .map(StationMember::joinDate)
                .orElse(null);
        return joinDate == null || !joinDate.isAfter(sessionDate);
    }

    public List<AttendanceEntry> createEntry(int sessionId, int memberId, AttendanceEntry.EntrySource source) {
        requireSessionOpen(sessionId);
        if (!hadJoinedBy(dateOf(sessionId), memberId)) {
            throw AttendanceRefusal.ATTENDANCE_MEMBER_NOT_YET_JOINED.raise();
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

    /** Sets what an entry says about somebody. */
    public boolean updateEntryStatus(int entryId, AttendanceEntry.AttendanceStatus status) {
        requireSessionOpenForEntry(entryId);
        if (attendanceRepository.updateEntryStatus(entryId, status)) {
            log.info("Updated attendance entry {} status to {}", entryId, status);
            return true;
        }
        log.warn("Cannot update attendance entry status: entry {} not found", entryId);
        return false;
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
     * - Whom the sheet expects (its own audience, or its template's) → put on the sheet if not on it yet
     * - Answers on the event → written into the sheet fields they are tied to, where the sheet is empty
     * - ACCEPTED registrations → on the sheet and still to be checked (or ABSENT if member has active absence)
     * - Anything else, where the event asked everybody to answer → DECLINED
     * - Members with active absence who already have PRESENT status → updated to ABSENT
     * - Members from autoAttend fields → added as PRESENT at the end
     *
     * <p>Only the sheet's audience puts anybody on it, so whom the event was open to has no say here
     * at all.
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

        var expected = expectedFor(session.get().templateId(), audienceOf(session.get()));
        enterExpectedMembers(sessionId, expected, existingMemberIds);

        Integer sessionEventId = session.get().eventId();
        if (sessionEventId != null) {
            int eventId = sessionEventId;
            takeEventFieldValues(sessionId, eventId, dayOf(session.get()), true);
            applyRegistrations(sessionId, eventId, expected);
        }

        existingEntries = attendanceRepository.findEntries(sessionId);
        for (var entry : existingEntries) {
            if ((entry.status() == AttendanceEntry.AttendanceStatus.PRESENT
                            || entry.status() == AttendanceEntry.AttendanceStatus.UNCONFIRMED)
                    && attendanceRepository.isAbsent(entry.memberId())) {
                attendanceRepository.updateEntryStatus(entry.id(), AttendanceEntry.AttendanceStatus.ABSENT);
            }
        }

        enterMembersNamedInAutoAttendFields(sessionId, session.get().templateId());

        log.info("Synced attendance entries from event for session {}", sessionId);
        return attendanceRepository.findEntries(sessionId);
    }

    /**
     * Marks everybody named in a field that attends by itself as present.
     *
     * <p>A field marked that way says that whoever stands in it was there: the leader of the
     * appointment, the people on the equipment, whoever the sheet names in that way. Somebody
     * already on the sheet is moved to present rather than entered a second time.
     *
     * <p>Run when the sheet is opened as well as when it is filled in from its appointment, because
     * an appointment's answer is carried into such a field the moment the sheet is made. Only
     * filling it in later did the naming, and until somebody pressed that the people named stood in
     * the field with no row on the sheet at all.
     *
     * @param sessionId  the sheet being written
     * @param templateId the template it was made from
     */
    private void enterMembersNamedInAutoAttendFields(int sessionId, int templateId) {
        var values = attendanceRepository.findSessionFields(sessionId).stream()
                .collect(Collectors.toMap(
                        AttendanceSessionField::fieldId, field -> field.value() != null ? field.value() : ""));
        var entered = attendanceRepository.findEntries(sessionId).stream()
                .map(AttendanceEntry::memberId)
                .collect(Collectors.toCollection(HashSet::new));

        for (var field : attendanceRepository.findTemplateFields(templateId)) {
            if (!field.config().autoAttend()) continue;
            String value = values.getOrDefault(field.id(), "");
            if (value.isBlank()) continue;

            for (int memberId : QuestionValues.memberIds(value)) {
                if (entered.contains(memberId)) {
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
                entered.add(memberId);
            }
        }
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
            int memberId,
            LocalDate absentFrom,
            LocalDate absentUntil,
            @Nullable String reason,
            @Nullable Integer createdBy) {
        var absence = attendanceRepository.createAbsence(memberId, absentFrom, absentUntil, reason, createdBy);
        log.info("Created absence {} for member {} ({} - {})", absence.id(), memberId, absentFrom, absentUntil);
        return absence;
    }

    public Optional<MemberAbsence> findAbsenceById(int id) {
        return attendanceRepository.findAbsenceById(id);
    }

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
     * Whether the member said they were not coming to the appointment the sheet is about.
     *
     * <p>Asked of the sheet's own day, since a repeating appointment is answered once per occurrence.
     * Silence is not an answer here: somebody entered by hand is entered because they turned up.
     */
    private boolean isDeclinedForSession(int sessionId, int memberId) {
        var session = attendanceRepository.findSessionById(sessionId);
        Integer eventId = session.map(AttendanceSession::eventId).orElse(null);
        if (eventId == null) return false;

        return eventRegistrationRepository
                .findNotAttendingMemberIds(eventId, dateOf(sessionId))
                .contains(memberId);
    }
}
