/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.legal.entity.GdprConsent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Service managing GDPR consent operations including legal document retrieval, version tracking,
 * diff generation, consent recording, and consent status verification.
 */
@Singleton
public class ConsentService {
    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    private final AccountRepository accountRepository;
    private final LegalDocumentService documentService;
    private final Path privacyPolicyDir;
    private final Path consentDir;
    private final Path tosDir;
    private final Path imprintDir;

    private boolean privacyChanged;
    private boolean tosChanged;
    private boolean consentChanged;

    @Inject
    public ConsentService(AccountRepository accountRepository, Api apiConfig) {
        this.accountRepository = accountRepository;
        this.documentService = new LegalDocumentService();
        this.privacyPolicyDir = Path.of(apiConfig.privacyPolicyDir());
        this.consentDir = Path.of(apiConfig.consentDir());
        this.tosDir = Path.of(apiConfig.tosDir());
        this.imprintDir = Path.of(apiConfig.imprintDir());
    }

    /**
     * Called on application startup. Initializes all legal documents,
     * detects version changes, archives old content.
     */
    public void initialize() {
        privacyChanged = documentService.initialize(privacyPolicyDir);
        tosChanged = documentService.initialize(tosDir);
        consentChanged = documentService.initialize(consentDir);

        if (privacyChanged || tosChanged || consentChanged) {
            log.warn(
                    "Legal documents changed since last startup — users will be prompted for re-consent. "
                            + "Privacy: {}, ToS: {}, Consent: {}",
                    privacyChanged ? "CHANGED" : "unchanged",
                    tosChanged ? "CHANGED" : "unchanged",
                    consentChanged ? "CHANGED" : "unchanged");
        }
    }

    // -- Document retrieval --

    /**
     * Retrieves the privacy policy rendered for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the rendered privacy policy document
     */
    public LegalDocumentService.RenderedDocument getPrivacyPolicy(String locale) {
        return documentService.getDocument(privacyPolicyDir, locale);
    }

    /**
     * Retrieves the privacy policy rendered in the default locale.
     */
    public LegalDocumentService.RenderedDocument getPrivacyPolicy() {
        return documentService.getDocument(privacyPolicyDir);
    }

    /**
     * Retrieves the terms of service rendered for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the rendered terms of service document
     */
    public LegalDocumentService.RenderedDocument getTermsOfService(String locale) {
        return documentService.getDocument(tosDir, locale);
    }

    /**
     * Retrieves the terms of service rendered in the default locale.
     */
    public LegalDocumentService.RenderedDocument getTermsOfService() {
        return documentService.getDocument(tosDir);
    }

    /**
     * Retrieves the imprint (Impressum) rendered for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the rendered imprint document
     */
    public LegalDocumentService.RenderedDocument getImprint(String locale) {
        return documentService.getDocument(imprintDir, locale);
    }

    /**
     * Retrieves the imprint (Impressum) rendered in the default locale.
     */
    public LegalDocumentService.RenderedDocument getImprint() {
        return documentService.getDocument(imprintDir);
    }

    /**
     * Retrieves the GDPR consent text rendered for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the rendered consent text document
     */
    public LegalDocumentService.RenderedDocument getConsentText(String locale) {
        return documentService.getDocument(consentDir, locale);
    }

    /**
     * Retrieves the GDPR consent text rendered in the default locale.
     */
    public LegalDocumentService.RenderedDocument getConsentText() {
        return documentService.getDocument(consentDir);
    }

    // -- Version info --

    /**
     * Returns the current version hashes of all legal documents.
     *
     * @return the version hashes for privacy policy, terms of service, and consent text
     */
    public DocumentVersions getCurrentVersions() {
        return new DocumentVersions(
                documentService.getDocument(privacyPolicyDir).version(),
                documentService.getDocument(tosDir).version(),
                documentService.getDocument(consentDir).version());
    }

    // -- Diff --

    /**
     * Gets the diff between two privacy policy versions.
     *
     * @param fromVersion the version hash to diff from
     * @param toVersion   the version hash to diff to
     * @return the line-based diff text, or null if unavailable
     */
    public String getPrivacyDiff(String fromVersion, String toVersion) {
        return documentService.getDiff(privacyPolicyDir, fromVersion, toVersion);
    }

    /**
     * Gets the diff between two terms of service versions.
     *
     * @param fromVersion the version hash to diff from
     * @param toVersion   the version hash to diff to
     * @return the line-based diff text, or null if unavailable
     */
    public String getTosDiff(String fromVersion, String toVersion) {
        return documentService.getDiff(tosDir, fromVersion, toVersion);
    }

    // -- Consent recording --

    /**
     * Records a consent proof for the given account, storing document versions and client metadata.
     *
     * @param accountId      the account giving consent
     * @param consentVersion the consent text version hash accepted
     * @param privacyVersion the privacy policy version hash accepted
     * @param tosVersion     the terms of service version hash accepted
     * @param ipAddress      the client IP address
     * @param country        the country from Cloudflare headers (may be null)
     * @param userAgent      the client user agent string
     */
    public void recordConsent(
            int accountId,
            String consentVersion,
            String privacyVersion,
            String tosVersion,
            String ipAddress,
            String country,
            String userAgent) {
        accountRepository.recordConsent(
                accountId, consentVersion, privacyVersion, tosVersion, ipAddress, country, userAgent);
    }

    /**
     * Finds the most recent consent record for the given account.
     *
     * @param accountId the account to look up
     * @return the latest consent record, or empty if none exists
     */
    public Optional<GdprConsent> findLatestConsent(int accountId) {
        return accountRepository.findLatestConsent(accountId);
    }

    /**
     * Retrieves all consent records for the given account, ordered by date.
     *
     * @param accountId the account to look up
     * @return list of all consent records
     */
    public List<GdprConsent> findAllConsents(int accountId) {
        return accountRepository.findAllConsents(accountId);
    }

    /**
     * Checks whether the user's latest consent matches all current document versions.
     */
    public boolean isConsentCurrent(int accountId) {
        var latest = findLatestConsent(accountId);
        if (latest.isEmpty()) return false;
        var c = latest.get();
        var current = getCurrentVersions();
        return current.privacyVersion().equals(c.privacyVersion())
                && current.tosVersion().equals(c.tosVersion())
                && current.consentVersion().equals(c.consentVersion());
    }

    /**
     * Container for the current version hashes of all legal documents.
     *
     * @param privacyVersion the privacy policy version hash
     * @param tosVersion     the terms of service version hash
     * @param consentVersion the consent text version hash
     */
    public record DocumentVersions(String privacyVersion, String tosVersion, String consentVersion) {}
}
