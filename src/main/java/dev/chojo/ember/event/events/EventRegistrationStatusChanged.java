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
 * A registration changed state.
 *
 * @param memberName the member's name, carried so the notification means something to an event
 *                   manager, who is notified of somebody else's registration and cannot resolve an
 *                   identifier
 * @param queuePosition the place in line where the registration now waits, or null
 * @param movedUp       whether it moved up from the waiting list into a place that fell free
 * @param shortNotice   whether it moved up less than a day before the start
 * @param decided       whether whoever runs it accepted or denied it, which they need not hear about
 */
public record EventRegistrationStatusChanged(
        int stationId,
        int eventId,
        String eventName,
        int memberId,
        String memberName,
        RegistrationStatus newStatus,
        @Nullable Integer queuePosition,
        boolean movedUp,
        boolean shortNotice,
        boolean decided)
        implements DomainEvent {
    /**
     * A change that is neither a place in line nor a move up.
     *
     * @param stationId  the station
     * @param memberId   the member
     * @param memberName the member's name
     * @param newStatus  the new status
     */
    public EventRegistrationStatusChanged(
            int stationId,
            int eventId,
            String eventName,
            int memberId,
            String memberName,
            RegistrationStatus newStatus) {
        this(stationId, eventId, eventName, memberId, memberName, newStatus, null, false, false, false);
    }

    /**
     * Whoever runs it accepted or denied the registration.
     *
     * @param newStatus the decision
     */
    public static EventRegistrationStatusChanged decision(
            int stationId,
            int eventId,
            String eventName,
            int memberId,
            String memberName,
            RegistrationStatus newStatus) {
        return new EventRegistrationStatusChanged(
                stationId, eventId, eventName, memberId, memberName, newStatus, null, false, false, true);
    }
}
