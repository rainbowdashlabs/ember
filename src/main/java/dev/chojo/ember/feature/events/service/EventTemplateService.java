/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import dev.chojo.ember.feature.attendance.service.AttendanceTemplateGuards;
import dev.chojo.ember.feature.events.entity.AppointmentTemplateField;
import dev.chojo.ember.feature.events.entity.AppointmentTemplateFieldDraft;
import dev.chojo.ember.feature.events.entity.EventTemplate;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationTemplateField;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.question.Question;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Appointment templates, and the one path both kinds of question take through them.
 *
 * <p>A template carries the questions the organiser answers on every appointment made from it and the
 * questions every registrant answers. Both are written here, both have their starting values measured
 * by the same check, and both are handed to an appointment only from a template of the station
 * making it.
 */
@Singleton
public class EventTemplateService {
    private static final Logger log = LoggerFactory.getLogger(EventTemplateService.class);

    private final EventTemplateRepository repository;
    private final AttendanceRepository attendanceRepository;
    private final MemberEligibility eligibility;
    private final AttendanceTemplateGuards attendanceTemplateGuards;

    @Inject
    public EventTemplateService(
            EventTemplateRepository repository,
            AttendanceRepository attendanceRepository,
            MemberEligibility eligibility,
            AttendanceTemplateGuards attendanceTemplateGuards) {
        this.repository = repository;
        this.attendanceRepository = attendanceRepository;
        this.eligibility = eligibility;
        this.attendanceTemplateGuards = attendanceTemplateGuards;
    }

    public List<EventTemplate> findByStation(int stationId) {
        return repository.findByStation(stationId);
    }

    public Optional<EventTemplate> findById(int id) {
        return repository.findById(id);
    }

    /**
     * A template of this station, for an appointment about to be made from it.
     *
     * <p>Asked before the appointment is written, so a template that is not the station's refuses the
     * appointment rather than leaving one behind without its questions.
     *
     * @param stationId  the station making the appointment
     * @param templateId the template it names
     * @throws io.javalin.http.HttpResponseException where the template is gone or another station's,
     *                                               in the same words for both
     */
    public EventTemplate requireOwn(int stationId, int templateId) {
        return repository
                .findById(templateId)
                .filter(template -> template.stationId() == stationId)
                .orElseThrow(EventRefusal.EVENT_TEMPLATE_TO_APPLY_NOT_HERE::raise);
    }

    /**
     * Hands a template's registration questions to an appointment made from it. The copies are
     * independent: a later template edit never rewrites questions members have already answered.
     *
     * @param template the template, as {@link #requireOwn} found it for the appointment's station
     * @param eventId  the appointment being made from it
     */
    public void copyInto(EventTemplate template, int eventId) {
        int copied = repository.copyRegistrationFields(template.id(), eventId);
        if (copied > 0) log.info("Event {} took {} question(s) from template {}", eventId, copied, template.id());
    }

    public EventTemplate create(int stationId, String name) {
        var template = repository.create(stationId, name);
        log.info("Created event template {} for station {}", template.id(), stationId);
        return template;
    }

    public boolean update(
            int id,
            String name,
            @Nullable String title,
            @Nullable String description,
            @Nullable Integer categoryId,
            StationEvent.@Nullable EventType eventType,
            @Nullable Boolean requiresRegistration,
            @Nullable String registrationDeadlineOffset,
            @Nullable Boolean requiresConfirmation,
            @Nullable RestrictionMode restrictionMode,
            @Nullable Integer attendanceTemplateId,
            @Nullable Integer registrationLimit) {
        attendanceTemplateGuards.requireOpenForNewWork(attendanceTemplateId);
        if (repository.update(
                id,
                name,
                title,
                description,
                categoryId,
                eventType,
                requiresRegistration,
                registrationDeadlineOffset,
                requiresConfirmation,
                restrictionMode,
                attendanceTemplateId,
                registrationLimit)) {
            log.info("Updated event template {}", id);
            return true;
        }
        log.warn("Cannot update event template: template {} not found", id);
        return false;
    }

    public boolean delete(int id) {
        if (repository.delete(id)) {
            log.info("Deleted event template {}", id);
            return true;
        }
        log.warn("Cannot delete event template: template {} not found", id);
        return false;
    }

    public List<AppointmentTemplateField> findFields(int templateId) {
        return repository.findFields(templateId);
    }

    public List<RegistrationTemplateField> findRegistrationFields(int templateId) {
        return repository.findRegistrationFields(templateId);
    }

