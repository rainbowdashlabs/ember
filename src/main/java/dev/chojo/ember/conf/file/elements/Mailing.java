/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

public class Mailing {
    private MailSettings smtp = new MailSettings();
    private MailSettings imap = new MailSettings();
    private String user = "";
    private String password = "";
    private String senderAddress = "";
    private String senderName = "Ember";
    private Map<String, String> properties = Collections.emptyMap();
    private int dailySendLimit = 200;

    /**
     * Get the SMTP settings.
     *
     * @return the SMTP settings
     */
    public MailSettings smtp() {
        return smtp;
    }

    /**
     * Get the IMAP settings.
     *
     * @return the IMAP settings
     */
    public MailSettings imap() {
        return imap;
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

    public Properties properties() {
        Properties props = new Properties();
        props.putAll(smtp().properties("smtp"));
        props.putAll(imap().properties("imap"));
        props.putAll(properties);
        return props;
    }
}
