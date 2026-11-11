/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/**
 * One of the instance's own mails that did not arrive and that nobody has taken note of yet.
 *
 * @param id          the mail
 * @param recipient   whom it was meant for
 * @param subject     its subject
 * @param createdAt   when it was written
 * @param failedAt    when it failed: when the queue last took it in hand, or when the provider reported
 * @param cause       why it did not arrive
 * @param detail      what the provider said about it, or null where the queue gave up on its own
 * @param requeueable whether it can be sent again: only a mail the queue gave up on, and only one
 *                    that failed since the queue keeps its reason and its files
 */
public record FailedMail(
        int id,
        String recipient,
        String subject,
        Instant createdAt,
        Instant failedAt,
        FailedMailCause cause,
        @Nullable String detail,
        boolean requeueable) {}
