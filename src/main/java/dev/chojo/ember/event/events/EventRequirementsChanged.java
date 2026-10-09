/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

import java.util.List;

/**
 * The documents an appointment asks participants to bring were changed after it was made.
 *
 * @param stationId the station the appointment belongs to
 * @param eventId   the appointment
 * @param added     the templates it asks for now and did not before
 * @param removed   the templates it asked for before and no longer does
 */
public record EventRequirementsChanged(int stationId, int eventId, List<Integer> added, List<Integer> removed)
        implements DomainEvent {}
