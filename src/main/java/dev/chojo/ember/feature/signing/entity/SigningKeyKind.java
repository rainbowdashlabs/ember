/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

/** Which of the two kinds of signing key a stored key is. */
public enum SigningKeyKind {
    /** One of the installation's certificate authorities, which issue the station certificates. */
    AUTHORITY,
    /** A key a station seals documents with. */
    STATION_KEY
}
