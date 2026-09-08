/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.EventTemplateRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository.FieldEntry;
import dev.chojo.ember.feature.question.QuestionCheck;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The questions an event asks of everyone registering for it, and the answers members give.
 *
 * <p>Validation lives here and nowhere else: registering and editing an answer afterwards both go
 * through {@link #validate}, so the two paths cannot drift into different levels of strictness.
 */
@Singleton
public class EventRegistrationFieldService {
    private static final Logger log = LoggerFactory.getLogger(EventRegistrationFieldService.class);

    private final EventRegistrationFieldRepository repository;

    @Inject
    public EventRegistrationFieldService(EventRegistrationFieldRepository repository) {
        this.repository = repository;
    }

    public List<EventRegistrationField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    public void replaceFields(int eventId, List<FieldEntry> fields) {
        requireUsableDefaults(fields);
        repository.replaceFields(eventId, fields);
        log.info("Event {} now asks {} question(s)", eventId, fields.size());
    }

    public List<EventTemplateRegistrationField> findByTemplate(int templateId) {
        return repository.findByTemplate(templateId);
    }

    public void replaceTemplateFields(int templateId, List<FieldEntry> fields) {
        requireUsableDefaults(fields);
        repository.replaceTemplateFields(templateId, fields);
        log.info("Event template {} now asks {} question(s)", templateId, fields.size());
    }

    /**
     * Copies a template's questions into a freshly created event. The copies are independent: a
     * later template edit never rewrites questions members have already answered.
     *
     * @param templateId the template being applied
     * @param eventId    the event being created from it
     */
    public void copyTemplateFields(int templateId, int eventId) {
        var fields = repository.findByTemplate(templateId).stream()
                .map(f -> new FieldEntry(f.name(), f.fieldType(), f.config(), f.overview()))
                .toList();
        if (fields.isEmpty()) return;
        repository.replaceFields(eventId, fields);
        log.info("Event {} took {} question(s) from template {}", eventId, fields.size(), templateId);
    }

    public List<RegistrationFieldValue> findValues(int registrationId) {
        return repository.findValues(registrationId);
    }

    /**
     * Groups the answers of many registrations by registration id, so a whole list is rendered
     * from one query.
     *
     * @param registrationIds the registrations being listed
     * @return the answers per registration, empty entries omitted
     */
    public Map<Integer, List<RegistrationFieldValue>> findValuesByRegistration(List<Integer> registrationIds) {
        return repository.findValuesForRegistrations(registrationIds).stream()
                .collect(Collectors.groupingBy(RegistrationFieldValue::registrationId));
    }

    /**
     * Resolves what a registration should carry for an event's questions, before anything is
     * written. Registering validates first and inserts second, so a refused answer never leaves a
     * registration behind.
     *
     * <p>A question with no answer falls back to its configured default. A required question with
     * neither is refused; every other rule - the options of a choice, the range of a number - is
     * checked the same way for a member answering their own registration and for a manager filling
     * one in on their behalf.
     *
     * <p>Every question the appointment asks takes part, the ones whose answers are kept for
     * organisers included. Those are answered by whoever registers like any other; only the answer
     * is theirs to keep from the rest of the station.
     *
     * @param eventId the event being registered for
     * @param answers the answers keyed by question id, as submitted
     * @return the value to store per question id
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public Map<Integer, String> resolveAnswers(int eventId, Map<Integer, String> answers) {
        return resolveAnswers(eventId, answers, true);
    }

    /**
     * Resolves the answers of somebody correcting a registration, leaving the questions whose
     * answers they may not read out of it.
     *
     * <p>Only editing needs this. What such a caller cannot read they cannot have been shown, so
     * requiring them to send it back would refuse every correction they make, and the answer they
     * did not send stays where it is rather than being wiped by their edit.
     *
     * @param readsHiddenAnswers whether this caller may read the answers kept for organisers
     */
    private Map<Integer, String> resolveAnswers(int eventId, Map<Integer, String> answers, boolean readsHiddenAnswers) {
        var fields = repository.findByEvent(eventId).stream()
                .filter(field -> readsHiddenAnswers || !field.config().managersOnly())
                .toList();
        if (fields.isEmpty()) return Map.of();
        return validate(fields, answers);
    }

    /**
     * The ids of the questions whose answers are kept for organisers, so they can be stripped from a
     * response.
     *
     * <p>The setting says who may read the answer, not who gives it: whoever registers is asked the
     * question like any other, and it is the answer that is kept from everybody but the organisers
     * and the household it belongs to.
     *
     * @param readsHiddenAnswers whether this reader may read them, which is true for whoever runs
     *                           the appointment and for whoever the answers are about
     */
    public Set<Integer> hiddenFieldIds(int eventId, boolean readsHiddenAnswers) {
        if (readsHiddenAnswers) return Set.of();
        return repository.findByEvent(eventId).stream()
                .filter(field -> field.config().managersOnly())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
    }

    /**
     * The questions of an appointment that have to be answered, by id.
     *
     * @param eventId the appointment
     * @return the ids of its required questions, empty where it requires none
     */
    public Set<Integer> requiredFieldIds(int eventId) {
        return repository.findByEvent(eventId).stream()
                .filter(field -> field.config().required())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
    }

    /**
     * Whether a registration is short of an answer somebody still has to give.
     *
     * <p>A required question always got an answer when it was answered at all, because registering
     * refuses to go through without one. A required question with nothing stored against it can
     * therefore only be one that came after the registration, which is the whole of what this says.
     *
     * <p>Only a registration that still stands owes anything. Somebody who declined, withdrew or was
     * turned away holds no place, and a question added afterwards is not theirs to answer.
     *
     * @param status   where the registration stands
     * @param required the ids of the appointment's required questions
     * @param answers  what the registration carries, the answers kept for organisers included
     * @return true where an answer is still owed
     */
    public static boolean owesAnswer(
            RegistrationStatus status, Set<Integer> required, List<RegistrationFieldValue> answers) {
        if (required.isEmpty()) return false;
        if (status != RegistrationStatus.PENDING && status != RegistrationStatus.ACCEPTED) return false;
        var answered = answers.stream()
                .filter(value -> !isBlank(value.value()))
                .map(RegistrationFieldValue::fieldId)
                .collect(Collectors.toSet());
        return !answered.containsAll(required);
    }

    /**
     * Writes resolved answers onto a registration, replacing whatever it carried before.
     *
     * @param registrationId the registration the answers belong to
     * @param resolved       the answers as {@link #resolveAnswers} returned them
     */
    public void persistAnswers(int registrationId, Map<Integer, String> resolved) {
        if (resolved.isEmpty()) return;
        for (var entry : resolved.entrySet()) {
            repository.setValue(registrationId, entry.getKey(), entry.getValue());
        }
    }

    /**
     * Replaces the answers of an existing registration, dropping any that the new set leaves out.
     *
     * <p>Only the questions whose answers the caller may read take part. An answer they were never
     * shown is not theirs to erase, so it survives their edit untouched.
     *
     * @param eventId            the event the registration belongs to
     * @param registrationId     the registration being updated
     * @param answers            the answers keyed by question id, as submitted
     * @param readsHiddenAnswers whether the caller may read the answers kept for organisers
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public void replaceAnswers(
            int eventId, int registrationId, Map<Integer, String> answers, boolean readsHiddenAnswers) {
        var resolved = resolveAnswers(eventId, answers, readsHiddenAnswers);
        var hidden = hiddenFieldIds(eventId, readsHiddenAnswers);

        for (var value : repository.findValues(registrationId)) {
            if (hidden.contains(value.fieldId())) continue;
            repository.deleteValue(registrationId, value.fieldId());
        }
        persistAnswers(registrationId, resolved);
        log.debug(
                "Registration {} for event {} now carries {} answer(s), {} question(s) stayed hidden",
                registrationId,
                eventId,
                resolved.size(),
                hidden.size());
    }

    /**
     * Resolves the answers a registration should carry, applying defaults and refusing anything the
     * questions do not allow. Returns the value to store per question id.
     *
     * @param fields  the event's questions
     * @param answers the answers as submitted, keyed by question id
     * @return the value to store per question id
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public Map<Integer, String> validate(List<EventRegistrationField> fields, Map<Integer, String> answers) {
        var known = fields.stream().map(EventRegistrationField::id).collect(Collectors.toSet());
        for (Integer fieldId : answers.keySet()) {
            if (!known.contains(fieldId)) {
                throw new BadRequestResponse("Unknown registration field " + fieldId);
            }
        }

        var resolved = new LinkedHashMap<Integer, String>();
        for (var field : fields) {
            String value = answers.get(field.id());
            if (isBlank(value)) value = field.config().defaultValue();
            QuestionCheck.answer(field.question(), value).ifPresent(problem -> {
                throw new BadRequestResponse(problem.message());
            });
            if (isBlank(value)) continue;
            resolved.put(field.id(), value);
        }
        return resolved;
    }

    /**
     * Refuses a question configured to start from a value it would then refuse as an answer.
     *
     * <p>A choice whose default is not among its options, a number whose default lies outside its
     * bounds: both were written happily and only ever failed later, at somebody else's screen, in
     * words about an answer they had not given.
     *
     * @param fields the questions as they are being written
     * @throws BadRequestResponse naming the question and what is wrong with its default
     */
    private void requireUsableDefaults(List<FieldEntry> fields) {
        for (var field : fields) {
            var type = field.fieldType() != null ? field.fieldType() : EventFieldType.STRING;
            var config = field.config() != null ? field.config() : EventRegistrationFieldConfig.empty();
            var question = new EventRegistrationField(0, 0, field.name(), type, config, 0, field.overview()).question();
            QuestionCheck.defaultValue(question).ifPresent(problem -> {
                throw new BadRequestResponse(problem.message());
            });
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
