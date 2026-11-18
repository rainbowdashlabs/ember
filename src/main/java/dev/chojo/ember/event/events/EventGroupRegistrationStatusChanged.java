/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import org.jspecify.annotations.Nullable;

/**
 * An event group sign-up changed state, for all its parts at once.
 *
 * @param stationId  the station
 * @param eventGroupId   the event group
 * @param eventGroupName its name
 * @param memberId   the member signed up
 * @param memberName the member's name, for whoever runs events and cannot resolve an identifier
 * @param newStatus  the status the sign-up holds now
 * @param queuePosition the place in line where the registration now waits, or null
 * @param movedUp       whether it moved up from the waiting list into a place that fell free
 * @param shortNotice   whether it moved up less than a day before the start
 */
public record EventGroupRegistrationStatusChanged(
        int stationId,
        int eventGroupId,
        String eventGroupName,
        int memberId,
        String memberName,
        RegistrationStatus newStatus,
        @Nullable Integer queuePosition,
        boolean movedUp,
        boolean shortNotice)
        implements DomainEvent {
    /**
     * A change that is neither a place in line nor a move up.
     *
     * @param stationId  the station
     * @param memberId   the member
     * @param memberName the member's name
     * @param newStatus  the new status
     */
    public EventGroupRegistrationStatusChanged(
            int stationId,
            int eventGroupId,
            String eventGroupName,
            int memberId,
            String memberName,
            RegistrationStatus newStatus) {
        this(stationId, eventGroupId, eventGroupName, memberId, memberName, newStatus, null, false, false);
    }
}
