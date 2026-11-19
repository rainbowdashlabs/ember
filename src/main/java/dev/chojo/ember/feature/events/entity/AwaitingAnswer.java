/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One date of an appointment still waiting on one member's answer.
 *
 * @param eventId              the appointment
 * @param name                 its name
 * @param eventDate            the date the answer is owed for
 * @param startTime            when that date starts
 * @param registrationClosesAt when sign-up for that date closes
 * @param categoryId           its category, or null
 * @param memberId             the member who owes the answer
 */
public record AwaitingAnswer(
        int eventId,
        String name,
        LocalDate eventDate,
        Instant startTime,
        Instant registrationClosesAt,
        @Nullable Integer categoryId,
        int memberId) {}
