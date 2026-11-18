/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.EventChanged;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.OccurrenceCalendar;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * Moves people up from the waiting lists of an appointment's coming dates once its participant limit
 * is raised or removed, as many as now fit. A lowered limit takes nobody's place: moving up simply
 * waits until the count is below it again.
 *
 * <p>The registration service is asked for when a change arrives, because it is built on services
 * that publish events themselves.
 */
@Singleton
public class EventLimitRaisedHandler implements DomainEventHandler<EventChanged> {
    private final Provider<EventRegistrationService> registrations;
    private final OccurrenceCalendar calendar;

    /**
     * Creates the handler.
     *
     * @param registrations the registrations, asked for when a limit is raised
     * @param calendar      the station's clock, for which dates are still to come
     */
    @Inject
    public EventLimitRaisedHandler(Provider<EventRegistrationService> registrations, OccurrenceCalendar calendar) {
        this.registrations = registrations;
        this.calendar = calendar;
    }

    @Override
    public Class<EventChanged> eventType() {
        return EventChanged.class;
    }

    @Override
    public void handle(EventChanged event) {
        if (!raised(event.before().registrationLimit(), event.after().registrationLimit())) return;
        var today = LocalDate.now(calendar.zoneOf(event.stationId()));
        registrations.get().moveUpFrom(event.after().id(), today);
    }

    /**
     * Whether a limit went up or went away.
     *
     * @param before the limit before, or null for none
     * @param after  the limit after, or null for none
     * @return true where more places exist now
     */
    static boolean raised(@Nullable Integer before, @Nullable Integer after) {
        if (before == null) return false;
        return after == null || after > before;
    }
}
