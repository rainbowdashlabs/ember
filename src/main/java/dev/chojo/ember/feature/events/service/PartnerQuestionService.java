/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationFieldValue;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.PartnerAnswerRepository;
import dev.chojo.ember.feature.question.MemberEligibility;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The registration questions of a shared appointment as partner stations' members answer them, and the
 * answers they gave.
 *
 * <p>A partner's member is asked every question this station's own members are asked, with the same
 * rules, except the ones whose answer names members of this station: nobody at a partner can pick from
 * them, so those questions are neither shown nor required of a partner.
 *
 * <p>A partner on an older version sends no answers at all. Its sign-up is taken as one that answered
 * nothing: where no question is required that is a sign-up like any other, with the defaults filled in;
 * where one is, it is refused with {@link EventRefusal#PARTNER_REQUIRED_ANSWER_MISSING}, since this
 * station's own members cannot sign up without that answer either.
 */
@Singleton
public class PartnerQuestionService {
    private final EventRegistrationFieldRepository fields;
    private final PartnerAnswerRepository answers;
    private final MemberEligibility eligibility;

    @Inject
    public PartnerQuestionService(
            EventRegistrationFieldRepository fields, PartnerAnswerRepository answers, MemberEligibility eligibility) {
        this.fields = fields;
        this.answers = answers;
        this.eligibility = eligibility;
    }

    /**
     * The questions a partner's member answers on signing up for an appointment.
     *
     * @param eventId the appointment
     * @return its questions in their order, leaving out those whose answer names members
     */
    public List<EventRegistrationField> askedOfPartners(int eventId) {
        return fields.findByEvent(eventId).stream()
                .filter(field -> field.fieldType().isWritten())
                .filter(field -> !field.fieldType().namesMembers())
                .toList();
    }

    /**
     * Checks what a partner's member answered before anything is written, applying the defaults.
     *
     * @param eventId the appointment
     * @param given   the answers as sent, or null where the partner sent none
     * @return the value to store per question id
     */
    public Map<Integer, String> resolve(int eventId, @Nullable List<EventRegistrationFieldValue> given) {
        var asked = askedOfPartners(eventId);
        if (asked.isEmpty() && (given == null || given.isEmpty())) return Map.of();
        var byField = given == null
                ? Map.<Integer, String>of()
                : given.stream()
                        .collect(Collectors.toMap(
                                EventRegistrationFieldValue::fieldId,
                                EventRegistrationFieldValue::value,
                                (first, second) -> second));
        for (var field : asked) {
            if (field.config().required()
                    && isBlank(byField.get(field.id()))
                    && isBlank(field.config().defaultValue())) {
                throw EventRefusal.PARTNER_REQUIRED_ANSWER_MISSING.raise(field.name());
            }
        }
        return EventRegistrationFieldService.validate(asked, byField, eligibility);
    }

    /**
     * Writes the answers of a partner's registration, replacing whatever it carried before.
     *
     * @param federationRegistrationId the partner's registration
     * @param resolved                 the answers as {@link #resolve} returned them
     */
    public void store(int federationRegistrationId, Map<Integer, String> resolved) {
        answers.setAnswers(federationRegistrationId, resolved);
    }

    /**
     * The answers partners' members gave for an appointment of a station.
     *
     * <p>Answers to a question kept for organisers are left out for a reader who is not one, as they are
     * for the answers of this station's own members.
     *
     * @param stationId          the station asking, which has to hold the appointment
     * @param eventId            the appointment
     * @param readsHiddenAnswers whether the reader may read the answers kept for organisers
     * @return the answers per partner registration on every date, registrations without answers left out
     */
    public Map<Integer, List<EventRegistrationFieldValue>> answers(
            int stationId, int eventId, boolean readsHiddenAnswers) {
        var hidden = readsHiddenAnswers
                ? Set.<Integer>of()
                : fields.findByEvent(eventId).stream()
                        .filter(field -> field.config().managersOnly())
                        .map(EventRegistrationField::id)
                        .collect(Collectors.toSet());
        return answers.findForEvent(stationId, eventId).stream()
                .filter(value -> !hidden.contains(value.fieldId()))
                .collect(Collectors.groupingBy(
                        RegistrationFieldValue::registrationId,
                        Collectors.mapping(
                                value -> new EventRegistrationFieldValue(value.fieldId(), value.value()),
                                Collectors.toList())));
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }
}
