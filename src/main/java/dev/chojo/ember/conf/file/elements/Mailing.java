/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.station.entity.MailProviderType;
import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Email sending configuration including SMTP settings, authentication credentials,
 * sender identity, daily send limits, and notification digest intervals.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class Mailing {
    @Overwrite(env = @Env)
    private MailProviderType provider = MailProviderType.SMTP;

    @Overwrite(env = @Env)
    private MailSettings smtp = new MailSettings();

    @Overwrite(env = @Env)
    private String user = "";

    @Overwrite(env = @Env)
    private String password = "";

    @Overwrite(env = @Env)
    private String apiKey = "";

    @Overwrite(env = @Env)
    private String senderAddress = "";

    @Overwrite(env = @Env)
    private String senderName = "Ember";

    private Map<String, String> properties = Collections.emptyMap();

    @Overwrite(env = @Env)
    private int dailySendLimit = 200;

    @Overwrite(env = @Env)
    private int notificationDigestIntervalMinutes = 60;

    /**
     * The secret a mail provider must present to report delivery events. Empty switches the
     * webhook off, which is the default: an endpoint that accepts anything would let a stranger
     * mark mail as bounced.
     */
    @Overwrite(env = @Env)
    private String webhookSecret = "";

    /**
     * Providers to fall back to when the one above has used its attempts, in the order they are
     * tried. Empty means there is nothing to fall back to, which is how an instance starts.
     */
    private List<MailFallback> fallbacks = Collections.emptyList();

    /**
     * How many attempts the provider configured here gets before the first fallback takes over.
     */
    @Overwrite(env = @Env)
    private int attempts = 2;

    /**
     * The signing secret Sweego issued for its webhook. Set it and every report from Sweego is
     * checked against it; leave it empty and the key in the address is what authorises a report,
     * as with the relays that do not sign at all.
     */
    @Overwrite(env = @Env)
    private String sweegoWebhookSecret = "";

    public MailProviderType provider() {
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

    /**
     * The secret that authorises a delivery-event report. Empty until Ember has generated one.
     */
    public String webhookSecret() {
        return webhookSecret;
    }

    /**
     * The providers tried after the one configured here, in order.
     */
    public List<MailFallback> fallbacks() {
        return fallbacks == null ? Collections.emptyList() : fallbacks;
    }

    /**
     * How many attempts the first provider gets before the chain moves on.
     */
    public int attempts() {
        return attempts;
    }

    /**
     * The signing secret Sweego issued, or empty when reports are not checked against one.
     */
    public String sweegoWebhookSecret() {
        return sweegoWebhookSecret;
    }

    /**
     * Sets the generated webhook secret.
     *
     * <p>Ember generates this itself on first start rather than asking an operator for it - nobody
     * needs another secret to look after - so unlike every other value here it is written from the
     * inside.
     */
    public void webhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
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
