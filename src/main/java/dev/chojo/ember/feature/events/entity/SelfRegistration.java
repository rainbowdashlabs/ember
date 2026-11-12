/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

/**
 * Whether one reader may put themselves into a member question of an appointment that takes
 * self-registration.
 *
 * <p>Taking oneself back out is never refused, so this only speaks about coming in.
 */
public enum SelfRegistration {
    /** The reader may put themselves in, as far as who they are and the date go; a single slot may still be taken. */
    OPEN,
    /** The question is narrowed to a group, member type or tag the reader is outside of. */
    NOT_ELIGIBLE,
    /** The appointment has stopped taking people. */
    CLOSED
}
