/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * The sign-up deadline of an appointment passed with sign-ups still waiting for a decision.
 *
 * @param stationId    the station
 * @param eventId      the appointment
 * @param eventName    its name
 * @param pendingCount how many sign-ups wait
 * @param eventDate    the date whose deadline passed, where a series closes each date on its own;
 *                     null where the appointment closes once
 */
public record RegistrationDeadlineExpired(
        int stationId,
        int eventId,
        String eventName,
        int pendingCount,
        @Nullable LocalDate eventDate) implements DomainEvent {}
