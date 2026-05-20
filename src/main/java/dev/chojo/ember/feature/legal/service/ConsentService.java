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

    public LegalDocumentService.RenderedDocument getPrivacyPolicy(String locale) {
        return documentService.getDocument(privacyPolicyDir, locale);
    }

    public LegalDocumentService.RenderedDocument getPrivacyPolicy() {
        return documentService.getDocument(privacyPolicyDir);
    }

    public LegalDocumentService.RenderedDocument getTermsOfService(String locale) {
        return documentService.getDocument(tosDir, locale);
    }

    public LegalDocumentService.RenderedDocument getTermsOfService() {
        return documentService.getDocument(tosDir);
    }

    public LegalDocumentService.RenderedDocument getImprint(String locale) {
        return documentService.getDocument(imprintDir, locale);
    }

    public LegalDocumentService.RenderedDocument getImprint() {
        return documentService.getDocument(imprintDir);
    }

    public LegalDocumentService.RenderedDocument getConsentText(String locale) {
        return documentService.getDocument(consentDir, locale);
    }

    public LegalDocumentService.RenderedDocument getConsentText() {
        return documentService.getDocument(consentDir);
    }

    // -- Version info --

    public DocumentVersions getCurrentVersions() {
        return new DocumentVersions(
                documentService.getDocument(privacyPolicyDir).version(),
                documentService.getDocument(tosDir).version(),
                documentService.getDocument(consentDir).version());
    }

    // -- Diff --

    public String getPrivacyDiff(String fromVersion, String toVersion) {
        return documentService.getDiff(privacyPolicyDir, fromVersion, toVersion);
    }

    public String getTosDiff(String fromVersion, String toVersion) {
        return documentService.getDiff(tosDir, fromVersion, toVersion);
    }

    // -- Consent recording --

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

    public Optional<GdprConsent> findLatestConsent(int accountId) {
        return accountRepository.findLatestConsent(accountId);
    }

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

    public record DocumentVersions(String privacyVersion, String tosVersion, String consentVersion) {}
}
