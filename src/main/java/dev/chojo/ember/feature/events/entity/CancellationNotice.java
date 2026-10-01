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
 * What a reader is told about a date that is off: which one, who called it off, why and when.
 *
 * <p>Who the manager was stays out of it. A list or a calendar has no use for the member id, and
 * the reason is what the reader needs.
 *
 * @param date        the date that is off
 * @param cause       who called it off
 * @param reason      the reason a manager gave, null for the check and where none was given
 * @param cancelledAt when it was called off
 */
public record CancellationNotice(
        @Nullable LocalDate date,
        CancellationCause cause,
        @Nullable String reason,
        @Nullable Instant cancelledAt) {}
