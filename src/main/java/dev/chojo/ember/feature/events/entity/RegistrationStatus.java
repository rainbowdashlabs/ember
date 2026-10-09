/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * The possible states of an event registration.
 */
public enum RegistrationStatus {
    PENDING,
    ACCEPTED,
    DENIED,
    DECLINED,
    WITHDRAWN;

    /**
     * Whether this answer, as the member's own, says they will not be at the appointment.
     *
     * <p>A refusal always does. A withdrawal only does where the appointment has to be signed up for,
     * because there it gives a place up. Where everybody is expected, the only answer is a refusal, and
     * a withdrawal is that refusal taken back: the member is expected again, exactly as if they had
     * never answered. A denial is the station's word and not the member's, so it is left to the reads
     * that care about it.
     *
     * @param registrationRequired whether the appointment has to be signed up for
     * @return true where the member is not coming
     */
    public boolean saysNotComing(boolean registrationRequired) {
        return this == DECLINED || (this == WITHDRAWN && registrationRequired);
    }
}
