/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A family ended a trial themselves. The entry is gone by the time this travels, so the station
 * learns of it only from here.
 */
public record WaitlistTrialEnded(int stationId, String applicantName, String listName) implements DomainEvent {}
