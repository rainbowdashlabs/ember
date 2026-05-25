/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

/**
 * Email sending configuration including SMTP settings, authentication credentials,
 * sender identity, daily send limits, and notification digest intervals.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Mailing {
    private String provider = "SMTP";
    private MailSettings smtp = new MailSettings();
    private String user = "";
    private String password = "";
    private String apiKey = "";
    private String senderAddress = "";
    private String senderName = "Ember";
    private Map<String, String> properties = Collections.emptyMap();
    private int dailySendLimit = 200;
    private int notificationDigestIntervalMinutes = 60;

    public String provider() {
        return provider;
    }

    public MailSettings smtp() {
        return smtp;
    }

    public String apiKey() {
        return apiKey;
    }

    /**
     * Get the username for authentication.
     *
     * @return the username
     */
    public String user() {
        return user;
    }

    /**
     * Get the password for authentication.
     *
     * @return the password
     */
    public String password() {
        return password;
    }

    public String senderAddress() {
        return senderAddress;
    }

    public String senderName() {
        return senderName;
    }

    /**
     * Get all mail properties including SMTP and IMAP settings.
     *
     * @return the properties
     */
    public int dailySendLimit() {
        return dailySendLimit;
    }

    public int notificationDigestIntervalMinutes() {
        return notificationDigestIntervalMinutes;
    }

    /**
     * Builds a {@link Properties} object combining SMTP settings with any additional custom properties.
     *
     * @return the merged mail properties
     */
    public Properties properties() {
        Properties props = new Properties();
        props.putAll(smtp().properties("smtp"));
        props.putAll(properties);
        return props;
    }

    @Override
    public String toString() {
        return "Mailing{" + "smtp="
                + smtp + ", user="
                + user + '\'' + ", password='"
                + password + '\'' + ", senderAddress='"
                + senderAddress + '\'' + ", senderName='"
                + senderName + '\'' + ", properties="
                + properties + ", dailySendLimit="
                + dailySendLimit + '}';
    }
}
