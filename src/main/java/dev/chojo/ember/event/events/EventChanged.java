/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;

import java.util.Objects;

/**
 * An appointment has moved, been renamed, or had its series shortened.
 *
 * <p>Until this existed, changing an appointment announced nothing at all: nobody was told, and
 * nothing keyed to a date was cleaned up, which is why a moved appointment left sign-ups on a day it
 * no longer falls on and fired reminders for it. Anything that has to react to a move has to be able
 * to hear about one first.
 *
 * <p>It carries the appointment as it was and as it now stands, because the interesting thing about a
 * move is which day it left.
 *
 * @param before the appointment as it stood
 * @param after  the appointment as it now stands
 */
public record EventChanged(int stationId, StationEvent before, StationEvent after) implements DomainEvent {

    /**
     * Whether the change moved the appointment in time or changed how it repeats.
     *
     * @return {@code true} when the dates it falls on may have changed
     */
    public boolean moved() {
        return !Objects.equals(before.startTime(), after.startTime())
                || !Objects.equals(before.endTime(), after.endTime())
                || !Objects.equals(before.dayOfWeek(), after.dayOfWeek())
                || before.eventType() != after.eventType()
                || !Objects.equals(before.repeatUntil(), after.repeatUntil())
                || !Objects.equals(before.repeatCount(), after.repeatCount());
    }
}
