/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * What a listed appointment says about the event group it is a part of: "Jugendflamme 1, part 2 of 4".
 *
 * @param eventGroupId   the event group
 * @param eventGroupName its name
 * @param setId      the set the part belongs to
 * @param setName    the set's name
 * @param number     the part's place among the event group's parts in date order, from one
 * @param count      how many parts the event group has
 * @param color      the event group's colour (#RRGGBB), or null for one picked by its id
 * @param firstStart when the event group's first part starts
 * @param lastEnd    when the event group's last part ends
 */
public record EventGroupPartSummary(
        int eventGroupId,
        String eventGroupName,
        int setId,
        String setName,
        int number,
        int count,
        @Nullable String color,
        Instant firstStart,
        Instant lastEnd) {}
