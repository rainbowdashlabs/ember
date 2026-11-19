/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.events.entity.EventFieldDraft;
import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupContent;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.EventGroupSet;
import dev.chojo.ember.feature.events.entity.EventGroupSetDraft;
import dev.chojo.ember.feature.events.entity.EventQuestionSettings;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.question.FieldType;
import dev.chojo.ember.feature.restriction.RestrictionAudience;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Shaping an event group like a Jugendflamme weekend: three practice days and a test, sorted into two
 * sets. Holds the stations, members and builders the event group service tests share.
 */
abstract class EventGroupServiceTestBase extends RepositoryTestBase {
    protected static Station station;
    protected static Station otherStation;
    protected static StationMember anna;
    protected static StationMember outsider;
    protected static final List<Integer> accounts = new ArrayList<>();

    protected final EventGroupRepository eventGroupRepo = new EventGroupRepository();
    protected EventGroupService service;
    protected Instant deadline;

    @BeforeAll
    static void setup() {
        station = stationRepo.create("Event group Station");
        otherStation = stationRepo.create("Other Event group Station");
        var account = accountRepo.create("anna@eventGroup.test", "Anna", "EventGroup");
        accounts.add(account.id());
        anna = stationMemberRepo.create(station.id(), account.id());
        var otherAccount = accountRepo.create("otto@eventGroup.test", "Otto", "EventGroup");
        accounts.add(otherAccount.id());
        outsider = stationMemberRepo.create(otherStation.id(), otherAccount.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(otherStation.id());
        accounts.forEach(accountRepo::delete);
        accounts.clear();
    }

    @BeforeEach
    void wire() {
        service = newEventGroupService();
        deadline = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.SECONDS);
    }

    protected EventGroupDetails details(String name, boolean confirm) {
        return new EventGroupDetails(name, "Lehrgang", 12, RegistrationDeadline.fixed(deadline), confirm, null);
    }

    protected EventGroup jugendflamme() {
        return service.create(
                station.id(),
                details("Jugendflamme 1", true),
                List.of(new EventGroupSetDraft(null, "Übung", 2), new EventGroupSetDraft(null, "Prüfung", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                null);
    }

    protected EventGroup create(EventGroupDetails details, List<EventGroupSetDraft> sets) {
        return service.create(
                station.id(), details, sets, RestrictionAudience.empty(), RestrictionAudience.empty(), null);
    }

    protected StationEvent appointment(Station at, int daysAhead, StationEvent.EventType type) {
        var start = LocalDate.now().plusDays(daysAhead).atTime(9, 0).toInstant(ZoneOffset.UTC);
        return eventRepo.create(
                at.id(),
                "Tag " + daysAhead,
                null,
                type,
                type == StationEvent.EventType.ONE_TIME ? null : 6,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                RegistrationDeadline.relative(3 * 24 * 60, RegistrationDeadline.Anchor.MIDNIGHT),
                false,
                null,
                5,
                null,
                null);
    }

    protected StationEvent oneTime(int daysAhead) {
        return appointment(station, daysAhead, StationEvent.EventType.ONE_TIME);
    }

    protected EventGroupSet set(EventGroup eventGroup, String name) {
        return service.findSets(eventGroup.id()).stream()
                .filter(set -> set.name().equals(name))
                .findFirst()
                .orElseThrow();
    }

    protected static RestrictionAudience youth() {
        return new RestrictionAudience(
                List.of(StationUserType.MEMBER), List.of(), List.of(), List.of(), RestrictionMode.AND);
    }

    protected static EventFieldDraft valueField(String name, FieldType type, String value, boolean overview) {
        return new EventFieldDraft(null, name, type, EventQuestionSettings.empty(), value, overview, null, true);
    }

    protected EventGroup groupWith(String name, EventFieldDraft... fields) {
        return service.create(
                station.id(),
                details(name, false),
                List.of(new EventGroupSetDraft(null, "Tage", 1)),
                RestrictionAudience.empty(),
                RestrictionAudience.empty(),
                new EventGroupContent(null, List.of(fields)));
    }

    protected EventFieldService fieldService() {
        return new EventFieldService(
                eventFieldRepo,
                stationMemberRepo,
                memberEligibility,
                eventRepo,
                attendanceRepo,
                eventFieldRegistrationService,
                new EventGroupRepository(),
                occurrenceCalendar);
    }
}
