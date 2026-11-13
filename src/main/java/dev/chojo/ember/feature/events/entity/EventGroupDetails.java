/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * What a manager sets on an event group besides its sets, parts and audiences.
 *
 * @param name                 the event group name
 * @param description          what it is about, as Markdown
 * @param registrationLimit    how many people it takes, or null for no limit
 * @param registrationDeadline when sign-ups close, or null for no deadline
 * @param requiresConfirmation whether a manager confirms each sign-up
 * @param color                the colour that marks its parts in lists (#RRGGBB), or null for one
 *                             picked by its id
 */
public record EventGroupDetails(
        String name,
        @Nullable String description,
        @Nullable Integer registrationLimit,
        @Nullable Instant registrationDeadline,
        boolean requiresConfirmation,
        @Nullable String color) {}
