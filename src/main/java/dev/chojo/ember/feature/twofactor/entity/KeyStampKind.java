/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.entity;

/**
 * How a credential's public key came by its timestamp, which says how much the timestamp proves about
 * the key. Only a stamp taken right after the registration fixes the key as the one registered; every
 * later stamp fixes it only from its own time on.
 */
public enum KeyStampKind {
    /** Stamped right after the credential was registered. */
    AT_REGISTRATION,
    /**
     * Stamped by the daily retry, some time after the registration, because no timestamp service answered
     * then or the credential is older than key timestamps.
     */
    AFTER_REGISTRATION,
    /**
     * Stamped during a signing act with the credential, the first one that reached a timestamp service,
     * because the credential had no timestamp yet.
     */
    AT_FIRST_SIGNING
}
