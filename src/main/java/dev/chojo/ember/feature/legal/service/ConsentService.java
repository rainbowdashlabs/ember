/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.legal.entity.ConsentProof;
import dev.chojo.ember.feature.legal.entity.DocumentVersions;
import dev.chojo.ember.feature.legal.entity.GdprConsent;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

/**
 * Service managing GDPR consent operations including legal document retrieval, version tracking,
 * diff generation, consent recording, and consent status verification.
 */
@Singleton
public class ConsentService {
    private static final Logger log = LoggerFactory.getLogger(ConsentService.class);

    private final AccountRepository accountRepository;
    private final Network network;
    private final LegalDocumentService documentService;
    private final Path privacyPolicyDir;
    private final Path consentDir;
    private final Path tosDir;
    private final Path imprintDir;

    @Inject
    public ConsentService(AccountRepository accountRepository, Api apiConfig, Network network) {
        this.accountRepository = accountRepository;
        this.network = network;
        this.documentService = new LegalDocumentService(apiConfig.placeholderFile());
        this.privacyPolicyDir = Path.of(apiConfig.privacyPolicyDir());
        this.consentDir = Path.of(apiConfig.consentDir());
        this.tosDir = Path.of(apiConfig.tosDir());
        this.imprintDir = Path.of(apiConfig.imprintDir());
    }

    /**
     * Returns a privacy-preserving form of the given client IP for proof of consent.
     * The last octet of an IPv4 address is zeroed (e.g. {@code 203.0.113.7} → {@code 203.0.113.0}),
     * the last 80 bits of an IPv6 address are zeroed (so only the /48 prefix is kept).
     * This is the de-facto GDPR-compliant truncation used by Matomo and Plausible; it
     * still establishes geography for an audit, but is no longer personal data.
     *
     * @param address the resolved client {@link InetAddress}
     * @return the anonymised IP as a string in standard textual form
     */
    public static String anonymizeIp(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            bytes[3] = 0;
        } else if (bytes.length == 16) {
            Arrays.fill(bytes, 6, 16, (byte) 0);
        }
        try {
            return InetAddress.getByAddress(bytes).getHostAddress();
        } catch (UnknownHostException e) {
            return address.getHostAddress();
        }
    }

    // -- Document retrieval --

    /**
     * Called on application startup. Initializes all legal documents,
     * detects version changes, archives old content.
     */
    public void initialize() {
        documentService.ensureGeneratedSection(privacyPolicyDir);
        documentService.ensureGeneratedSection(consentDir);

        boolean privacyChanged = documentService.initialize(privacyPolicyDir);
        boolean tosChanged = documentService.initialize(tosDir);
        boolean consentChanged = documentService.initialize(consentDir);

        if (privacyChanged || tosChanged || consentChanged) {
            log.warn(
                    "Legal documents changed since last startup — users will be prompted for re-consent. "
                            + "Privacy: {}, ToS: {}, Consent: {}",
                    privacyChanged ? "CHANGED" : "unchanged",
                    tosChanged ? "CHANGED" : "unchanged",
                    consentChanged ? "CHANGED" : "unchanged");
        }
    }

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
     * Retrieves the terms of service rendered for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the rendered terms of service document
     */
    public LegalDocumentService.RenderedDocument getTermsOfService(String locale) {
        return documentService.getDocument(tosDir, locale);
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

    // -- Version info --

    /**
     * Retrieves the GDPR consent text rendered for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the rendered consent text document
     */
    public LegalDocumentService.RenderedDocument getConsentText(String locale) {
        return documentService.getDocument(consentDir, locale);
    }

    // -- Diff --

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

    // -- Consent recording --

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
        log.info(
                "Consent recorded: account={}, consentVersion={}, privacyVersion={}, tosVersion={}",
                accountId,
                consentVersion,
                privacyVersion,
                tosVersion);
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
     * Validates that the version hashes the anonymous submitter accepted match the
     * current document versions, then captures the surrounding request context (IP,
     * country, user-agent) into a {@link ConsentProof}. Used by every public,
     * unauthenticated submission endpoint as a precondition.
     *
     * @param ctx            the Javalin request context (used to derive IP / UA / country)
     * @param consentVersion the consent text version the submitter clicked through
     * @param privacyVersion the privacy policy version the submitter clicked through
     * @param tosVersion     the terms of service version the submitter clicked through
     * @return a populated {@link ConsentProof} ready to persist on the submission row
     * @throws BadRequestResponse if any hash is missing or does not match the current
     *                            published version (the caller's UI should refresh the
     *                            documents and re-prompt)
     */
    public ConsentProof requireAcceptance(
            Context ctx, String consentVersion, String privacyVersion, String tosVersion) {
        if (consentVersion == null || consentVersion.isBlank()) {
            throw new BadRequestResponse("consentVersion is required");
        }
        if (privacyVersion == null || privacyVersion.isBlank()) {
            throw new BadRequestResponse("privacyVersion is required");
        }
        if (tosVersion == null || tosVersion.isBlank()) {
            throw new BadRequestResponse("tosVersion is required");
        }

        var current = getCurrentVersions();
        if (!current.consentVersion().equals(consentVersion)
                || !current.privacyVersion().equals(privacyVersion)
                || !current.tosVersion().equals(tosVersion)) {
            throw new BadRequestResponse(
                    "Legal documents have changed since the form was loaded. Please reload and accept again.");
        }

        String ipAddress = anonymizeIp(ClientIp.resolve(ctx, network));
        String country = ctx.header("CF-IPCountry");
        String userAgent = ctx.userAgent();
        return new ConsentProof(
                consentVersion, privacyVersion, tosVersion, ipAddress, country, userAgent, Instant.now());
    }
}
