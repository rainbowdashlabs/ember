/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import java.time.Instant;

/** One event still waiting on one member's answer. */
public record AwaitingAnswer(
        int eventId, String name, Instant startTime, Instant registrationDeadline, Integer categoryId, int memberId) {}
