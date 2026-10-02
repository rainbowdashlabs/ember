/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;

import java.time.LocalDate;

/**
 * A member's answer to one date of an appointment was written, by the member or by somebody
 * answering for them.
 *
 * @param stationId the station the appointment belongs to
 * @param eventId   the appointment
 * @param memberId  whose answer it is
 * @param eventDate the date it answers for
 * @param status    what the answer now says
 */
public record EventAnswerRecorded(
        int stationId, int eventId, int memberId, LocalDate eventDate, RegistrationStatus status)
        implements DomainEvent {}
