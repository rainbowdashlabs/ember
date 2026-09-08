/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository.FieldEntry;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Telling whoever a question added to an appointment has left owing an answer.
 *
 * <p>Questions are edited on an appointment at any time, and a registration given before a question
 * existed carries no answer to it. Nobody notices that by themselves: the member is registered and
 * the list simply has a hole in it. This is the one moment where that can be said, so it is said
 * here.
 */
@Singleton
public class RegistrationAnswerReminder {
    private static final Logger log = LoggerFactory.getLogger(RegistrationAnswerReminder.class);

    private final EventRegistrationFieldService fieldService;
    private final EventRegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberNameResolver memberNameResolver;
    private final NotificationService notificationService;

    @Inject
    public RegistrationAnswerReminder(
            EventRegistrationFieldService fieldService,
            EventRegistrationRepository registrationRepository,
            EventRepository eventRepository,
            StationMemberRepository stationMemberRepository,
            MemberNameResolver memberNameResolver,
            NotificationService notificationService) {
        this.fieldService = fieldService;
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberNameResolver = memberNameResolver;
        this.notificationService = notificationService;
    }

    /**
     * Replaces the questions an appointment asks, and tells everybody the change leaves owing an
     * answer.
     *
     * <p>Who owed one before is read first, so somebody who was already short of an answer is not
     * told again every time the questions are saved. The notification marks the change; the mark on
     * the registration says the state for as long as it lasts.
     *
     * @param eventId the appointment whose questions are being replaced
     * @param fields  the questions it asks from now on
     */
    public void replaceQuestions(int eventId, List<FieldEntry> fields) {
        var owedBefore = owing(eventId).stream().map(EventRegistration::id).collect(Collectors.toSet());
        fieldService.replaceFields(eventId, fields);
        var newlyOwing = owing(eventId).stream()
                .filter(registration -> !owedBefore.contains(registration.id()))
                .toList();
        if (newlyOwing.isEmpty()) return;

        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return;
        for (var registration : newlyOwing) {
            tell(event, registration);
        }
        log.info(
                "{} registration(s) of event {} now owe an answer to a question it has just gained",
                newlyOwing.size(),
                eventId);
    }

    /**
     * The registrations of an appointment that are short of an answer.
     *
     * <p>Read for the whole appointment at once: the questions once, the answers of every
     * registration in one go.
     */
    private List<EventRegistration> owing(int eventId) {
        var required = fieldService.requiredFieldIds(eventId);
        if (required.isEmpty()) return List.of();
        var registrations = registrationRepository.findByEvent(eventId);
        if (registrations.isEmpty()) return List.of();
        var answers = fieldService.findValuesByRegistration(
                registrations.stream().map(EventRegistration::id).toList());
        return registrations.stream()
                .filter(registration -> EventRegistrationFieldService.owesAnswer(
                        registration.status(), required, answers.getOrDefault(registration.id(), List.of())))
                .toList();
    }

    /**
     * Tells one member, and everyone who answers for them, that the appointment now wants something
     * from them. The name rides along because a guardian has to know which of their children it is
     * about.
     */
    private void tell(StationEvent event, EventRegistration registration) {
        Set<Integer> audience = new HashSet<>();
        audience.add(registration.memberId());
        for (StationMember manager : stationMemberRepository.findManagers(registration.memberId())) {
            audience.add(manager.id());
        }
        notificationService.notifyMembersIfAbsent(
                audience,
                NotificationType.REGISTRATION_ANSWER_MISSING,
                NotificationData.of(
                        new NotificationParams.RegistrationAnswerMissing(
                                event.name(),
                                registration.eventDate(),
                                memberNameResolver.resolveLocal(registration.memberId())),
                        NotificationLinks.eventDate(event.id(), registration.eventDate())),
                -1);
    }
}
