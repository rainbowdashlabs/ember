/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A whole event group was called off, every part with it.
 *
 * @param stationId  the station
 * @param eventGroupId   the event group
 * @param eventGroupName its name
 * @param reason     why, as told to the people signed up, or null
 * @param memberIds  the members whose sign-up held or waited for a place
 */
public record EventGroupCancelled(
        int stationId,
        int eventGroupId,
        String eventGroupName,
        @Nullable String reason,
        List<Integer> memberIds) implements DomainEvent {}
