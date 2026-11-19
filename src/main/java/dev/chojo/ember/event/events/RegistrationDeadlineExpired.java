/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.notifications.entity.NotificationData.NotificationLink;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * The sign-up deadline of an appointment or an event group passed with sign-ups still waiting for a
 * decision.
 *
 * @param stationId    the station
 * @param name         the appointment or event group, as the notice names it
 * @param pendingCount how many sign-ups wait
 * @param date         the date whose deadline passed, where a series closes each date on its own;
 *                     null for anything that closes once
 * @param link         where the notice leads: the sign-ups of the appointment, or the event group
 */
public record RegistrationDeadlineExpired(
        int stationId,
        String name,
        int pendingCount,
        @Nullable LocalDate date,
        NotificationLink link) implements DomainEvent {}
