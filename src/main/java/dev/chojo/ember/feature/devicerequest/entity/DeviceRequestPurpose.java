/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.entity;

/**
 * What approving a device request buys.
 *
 * <p>The handshake either side of this is the same whichever it is: a device shows a code, somebody
 * signed in approves it, and the poll that follows hands over one token that may be spent once. What
 * differs is only what that token is good for, which is why this is one value on one row rather than
 * three tables carrying the same columns.
 *
 * <p>The purpose belongs in the {@code WHERE} clause of every guarded update and never only in a
 * check afterwards. A request approved for one of these must be unspendable on another.
 */
public enum DeviceRequestPurpose {
    /** The right to create exactly one passkey on the asking device. The original purpose. */
    ENROL_PASSKEY,

    /**
     * A session on the asking device, and nothing else. No credential is left behind, so a borrowed
     * machine keeps nothing once the session ends.
     */
    SIGN_IN,

    /**
     * The answer to a step-up demand the asking session has already met. Unlike the other two this is
     * raised by a session that is signed in already, so the row records which one.
     */
    STEP_UP
}
