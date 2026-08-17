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
    default SendResult send(String to, String subject, String htmlBody) {
        return send(to, subject, htmlBody, null);
    }

    /**
     * Sends an HTML email, tagging it so the provider's later delivery events can be traced back to
     * it.
     *
     * <p>A relay answers our send the moment it accepts the message; whether it arrives is reported
     * afterwards, out of band. The token travels with the message and comes back in those reports,
     * which is the only thing tying the two together. A provider that has no way to carry it simply
     * ignores it.
     *
     * @param to            the recipient email address
     * @param subject       the email subject
     * @param htmlBody      the HTML email body
     * @param correlationId our own identifier for this message, or null to send it untagged
     * @return the outcome of the send attempt
     */
    SendResult send(String to, String subject, String htmlBody, String correlationId);

    /**
     * Outcome of a connection test.
     *
     * @param error       human-readable error, or {@code null} when the connection succeeded
     * @param authFailure whether the server rejected the login credentials
     */
    record TestResult(String error, boolean authFailure) {

        /**
         * Creates a successful result.
         */
        public static TestResult ok() {
            return new TestResult(null, false);
        }

        /**
         * Whether the connection test succeeded.
         */
        public boolean success() {
            return error == null;
        }
    }

    /**
     * Test the connection/configuration.
     *
     * @return the outcome of the connection attempt
     */
    TestResult testConnection();
}
