/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldConfig;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository.FieldEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * A question added to an appointment somebody has already signed up for.
 */
class RegistrationAnswerReminderTest extends RepositoryTestBase {

    private static EventRegistrationFieldService fieldService;
    private static EventCrudService crudService;
    private static EventRegistrationService registrationService;
    private static Station station;
    private static Account account;
    private static StationMember member;

    private NotificationService notifications;
    private RegistrationAnswerReminder reminder;
    private StationEvent event;

    @BeforeAll
    static void setup() {
        var services = newEventServices(new DomainEventBus(Set.of()));
        crudService = services.crud();
        registrationService = services.registration();
        fieldService = new EventRegistrationFieldService(new EventRegistrationFieldRepository());

        station = stationRepo.create("AnswerReminderStation");
        account = accountRepo.create("answer-reminder@test.com", "Anna", "Antwort");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @BeforeEach
    void freshEvent() {
        notifications = mock(NotificationService.class);
        reminder = new RegistrationAnswerReminder(
                fieldService, eventRegistrationRepo, eventRepo, stationMemberRepo, memberNameResolver, notifications);
        var start = Instant.now().plus(3, ChronoUnit.DAYS);
        event = crudService.create(
                station.id(),
                "Zeltlager",
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

    private static FieldEntry required(String name) {
        return new FieldEntry(
                name,
                EventFieldType.STRING,
                new EventRegistrationFieldConfig(true, null, null, null, null, null, null, null, false),
                true);
    }

    private static FieldEntry optional(String name) {
        return new FieldEntry(
                name,
                EventFieldType.STRING,
                new EventRegistrationFieldConfig(false, null, null, null, null, null, null, null, false),
                true);
    }

    /** What the member is told, which is that this appointment wants something from them. */
    private void verifyToldOnce() {
        verify(notifications)
                .notifyMembersIfAbsent(
                        argThat((Collection<Integer> audience) -> audience.contains(member.id())),
                        eq(NotificationType.REGISTRATION_ANSWER_MISSING),
                        any(NotificationData.class),
                        anyInt());
    }

    /**
     * Somebody who signed up before the question existed is told that it is there, and the second
     * save of the same questions tells them nothing: the notification marks the change, and being
     * short of an answer is a state the screens say for as long as it lasts.
     */
    @Test
    void aQuestionAddedAfterwardsTellsWhoeverIsNowShortOfAnAnswer() {
        registrationService.register(event.id(), member.id(), LocalDate.now().plusDays(3), true, null);

        reminder.replaceQuestions(event.id(), List.of(required("Schwimmabzeichen")));
        verifyToldOnce();

        reminder.replaceQuestions(event.id(), List.of(required("Schwimmabzeichen"), optional("Allergien")));
        verifyToldOnce();
    }

    /** A question nobody has to answer leaves nobody owing anything. */
    @Test
    void anOptionalQuestionTellsNobody() {
        registrationService.register(event.id(), member.id(), LocalDate.now().plusDays(3), true, null);

        reminder.replaceQuestions(event.id(), List.of(optional("Allergien")));

        verify(notifications, never()).notifyMembersIfAbsent(any(), any(), any(), anyInt());
    }

    /** Somebody who answered the question at the moment it appeared is short of nothing. */
    @Test
    void aRegistrationThatCarriesTheAnswerIsLeftAlone() {
        reminder.replaceQuestions(event.id(), List.of(required("Schwimmabzeichen")));
        int field = fieldService.findByEvent(event.id()).getFirst().id();
        var registration = registrationService.register(
                event.id(), member.id(), LocalDate.now().plusDays(3), true, null);
        fieldService.persistAnswers(registration.id(), Map.of(field, "Bronze"));

        reminder.replaceQuestions(event.id(), List.of(required("Schwimmabzeichen"), optional("Allergien")));

        verify(notifications, never()).notifyMembersIfAbsent(any(), any(), any(), anyInt());
    }

    /**
     * Somebody who gave their place up owes nothing, whatever the appointment asks afterwards.
     *
     * <p>The registration itself stays, marked, because a station that gave somebody a place needs
     * to see that they gave it up. What they had answered does not stay with it.
     */
    @Test
    void aWithdrawnRegistrationOwesNothing() {
        var registration = registrationService.register(
                event.id(), member.id(), LocalDate.now().plusDays(3), true, null);
        registrationService.withdraw(registration.id());

        reminder.replaceQuestions(event.id(), List.of(required("Schwimmabzeichen")));

        verify(notifications, never()).notifyMembersIfAbsent(any(), any(), any(), anyInt());
        assertEquals(
                RegistrationStatus.WITHDRAWN,
                registrationService.findById(registration.id()).orElseThrow().status());
    }
}
