/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

import dev.chojo.ember.feature.station.entity.MailProviderType;

/**
 * A provider the instance falls back to when the one before it has used its attempts.
 *
 * <p>The provider configured directly on {@link Mailing} is always the first one tried; these come
 * after it, in the order they are written.
 */
@SuppressWarnings("unused")
public class MailFallback {

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
     * Required by the configuration reader, which builds the object before filling it.
     */
    public MailFallback() {}

    /**
     * Builds a fallback from values an administrator has just entered.
     */
    public MailFallback(
            MailProviderType provider,
            String host,
            int port,
            boolean ssl,
            String user,
            String password,
            String apiKey,
            String senderAddress,
            String senderName,
            int attempts) {
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
}
