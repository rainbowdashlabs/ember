/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Lightweight picker result row. Exposes only the public UUID - never the internal id.
 *
 * @param categoryName the name of the event's category, or {@code null} where it has none
 */
public record PickerEvent(
        UUID eventUid,
        String name,
        Instant startTime,
        @Nullable String categoryName) {}
