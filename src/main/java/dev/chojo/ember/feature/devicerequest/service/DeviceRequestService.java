/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.devicerequest.service;

import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequest;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import dev.chojo.ember.feature.devicerequest.repository.DeviceRequestRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * The device handshake: the asking device shows a code, a device that is already signed in approves
 * it, and the poll that follows hands the asking device a token it may spend exactly once. What that
 * token buys is the request's purpose and is fixed when the request is raised, never later.
 *
 * <p>This direction on purpose: the mirror version, where the signed-in device shows a code the new
 * one consumes, fails to the same social-engineering phone call at a lower price, because reading a
 * code out loud is easier to talk somebody into than approving a named, red-bordered request.
 *
 * <p>What stops somebody guessing a code is not this class. A wrong code is thrown away by the
 * lookup without ever reaching a row, so the attempt counter here never sees it; the brake is the
 * rate limiter on the approval screen, which can only exist because that screen is authenticated.
 *
 * <p>A request belongs to the instance it was raised on and to no other. The rows live in that
 * instance's own schema and the lookup reads nothing else, so a code shown here cannot be approved
 * from another installation. Nothing wants otherwise: federation joins installations that each hold
 * their own accounts, and an account there is not an account here, while a cluster is stations of
 * one installation sharing the one database, where this is already the same instance.
 */
