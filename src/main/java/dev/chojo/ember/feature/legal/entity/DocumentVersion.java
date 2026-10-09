/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import org.jspecify.annotations.Nullable;

/**
 * The version of one legal document a consent is given for, and the legacy hash still taken as
 * the same version.
 *
 * <p>The version covers the written text and, where the document carries the generated browser
 * storage section, the storage categories, never the list of stored keys. Consents given before
 * were recorded under the hash of the whole document, keys included; the hash the document had
 * when the new scheme first started still counts as current while the version is unchanged.
 *
 * @param version       the version over the text and the storage categories
 * @param legacyVersion the hash of the whole document, every stored key included, that earlier
 *                      consents were recorded under
 */
public record DocumentVersion(String version, String legacyVersion) {

    /**
     * Whether a consent recorded under the given version covers the document in force.
     *
     * @param consented the version a consent was given for
     * @return true for the current version and for the legacy hash of the current document
     */
    public boolean covers(@Nullable String consented) {
        return version.equals(consented) || legacyVersion.equals(consented);
    }
}
