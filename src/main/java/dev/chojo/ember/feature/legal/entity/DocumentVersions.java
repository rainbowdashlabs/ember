/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import org.jspecify.annotations.Nullable;

/**
 * Container for the current version hashes of all legal documents.
 *
 * <p>The consent version covers the consent text and the storage categories, not every stored key.
 * Consents given before it did were recorded under the hash of the whole document; the hash of the
 * document as it reads now is still taken as the same consent, so the change of scheme asks nobody
 * again. Hashes of earlier states of the document stay outdated.
 *
 * @param privacyVersion       the privacy policy version hash
 * @param tosVersion           the terms of service version hash
 * @param consentVersion       the consent text version hash, over the text and the storage categories
 * @param legacyConsentVersion the hash of the whole consent document as it reads now, every stored
 *                             key included, which earlier consents were recorded under
 */
public record DocumentVersions(
        String privacyVersion, String tosVersion, String consentVersion, String legacyConsentVersion) {

    /**
     * Whether a consent recorded under the given version covers the consent text in force.
     *
     * @param version the consent version a consent was given for
     * @return true for the current version and for the legacy hash of the current document
     */
    public boolean coversConsent(@Nullable String version) {
        return consentVersion.equals(version) || legacyConsentVersion.equals(version);
    }
}
