/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.conf.file.elements;

/**
 * Configuration for the "Have I Been Pwned" k-anonymity breach lookup used by
 * the password set-time validation and the post-login background worker.
 */
@SuppressWarnings({"FieldCanBeLocal", "FieldMayBeFinal", "CanBeFinal"})
public class HibpSettings {
    private boolean enabled = true;
    private int staleAfterDays = 30;
    private String endpoint = "https://api.pwnedpasswords.com/range/";
    private int timeoutSeconds = 5;

    /**
     * @return {@code true} when the breach lookup is enabled; otherwise both the
     * synchronous and asynchronous checks become no-ops.
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * @return the number of days after the last successful check when the next
     * post-login lookup is allowed to run for the same credential.
     */
    public int staleAfterDays() {
        return staleAfterDays;
    }

    /**
     * @return the HIBP range endpoint, including the trailing {@code /}. The
     * first five hex characters of {@code SHA-1(plaintext)} are appended
     * to form the request URL.
     */
    public String endpoint() {
        return endpoint;
    }

    /**
     * @return the per-request connect and read timeout in seconds.
     */
    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public String toString() {
        return "HibpSettings{enabled=" + enabled
                + ", staleAfterDays=" + staleAfterDays
                + ", endpoint='" + endpoint + "'"
                + ", timeoutSeconds=" + timeoutSeconds + '}';
    }
}
