/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionValues;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class EventFieldService {
    private static final Logger log = LoggerFactory.getLogger(EventFieldService.class);

    private final EventFieldRepository repository;
    private final StationMemberRepository memberRepository;
    private final MemberGroupRepository groupRepository;
    private final UserTagService tagService;
    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventFieldRegistrationService fieldRegistrationService;

    @Inject
    public EventFieldService(
            EventFieldRepository repository,
            StationMemberRepository memberRepository,
            MemberGroupRepository groupRepository,
            UserTagService tagService,
            EventRepository eventRepository,
            AttendanceRepository attendanceRepository,
            EventFieldRegistrationService fieldRegistrationService) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.groupRepository = groupRepository;
        this.tagService = tagService;
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.fieldRegistrationService = fieldRegistrationService;
    }

    public List<String> findDistinctFieldNames(int stationId) {
        return repository.findDistinctFieldNames(stationId);
    }

    public List<EventField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    /**
     * The questions of an appointment as they stand on one of its dates, which is the only way a
     * question answered per date can be read.
     *
     * @param eventId the appointment
     * @param date    the occurrence, or null to read the answers the appointment itself carries
     */
    public List<EventField> findByEvent(int eventId, LocalDate date) {
        return date == null ? repository.findByEvent(eventId) : repository.findByEventOn(eventId, date);
    }

    /**
     * The value of a field as a reader outside the app should see it. A member field stores the
     * internal IDs of the members it holds, which mean nothing in a feed or a calendar entry, so
     * they are resolved to names here. Every other field already carries the text it was answered
     * with and is returned unchanged. An ID that no longer resolves keeps its number, marked with a
     * {@code #}, rather than dropping a name silently.
     */
    public String displayValue(EventField field) {
        if (field == null || field.value() == null) return "";
        if (!field.fieldType().isMemberField()) return field.value().trim();
        var ids = QuestionValues.memberIds(field.value());
        if (ids.isEmpty()) return "";
        var names = memberRepository.findDisplayNames(ids);
        return ids.stream().map(id -> names.getOrDefault(id, "#" + id)).collect(Collectors.joining(", "));
    }

    public Map<Integer, List<EventField>> findOverviewFieldsByEvents(List<Integer> eventIds) {
        return grouped(repository.findOverviewFieldsByEvents(eventIds));
    }

    /**
     * The same questions, each answered for the date its own appointment is drawn on.
     *
     * <p>An appointment with no date at all is left out: there is no occurrence to answer for, and a
     * question answered per date would otherwise fall back to the appointment's own answer, which a
     * question answered per date does not have.
     *
     * @param eventIds  every appointment on the list
     * @param datesById the date each of them is drawn on
     */
    public Map<Integer, List<EventField>> findOverviewFieldsByEvents(
            List<Integer> eventIds, Map<Integer, LocalDate> datesById) {
        var asked = eventIds.stream().filter(datesById::containsKey).toList();
        var dates = asked.stream().map(datesById::get).toList();
        return grouped(repository.findOverviewFieldsByEventsOn(asked, dates));
    }

    private static Map<Integer, List<EventField>> grouped(List<EventField> fields) {
        var result = new LinkedHashMap<Integer, List<EventField>>();
        for (var field : fields) {
            result.computeIfAbsent(field.eventId(), _ -> new ArrayList<>()).add(field);
        }
        return result;
    }

    /**
     * Replaces the questions an appointment asks, keeping only the ties that lead somewhere.
     *
     * <p>A question can be tied to a field of the attendance sheet the appointment is taken on, so
     * that answering the question fills the sheet in. A tie to a field of some other sheet writes the
     * answer into a sheet nobody opens, where it is never seen again, so it is dropped rather than
     * stored. The editor only offers the right sheet's fields; what arrives here otherwise is a stale
     * value left behind when the sheet was changed, or a caller that is not the editor.
     */
    public void replaceFields(int eventId, List<EventFieldRepository.FieldEntry> fields) {
        requireAnswerable(fields);
        var sheetFieldIds = sheetFieldIds(eventId);
        var kept = fields.stream()
                .map(field -> field.attendanceFieldId() == null || sheetFieldIds.contains(field.attendanceFieldId())
                        ? field
                        : dropTie(eventId, field))
                .toList();
        repository.replaceFields(eventId, kept);
        fieldRegistrationService.reconcile(eventId);
        log.info("Replaced {} fields for event {}", kept.size(), eventId);
    }

    /**
     * Writes the answer a question carries on one date, for whoever may edit the appointment.
     *
     * <p>A question answered per date has no answer on the appointment itself, so the editor cannot
     * reach it: this is where the answer for one occurrence is given. Naming members here puts them
     * on that date's list, the same as naming them anywhere else does.
     *
     * @throws NotFoundResponse   when the question does not belong to this appointment
     * @throws BadRequestResponse when the question is not answered per date, or the answer is not one
     *                            it takes
     */
    public EventField setValueOn(int eventId, int fieldId, LocalDate date, String value) {
        var field = repository.findById(fieldId).orElseThrow(NotFoundResponse::new);
        if (field.eventId() != eventId) throw new NotFoundResponse();
        if (!field.config().perDate()) {
            throw new BadRequestResponse("Field is not answered per date");
        }
        String answer = value != null ? value : "";
        var question = field.config()
                .settings()
                .asQuestion(field.name(), field.fieldType().kind());
        QuestionCheck.answerIfGiven(question, answer).ifPresent(problem -> {
            throw new BadRequestResponse(problem.message());
        });
        repository.updateValueOn(fieldId, date, answer);
        fieldRegistrationService.reconcile(eventId);
        return repository.findByIdOn(fieldId, date).orElseThrow(NotFoundResponse::new);
    }

    /**
     * Refuses what a field of the appointment does not take.
     *
     * <p>These are answers like any other: what stands in a choice has to be one of the choices, and
     * a date has to be a date. Nothing measured them, so an appointment could carry a colour nobody
     * offered and a day that is not one, and every list and export carried it onward.
     *
     * @throws BadRequestResponse naming the field and what is wrong with what stands in it
     */
    private void requireAnswerable(List<EventFieldRepository.FieldEntry> fields) {
        for (var field : fields) {
            var type = field.fieldType() != null ? field.fieldType() : EventFieldType.STRING;
            var config = field.config() != null ? field.config() : EventFieldConfig.empty();
            var question = config.settings().asQuestion(field.name(), type.kind());
            QuestionCheck.answerIfGiven(question, field.value()).ifPresent(problem -> {
                throw new BadRequestResponse(problem.message());
            });
        }
    }

    /** The fields of the sheet this appointment is taken on, empty where it is taken on none. */
    private Set<Integer> sheetFieldIds(int eventId) {
        return eventRepository
                .findById(eventId)
                .map(StationEvent::templateId)
                .map(sheetId -> attendanceRepository.findTemplateFields(sheetId).stream()
                        .map(AttendanceTemplateField::id)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private EventFieldRepository.FieldEntry dropTie(int eventId, EventFieldRepository.FieldEntry field) {
        log.info(
                "Dropped the tie of question \"{}\" to attendance field {}: it is not on the sheet event {} uses",
                field.name(),
                field.attendanceFieldId(),
                eventId);
        return new EventFieldRepository.FieldEntry(
                field.id(),
                field.name(),
                field.fieldType(),
                field.config(),
                field.value(),
                field.overview(),
                null,
                field.isPublic());
    }

    /**
     * Toggles the given member in or out of a MEMBER-type field whose
     * {@code selfRegistration} flag is set.
     *
     * <p>List variants ({@code MEMBER_LIST*}) add the member when absent and remove
     * them when present. Single-value variants take the slot when empty, clear it
     * when the caller already holds it, and raise {@link ConflictResponse} when the
     * slot belongs to someone else.
     *
     * @throws NotFoundResponse   when the field does not exist on the given event
     * @throws BadRequestResponse when the field is not a member-type field, when
     *                            self-registration is not enabled, or when the
     *                            field's required constraint is mis-configured
     * @throws ForbiddenResponse  when the caller does not satisfy the field's
     *                            group / type / tag constraint
     * @throws ConflictResponse   when a single-value slot is already taken
     */
    public EventField toggleSelfRegistration(int eventId, int fieldId, int memberId, LocalDate date) {
        var raw = repository.findById(fieldId).orElseThrow(NotFoundResponse::new);
        boolean perDate = raw.config().perDate();
        if (perDate && date == null) {
            throw new BadRequestResponse("This question is answered per date, so a date is required");
        }
        var field = perDate ? repository.findByIdOn(fieldId, date).orElseThrow(NotFoundResponse::new) : raw;
        if (field.eventId() != eventId) {
            throw new NotFoundResponse();
        }
        if (!field.fieldType().isMemberField()) {
            throw new BadRequestResponse("Field is not a member field");
        }
        if (!field.config().selfRegistration()) {
            throw new BadRequestResponse("Self-registration is not enabled for this field");
        }

        var member = memberRepository.findById(memberId).orElseThrow(() -> new BadRequestResponse("Member not found"));
        ensureEligible(field, member);

        String newValue;
        if (field.fieldType().isMemberListField()) {
            var ids = QuestionValues.memberIds(field.value());
            if (ids.contains(memberId)) {
                ids.removeIf(id -> id == memberId);
            } else {
                ids.add(memberId);
            }
            newValue = QuestionValues.formatMembers(ids);
        } else {
            var ids = QuestionValues.memberIds(field.value());
            if (ids.isEmpty()) {
                newValue = QuestionValues.formatMember(memberId);
            } else if (ids.getFirst() == memberId) {
                newValue = "";
            } else {
                throw new ConflictResponse("Slot is already taken");
            }
        }

        if (perDate) {
            repository.updateValueOn(fieldId, date, newValue);
        } else {
            repository.updateValue(fieldId, newValue);
        }
        fieldRegistrationService.reconcile(eventId);
        log.info("Member {} toggled self-registration on event field {}", memberId, fieldId);
        return perDate
                ? repository.findByIdOn(fieldId, date).orElseThrow(NotFoundResponse::new)
                : repository.findById(fieldId).orElseThrow(NotFoundResponse::new);
    }

    private void ensureEligible(EventField field, StationMember member) {
        var config = field.config();
        switch (field.fieldType().constraint()) {
            case GROUP -> {
                if (config.groupId() == null) {
                    throw new BadRequestResponse("Field is missing its group reference");
                }
                boolean inGroup = groupRepository.findGroupsForMember(member.id()).stream()
                        .anyMatch(g -> g.id() == config.groupId());
                if (!inGroup) {
                    throw new ForbiddenResponse("Member is not in the required group");
                }
            }
            case USER_TYPE -> {
                if (config.userType() == null) {
                    throw new BadRequestResponse("Field is missing its user type reference");
                }
                if (member.userType() != config.userType()) {
                    throw new ForbiddenResponse("Member does not have the required user type");
                }
            }
            case TAG -> {
                if (config.tagId() == null) {
                    throw new BadRequestResponse("Field is missing its tag reference");
                }
                boolean hasTag =
                        tagService.findTagsForMember(member.id()).stream().anyMatch(t -> t.id() == config.tagId());
                if (!hasTag) {
                    throw new ForbiddenResponse("Member does not have the required tag");
                }
            }
            case NONE -> {}
        }
    }
}
