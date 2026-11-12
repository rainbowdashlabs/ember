/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * Why a mail did not arrive, in one list for both ways that can happen: the queue gave up on it, or
 * a provider took it and reported afterwards that it was refused.
 *
 * <p>The first five are the queue's own reasons ({@link MailFailureReason}), the last three what a
 * provider reported ({@link MailDeliveryStatus}). A soft bounce or a deferral is not here: the
 * provider is still trying.
 */
public enum FailedMailCause {
    /** The station had no provider at all to send through. */
    NO_PROVIDER,
    /** Every provider of the station's list had been tried and none was left. */
    NO_PROVIDER_LEFT,
    /** The provider refused the mail for good. */
    PROVIDER_REFUSED,
    /** The provider's settings could not be used. */
    PROVIDER_BROKEN,
    /** Every provider used up its attempts without taking the mail. */
    RETRIES_EXHAUSTED,
    /** Given up on before the queue kept its reasons. */
    UNRECORDED,
    /** The provider reported that the address does not exist. */
    HARD_BOUNCE,
    /** The provider reported that the receiving side refused the sender. */
    BLOCKED,
    /** The recipient marked the mail as spam. */
    SPAM;

    /**
     * @return whether the queue gave up on the mail, as opposed to a provider reporting it refused
     */
    public boolean givenUp() {
        return ordinal() <= UNRECORDED.ordinal();
    }
}
