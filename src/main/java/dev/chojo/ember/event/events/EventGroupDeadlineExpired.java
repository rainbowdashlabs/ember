/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Sign-ups for an event group closed while some still wait for a decision.
 *
 * @param stationId    the station
 * @param eventGroupId     the event group
 * @param eventGroupName   its name
 * @param pendingCount how many sign-ups wait for a decision
 */
public record EventGroupDeadlineExpired(int stationId, int eventGroupId, String eventGroupName, int pendingCount)
        implements DomainEvent {}
