/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

/**
 * Container for the current version hashes of all legal documents.
 *
 * @param privacyVersion the privacy policy version hash
 * @param tosVersion     the terms of service version hash
 * @param consentVersion the consent text version hash
 */
public record DocumentVersions(String privacyVersion, String tosVersion, String consentVersion) {}
