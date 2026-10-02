/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.time.LocalDate;

/**
 * A manager brought back one date of an appointment that had been called off.
 *
 * @param stationId the station it belongs to
 * @param eventId   the appointment
 * @param eventName its name
 * @param eventDate the date that takes place again
 */
public record EventDateRestored(int stationId, int eventId, String eventName, LocalDate eventDate)
        implements DomainEvent {}
