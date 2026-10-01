/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Who owes an answer to a registration question, and who is told so.
 *
 * <p>A member named in an appointment field is registered by being named, and owes the answers like
 * anybody who registered themselves.
 */
class RegistrationAnswersOwedTest extends RepositoryTestBase {

    private static EventRegistrationFieldService questions;
    private static EventFieldService appointmentFields;
    private static EventCrudService crudService;
    private static EventRegistrationService registrationService;
    private static Station station;
    private static Account account;
    private static StationMember member;

    private Notifier notifications;
    private RegistrationAnswerReminder reminder;
    private StationEvent event;

    @BeforeAll
    static void setup() {
        var services = newEventServices(new DomainEventBus(Set.of()));
        crudService = services.crud();
        registrationService = services.registration();
        questions = new EventRegistrationFieldService(new EventRegistrationFieldRepository());
        appointmentFields = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberGroupRepo,
                new UserTagService(userTagRepo, memberGroupRepo),
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);

        station = stationRepo.create("AnswersOwedStation");
        account = accountRepo.create("answers-owed@test.com", "Otto", "Offen");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void freshEvent() {
        notifications = mock(Notifier.class);
        reminder = new RegistrationAnswerReminder(
                questions, eventRegistrationRepo, eventRepo, memberNameResolver, notifications);
        var start = Instant.now().plus(3, ChronoUnit.DAYS);
        event = crudService.create(
                station.id(),
                "Ausbildung",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
    }

    private static RegistrationFieldDraft required(String name, boolean managersOnly) {
        return new RegistrationFieldDraft(
                name,
                EventFieldType.STRING,
                new EventRegistrationFieldConfig(true, null, null, null, null, null, null, null, managersOnly),
                true);
    }

    private void verifyTold() {
        verify(notifications)
                .notify(
                        eq(StationAudience.household(List.of(member.id()))),
                        eq(NotificationType.REGISTRATION_ANSWER_MISSING),
                        any(),
                        any());
    }

    private EventRegistration onlyRegistration() {
        var registrations = eventRegistrationRepo.findByEvent(event.id());
        assertEquals(1, registrations.size());
        return registrations.getFirst();
    }

    /**
     * Naming a member in an appointment field registers them, and a required question added
     * afterwards is theirs to answer: they are told, and the registration stays short of the answer.
     */
    @Test
    void aMemberNamedInAFieldOwesTheAnswers() {
        appointmentFields.replaceFields(
                event.id(),
                List.of(new EventFieldDraft(
                        "Ausbilder",
                        EventFieldType.MEMBER,
                        EventFieldConfig.empty(),
                        String.valueOf(member.id()),
                        false,
                        null,
                        false)));
        var named = onlyRegistration();
        assertEquals(RegistrationStatus.ACCEPTED, named.status());

        reminder.replaceQuestions(event.id(), List.of(required("Schwimmabzeichen", false)));

        verifyTold();
        assertTrue(EventRegistrationFieldService.owesAnswer(
                named.status(), questions.requiredFieldIds(event.id()), questions.findValues(named.id())));
    }

    /**
     * A question only the organisers answer is not the member's to answer, so a registration is never
     * short of it and nobody is told about it.
     */
    @Test
    @Disabled("TODO: managers-only questions are still counted as owed")
    void aQuestionOnlyManagersAnswerIsOwedByNobody() {
        registrationService.register(event.id(), member.id(), LocalDate.now().plusDays(3), true, null);

        reminder.replaceQuestions(event.id(), List.of(required("Interne Notiz", true)));

        verify(notifications, never()).notify(any(), any(), any(), any());
        assertTrue(questions.requiredFieldIds(event.id()).isEmpty());
    }
}
