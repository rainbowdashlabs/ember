/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * A member has been marked present at an evening they were not marked present at before.
 *
 * <p>Raised on the change rather than on every save, so putting the same sheet in twice counts the
 * evening once.
 */
public record AttendanceRecorded(int stationId, int memberId, int sessionId) implements DomainEvent {}
