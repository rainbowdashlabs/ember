/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.time.LocalDate;
import java.util.Set;

/**
 * Places on dates of an appointment fell free without a member giving them up, such as when a
 * question of the appointment no longer names whoever held them.
 *
 * @param stationId the station the appointment belongs to
 * @param eventId   the appointment
 * @param dates     the dates with a place free again
 */
public record RegistrationPlacesFreed(int stationId, int eventId, Set<LocalDate> dates) implements DomainEvent {}
