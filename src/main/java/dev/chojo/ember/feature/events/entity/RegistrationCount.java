/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.LocalDate;

/**
 * Aggregated registration count for an event on a specific date with a given status.
 *
 * @param eventId   the event ID
 * @param eventDate the event occurrence date
 * @param status    the registration status name
 * @param count     the number of registrations
 */
public record RegistrationCount(int eventId, LocalDate eventDate, RegistrationStatus status, int count) {}