@Singleton
public class DeviceRequestService {
    private static final Logger log = LoggerFactory.getLogger(DeviceRequestService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** No 0/O, 1/I/L or U (confusable with V), so the code survives being read from a screen. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int CODE_LENGTH = 8;

    /** Ten minutes, because somebody has to walk to another machine. */
    private static final Duration REQUEST_TTL = Duration.ofMinutes(10);

    private static final int MAX_ATTEMPTS = 5;

    private final DeviceRequestRepository repository;
    private final PasskeyService passkeyService;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final TwoFactorService twoFactorService;
    private final TwoFactorAuditService auditService;
    private final TokenHasher tokenHasher;
    private final EmailService emailService;
    private final MailLocaleService mailLocaleService;

    @Inject
    public DeviceRequestService(
            DeviceRequestRepository repository,
            PasskeyService passkeyService,
            AccountRepository accountRepository,
            AuthService authService,
            TwoFactorService twoFactorService,
            TwoFactorAuditService auditService,
            TokenHasher tokenHasher,
            EmailService emailService,
            MailLocaleService mailLocaleService) {
        this.repository = repository;
        this.passkeyService = passkeyService;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.twoFactorService = twoFactorService;
        this.auditService = auditService;
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

    /**
     * Raises a request from a device nobody has identified yet, which is how a passkey enrolment and
     * a sign-in both begin. What the approval will buy is fixed here and never later.
     */
    public CreatedRequest createRequest(DeviceRequestPurpose purpose, String userAgent, String country) {
        String code = newCode();
        String pollSecret = newSecret();
        Instant expiresAt = Instant.now().plus(REQUEST_TTL);
        repository.create(purpose, tokenHasher.hash(code), tokenHasher.hash(pollSecret), userAgent, country, expiresAt);
        return new CreatedRequest(code, pollSecret, expiresAt);
    }

    /**
     * What the approval screen shows before anybody approves anything: browser, place, time, in
     * plain words. Empty is all a wrong code earns.
     */
    public Optional<DeviceRequest> lookup(String code) {
        return repository.findOpenByCode(tokenHasher.hash(normalizeCode(code)));
    }

    /**
     * Approves the request, naming both who approved it and whose account the grant is for. The two
     * differ only where a guardian signs in a member in their care; everywhere else the approver is
     * the subject. The caller has already proved itself locally; this ties the row, exactly once.
     *
     * <p>The approval is written down here rather than only where the grant is spent. Up to ten
     * minutes lie between the two, and the cases worth investigating are the ones where the second
     * never happens: a grant nobody claimed and a grant a revoke voided would otherwise leave no
     * record that anybody ever said yes.
     */
    public boolean approve(int approvedAccountId, int subjectAccountId, String code) {
        Optional<DeviceRequest> request = repository.findOpenByCode(tokenHasher.hash(normalizeCode(code)));
        if (request.isEmpty()) return false;
        DeviceRequest open = request.get();
        boolean approved = repository.approve(open.id(), approvedAccountId, subjectAccountId);
        if (approved) {
            auditService.record(
                    subjectAccountId,
                    approvedAccountId,
                    TwoFactorEvent.DEVICE_REQUEST_APPROVED,
                    null,
                    open.requestedUserAgent(),
                    open.requestedCountry());
            log.info(
                    "Device request {} ({}) approved by account {} for account {}",
                    open.id(),
                    open.purpose(),
                    approvedAccountId,
                    subjectAccountId);
        }
        return approved;
    }

    /**
     * The asking device wanting to know whether anything happened yet. The claim token is minted on
     * the first poll after the approval and delivered exactly once; the guarded update means two
     * racing polls cannot both walk away with one.
     *
     * <p>The caller names the purpose it came for, and a request of any other is answered as though
     * the secret were unknown. Minting is where the one-time delivery is spent, so a poll that
     * reached the wrong door would either hand a grant to an endpoint that must not have it or burn
     * the only delivery of one it then throws away.
     *
     * @param allowed what the calling endpoint is entitled to hand over
     */
    public PollResult poll(String pollSecret, Set<DeviceRequestPurpose> allowed) {
        Optional<DeviceRequest> requestOpt = repository.findByPollSecret(tokenHasher.hash(pollSecret));
        if (requestOpt.isEmpty()) return new PollResult(PollStatus.UNKNOWN, null, null);
        DeviceRequest request = requestOpt.get();
        if (!allowed.contains(request.purpose())) return new PollResult(PollStatus.UNKNOWN, null, null);
        if (request.isExpired() || request.consumedAt() != null) {
            return new PollResult(PollStatus.EXPIRED, null, null);
        }
        if (!request.isApproved()) return new PollResult(PollStatus.PENDING, null, null);
        if (request.claimTokenIssued()) return new PollResult(PollStatus.APPROVED, null, request.purpose());

        String claimToken = newSecret();
        if (!repository.storeClaimToken(request.id(), tokenHasher.hash(claimToken))) {
            return new PollResult(PollStatus.APPROVED, null, request.purpose());
        }
        return new PollResult(PollStatus.APPROVED, claimToken, request.purpose());
    }

    /**
     * Opens the creation ceremony the enrolment token is good for. The token is not spent yet:
     * a browser that fails the ceremony may try again until the finish claims it.
     */
    public Optional<PasskeyService.CeremonyStart> beginEnrollment(String enrollToken) {
        Optional<DeviceRequest> requestOpt =
                repository.findByClaimToken(tokenHasher.hash(enrollToken), DeviceRequestPurpose.ENROL_PASSKEY);
        if (requestOpt.isEmpty()) return Optional.empty();
        DeviceRequest request = requestOpt.get();
        if (request.isExpired() || request.consumedAt() != null || request.subjectAccountId() == null) {
            return Optional.empty();
        }
        Optional<Account> account = accountRepository.findById(request.subjectAccountId());
        if (account.isEmpty()) return Optional.empty();
        String displayName = NameParts.of(account.get()).official();
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
        Optional<DeviceRequest> claimed =
                repository.claimByToken(tokenHasher.hash(enrollToken), DeviceRequestPurpose.ENROL_PASSKEY);
        if (claimed.isEmpty()) return false;
        DeviceRequest request = claimed.get();
        if (request.subjectAccountId() == null || request.attempts() >= MAX_ATTEMPTS) return false;

        Optional<TwoFactorFactor> factor = passkeyService.finishDeviceEnrollment(
                request.subjectAccountId(), challengeToken, credentialJson, request.requestedUserAgent(), country);
        if (factor.isEmpty()) {
            repository.incrementAttempts(request.id());
            return false;
        }

        accountRepository.findById(request.subjectAccountId()).ifPresent(account -> {
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

    /**
     * Spends a sign-in claim and answers with the session it bought.
     *
     * <p>The claim comes first and exactly once, the same way the enrolment token is claimed, so two
     * devices racing the same token cannot both walk away signed in. What follows is an ordinary
     * sign-in for the account the approval named: unstamped, untrusted, and refused outright if the
     * account is in no state to be used.
     *
     * <p>The request row is a scratchpad that the sweep deletes minutes later, so the audit entry is
     * what outlives it. It is written against the account that was signed in, because that is whose
     * access is in question, and names the approver so the two can be told apart afterwards.
     */
    public Optional<LoginResult> claimSignIn(String claimToken, String userAgent, String location) {
        Optional<DeviceRequest> claimed =
                repository.claimByToken(tokenHasher.hash(claimToken), DeviceRequestPurpose.SIGN_IN);
        if (claimed.isEmpty()) return Optional.empty();
        DeviceRequest request = claimed.get();
        if (request.subjectAccountId() == null) return Optional.empty();
        if (revokedSinceApproval(request)) {
            log.info(
                    "Device request {} was approved before account {} ended every session, so it opens nothing",
                    request.id(),
                    request.subjectAccountId());
            return Optional.empty();
        }

        LoginResult result = authService.admitVouchedForAccount(request.subjectAccountId(), userAgent, location);
        if (!result.success()) return Optional.of(result);

        auditService.record(
                request.subjectAccountId(),
                request.approvedAccountId(),
                TwoFactorEvent.SIGNED_IN_VIA_DEVICE_CODE,
                null,
                request.requestedUserAgent(),
                request.requestedCountry());
        notifyVouchedSignIn(request);
        log.info(
                "Device request {} signed account {} in on a device approved by account {}",
                request.id(),
                request.subjectAccountId(),
                request.approvedAccountId());
        return Optional.of(result);
    }

    /**
     * Raises a step-up request for the session that met the demand.
     *
     * <p>Unlike the other two purposes this one is raised by somebody already known, so the row holds
     * the session it will stamp and the account that session belongs to. The category travels with
     * it: an approval screen that can only say "confirm something sensitive" is not a thing anybody
     * can judge.
     */
    public CreatedRequest createStepUpRequest(
            int accountId, int sessionId, StepUpCategory category, String userAgent, String country) {
        String code = newCode();
        String pollSecret = newSecret();
        Instant expiresAt = Instant.now().plus(REQUEST_TTL);
        repository.createStepUp(
                tokenHasher.hash(code),
                tokenHasher.hash(pollSecret),
                accountId,
                sessionId,
                category,
                userAgent,
                country,
                expiresAt);
        return new CreatedRequest(code, pollSecret, expiresAt);
    }

    /**
     * Spends a step-up claim and stamps the session that raised it.
     *
     * <p>The stamp records that another device answered, which is what stops that session going on to
     * vouch for a third: every chain has to end in somebody proving themselves at a keyboard. It also
     * records the category the approver was shown, and the stamp answers that one only: a
     * confirmation given for the mildest thing the product asks about must not buy the gravest.
     */
    public boolean claimStepUp(String claimToken) {
        Optional<DeviceRequest> claimed =
                repository.claimByToken(tokenHasher.hash(claimToken), DeviceRequestPurpose.STEP_UP);
        if (claimed.isEmpty()) return false;
        DeviceRequest request = claimed.get();
        if (request.requestingSessionId() == null) return false;

        twoFactorService.markSessionTwoFactorVerified(
                request.requestingSessionId(), StepUpProof.ANOTHER_DEVICE, request.stepUpCategory());
        auditService.record(
                request.requestingAccountId(),
                request.approvedAccountId(),
                TwoFactorEvent.STEPUP_VIA_DEVICE_CODE,
                null,
                request.requestedUserAgent(),
                request.requestedCountry());
        log.info(
                "Device request {} confirmed a {} step-up for session {} of account {}",
                request.id(),
                request.stepUpCategory(),
                request.requestingSessionId(),
                request.requestingAccountId());
        return true;
    }

    /**
     * Whether somebody ended every session of this account after the grant was approved.
     *
     * <p>Voiding the pending requests catches everything that already names the account, and a
     * sign-in names nobody until it is approved. A revoke landing in the moment between the approval
     * screen's checks and its write therefore passes the row by. Asking here rather than trusting the
     * void is what makes saying stop mean it, however the two land relative to each other.
     */
    private boolean revokedSinceApproval(DeviceRequest request) {
        if (request.approvedAt() == null) return true;
        return accountRepository
                .findSessionsRevokedAt(request.subjectAccountId())
                .filter(revokedAt -> revokedAt.isAfter(request.approvedAt()))
                .isPresent();
    }

    /**
     * Tells the account a device was signed in as them. Best effort: an instance can be run with no
     * mail at all, and the audit row above is what an investigation actually reads.
     *
     * <p>Only where there is somewhere to tell. A managed member's address is synthetic and receives
     * nothing, and the person who would otherwise be told is the guardian who just approved it and
     * already knows.
     */
    private void notifyVouchedSignIn(DeviceRequest request) {
        accountRepository
                .findById(request.subjectAccountId())
                .filter(Account::hasRealEmail)
                .ifPresent(account -> {
                    try {
                        emailService.sendDeviceSignedInNotice(
                                account.email(),
                                account.firstName(),
                                request.requestedUserAgent(),
                                request.requestedCountry(),
                                mailLocaleService.forAccount(account.id()));
                    } catch (Exception e) {
                        log.warn("Failed to enqueue the vouched sign-in notice for account {}", account.id(), e);
                    }
                });
    }

    public record CreatedRequest(String code, String pollSecret, Instant expiresAt) {}

    public enum PollStatus {
        PENDING,
        APPROVED,
        EXPIRED,
        UNKNOWN
    }

    /**
     * @param claimToken the one-time token, present exactly once: on the poll that found the
     *         approval first
     * @param purpose what that token buys, so the asking device knows which ceremony follows.
     *         {@code null} where there is nothing yet to buy
     */
    public record PollResult(PollStatus status, String claimToken, DeviceRequestPurpose purpose) {}
}
