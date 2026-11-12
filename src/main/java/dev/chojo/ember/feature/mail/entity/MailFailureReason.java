/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * Why the queue gave up on a mail, kept with the mail so an administrator can read it later rather
 * than search the application log for it.
 */
public enum MailFailureReason {
    /** The station had no provider at all to send through. */
    NO_PROVIDER,
    /** Every provider of the station's list had been tried and none was left. */
    NO_PROVIDER_LEFT,
    /** The provider refused the mail for good. */
    PROVIDER_REFUSED,
    /** The provider's settings could not be used, so it could not even be asked. */
    PROVIDER_BROKEN,
    /** Every provider used up its attempts without taking the mail. */
    RETRIES_EXHAUSTED
}
