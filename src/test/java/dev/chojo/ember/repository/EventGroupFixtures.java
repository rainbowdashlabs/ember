/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import dev.chojo.ember.feature.events.entity.EventGroup;
import dev.chojo.ember.feature.events.entity.EventGroupDetails;
import dev.chojo.ember.feature.events.entity.RegistrationDeadline;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.restriction.RestrictionMode;

import java.time.Duration;
import java.time.LocalDate;

/**
 * What the event group tests build again and again: a part of an event group, an appointment of its own
 * days ahead sorted into one of the event group's sets, and an event group value for the route tests that
 * hand one out of a mocked service.
 *
 * <p>The parts are written through the shared repositories of {@link RepositoryTestBase}, so only a test
 * extending it may ask for one.
 */
public final class EventGroupFixtures {
    private EventGroupFixtures() {}

    /**
     * A one-time appointment of the event group's station, named {@code Tag <daysAhead>}, from nine in the
     * morning station time for eight hours, added to one set of the event group.
     *
     * @param eventGroup the event group
     * @param setId      the set it joins
     * @param daysAhead  how many days from today it takes place
     * @return the appointment
     */
    public static StationEvent part(EventGroup eventGroup, int setId, int daysAhead) {
        var zone = RepositoryTestBase.occurrenceCalendar.zoneOf(eventGroup.stationId());
        var start = LocalDate.now(zone)
                .plusDays(daysAhead)
                .atTime(9, 0)
                .atZone(zone)
                .toInstant();
        var event = RepositoryTestBase.eventRepo.create(
                eventGroup.stationId(),
                "Tag " + daysAhead,
                null,
                StationEvent.EventType.ONE_TIME,
                null,
                start,
                start.plus(Duration.ofHours(8)),
                null,
                false,
                RegistrationDeadline.none(),
                false,
                null,
                null,
                null,
                null);
        RepositoryTestBase.newEventGroupService().addPart(eventGroup.stationId(), eventGroup.id(), event.id(), setId);
        return event;
    }

    /**
     * A part as {@link #part(EventGroup, int, int)} gives it, in the event group's first set.
     *
     * @param eventGroup the event group
     * @param daysAhead  how many days from today it takes place
     * @return the appointment
     */
    public static StationEvent part(EventGroup eventGroup, int daysAhead) {
        int firstSet = RepositoryTestBase.newEventGroupService()
                .findSets(eventGroup.id())
                .getFirst()
                .id();
        return part(eventGroup, firstSet, daysAhead);
    }

    /**
     * An event group value as a mocked service hands it out: open, without a limit, a deadline or a
     * restriction.
     *
     * @param id        its id
     * @param stationId its station
     * @param name      its name
     * @return the event group
     */
    public static EventGroup eventGroup(int id, int stationId, String name) {
        return eventGroup(id, stationId, new EventGroupDetails(name, null, null, null, false, null, null, null));
    }

    /**
     * An event group value as a mocked service hands it out, open and without a restriction.
     *
     * @param id        its id
     * @param stationId its station
     * @param details   what it is
     * @return the event group
     */
    public static EventGroup eventGroup(int id, int stationId, EventGroupDetails details) {
        return new EventGroup(
                id,
                stationId,
                details,
                RestrictionMode.AND,
                RestrictionMode.AND,
                false,
                null,
                null,
                details.registrationDeadline().at());
    }
}
