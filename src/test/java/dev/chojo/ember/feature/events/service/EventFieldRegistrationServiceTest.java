/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventFieldConfig;
import dev.chojo.ember.feature.events.entity.EventFieldType;
import dev.chojo.ember.feature.events.entity.EventRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFieldRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.question.QuestionValues;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Being named in a question of an appointment is a place on its list. */
class EventFieldRegistrationServiceTest extends RepositoryTestBase {

    private static EventFieldService fieldService;
    private static EventRegistrationService registrationService;
    private static Station station;
    private static int memberA;
    private static int memberB;

    @BeforeAll
    static void setup() {
        fieldService = new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberGroupRepo,
                new UserTagService(userTagRepo, memberGroupRepo),
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService);
        registrationService = new EventRegistrationService(
                eventRegistrationRepo,
                new EventRegistrationFieldRepository(),
                eventRepo,
                new DomainEventBus(Set.of()),
                memberNameResolver);

        station = stationRepo.create("Named In A Field Station");
        var accA = accountRepo.create("a@named.test", "Alice", "Anders");
        var accB = accountRepo.create("b@named.test", "Bob", "Brown");
        memberA = stationMemberRepo.create(station.id(), accA.id()).id();
        memberB = stationMemberRepo.create(station.id(), accB.id()).id();
    }

    /** A one-off on a fixed day, so the date its registrations carry is known to the test. */
    private StationEvent oneTimeEvent(boolean requiresRegistration, boolean requiresConfirmation) {
        Instant start = Instant.now().plus(3, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return eventRepo.create(
                station.id(),
                "Named Event",
                "desc",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(2, ChronoUnit.HOURS),
                null,
                requiresRegistration,
                null,
                requiresConfirmation,
                null,
                null,
                null,
                null,
                null);
    }

    private LocalDate dayOf(StationEvent event) {
        return event.startTime().atZone(ZoneOffset.UTC).toLocalDate();
    }

    private EventField memberField(int eventId, String value, boolean perDate) {
        return eventFieldRepo.create(
                eventId,
                "Fahrer",
                EventFieldType.MEMBER_LIST,
                new EventFieldConfig(null, null, null, null, true, null, perDate),
                value,
                0,
                false,
                null,
                false);
    }

    private Optional<EventRegistration> registrationOf(int eventId, int memberId) {
        return eventRegistrationRepo.findByEvent(eventId).stream()
                .filter(registration -> registration.memberId() == memberId)
                .findFirst();
    }

    @Test
    void namingSomebodyConfirmsThemWithoutAsking() {
        var event = oneTimeEvent(true, true);
        memberField(event.id(), QuestionValues.formatMembers(List.of(memberA)), false);

        eventFieldRegistrationService.reconcile(event.id());

        var registration = registrationOf(event.id(), memberA).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, registration.status());
        assertTrue(registration.fromField());
        assertEquals(dayOf(event), registration.eventDate());
    }

    @Test
    void takingTheNameOutRecordsAWithdrawal() {
        var event = oneTimeEvent(true, false);
        var field = memberField(event.id(), QuestionValues.formatMembers(List.of(memberA)), false);
        eventFieldRegistrationService.reconcile(event.id());

        eventFieldRepo.updateValue(field.id(), "");
        eventFieldRegistrationService.reconcile(event.id());

        var registration = registrationOf(event.id(), memberA).orElseThrow();
        assertEquals(RegistrationStatus.WITHDRAWN, registration.status());
        assertFalse(registration.fromField());
    }

    @Test
    void takingTheNameOutOfAnAppointmentNobodySignsUpForRemovesTheRow() {
        var event = oneTimeEvent(false, false);
        var field = memberField(event.id(), QuestionValues.formatMembers(List.of(memberA)), false);
        eventFieldRegistrationService.reconcile(event.id());
        assertTrue(registrationOf(event.id(), memberA).isPresent());

        eventFieldRepo.updateValue(field.id(), "");
        eventFieldRegistrationService.reconcile(event.id());

        assertTrue(registrationOf(event.id(), memberA).isEmpty());
    }

    @Test
    void aPlaceSomebodyTookThemselvesIsNotTakenOver() {
        var event = oneTimeEvent(true, false);
        registrationService.register(event.id(), memberA, dayOf(event), true, null);
        var field = memberField(event.id(), QuestionValues.formatMembers(List.of(memberA)), false);
        eventFieldRegistrationService.reconcile(event.id());

        assertFalse(registrationOf(event.id(), memberA).orElseThrow().fromField());

        eventFieldRepo.updateValue(field.id(), "");
        eventFieldRegistrationService.reconcile(event.id());

        var registration = registrationOf(event.id(), memberA).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, registration.status());
    }

    @Test
    void aNameThatBelongsToNobodyIsPassedOver() {
        var event = oneTimeEvent(true, false);
        memberField(event.id(), QuestionValues.formatMembers(List.of(memberA, 987654)), false);

        eventFieldRegistrationService.reconcile(event.id());

        assertTrue(registrationOf(event.id(), memberA).isPresent());
        assertTrue(registrationOf(event.id(), 987654).isEmpty());
    }

    @Test
    void beingNamedOverridesARefusal() {
        var event = oneTimeEvent(true, false);
        registrationService.decline(event.id(), memberB, dayOf(event), null);
        memberField(event.id(), QuestionValues.formatMembers(List.of(memberB)), false);

        eventFieldRegistrationService.reconcile(event.id());

        var registration = registrationOf(event.id(), memberB).orElseThrow();
        assertEquals(RegistrationStatus.ACCEPTED, registration.status());
        assertTrue(registration.fromField());
    }

    @Test
    void aPlaceGivenByAQuestionCannotBeTakenBackFromTheList() {
        var event = oneTimeEvent(true, false);
        memberField(event.id(), QuestionValues.formatMembers(List.of(memberA)), false);
        eventFieldRegistrationService.reconcile(event.id());
        int registrationId = registrationOf(event.id(), memberA).orElseThrow().id();

        assertThrows(BadRequestResponse.class, () -> registrationService.withdraw(registrationId));
        assertThrows(BadRequestResponse.class, () -> registrationService.refuse(registrationId));
        assertThrows(
                BadRequestResponse.class,
                () -> registrationService.updateStatus(registrationId, RegistrationStatus.DENIED));
    }

    /**
     * A question answered once for a whole series names its people on every date the series has,
     * as far ahead as the calendar reaches.
     */
    @Test
    void aQuestionThatRepeatsItselfReachesEveryDateOfTheSeries() {
        Instant start = Instant.now().minus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        var weekly = eventRepo.create(
                station.id(),
                "Weekly Named Event",
                "desc",
                StationEvent.EventType.RECURRING,
                DayOfWeek.TUESDAY.getValue(),
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
        memberField(weekly.id(), QuestionValues.formatMembers(List.of(memberA)), false);

        eventFieldRegistrationService.reconcile(weekly.id());

        var dates = eventRegistrationRepo.findByEvent(weekly.id()).stream()
                .filter(registration -> registration.memberId() == memberA)
                .map(EventRegistration::eventDate)
                .toList();
        assertTrue(dates.size() > 50);
        assertTrue(dates.stream().allMatch(date -> date.getDayOfWeek() == DayOfWeek.TUESDAY));
        assertTrue(dates.stream().noneMatch(date -> date.isBefore(LocalDate.now(ZoneOffset.UTC))));
    }

    /** The pass that moves the far edge along reaches every appointment that names members. */
    @Test
    void theSweepReachesEveryAppointmentThatNamesMembers() {
        var event = oneTimeEvent(true, false);
        memberField(event.id(), QuestionValues.formatMembers(List.of(memberB)), false);

        assertTrue(eventFieldRegistrationService.reconcileAll() > 0);

        assertTrue(registrationOf(event.id(), memberB).orElseThrow().fromField());
    }

    @Test
    void anAnswerGivenPerDateReachesOnlyThatDate() {
        var event = oneTimeEvent(true, false);
        var field = memberField(event.id(), "", true);
        LocalDate day = dayOf(event);

        fieldService.setValueOn(event.id(), field.id(), day, QuestionValues.formatMembers(List.of(memberA)));

        assertTrue(registrationOf(event.id(), memberA).isPresent());
        assertEquals("", eventFieldRepo.findById(field.id()).orElseThrow().value());
        assertEquals(
                QuestionValues.formatMembers(List.of(memberA)),
                eventFieldRepo.findByIdOn(field.id(), day).orElseThrow().value());
        assertEquals(
                "",
                eventFieldRepo
                        .findByIdOn(field.id(), day.plusDays(1))
                        .orElseThrow()
                        .value());
    }

    @Test
    void anAnswerIsOnlyWrittenPerDateWhereTheQuestionSaysSo() {
        var event = oneTimeEvent(true, false);
        var field = memberField(event.id(), "", false);
        assertThrows(BadRequestResponse.class, () -> fieldService.setValueOn(event.id(), field.id(), dayOf(event), ""));
    }

    @Test
    void savingTheQuestionsAgainKeepsTheAnswersGivenPerDate() {
        var event = oneTimeEvent(true, false);
        var field = memberField(event.id(), "", true);
        LocalDate day = dayOf(event);
        fieldService.setValueOn(event.id(), field.id(), day, QuestionValues.formatMembers(List.of(memberA)));

        fieldService.replaceFields(
                event.id(),
                List.of(new EventFieldRepository.FieldEntry(
                        field.id(),
                        "Fahrer",
                        EventFieldType.MEMBER_LIST,
                        new EventFieldConfig(null, null, null, null, true, null, true),
                        "",
                        false,
                        null,
                        false)));

        assertEquals(
                QuestionValues.formatMembers(List.of(memberA)),
                eventFieldRepo.findByIdOn(field.id(), day).orElseThrow().value());
        assertTrue(registrationOf(event.id(), memberA).orElseThrow().fromField());
    }
}
