/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.EventRegistrationField;
import dev.chojo.ember.feature.events.entity.RegistrationFieldDraft;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRegistrationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.repository.EventRegistrationFieldRepository;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerSignUp;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.members.entity.MemberTable;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.service.MemberTableService;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The table of an event group's sign-ups: one row per standing sign-up, the station's own first with
 * their answers, then the partner stations' named with their station, and a column per part marked
 * where it was chosen. Questions kept for organisers appear only for those who may read them.
 */
class EventGroupSignUpTableTest extends RepositoryTestBase {
    private static Station station;
    private static StationMember anna;
    private static StationMember ben;
    private static StationMember cara;
    private static final List<Integer> accounts = new ArrayList<>();

    private EventGroupRegistrationService registrations;
    private EventGroupQuestionService questions;
    private EventGroupPartnerService partners;
    private EventGroupSignUpTable table;
    private EventGroup eventGroup;
    private int saturday;
    private int sunday;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Table Station");
        anna = member("anna@group-table.test", "Anna");
        ben = member("ben@group-table.test", "Ben");
        cara = member("cara@group-table.test", "Cara");
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accounts.forEach(accountRepo::delete);
    }

    private static StationMember member(String mail, String name) {
        var account = accountRepo.create(mail, name, "Tabelle");
        accounts.add(account.id());
        return stationMemberRepo.create(station.id(), account.id());
    }

    @BeforeEach
    void wire() {
        var eventGroups = newEventGroupService();
        questions = new EventGroupQuestionService(new EventRegistrationFieldRepository(), memberEligibility);
        registrations = new EventGroupRegistrationService(
                new EventGroupRepository(),
                new EventGroupRegistrationRepository(),
                eventRegistrationRepo,
                newEventServices(new DomainEventBus(Set.of())).registration(),
                mock(DomainEventBus.class),
                memberNameResolver,
                questions,
                newEventGroupSignUpRules());
        partners = mock(EventGroupPartnerService.class);
        table = new EventGroupSignUpTable(
                eventGroups,
                registrations,
                questions,
                partners,
                new MemberTableService(profileFieldRepo, stationMemberRepo));
        eventGroup = eventGroups.create(
                station.id(),
                new EventGroupDetails("Jugendflamme 1", null, null, null, false, null),
                List.of(new EventGroupSetDraft(null, "Übung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
        int setId = eventGroups.findSets(eventGroup.id()).getFirst().id();
        saturday = part(eventGroups, setId, "Samstag", 20);
        sunday = part(eventGroups, setId, "Sonntag", 21);
    }

    private int part(EventGroupService eventGroups, int setId, String name, int daysAhead) {
        var zone = occurrenceCalendar.zoneOf(station.id());
        var start = LocalDate.now(zone)
                .plusDays(daysAhead)
                .atTime(9, 0)
                .atZone(zone)
                .toInstant();
        var event = eventRepo.create(
                station.id(),
                name,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                null,
                false,
                null,
                null,
                null,
                null,
                null);
        eventGroups.addPart(station.id(), eventGroup.id(), event.id(), setId);
        return event.id();
    }

    private Map<String, Integer> askShirtAndAllergies() {
        var plain = new EventQuestionSettings(
                null, null, null, null, null, false, false, false, null, null, null, false, null);
        var forOrganisers = new EventQuestionSettings(
                null, null, null, null, null, false, false, false, null, null, null, true, null);
        questions.replaceQuestions(
                eventGroup.id(),
                List.of(
                        new RegistrationFieldDraft("T-Shirt", FieldType.TEXT, plain, false),
                        new RegistrationFieldDraft("Allergien", FieldType.TEXT, forOrganisers, false)));
        return questions.findByEventGroup(eventGroup.id()).stream()
                .collect(Collectors.toMap(EventRegistrationField::name, EventRegistrationField::id));
    }

    private static List<String> labels(MemberTable drawn) {
        return drawn.columns().stream()
                .map(MemberTable.MemberTableHeader::label)
                .toList();
    }

    private static List<String> rowOf(MemberTable drawn, String name) {
        return drawn.rows().stream()
                .map(MemberTable.MemberTableRow::values)
                .filter(values -> values.getFirst().contains(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void everyStandingSignUpIsARowWithItsAnswersAndItsParts() {
        var asked = askShirtAndAllergies();
        registrations.register(
                station.id(),
                eventGroup.id(),
                anna.id(),
                Set.of(saturday, sunday),
                Map.of(asked.get("T-Shirt"), "M", asked.get("Allergien"), "Nüsse"),
                null,
                false);
        registrations.register(
                station.id(),
                eventGroup.id(),
                ben.id(),
                Set.of(sunday),
                Map.of(asked.get("T-Shirt"), "L"),
                null,
                false);
        registrations.register(station.id(), eventGroup.id(), cara.id(), Set.of(saturday), Map.of(), null, false);
        registrations.withdraw(station.id(), eventGroup.id(), cara.id());
        when(partners.signUps(station.id(), eventGroup.id()))
                .thenReturn(List.of(new PartnerSignUp(
                        4,
                        new MemberIdentity(
                                UUID.randomUUID(), UUID.randomUUID(), "Mara Seeger", "Wache Nord", null, null),
                        new PartnerStanding(
                                UUID.randomUUID(),
                                RegistrationStatus.ACCEPTED,
                                Instant.now(),
                                List.of(saturday),
                                List.of()))));

        var drawn = table.table(station, eventGroup.id(), Set.of(StationPermission.EVENT_REGISTRATION), false);

        var columns = labels(drawn);
        assertTrue(columns.contains("T-Shirt"));
        assertFalse(columns.contains("Allergien"), "a question kept for organisers stays out");
        assertTrue(columns.get(columns.size() - 2).startsWith("Samstag "));
        assertTrue(columns.getLast().startsWith("Sonntag "));
        assertEquals(3, drawn.rows().size(), "the withdrawn sign-up is left out");

        var annasRow = rowOf(drawn, "Anna");
        assertEquals("ACCEPTED", annasRow.get(1));
        assertEquals("M", annasRow.get(columns.indexOf("T-Shirt")));
        assertEquals(
                List.of(EventGroupSignUpTable.CHOSEN, EventGroupSignUpTable.CHOSEN),
                annasRow.subList(annasRow.size() - 2, annasRow.size()));
        var bensRow = rowOf(drawn, "Ben");
        assertEquals(List.of("", EventGroupSignUpTable.CHOSEN), bensRow.subList(bensRow.size() - 2, bensRow.size()));
        var marasRow = rowOf(drawn, "Mara Seeger (Wache Nord)");
        assertEquals("ACCEPTED", marasRow.get(1));
        assertEquals("", marasRow.get(columns.indexOf("T-Shirt")));
        assertEquals(List.of(EventGroupSignUpTable.CHOSEN, ""), marasRow.subList(marasRow.size() - 2, marasRow.size()));
    }

    @Test
    void anOrganiserReadsTheQuestionsKeptForThem() {
        var asked = askShirtAndAllergies();
        registrations.register(
                station.id(),
                eventGroup.id(),
                anna.id(),
                Set.of(saturday),
                Map.of(asked.get("Allergien"), "Nüsse"),
                null,
                false);
        when(partners.signUps(station.id(), eventGroup.id())).thenReturn(List.of());

        var drawn = table.table(station, eventGroup.id(), Set.of(StationPermission.EVENT_EDIT), true);

        assertEquals("Nüsse", rowOf(drawn, "Anna").get(labels(drawn).indexOf("Allergien")));
    }
}
