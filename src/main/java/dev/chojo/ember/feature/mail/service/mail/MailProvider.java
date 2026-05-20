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
     * Sends an HTML email to the specified recipient.
     *
     * @param to       the recipient email address
     * @param subject  the email subject
     * @param htmlBody the HTML email body
     * @return true if sent successfully
     */
    boolean send(String to, String subject, String htmlBody);

    /**
     * Test the connection/configuration.
     *
     * @return null if OK, error message otherwise
     */
    String testConnection();
}
