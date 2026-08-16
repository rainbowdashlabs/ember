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
import dev.chojo.ember.feature.events.repository.EventTemplateRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventRegistrationFieldServiceTest extends RepositoryTestBase {
    private static EventRegistrationFieldService service;
    private static EventRegistrationFieldRepository repository;
    private static EventTemplateRepository templateRepository;
    private static EventCrudService crudService;
    private static EventRegistrationService registrationService;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static StationEvent event;

    @BeforeAll
    static void setup() {
        var services = newEventServices(new DomainEventBus(Set.of()));
        crudService = services.crud();
        registrationService = services.registration();
        repository = new EventRegistrationFieldRepository();
        templateRepository = new EventTemplateRepository();
        service = new EventRegistrationFieldService(repository);

        station = stationRepo.create("RegFieldStation");
        account = accountRepo.create("reg-field@test.com", "Reg", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());

        var start = Instant.now().plus(1, ChronoUnit.DAYS);
        event = crudService.create(
                station.id(),
                "Benefiz-Marathon",
                "Staffellauf",
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(4, ChronoUnit.HOURS),
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

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    private static void seedFields() {
        service.replaceFields(
                event.id(),
                List.of(
                        new FieldEntry(
                                "Shirtgröße",
                                EventFieldType.ENUM,
                                new EventRegistrationFieldConfig(
                                        true, "M", List.of("S", "M", "L"), null, null, null, null, null),
                                true),
                        new FieldEntry(
                                "Begleitpersonen",
                                EventFieldType.NUMBER,
                                new EventRegistrationFieldConfig(false, "0", null, 0, 5, null, null, null),
                                true)));
    }

    private static int fieldId(String name) {
        return service.findByEvent(event.id()).stream()
                .filter(f -> f.name().equals(name))
                .findFirst()
                .orElseThrow()
                .id();
    }

    @Test
    @Order(1)
    void replaceFieldsStoresQuestionsInOrder() {
        seedFields();
        var fields = service.findByEvent(event.id());
        assertEquals(2, fields.size());
        assertEquals("Shirtgröße", fields.get(0).name());
        assertEquals(0, fields.get(0).position());
        assertTrue(fields.get(0).config().required());
        assertEquals(List.of("S", "M", "L"), fields.get(0).config().options());
        assertEquals(5, fields.get(1).config().max());
    }

    @Test
    @Order(2)
    void eventWithoutQuestionsResolvesNothing() {
        var other = crudService.create(
                station.id(),
                "Ohne Fragen",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(2, ChronoUnit.DAYS),
                Instant.now().plus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        assertTrue(service.resolveAnswers(other.id(), Map.of()).isEmpty());
        crudService.delete(other.id());
    }

    @Test
    @Order(3)
    void defaultsFillUnansweredQuestions() {
        var resolved = service.resolveAnswers(event.id(), Map.of());
        assertEquals("M", resolved.get(fieldId("Shirtgröße")));
        assertEquals("0", resolved.get(fieldId("Begleitpersonen")));
    }

    @Test
    @Order(4)
    void requiredQuestionWithoutDefaultIsRefused() {
        service.replaceFields(
                event.id(),
                List.of(new FieldEntry(
                        "Startnummer",
                        EventFieldType.STRING,
                        new EventRegistrationFieldConfig(true, null, null, null, null, null, null, null),
                        true)));
        assertThrows(BadRequestResponse.class, () -> service.resolveAnswers(event.id(), Map.of()));
        seedFields();
    }

    @Test
    @Order(5)
    void valueOutsideOptionsIsRefused() {
        assertThrows(
                BadRequestResponse.class,
                () -> service.resolveAnswers(event.id(), Map.of(fieldId("Shirtgröße"), "XXXL")));
    }

    @Test
    @Order(6)
    void numberOutsideRangeIsRefused() {
        int guests = fieldId("Begleitpersonen");
        assertThrows(BadRequestResponse.class, () -> service.resolveAnswers(event.id(), Map.of(guests, "9")));
        assertThrows(BadRequestResponse.class, () -> service.resolveAnswers(event.id(), Map.of(guests, "-1")));
        assertThrows(BadRequestResponse.class, () -> service.resolveAnswers(event.id(), Map.of(guests, "drei")));
    }

    @Test
    @Order(7)
    void unknownQuestionIsRefused() {
        assertThrows(BadRequestResponse.class, () -> service.resolveAnswers(event.id(), Map.of(-1, "x")));
    }

    @Test
    @Order(8)
    void answersAreStoredPerRegistrationDate() {
        LocalDate june = LocalDate.now().plusMonths(1).withDayOfMonth(8);
        LocalDate july = june.plusMonths(1);
        int size = fieldId("Shirtgröße");
        int guests = fieldId("Begleitpersonen");

        var first = registrationService.register(event.id(), member.id(), june, true, null);
        service.persistAnswers(first.id(), service.resolveAnswers(event.id(), Map.of(size, "L", guests, "2")));
        var second = registrationService.register(event.id(), member.id(), july, true, null);
        service.persistAnswers(second.id(), service.resolveAnswers(event.id(), Map.of(size, "S", guests, "0")));

        assertEquals(
                "L",
                service.findValues(first.id()).stream()
                        .filter(v -> v.fieldId() == size)
                        .findFirst()
                        .orElseThrow()
                        .value());
        assertEquals(
                "S",
                service.findValues(second.id()).stream()
                        .filter(v -> v.fieldId() == size)
                        .findFirst()
                        .orElseThrow()
                        .value());

        var grouped = service.findValuesByRegistration(List.of(first.id(), second.id()));
        assertEquals(2, grouped.get(first.id()).size());
        assertEquals(2, grouped.get(second.id()).size());
    }

    @Test
    @Order(85)
    void replacingAnswersDropsTheOnesLeftOut() {
        LocalDate day = LocalDate.now().plusMonths(5).withDayOfMonth(6);
        int size = fieldId("Shirtgröße");
        int guests = fieldId("Begleitpersonen");

        var registration = registrationService.register(event.id(), member.id(), day, true, null);
        service.persistAnswers(registration.id(), service.resolveAnswers(event.id(), Map.of(size, "L", guests, "3")));
        assertEquals(2, service.findValues(registration.id()).size());

        service.replaceAnswers(event.id(), registration.id(), Map.of(size, "S"));

        var values = service.findValues(registration.id());
        assertEquals(
                "S",
                values.stream()
                        .filter(v -> v.fieldId() == size)
                        .findFirst()
                        .orElseThrow()
                        .value());
        assertEquals(
                "0",
                values.stream()
                        .filter(v -> v.fieldId() == guests)
                        .findFirst()
                        .orElseThrow()
                        .value(),
                "a question left out of the update falls back to its default, not to the old answer");

        registrationService.withdraw(registration.id());
    }

    @Test
    @Order(86)
    void readingAnswersOfNoRegistrationsAsksNothing() {
        assertTrue(service.findValuesByRegistration(List.of()).isEmpty());
    }

    @Test
    @Order(9)
    void withdrawingARegistrationRemovesItsAnswers() {
        LocalDate day = LocalDate.now().plusMonths(3).withDayOfMonth(4);
        var registration = registrationService.register(event.id(), member.id(), day, true, null);
        service.persistAnswers(
                registration.id(), service.resolveAnswers(event.id(), Map.of(fieldId("Shirtgröße"), "M")));
        assertTrue(!service.findValues(registration.id()).isEmpty());

        registrationService.withdraw(registration.id());
        assertTrue(service.findValues(registration.id()).isEmpty());
    }

    @Test
    @Order(10)
    void questionAddedLaterLeavesExistingRegistrationsValid() {
        LocalDate day = LocalDate.now().plusMonths(4).withDayOfMonth(4);
        var registration = registrationService.register(event.id(), member.id(), day, true, null);
        service.persistAnswers(
                registration.id(), service.resolveAnswers(event.id(), Map.of(fieldId("Shirtgröße"), "M")));
        int before = service.findValues(registration.id()).size();

        service.replaceFields(
                event.id(),
                List.of(new FieldEntry(
                        "Verpflegung",
                        EventFieldType.STRING,
                        new EventRegistrationFieldConfig(true, null, null, null, null, null, null, null),
                        true)));

        assertEquals(
                RegistrationStatus.ACCEPTED,
                registrationService.findById(registration.id()).orElseThrow().status());
        assertTrue(service.findValues(registration.id()).size() <= before);
        seedFields();
    }

    @Test
    @Order(11)
    void templateQuestionsAreCopiedIndependently() {
        var template = templateRepository.create(station.id(), "Marathon-Vorlage");
        service.replaceTemplateFields(
                template.id(),
                List.of(new FieldEntry(
                        "Shirtgröße",
                        EventFieldType.ENUM,
                        new EventRegistrationFieldConfig(
                                true, "M", List.of("S", "M", "L"), null, null, null, null, null),
                        true)));

        var created = crudService.create(
                station.id(),
                "Marathon aus Vorlage",
                "",
                StationEvent.EventType.ONE_TIME,
                null,
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(5, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS),
                null,
                true,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        service.copyTemplateFields(template.id(), created.id());

        var copied = service.findByEvent(created.id());
        assertEquals(1, copied.size());
        assertEquals("Shirtgröße", copied.get(0).name());

        service.replaceTemplateFields(template.id(), List.of());
        assertEquals(1, service.findByEvent(created.id()).size());

        crudService.delete(created.id());
        templateRepository.delete(template.id());
    }
}
