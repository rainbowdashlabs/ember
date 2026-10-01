/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.question.FieldTypes;
import dev.chojo.ember.feature.question.MemberEligibility;
import dev.chojo.ember.feature.question.QuestionCheck;
import dev.chojo.ember.feature.question.QuestionValues;
import io.javalin.http.BadRequestResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
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
    private final MemberEligibility eligibility;

    @Inject
    public EventRegistrationFieldService(EventRegistrationFieldRepository repository, MemberEligibility eligibility) {
        this.repository = repository;
        this.eligibility = eligibility;
    }

    public List<EventRegistrationField> findByEvent(int eventId) {
        return repository.findByEvent(eventId);
    }

    /**
     * Replaces the questions an appointment asks of its registrants.
     *
     * @throws io.javalin.http.HttpResponseException where a new question asks for a kind of answer
     *                                               the registration form does not offer
     * @throws BadRequestResponse                    naming a question whose default it would refuse
     */
    public void replaceFields(int eventId, List<RegistrationFieldDraft> fields) {
        var asked = repository.findByEvent(eventId).stream()
                .map(field -> new Asked(field.name(), field.fieldType()))
                .collect(Collectors.toSet());
        requireOffered(fields, asked);
        requireUsableDefaults(fields);
        repository.replaceFields(eventId, fields);
        log.info("Event {} now asks {} question(s)", eventId, fields.size());
    }

    /**
     * Refuses a kind of answer the registration form does not offer.
     *
     * <p>The editor offers a handful of kinds, and anything else could only arrive from a caller that
     * is not the editor: a place or a member of a group asked of every registrant has no form that
     * answers it. A question already asked under its name and type is kept, because the editor writes
     * every question back on each save and what was accepted once is not refused on the next.
     *
     * @param fields       the questions being written
     * @param alreadyAsked the questions asked before this write
     * @throws io.javalin.http.HttpResponseException where a new question is of a kind not offered
     */
    static void requireOffered(List<RegistrationFieldDraft> fields, Set<Asked> alreadyAsked) {
        for (var field : fields) {
            if (FieldTypes.REGISTRATION.contains(field.fieldType())) continue;
            if (alreadyAsked.contains(new Asked(field.name(), field.fieldType()))) continue;
            throw Refusal.REGISTRATION_QUESTION_TYPE_NOT_OFFERED.raise();
        }
    }

    /**
     * Refuses a question configured to start from a value it would then refuse as an answer.
     *
     * <p>A choice whose default is not among its options, a number whose default lies outside its
     * bounds: both were written happily and only ever failed later, at somebody else's screen, in
     * words about an answer they had not given.
     *
     * @param fields      the questions as they are being written
     * @param eligibility who passes the group, user type or tag a member question is narrowed to
     * @throws BadRequestResponse naming the question and what is wrong with its default
     */
    static void requireUsableDefaults(List<RegistrationFieldDraft> fields, MemberEligibility eligibility) {
        for (var field : fields) {
            var question = field.config().asQuestion(field.name(), field.fieldType());
            QuestionCheck.defaultValue(question, eligibility).ifPresent(problem -> {
                throw new BadRequestResponse(problem.message());
            });
        }
    }

    private void requireUsableDefaults(List<RegistrationFieldDraft> fields) {
        requireUsableDefaults(fields, eligibility);
    }

    /**
     * A question by what identifies it from one save to the next.
     *
     * @param name what it is called
     * @param type the answer it takes
     */
    record Asked(String name, FieldType type) {}

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
     * neither is refused; every other rule - the options of a choice, the range of a number, the group
     * a member question is narrowed to - is checked the same way for a member answering their own
     * registration and for a manager filling one in on their behalf.
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
        var fields = repository.findByEvent(eventId);
        if (fields.isEmpty()) return Map.of();
        return validate(fields, answers, eligibility);
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
     * shown is not theirs to erase, so it survives their edit untouched. A member the registration
     * already names stays named although they have since left the group the question is narrowed to:
     * correcting another answer is not the moment to refuse what was accepted before.
     *
     * @param eventId            the event the registration belongs to
     * @param registrationId     the registration being updated
     * @param answers            the answers keyed by question id, as submitted
     * @param readsHiddenAnswers whether the caller may read the answers kept for organisers
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    public void replaceAnswers(
            int eventId, int registrationId, Map<Integer, String> answers, boolean readsHiddenAnswers) {
        var stored = repository.findValues(registrationId);
        var fields = repository.findByEvent(eventId).stream()
                .filter(field -> readsHiddenAnswers || !field.config().managersOnly())
                .toList();
        var keeping = NamedAlready.in(
                stored.stream().map(RegistrationFieldValue::value).toList(), eligibility);
        var resolved = fields.isEmpty() ? Map.<Integer, String>of() : validate(fields, answers, keeping);
        var hidden = hiddenFieldIds(eventId, readsHiddenAnswers);

        for (var value : stored) {
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
     * questions do not allow. Returns the value to store per question id, in the one shape its type is
     * stored in.
     *
     * @param fields      the event's questions
     * @param answers     the answers as submitted, keyed by question id
     * @param eligibility who passes the group, user type or tag a member question is narrowed to
     * @return the value to store per question id
     * @throws BadRequestResponse when a question is unanswered, unknown, or answered out of range
     */
    private static Map<Integer, String> validate(
            List<EventRegistrationField> fields, Map<Integer, String> answers, MemberEligibility eligibility) {
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
            QuestionCheck.answer(field.question(), value, eligibility).ifPresent(problem -> {
                throw new BadRequestResponse(problem.message());
            });
            if (isBlank(value)) continue;
            resolved.put(field.id(), QuestionValues.writeText(field.fieldType(), value));
        }
        return resolved;
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
