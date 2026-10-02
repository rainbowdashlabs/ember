/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

/**
 * Where a queued mail stands in being handed to a provider. Says nothing about what happened after
 * the provider took it; that is {@link MailDeliveryStatus}.
 */
public enum EmailQueueStatus {
    /** Waiting for its turn. */
    PENDING,
    /** Taken by a sender and being handed over right now. */
    SENDING,
    /** Taken by the provider. */
    SENT,
    /** Given up on after its attempts ran out. */
    FAILED
}
