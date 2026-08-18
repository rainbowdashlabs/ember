/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.station.entity.MailProviderType;

/**
 * One provider the instance sends through, in the order it is tried.
 *
 * <p>The first is simply the first, not a provider of a different kind. Each gets a number of
 * attempts and a daily allowance; when either is spent the next one takes over.
 */
@SuppressWarnings("unused")
public class MailProviderEntry {

    private MailProviderType provider = MailProviderType.NONE;
    private String host = "";
    private int port = 587;
    private boolean ssl = false;
    private String user = "";
    private String password = "";
    private String apiKey = "";
    private String senderAddress = "";
    private String senderName = "";

    /**
     * How many attempts this provider gets before the next one takes over.
     */
    private int attempts = 2;

    /**
     * How many mails this provider may send in a day, or zero for no limit. Free tiers are sold by
     * the day, so a list that ignores the allowance keeps pushing at a provider that has spent it.
     */
    private int dailySendLimit = 0;

    /**
     * Required by the configuration reader, which builds the object before filling it.
     */
    public MailProviderEntry() {}

    /**
     * Builds an entry from values an administrator has just entered.
     */
    public MailProviderEntry(
            MailProviderType provider,
            String host,
            int port,
            boolean ssl,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            String senderName,
            int attempts,
            int dailySendLimit) {
        this.provider = provider;
        this.host = host;
        this.port = port;
        this.ssl = ssl;
        this.user = user;
        this.password = password;
        this.apiKey = apiKey;
        this.senderAddress = senderAddress;
        this.senderName = senderName;
        this.attempts = attempts;
        this.dailySendLimit = dailySendLimit;
    }

    public MailProviderType provider() {
        return provider;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public boolean ssl() {
        return ssl;
    }

    public String user() {
        return user;
    }

    public String password() {
        return password;
    }

    public String apiKey() {
        return apiKey;
    }

    public String senderAddress() {
        return senderAddress;
    }

    public String senderName() {
        return senderName;
    }

    public int attempts() {
        return attempts;
    }

    public int dailySendLimit() {
        return dailySendLimit;
    }
}
