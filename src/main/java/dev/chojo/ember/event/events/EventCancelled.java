/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.CancellationCause;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;

/**
 * An appointment was called off, one date of it or the whole series.
 *
 * @param stationId the station it belongs to
 * @param eventId   the appointment
 * @param eventName its name
 * @param reason    the reason a manager gave, null where none was given or the check called it off
 * @param eventDate the date called off, null where the whole series was
 * @param cause     who called it off
 */
public record EventCancelled(
        int stationId,
        int eventId,
        String eventName,
        @Nullable String reason,
        @Nullable LocalDate eventDate,
        CancellationCause cause)
        implements DomainEvent {

    /** The whole series, called off by a manager. */
    public static EventCancelled series(int stationId, int eventId, String eventName, @Nullable String reason) {
        return new EventCancelled(stationId, eventId, eventName, reason, null, CancellationCause.MANUAL);
    }
}
