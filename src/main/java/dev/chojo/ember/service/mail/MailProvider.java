/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service.mail;

/**
 * Abstraction for sending emails through different providers.
 */
public interface MailProvider {

    /**
     * Send an email.
     *
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
