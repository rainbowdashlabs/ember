/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * How a station shows its members beside their names may have changed: a group's or tag's color,
 * its place among the others, or whether a tag shows as a badge. Nobody joins or leaves anything.
 *
 * @param stationId the station
 */
public record MemberDisplayChanged(int stationId) implements DomainEvent {

    /**
     * The look of a station's groups or tags changed.
     *
     * @param stationId the station
     * @return the event
     */
    public static MemberDisplayChanged at(int stationId) {
        return new MemberDisplayChanged(stationId);
    }
}
