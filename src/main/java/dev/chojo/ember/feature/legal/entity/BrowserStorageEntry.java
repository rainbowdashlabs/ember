/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

/**
 * One value the frontend keeps in the browser's local storage, as declared in
 * {@code browser_storage.json} and rendered into the generated storage disclosure
 * of the privacy policy and the consent text.
 *
 * @param key       the literal local storage key as used by the frontend
 * @param necessity how far the application depends on the value
 * @param retention how long the value stays in the browser
 * @param purpose   what the value is for
 */
public record BrowserStorageEntry(String key, Necessity necessity, Retention retention, LocalizedText purpose) {

    /**
     * How far the application depends on a stored value.
     */
    public enum Necessity {
        /**
         * Login, session handling and the consent decision itself. Without these the
         * protected areas cannot be used at all.
         */
        REQUIRED,
        /**
         * Written only once a single feature is used. Everything else keeps working.
         */
        FUNCTIONAL,
        /**
         * Remembers a display preference. Without it the application starts with its defaults.
         */
        COMFORT
    }

    /**
     * How long a stored value stays in the browser.
     */
    public enum Retention {
        /**
         * Removed when the user signs out.
         */
        UNTIL_LOGOUT,
        /**
         * Stays until the user clears the browser data.
         */
        UNTIL_CLEARED
    }
}
