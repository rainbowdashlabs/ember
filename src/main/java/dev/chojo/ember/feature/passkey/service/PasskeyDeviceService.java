/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.passkey.entity.PasskeyDeviceRequest;
import dev.chojo.ember.feature.passkey.repository.PasskeyDeviceRequestRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * The device handshake: the new device asks, a device that is already signed in approves, and
 * the poll that follows hands the new device a token that may create exactly one passkey. This
 * direction on purpose: the mirror version, where the signed-in device shows a code the new one
 * consumes, fails to the same social-engineering phone call at a lower price, because reading a
 * code out loud is easier to talk somebody into than approving a named, red-bordered request.
 */
@Singleton
public class PasskeyDeviceService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyDeviceService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** No 0/O, 1/I/L or U (confusable with V), so the code survives being read from a screen. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int CODE_LENGTH = 8;

    /** Ten minutes, because somebody has to walk to another machine. */
    private static final Duration REQUEST_TTL = Duration.ofMinutes(10);

    private static final int MAX_ATTEMPTS = 5;

    private final PasskeyDeviceRequestRepository repository;
    private final PasskeyService passkeyService;
    private final AccountRepository accountRepository;
    private final TokenHasher tokenHasher;
    private final EmailService emailService;
    private final MailLocaleService mailLocaleService;

    @Inject
    public PasskeyDeviceService(
            PasskeyDeviceRequestRepository repository,
            PasskeyService passkeyService,
            AccountRepository accountRepository,
            TokenHasher tokenHasher,
            EmailService emailService,
            MailLocaleService mailLocaleService) {
        this.repository = repository;
        this.passkeyService = passkeyService;
        this.accountRepository = accountRepository;
        this.tokenHasher = tokenHasher;
        this.emailService = emailService;
        this.mailLocaleService = mailLocaleService;
    }

    private static String newCode() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private static String newSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Strips the display grouping and the easy mistakes before hashing a typed code. */
    private static String normalizeCode(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    public CreatedRequest createRequest(String userAgent, String country) {
        String code = newCode();
        String pollSecret = newSecret();
        Instant expiresAt = Instant.now().plus(REQUEST_TTL);
        repository.create(tokenHasher.hash(code), tokenHasher.hash(pollSecret), userAgent, country, expiresAt);
        return new CreatedRequest(code, pollSecret, expiresAt);
    }

    /**
     * What the approval screen shows before anybody approves anything: browser, place, time, in
     * plain words. Empty is all a wrong code earns.
     */
    public Optional<PasskeyDeviceRequest> lookup(String code) {
        return repository.findOpenByCode(tokenHasher.hash(normalizeCode(code)));
    }

    /**
     * Approves the request for the signed-in account. The caller has already passed step-up;
     * this only ties the account to the request, exactly once.
     */
    public boolean approve(int accountId, String code) {
        Optional<PasskeyDeviceRequest> request = repository.findOpenByCode(tokenHasher.hash(normalizeCode(code)));
        if (request.isEmpty()) return false;
        boolean approved = repository.approve(request.get().id(), accountId);
        if (approved) {
            log.info("Device request {} approved by account {}", request.get().id(), accountId);
        }
        return approved;
    }

    /**
     * The new device asking whether anything happened yet. The enrolment token is minted on the
     * first poll after the approval and delivered exactly once; the guarded update means two
     * racing polls cannot both walk away with one.
     */
    public PollResult poll(String pollSecret) {
        Optional<PasskeyDeviceRequest> requestOpt = repository.findByPollSecret(tokenHasher.hash(pollSecret));
        if (requestOpt.isEmpty()) return new PollResult(PollStatus.UNKNOWN, null);
        PasskeyDeviceRequest request = requestOpt.get();
        if (request.isExpired() || request.consumedAt() != null) return new PollResult(PollStatus.EXPIRED, null);
        if (!request.isApproved()) return new PollResult(PollStatus.PENDING, null);
        if (request.enrollTokenIssued()) return new PollResult(PollStatus.APPROVED, null);

        String enrollToken = newSecret();
        if (!repository.storeEnrollToken(request.id(), tokenHasher.hash(enrollToken))) {
            return new PollResult(PollStatus.APPROVED, null);
        }
        return new PollResult(PollStatus.APPROVED, enrollToken);
    }

    /**
     * Opens the creation ceremony the enrolment token is good for. The token is not spent yet:
     * a browser that fails the ceremony may try again until the finish claims it.
     */
    public Optional<PasskeyService.CeremonyStart> beginEnrollment(String enrollToken) {
        Optional<PasskeyDeviceRequest> requestOpt = repository.findByEnrollToken(tokenHasher.hash(enrollToken));
        if (requestOpt.isEmpty()) return Optional.empty();
        PasskeyDeviceRequest request = requestOpt.get();
        if (request.isExpired() || request.consumedAt() != null || request.approvedAccountId() == null) {
            return Optional.empty();
        }
        Optional<Account> account = accountRepository.findById(request.approvedAccountId());
        if (account.isEmpty()) return Optional.empty();
        String displayName = (account.get().firstName() + " " + account.get().lastName()).trim();
        return Optional.of(passkeyService.startDeviceEnrollment(
                account.get().id(),
                account.get().email(),
                displayName.isBlank() ? account.get().email() : displayName));
    }

    /**
     * Spends the token and creates the credential. The claim comes first, so the token can do
     * exactly one thing exactly once; a ceremony that fails after the claim burns it, and the
     * way forward is a fresh request rather than a second try on a spent token.
     */
    public boolean finishEnrollment(String enrollToken, String challengeToken, String credentialJson, String country) {
        Optional<PasskeyDeviceRequest> claimed = repository.claimByEnrollToken(tokenHasher.hash(enrollToken));
        if (claimed.isEmpty()) return false;
        PasskeyDeviceRequest request = claimed.get();
        if (request.approvedAccountId() == null || request.attempts() >= MAX_ATTEMPTS) return false;

        Optional<TwoFactorFactor> factor = passkeyService.finishDeviceEnrollment(
                request.approvedAccountId(), challengeToken, credentialJson, request.requestedUserAgent(), country);
        if (factor.isEmpty()) {
            repository.incrementAttempts(request.id());
            return false;
        }

        accountRepository.findById(request.approvedAccountId()).ifPresent(account -> {
            try {
                emailService.sendPasskeyDeviceApprovedNotice(
                        account.email(),
                        account.firstName(),
                        request.requestedUserAgent(),
                        request.requestedCountry(),
                        mailLocaleService.forAccount(account.id()));
            } catch (Exception e) {
                log.warn("Failed to enqueue the device-approval notice for account {}", account.id(), e);
            }
        });
        return true;
    }

    public record CreatedRequest(String code, String pollSecret, Instant expiresAt) {}

    public enum PollStatus {
        PENDING,
        APPROVED,
        EXPIRED,
        UNKNOWN
    }

    /**
     * @param enrollToken the one-time enrolment token, present exactly once: on the poll that
     *         found the approval first
     */
    public record PollResult(PollStatus status, String enrollToken) {}
}
