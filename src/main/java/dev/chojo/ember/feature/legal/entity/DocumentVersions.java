/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

/**
 * The versions of the three legal documents a consent names.
 *
 * @param privacy the privacy policy
 * @param tos     the terms of service
 * @param consent the consent text
 */
public record DocumentVersions(DocumentVersion privacy, DocumentVersion tos, DocumentVersion consent) {

    /** The current privacy policy version. */
    public String privacyVersion() {
        return privacy.version();
    }

    /** The current terms of service version. */
    public String tosVersion() {
        return tos.version();
    }

    /** The current consent text version. */
    public String consentVersion() {
        return consent.version();
    }
}