    /**
     * Replaces the questions a template asks, keeping only the ties that lead somewhere.
     *
     * <p>A question can be tied to a field of the attendance sheet the template names, so that
     * answering the question fills the sheet in. A tie to a field of some other sheet fills in a
     * sheet nobody opens: the value is written and never seen again. Such a tie is dropped here
     * rather than stored, because the only ways to acquire one are a stale value left behind when
     * the sheet was changed and a caller that is not the editor.
     *
     * @throws io.javalin.http.HttpResponseException for a type an appointment does not offer
     */
    public void replaceFields(int templateId, List<AppointmentTemplateFieldDraft> fields) {
        if (fields.stream().anyMatch(field -> !FieldTypes.APPOINTMENT.contains(field.fieldType()))) {
            throw EventRefusal.TEMPLATE_FIELD_TYPE_NOT_OFFERED.raise();
        }
        var keeping = NamedAlready.in(
                repository.findFields(templateId).stream()
                        .map(AppointmentTemplateField::defaultValue)
                        .toList(),
                eligibility);
        fields.forEach(field -> requireUsableDefault(field.question(), keeping));
        var kept = fields.stream()
                .map(field -> reachable(templateId, field.attendanceFieldId()) ? field : field.untied())
                .toList();
        repository.replaceFields(templateId, kept);
        log.info("Replaced fields for event template {} ({} fields)", templateId, kept.size());
    }

    /**
     * Replaces the registration questions a template carries.
     *
     * @throws io.javalin.http.HttpResponseException where a new question asks for a kind of answer
     *                                               the registration form does not offer
     * @throws RefusalResponse                       naming a question whose default it would refuse
     */
    public void replaceRegistrationFields(int templateId, List<RegistrationFieldDraft> fields) {
        var stored = repository.findRegistrationFields(templateId);
        EventRegistrationFieldService.requireOffered(
                fields,
                stored.stream()
                        .map(field -> new EventRegistrationFieldService.Asked(field.name(), field.fieldType()))
                        .collect(Collectors.toSet()));
        EventRegistrationFieldService.requireUsableDefaults(
                fields,
                NamedAlready.in(
                        stored.stream()
                                .map(field -> field.config().defaultValue())
                                .toList(),
                        eligibility),
                EventRefusal.EVENT_TEMPLATE_REGISTRATION_DEFAULT_NOT_ACCEPTED);
        repository.replaceRegistrationFields(templateId, fields);
        log.info("Event template {} now asks {} registration question(s)", templateId, fields.size());
    }

    /**
     * Refuses a question set up to start from a value it would not take as an answer.
     *
     * <p>What an appointment made from this template starts the question off with is an answer to it,
     * so it is measured as one: a choice starting outside its own choices, or a member question
     * starting on somebody outside its group, is a mistake made once here and carried into everything
     * written from the template. A member the template already started from is not refused again on
     * the next save, though they may have left the group since.
     *
     * @param keeping who passes a narrowing, the members the template already started from included
     * @throws RefusalResponse naming the question and what is wrong with its starting value
     */
    private static void requireUsableDefault(Question question, MemberEligibility keeping) {
        QuestionCheck.defaultValue(question, keeping).ifPresent(problem -> {
            throw EventRefusal.EVENT_TEMPLATE_FIELD_DEFAULT_NOT_ACCEPTED.raise(problem.question());
        });
    }

    /** Whether this attendance field belongs to the sheet the template writes into. */
    private boolean reachable(int templateId, @Nullable Integer attendanceFieldId) {
        if (attendanceFieldId == null) return true;
        var sheetId = repository
                .findById(templateId)
                .map(EventTemplate::attendanceTemplateId)
                .orElse(null);
        if (sheetId == null) {
            log.info(
                    "Dropped the tie to attendance field {}: template {} names no sheet",
                    attendanceFieldId,
                    templateId);
            return false;
        }
        boolean belongs = attendanceRepository.findTemplateFields(sheetId).stream()
                .anyMatch(field -> field.id() == attendanceFieldId);
        if (!belongs) {
            log.info(
                    "Dropped the tie to attendance field {}: it is not on sheet {} of template {}",
                    attendanceFieldId,
                    sheetId,
                    templateId);
        }
        return belongs;
    }

    public List<Integer> findReminderDays(int templateId) {
        return repository.findReminderDays(templateId);
    }

    public void setReminders(int templateId, List<Integer> daysBefore) {
        repository.replaceReminders(templateId, daysBefore);
        log.info("Set reminders for event template {} ({} days)", templateId, daysBefore.size());
    }
}
