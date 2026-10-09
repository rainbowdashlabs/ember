/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * What came of asking a partner for its signing authorities.
 *
 * @param state        whether the statement was taken, refused or never came
 * @param refusal      why it was refused; null unless it was
 * @param newlyPinned  the SHA-256 of every authority this statement pinned for the first time, lower-case
 *                     hexadecimal; empty unless it was taken
 */
public record PinOutcome(State state, @Nullable StatementRefusal refusal, List<String> newlyPinned) {

    /** Copies the newly pinned authorities, so the outcome cannot change after the fact. */
    public PinOutcome {
        newlyPinned = List.copyOf(newlyPinned);
    }

    /**
     * @param newlyPinned the authorities pinned for the first time
     * @return a statement that was taken
     */
    public static PinOutcome taken(List<String> newlyPinned) {
        return new PinOutcome(State.TAKEN, null, newlyPinned);
    }

    /**
     * @param refusal why it was refused
     * @return a statement that was refused
     */
    public static PinOutcome refused(StatementRefusal refusal) {
        return new PinOutcome(State.REFUSED, refusal, List.of());
    }

    /** @return no statement came within the time it was given */
    public static PinOutcome unanswered() {
        return new PinOutcome(State.UNANSWERED, null, List.of());
    }

    /** What became of the statement. */
    public enum State {
        /** It was checked and what it names is pinned. */
        TAKEN,
        /** It came and was not taken. */
        REFUSED,
        /** The partner did not answer within the time given. */
        UNANSWERED
    }
}
