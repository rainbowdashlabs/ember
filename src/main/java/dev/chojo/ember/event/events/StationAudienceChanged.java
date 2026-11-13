/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import org.jspecify.annotations.Nullable;

/**
 * Who belongs to a station, one of its groups or tags, or who may use messages may have changed,
 * so the members of the station's chats are to be looked at again.
 *
 * @param stationId the station whose people changed, or 0 where only an account changed
 * @param accountId the account whose own state changed, which concerns every station it is a member
 *                  of, or null where the change is the station's
 */
public record StationAudienceChanged(
        int stationId, @Nullable Integer accountId) implements DomainEvent {

    /**
     * The people of a station changed: somebody joined, left, changed type, or a group, tag or
     * guardian link changed.
     *
     * @param stationId the station
     * @return the event
     */
    public static StationAudienceChanged at(int stationId) {
        return new StationAudienceChanged(stationId, null);
    }

    /**
     * Something about an account changed that holds at every station it is a member of, such as
     * whether it may use messages.
     *
     * @param accountId the account
     * @return the event
     */
    public static StationAudienceChanged ofAccount(int accountId) {
        return new StationAudienceChanged(0, accountId);
    }
}
