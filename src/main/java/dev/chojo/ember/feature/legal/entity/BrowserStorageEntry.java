/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

/**
 * One value the application keeps in the browser, as declared in {@code browser_storage.json}
 * and rendered into the generated storage disclosure of the privacy policy and the consent text.
 *
 * @param key       the literal local storage key as used by the frontend, or the cookie's name
 * @param kind      where the value lives; absent in the catalog means local storage
 * @param necessity how far the application depends on the value
 * @param retention how long the value stays in the browser
 * @param purpose   what the value is for
 */
public record BrowserStorageEntry(
        String key, Kind kind, Necessity necessity, Retention retention, LocalizedText purpose) {

    public BrowserStorageEntry {
        if (kind == null) kind = Kind.LOCAL_STORAGE;
    }

    /**
     * Where a value lives in the browser.
     */
    public enum Kind {
        /**
         * The browser's local storage, which only the frontend reads and writes.
         */
        LOCAL_STORAGE,
        /**
         * A cookie the server sets. It travels with every request to this site.
         */
        COOKIE
    }

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
        UNTIL_CLEARED,
        /**
         * Removed on sign-out, and gone at the latest when the session runs out.
         */
        UNTIL_SESSION_ENDS,
        /**
         * Stays for the period the user chose to trust the device, or until they remove it.
         */
        UNTIL_TRUST_ENDS
    }
}
