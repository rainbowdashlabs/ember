/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * The possible states of an event registration.
 *
 * <p>{@link #QUEUED} is a sign-up waiting on the waiting list of a full appointment or event group. It
 * holds no place and is not a decision yet: it counts neither as coming nor as an answer that says no,
 * and it moves to {@link #ACCEPTED} when a place falls free.
 */
public enum RegistrationStatus {
    PENDING,
    ACCEPTED,
    DENIED,
    DECLINED,
    WITHDRAWN,
    QUEUED;

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

    /**
     * Whether the sign-up still stands: it holds a place, waits for a decision or waits on the waiting
     * list. Such a sign-up can be taken back, and the member cannot sign up a second time beside it.
     *
     * @return true for PENDING, ACCEPTED and QUEUED
     */
    public boolean standing() {
        return this == PENDING || this == ACCEPTED || this == QUEUED;
    }
}
