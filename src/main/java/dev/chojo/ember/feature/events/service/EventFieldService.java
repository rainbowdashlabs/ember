/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.feature.attendance.entity.AttendanceTemplateField;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.events.entity.AppointmentField;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionKind;
import dev.chojo.ember.feature.question.QuestionProblem;
import dev.chojo.ember.feature.question.QuestionValues;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
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
    private final MemberEligibility eligibility;
    private final EventRepository eventRepository;
    private final AttendanceRepository attendanceRepository;
    private final EventFieldRegistrationService fieldRegistrationService;

    @Inject
    public EventFieldService(
            EventFieldRepository repository,
            StationMemberRepository memberRepository,
            MemberEligibility eligibility,
            EventRepository eventRepository,
            AttendanceRepository attendanceRepository,
            EventFieldRegistrationService fieldRegistrationService) {
        this.repository = repository;
        this.memberRepository = memberRepository;
        this.eligibility = eligibility;
        this.eventRepository = eventRepository;
        this.attendanceRepository = attendanceRepository;
        this.fieldRegistrationService = fieldRegistrationService;
    }

    public List<String> findDistinctFieldNames(int stationId) {
        return repository.findDistinctFieldNames(stationId);
    }

    public List<AppointmentField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    /**
     * The questions of an appointment as they stand on one of its dates, which is the only way a
     * question answered per date can be read.
     *
     * @param eventId the appointment
     * @param date    the occurrence, or null to read the answers the appointment itself carries
     */
    public List<AppointmentField> findByEvent(int eventId, @Nullable LocalDate date) {
        return date == null ? repository.findByEvent(eventId) : repository.findByEventOn(eventId, date);
    }

    /**
     * The value of a field as a reader outside the app should see it. A member field stores the
     * internal IDs of the members it holds, which mean nothing in a feed or a calendar entry, so
     * they are resolved to names here. Every other field already carries the text it was answered
     * with and is returned unchanged. An ID that no longer resolves keeps its number, marked with a
     * {@code #}, rather than dropping a name silently.
     */
    public String displayValue(AppointmentField field) {
        if (field.value() == null) return "";
        if (!field.fieldType().namesMembers()) return field.value().trim();
        var ids = QuestionValues.memberIds(field.value());
        if (ids.isEmpty()) return "";
        var names = memberRepository.findDisplayNames(ids);
        return ids.stream().map(id -> names.getOrDefault(id, "#" + id)).collect(Collectors.joining(", "));
    }

    public Map<Integer, List<AppointmentField>> findOverviewFieldsByEvents(List<Integer> eventIds) {
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
    public Map<Integer, List<AppointmentField>> findOverviewFieldsByEvents(
            List<Integer> eventIds, Map<Integer, LocalDate> datesById) {
        var asked = eventIds.stream().filter(datesById::containsKey).toList();
        var dates = asked.stream().map(datesById::get).toList();
        return grouped(repository.findOverviewFieldsByEventsOn(asked, dates));
    }

    private static Map<Integer, List<AppointmentField>> grouped(List<AppointmentField> fields) {
        var result = new LinkedHashMap<Integer, List<AppointmentField>>();
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
     *
     * <p>Every answer is measured as it would be anywhere else, a member question's narrowing
     * included, and stored in the one shape its type is stored in. A member the appointment already
     * names stays named on the next save although they may have left the group since.
     *
     * @throws io.javalin.http.HttpResponseException for a type an appointment does not offer
     */
    public void replaceFields(int eventId, List<EventFieldDraft> fields) {
        if (fields.stream().anyMatch(field -> !FieldTypes.APPOINTMENT.contains(field.fieldType()))) {
            throw Refusal.APPOINTMENT_FIELD_TYPE_NOT_OFFERED.raise();
        }
        var keeping = NamedAlready.in(
                repository.findByEvent(eventId).stream()
                        .map(AppointmentField::value)
                        .toList(),
                eligibility);
        var sheetFieldIds = sheetFieldIds(eventId);
        var kept = fields.stream()
                .map(field -> answered(field, keeping))
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
     * @throws RefusalResponse when the question does not belong to this appointment, is not answered
     *                         per date, or the answer is not one it takes
     */
    public AppointmentField setValueOn(int eventId, int fieldId, LocalDate date, @Nullable String value) {
        var field = repository.findById(fieldId).orElseThrow(Refusal.APPOINTMENT_FIELD_NOT_HERE_FOR_DATE_ANSWER::raise);
        if (field.eventId() != eventId) throw Refusal.APPOINTMENT_FIELD_NOT_HERE_FOR_DATE_ANSWER.raise();
        if (!field.config().perDate()) {
            throw Refusal.APPOINTMENT_FIELD_NOT_PER_DATE.raise();
        }
        var stored = repository
                .findByIdOn(fieldId, date)
                .map(AppointmentField::value)
                .orElse("");
        var keeping = NamedAlready.in(List.of(stored), eligibility);
        QuestionCheck.answerIfGiven(field.question(), value, keeping).ifPresent(problem -> {
            throw Refusal.APPOINTMENT_DATE_ANSWER_NOT_ACCEPTED.raise(problem.message());
        });
        repository.updateValueOn(fieldId, date, QuestionValues.writeText(field.fieldType(), value));
        fieldRegistrationService.reconcile(eventId);
        return repository
                .findByIdOn(fieldId, date)
                .orElseThrow(Refusal.APPOINTMENT_DATE_ANSWER_NOT_HERE_AFTER_SAVE::raise);
    }

    /**
     * The question with its answer measured and written in the one shape its type is stored in.
     *
     * <p>These are answers like any other: what stands in a choice has to be one of the choices, a
     * date has to be a date, and a member question narrowed to a group names members of the group.
     * Nothing measured them once, so an appointment could carry a colour nobody offered and a day
     * that is not one, and every list and export carried it onward.
     *
     * @throws RefusalResponse naming the field and what is wrong with what stands in it
     */
    private static EventFieldDraft answered(EventFieldDraft field, MemberEligibility eligibility) {
        QuestionCheck.answerIfGiven(field.question(), field.value(), eligibility)
                .ifPresent(problem -> {
                    throw Refusal.APPOINTMENT_FIELD_VALUE_NOT_ACCEPTED.raise(problem.message());
                });
        return field.withValue(QuestionValues.writeText(field.fieldType(), field.value()));
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

    private static EventFieldDraft dropTie(int eventId, EventFieldDraft field) {
        log.info(
                "Dropped the tie of question \"{}\" to attendance field {}: it is not on the sheet event {} uses",
                field.name(),
                field.attendanceFieldId(),
                eventId);
        return field.untied();
    }

    /**
     * Toggles the given member in or out of a member question whose {@code selfRegistration} flag is
     * set.
     *
     * <p>List questions add the member when absent and remove them when present. Single-member
     * questions take the slot when empty, clear it when the caller already holds it, and refuse when
     * the slot belongs to someone else.
     *
     * <p>Who may put themselves in is the field's own business and not the appointment's. Standing
     * in one now holds a place on the list, so it is a second way onto it, and deliberately so: a
     * station that switched self-registration on for a field and then narrowed who may stand in it
     * has said twice over who belongs there, and a register audience that happens not to name the
     * same group is far more likely to be an oversight than a decision. The closing date is the one
     * thing that still holds, because it is the appointment saying it is done taking people, and
     * that is not a thing a field of it can overrule.
     *
     * <p>Coming back off the list is never refused, whatever the date and whoever the question is
     * narrowed to. Somebody who cannot be there has to be able to say so, and the alternative is a
     * name on a rota that everybody knows is wrong.
     *
     * @param runsTheEvent whoever keeps the appointment's list, for whom the closing date is not a
     *                     refusal: they are not answering the appointment, they are running it
     * @throws RefusalResponse when the field does not exist on the given event, is not a member
     *                         question, has self-registration switched off, lost the group, user type
     *                         or tag it is narrowed to, when the appointment has stopped taking people,
     *                         when the caller is outside what the question is narrowed to, or when a
     *                         single-member slot is already taken
     */
    public AppointmentField toggleSelfRegistration(
            int eventId, int fieldId, int memberId, @Nullable LocalDate date, boolean runsTheEvent) {
        var raw = repository
                .findById(fieldId)
                .orElseThrow(Refusal.APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION::raise);
        LocalDate day = raw.config().perDate() ? requiredDay(date) : null;
        var field = day != null
                ? repository
                        .findByIdOn(fieldId, day)
                        .orElseThrow(Refusal.APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION::raise)
                : raw;
        if (field.eventId() != eventId) {
            throw Refusal.APPOINTMENT_FIELD_NOT_HERE_FOR_SELF_REGISTRATION.raise();
        }
        if (!field.fieldType().namesMembers()) {
            throw Refusal.APPOINTMENT_FIELD_NAMES_NO_MEMBERS.raise();
        }
        if (!field.config().selfRegistration()) {
            throw Refusal.APPOINTMENT_FIELD_SELF_REGISTRATION_OFF.raise();
        }
        if (memberRepository.findById(memberId).isEmpty()) {
            throw Refusal.APPOINTMENT_SELF_REGISTRATION_MEMBER_NOT_HERE.raise();
        }

        var ids = QuestionValues.memberIds(field.value());
        boolean entering = !ids.contains(memberId);
        String newValue = entering ? entered(field, ids, memberId) : left(ids, memberId);
        if (entering) {
            requireEligible(field, memberId);
            if (!runsTheEvent) requireStillTakingPeople(eventId);
        }

        if (day != null) {
            repository.updateValueOn(fieldId, day, newValue);
        } else {
            repository.updateValue(fieldId, newValue);
        }
        fieldRegistrationService.reconcile(eventId);
        log.info("Member {} toggled self-registration on event field {}", memberId, fieldId);
        return day != null
                ? repository
                        .findByIdOn(fieldId, day)
                        .orElseThrow(Refusal.APPOINTMENT_FIELD_NOT_HERE_AFTER_SELF_REGISTRATION::raise)
                : repository
                        .findById(fieldId)
                        .orElseThrow(Refusal.APPOINTMENT_FIELD_NOT_HERE_AFTER_SELF_REGISTRATION::raise);
    }

    /**
     * The answer once the member stands in the question.
     *
     * @throws RefusalResponse where a single-member question already names somebody else
     */
    private static String entered(AppointmentField field, List<Integer> ids, int memberId) {
        if (field.fieldType().kind().filter(QuestionKind::namesSeveralMembers).isPresent()) {
            ids.add(memberId);
            return QuestionValues.formatMembers(ids);
        }
        if (!ids.isEmpty()) throw Refusal.APPOINTMENT_FIELD_SLOT_TAKEN.raise();
        return QuestionValues.formatMember(memberId);
    }

    /** The answer once the member stepped out of the question, empty where nobody is left. */
    private static String left(List<Integer> ids, int memberId) {
        ids.removeIf(id -> id == memberId);
        return ids.isEmpty() ? "" : QuestionValues.formatMembers(ids);
    }

    /**
     * Refuses somebody outside the group, user type or tag the question is narrowed to, by the same
     * check every other answer to it goes through.
     *
     * @throws RefusalResponse where the member is outside it, or the question lost what it is
     *                         narrowed to
     */
    private void requireEligible(AppointmentField field, int memberId) {
        QuestionCheck.answerIfGiven(field.question(), QuestionValues.formatMember(memberId), eligibility)
                .ifPresent(problem -> {
                    if (problem.code() == QuestionProblem.Code.NOT_ELIGIBLE) {
                        throw Refusal.APPOINTMENT_FIELD_NOT_OPEN_TO_YOU.raise(problem.message());
                    }
                    throw Refusal.APPOINTMENT_FIELD_NARROWING_LOST.raise(problem.message());
                });
    }

    private static LocalDate requiredDay(@Nullable LocalDate date) {
        if (date == null) {
            throw Refusal.APPOINTMENT_FIELD_DATE_MISSING.raise();
        }
        return date;
    }

    /**
     * Refuses to put anybody on an appointment that has stopped taking people.
     *
     * <p>The same refusal the sign-up button gives, in the same words, because it is the same
     * refusal: a field of an appointment cannot be a way past the date the appointment stopped at.
     *
     * @throws RefusalResponse where the closing date has gone by
     */
    private void requireStillTakingPeople(int eventId) {
        var event = eventRepository
                .findById(eventId)
                .orElseThrow(Refusal.APPOINTMENT_NOT_HERE_FOR_SELF_REGISTRATION::raise);
        Instant deadline = event.registrationDeadline();
        if (!event.requiresRegistration() || deadline == null) return;
        if (Instant.now().isAfter(deadline)) {
            throw Refusal.REGISTRATION_CLOSED_ON_SELF_REGISTRATION.raise();
        }
    }
}
