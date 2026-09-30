/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.DocumentVersions;
import dev.chojo.ember.feature.legal.entity.GdprConsent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;

/**
 * Where an account stands with the legal documents: whether it consented to the versions in force,
 * and what changed in them since it last did.
 */
@Singleton
public class ConsentStatusService {
    private final ConsentService consentService;

    @Inject
    public ConsentStatusService(ConsentService consentService) {
        this.consentService = consentService;
    }

    /**
     * The account's latest consent, and whether it still covers every document as it now stands.
     * A consent that named no privacy policy or terms version is not held against the account for
     * those.
     */
    public ConsentStatusResponse status(int accountId) {
        var current = consentService.getCurrentVersions();
        return consentService
                .findLatestConsent(accountId)
                .map(consent -> new ConsentStatusResponse(
                        true,
                        current.consentVersion().equals(consent.consentVersion())
                                && !changed(consent.privacyVersion(), current.privacyVersion())
                                && !changed(consent.tosVersion(), current.tosVersion()),
                        consent.consentVersion(),
                        consent.privacyVersion(),
                        consent.tosVersion(),
                        consent.consentedAt(),
                        current.privacyVersion(),
                        current.tosVersion(),
                        current.consentVersion()))
                .orElseGet(() -> new ConsentStatusResponse(
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        current.privacyVersion(),
                        current.tosVersion(),
                        current.consentVersion()));
    }

    private static boolean changed(String consented, String current) {
        return consented != null && !current.equals(consented);
    }

    /**
     * What changed in the privacy policy and the terms since the account last consented, each as a
     * diff and as the document now reads.
     *
     * @param locale the language the documents are shown in
     */
    public ConsentChangesResponse changes(int accountId, String locale) {
        DocumentVersions current = consentService.getCurrentVersions();
        var consent = consentService.findLatestConsent(accountId);
        String privacyFrom = consent.map(GdprConsent::privacyVersion).orElse(null);
        String tosFrom = consent.map(GdprConsent::tosVersion).orElse(null);
        boolean privacyChanged = changed(privacyFrom, current.privacyVersion());
        boolean tosChanged = changed(tosFrom, current.tosVersion());
        return new ConsentChangesResponse(
                privacyChanged,
                tosChanged,
                privacyChanged ? consentService.getPrivacyDiff(privacyFrom, current.privacyVersion()) : null,
                tosChanged ? consentService.getTosDiff(tosFrom, current.tosVersion()) : null,
                privacyChanged ? consentService.getPrivacyPolicy(locale).html() : null,
                tosChanged ? consentService.getTermsOfService(locale).html() : null,
                current.privacyVersion(),
                current.tosVersion(),
                current.consentVersion());
    }

    /**
     * Where an account stands with its consent.
     *
     * @param consented             whether the user has ever consented
     * @param current               whether the user's consent matches all current document versions
     * @param consentVersion        the consent text version the user accepted (null if never consented)
     * @param privacyVersion        the privacy policy version the user accepted
     * @param tosVersion            the terms of service version the user accepted
     * @param consentedAt           the timestamp when consent was last given
     * @param currentPrivacyVersion the current privacy policy version hash
     * @param currentTosVersion     the current terms of service version hash
     * @param currentConsentVersion the current consent text version hash
     */
    public record ConsentStatusResponse(
            boolean consented,
            boolean current,
            String consentVersion,
            String privacyVersion,
            String tosVersion,
            Instant consentedAt,
            String currentPrivacyVersion,
            String currentTosVersion,
            String currentConsentVersion) {}

    /**
     * What changed in the legal documents since an account's last consent.
     *
     * @param privacyChanged        whether the privacy policy changed since last consent
     * @param tosChanged            whether the terms of service changed since last consent
     * @param privacyDiff           line-based diff of the privacy policy (null if unchanged)
     * @param tosDiff               line-based diff of the terms of service (null if unchanged)
     * @param privacyHtml           current privacy policy HTML (null if unchanged)
     * @param tosHtml               current terms of service HTML (null if unchanged)
     * @param currentPrivacyVersion the current privacy policy version hash
     * @param currentTosVersion     the current terms of service version hash
     * @param currentConsentVersion the current consent text version hash
     */
    public record ConsentChangesResponse(
            boolean privacyChanged,
            boolean tosChanged,
            String privacyDiff,
            String tosDiff,
            String privacyHtml,
            String tosHtml,
            String currentPrivacyVersion,
            String currentTosVersion,
            String currentConsentVersion) {}
}
