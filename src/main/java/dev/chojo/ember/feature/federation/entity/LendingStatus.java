/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.federation.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Status lifecycle for inventory lending requests, with the steps between them.
 *
 * <p>A request is asked for, agreed or declined, handed over, checked back and closed. The borrowing
 * station may withdraw it until the gear is handed over, which is the same step as declining it. Gear
 * that is out leaves {@link #LENT} only through the lending station's return check. Every path that
 * moves a request, on this instance or from a partner elsewhere, asks this table.
 */
public enum LendingStatus {
    REQUESTED,
    APPROVED,
    DECLINED,
    LENT,
    RETURNED,
    CLOSED;

    /**
     * The statuses a request may move to this one from.
     *
     * @param next the status asked for
     * @return its allowed predecessors, empty for the first status
     */
    public static Set<LendingStatus> predecessorsOf(LendingStatus next) {
        return switch (next) {
            case REQUESTED -> EnumSet.noneOf(LendingStatus.class);
            case APPROVED -> EnumSet.of(REQUESTED);
            case DECLINED -> EnumSet.of(REQUESTED, APPROVED);
            case LENT -> EnumSet.of(APPROVED);
            case RETURNED -> EnumSet.of(LENT);
            case CLOSED -> EnumSet.of(DECLINED, RETURNED);
        };
    }

    /**
     * The statuses a copy may catch up from when the lending station reports one of its own steps.
     *
     * <p>A step taken on another instance arrives as a message that can come late or not at all, and a
     * missed one must not leave the copy behind for good. The lending station's word on its own steps
     * is the truth about where the gear is, so a copy still earlier on the lending station's path
     * (asked, agreed, out) moves straight to the step reported. A copy that was declined or closed
     * does not: those are not the lending station's steps to override.
     *
     * @param lenderStep a step only the lending station takes
     * @return the earlier statuses on the lending station's path, empty for any other status
     */
    public static Set<LendingStatus> catchUpFrom(LendingStatus lenderStep) {
        return switch (lenderStep) {
            case APPROVED -> EnumSet.of(REQUESTED);
            case LENT -> EnumSet.of(REQUESTED, APPROVED);
            case RETURNED -> EnumSet.of(REQUESTED, APPROVED, LENT);
            default -> EnumSet.noneOf(LendingStatus.class);
        };
    }

    /**
     * Whether a request in this status may move to the next one.
     *
     * @param next the status asked for
     * @return {@code true} when the table allows the step
     */
    public boolean canMoveTo(LendingStatus next) {
        return predecessorsOf(next).contains(this);
    }

    /**
     * Whether only the lending station may take the step to this status: agreeing, handing over and
     * taking back are the lender's, withdrawing and closing are either station's.
     *
     * @return {@code true} for a step of the lending station alone
     */
    public boolean lenderOnly() {
        return this == APPROVED || this == LENT || this == RETURNED;
    }
}
