/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * Creates a new appointment straight into an event group.
 *
 * <p>An appointment created on its own tells the people it is meant for that it exists. A part
 * created from the event group does not: the event group is what people sign up for, and an event group with four
 * days would otherwise announce itself four times, each time as a loose appointment.
 */
@Singleton
public class EventGroupPartFactory {
    private final EventCrudService crud;
    private final EventGroupService eventGroups;

    @Inject
    public EventGroupPartFactory(EventCrudService crud, EventGroupService eventGroups) {
        this.crud = crud;
        this.eventGroups = eventGroups;
    }

    /**
     * Creates a one-time appointment and makes it a part of an event group in one go.
     *
     * @param stationId   the station
     * @param eventGroupId    the event group
     * @param setId       the set it goes into
     * @param name        the appointment's name
     * @param description what it is about, as Markdown, or null
     * @param startTime   when it begins
     * @param endTime     when it ends
     * @return the part
     */
    public EventGroupPart create(
            int stationId,
            int eventGroupId,
            int setId,
            String name,
            @Nullable String description,
            Instant startTime,
            Instant endTime) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        return Transactions.call(() -> {
            var event = crud.createWithoutEvent(
                    stationId,
                    name,
                    description,
                    StationEvent.EventType.ONE_TIME,
                    null,
                    startTime,
                    endTime,
                    null,
                    true,
                    null,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null);
            return eventGroups.addPart(stationId, eventGroupId, event.id(), setId);
        });
    }
}
