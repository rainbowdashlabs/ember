/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.service.mail;

/**
 * Abstraction for sending emails through different providers.
 */
public interface MailProvider {

    /**
     * Outcome of a single send attempt.
     */
    enum SendResult {
        /** Message was accepted by the relay. */
        SENT,
        /**
         * Send failed because of the relay or network (connection refused, timeout, host unreachable,
         * 4xx temporary errors). The worker should retry the same queued row on the next tick.
         */
        TRANSIENT_FAILURE,
        /**
         * Send failed for a reason that will not improve on retry (recipient rejected the address,
         * authentication credentials are wrong, encoding error). The worker should mark the row as
         * FAILED so the operator notices.
         */
        PERMANENT_FAILURE
    }

    /**
     * Sends an HTML email to the specified recipient.
     *
     * @param to       the recipient email address
     * @param subject  the email subject
     * @param htmlBody the HTML email body
     * @return the outcome of the send attempt
     */
    SendResult send(String to, String subject, String htmlBody);

    /**
     * Test the connection/configuration.
     *
     * @return null if OK, error message otherwise
     */
    String testConnection();
}
