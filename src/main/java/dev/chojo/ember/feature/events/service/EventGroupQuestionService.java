/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.service.EventRegistrationFieldService.Asked;
import dev.chojo.ember.feature.question.MemberEligibility;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The questions an event group asks of everybody signing up, and the answers each sign-up gives.
 *
 * <p>An event group asks once for all its parts, so the answers belong to the event group sign-up and not to the
 * registrations of its parts. The questions follow the same rules as an appointment's: the same kinds
 * of answer, the same defaults, the same checks, and answers kept for organisers stay with them.
 */
@Singleton
public class EventGroupQuestionService {
    private static final Logger log = LoggerFactory.getLogger(EventGroupQuestionService.class);

    private final EventRegistrationFieldRepository repository;
    private final MemberEligibility eligibility;

    @Inject
    public EventGroupQuestionService(EventRegistrationFieldRepository repository, MemberEligibility eligibility) {
        this.repository = repository;
        this.eligibility = eligibility;
    }

    /**
     * Group fields checked and shaped the way an appointment's are before they are stored.
     *
     * @param fields the fields as submitted
     * @return the fields to store
     */
    List<EventFieldDraft> checkedFields(List<EventFieldDraft> fields) {
        return EventFieldService.checkedGroupFields(fields, eligibility);
    }

    /**
     * The questions of an event group in their order.
     *
     * @param eventGroupId the event group
     * @return its questions
     */
    public List<EventRegistrationField> findByEventGroup(int eventGroupId) {
        return repository.findByEventGroup(eventGroupId);
    }

    /**
     * Replaces the questions of an event group. A question that keeps its name keeps its answers.
     *
     * @param eventGroupId the event group
     * @param fields   the questions in their new order
     * @throws io.javalin.http.HttpResponseException where a new question asks for a kind of answer
     *                                               the sign-up does not offer
     * @throws dev.chojo.ember.api.refusal.RefusalResponse naming a question whose default it would refuse
     */
    public void replaceQuestions(int eventGroupId, List<RegistrationFieldDraft> fields) {
        var asked = repository.findByEventGroup(eventGroupId).stream()
                .map(field -> new Asked(field.name(), field.fieldType()))
                .collect(Collectors.toSet());
        requireDistinctNames(fields);
        EventRegistrationFieldService.requireOffered(fields, asked);
        EventRegistrationFieldService.requireUsableDefaults(
                fields, eligibility, EventRefusal.REGISTRATION_QUESTION_DEFAULT_NOT_ACCEPTED);
        repository.replaceEventGroupFields(eventGroupId, fields);
        log.info("Event group {} now asks {} question(s)", eventGroupId, fields.size());
    }

    private static void requireDistinctNames(List<RegistrationFieldDraft> fields) {
        var seen = new HashSet<String>();
        for (var field : fields) {
            if (!seen.add(field.name())) throw EventRefusal.EVENT_GROUP_QUESTION_NAME_TWICE.raise(field.name());
        }
    }

    /**
     * The ids of the questions of an event group that have to be answered.
     *
     * @param fields the event group's questions
     * @return the ids of the required ones
     */
    public static Set<Integer> requiredIds(List<EventRegistrationField> fields) {
        return fields.stream()
                .filter(field -> field.config().required())
                .map(EventRegistrationField::id)
                .collect(Collectors.toSet());
    }

    /**
     * Drops the questions an appointment asked of its own, once it becomes a part of an event group. From
     * then on the event group asks, and a question nobody can answer any more would leave everybody short.
     *
     * @param eventId the appointment
     */
    public void dropQuestionsOfPart(int eventId) {
        repository.deleteEventFields(eventId);
    }

    /**
     * Forgets the answers of a sign-up that was taken back. Signing up again asks anew.
     *
     * @param eventGroupRegistrationId the event group sign-up
     */
    public void forgetAnswers(int eventGroupRegistrationId) {
        repository.deleteEventGroupAnswers(eventGroupRegistrationId);
    }

    /**
     * Resolves what a new sign-up carries for the event group's questions, before anything is written.
     *
     * @param eventGroupId the event group
     * @param answers  the answers keyed by question id, as submitted
     * @return the value to store per question id
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when a question is unanswered, unknown, or
     *                                                     answered out of range
     */
    public Map<Integer, String> resolveAnswers(int eventGroupId, Map<Integer, String> answers) {
        var fields = repository.findByEventGroup(eventGroupId);
        if (fields.isEmpty() && answers.isEmpty()) return Map.of();
        return EventRegistrationFieldService.validate(fields, answers, eligibility);
    }

    /**
     * Writes the answers of a sign-up, dropping whatever it carried before. A sign-up taken up again
     * after a withdrawal starts from the answers given this time.
     *
     * @param eventGroupRegistrationId the event group sign-up
     * @param resolved             the answers as {@link #resolveAnswers} returned them
     */
    public void writeAnswers(int eventGroupRegistrationId, Map<Integer, String> resolved) {
        for (var stored : repository.findEventGroupAnswers(List.of(eventGroupRegistrationId))) {
            if (!resolved.containsKey(stored.fieldId())) {
                repository.deleteEventGroupAnswer(eventGroupRegistrationId, stored.fieldId());
            }
        }
        resolved.forEach((fieldId, value) -> repository.setEventGroupAnswer(eventGroupRegistrationId, fieldId, value));
    }

    /**
     * Replaces the answers of a standing sign-up. Only the questions whose answers the caller may read
     * take part; an answer they were never shown survives their edit. A member already named stays
     * named although they have since left the group the question is narrowed to.
     *
     * @param eventGroupId             the event group
     * @param eventGroupRegistrationId the event group sign-up
     * @param answers              the answers keyed by question id, as submitted
     * @param readsHiddenAnswers   whether the caller may read the answers kept for organisers
     * @throws dev.chojo.ember.api.refusal.RefusalResponse when a question is unanswered, unknown, or
     *                                                     answered out of range
     */
    public void replaceAnswers(
            int eventGroupId, int eventGroupRegistrationId, Map<Integer, String> answers, boolean readsHiddenAnswers) {
        var stored = repository.findEventGroupAnswers(List.of(eventGroupRegistrationId));
        var fields = repository.findByEventGroup(eventGroupId).stream()
                .filter(field -> readsHiddenAnswers || !field.config().managersOnly())
                .toList();
        var keeping = NamedAlready.in(
                stored.stream().map(RegistrationFieldValue::value).toList(), eligibility);
        var resolved = EventRegistrationFieldService.validate(fields, answers, keeping);
        var editable = fields.stream().map(EventRegistrationField::id).collect(Collectors.toSet());
        for (var value : stored) {
            if (editable.contains(value.fieldId()) && !resolved.containsKey(value.fieldId())) {
                repository.deleteEventGroupAnswer(eventGroupRegistrationId, value.fieldId());
            }
        }
        resolved.forEach((fieldId, value) -> repository.setEventGroupAnswer(eventGroupRegistrationId, fieldId, value));
    }

    /**
     * The answers of many sign-ups, grouped by event group sign-up, so a whole list is read at once.
     *
     * @param eventGroupRegistrationIds the event group sign-ups
     * @return the answers per event group sign-up, empty entries omitted
     */
    public Map<Integer, List<RegistrationFieldValue>> answersBySignUp(List<Integer> eventGroupRegistrationIds) {
        return repository.findEventGroupAnswers(eventGroupRegistrationIds).stream()
                .collect(Collectors.groupingBy(RegistrationFieldValue::registrationId));
    }
}
