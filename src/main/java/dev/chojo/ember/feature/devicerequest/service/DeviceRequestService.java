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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
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

    /**
     * Six numbers on the approval screen, one of them right.
     *
     * <p>This is what lets the code travel inside the QR. A forwarded picture carries the code but
     * not the screen that raised it, so whoever scanned it is asked for something they cannot see.
     * One in six is the price of guessing rather than stopping, and a wrong pick ends the request,
     * so it is one chance and not a series of them.
     */
    private static final int MATCH_CHOICES = 6;

    private static final int MATCH_LOWEST = 10;
    private static final int MATCH_HIGHEST = 99;

    /** Far enough apart that a digit read wrong off a screen lands on no other choice. */
    private static final int MATCH_MIN_DISTANCE = 3;

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

    /** Two digits, so it is read off a screen and said out loud without being written down. */
    private static int newMatchNumber() {
        return MATCH_LOWEST + RANDOM.nextInt(MATCH_HIGHEST - MATCH_LOWEST + 1);
    }

    /**
     * The numbers the approval screen offers, the real one among them, in the order it offers them.
     *
     * <p>Drawn once and stored, never per lookup: a fresh draw each time would leave the right
     * number the only one appearing in every round, and two looks at the same code would give it
     * away. The decoys keep their distance from the real one so that a misread digit lands on
     * nothing rather than on a plausible wrong answer.
     */
    private static List<Integer> matchChoicesAround(int matchNumber) {
        var choices = new ArrayList<Integer>(MATCH_CHOICES);
        choices.add(matchNumber);
        while (choices.size() < MATCH_CHOICES) {
            int candidate = newMatchNumber();
            boolean tooClose = choices.stream().anyMatch(taken -> Math.abs(taken - candidate) < MATCH_MIN_DISTANCE);
            if (!tooClose) choices.add(candidate);
        }
        Collections.shuffle(choices, RANDOM);
        return List.copyOf(choices);
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
     *
     * <p>The device says which account it wants, and that decides who may approve it. It is a claim
     * and not a proof: an address is public, so naming one buys nothing on its own. What it buys is
     * that a code raised for one person cannot be approved by another, which is what used to let a
     * code be sent to a crowd for whoever bit to answer with their own account.
     *
     * <p>An identifier that matches nothing still gets a code, a number and a poll, and is written
     * down naming nobody. The request simply cannot be approved and dies at its expiry. Anything
     * else would answer whether an address exists here.
     */
    public CreatedRequest createRequest(
            DeviceRequestPurpose purpose, String identifier, String userAgent, String country) {
        String code = newCode();
        String pollSecret = newSecret();
        int matchNumber = newMatchNumber();
        Instant expiresAt = Instant.now().plus(REQUEST_TTL);
        repository.create(
                purpose,
                tokenHasher.hash(code),
                tokenHasher.hash(pollSecret),
                resolveNamed(identifier).orElse(null),
                matchNumber,
                matchChoicesAround(matchNumber),
                userAgent,
                country,
                expiresAt);
        return new CreatedRequest(code, pollSecret, matchNumber, expiresAt);
    }

    /**
     * The account behind what somebody typed, by address or by username, or empty when it is
     * neither. Never says which of the two it was, and never says that it found nothing.
     */
    private Optional<Integer> resolveNamed(String identifier) {
        if (identifier == null || identifier.isBlank()) return Optional.empty();
        String trimmed = identifier.trim();
        return accountRepository
                .findByEmail(trimmed)
                .or(() -> accountRepository.findByUsername(trimmed))
                .map(Account::id);
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
    public ApprovalResult approve(int approvedAccountId, int subjectAccountId, String code, int pickedNumber) {
        Optional<DeviceRequest> request = repository.findOpenByCode(tokenHasher.hash(normalizeCode(code)));
        if (request.isEmpty()) return ApprovalResult.UNKNOWN;
        DeviceRequest open = request.get();
        if (open.matchNumber() != pickedNumber) {
            repository.reject(open.id());
            log.info("Device request {} ({}) refused: the number did not match", open.id(), open.purpose());
            return ApprovalResult.WRONG_NUMBER;
        }
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
        return approved ? ApprovalResult.APPROVED : ApprovalResult.UNKNOWN;
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
        if (request.isRejected()) return new PollResult(PollStatus.REJECTED, null, null);
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
                        NameParts.of(account).greeting(),
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
        int matchNumber = newMatchNumber();
        Instant expiresAt = Instant.now().plus(REQUEST_TTL);
        repository.createStepUp(
                tokenHasher.hash(code),
                tokenHasher.hash(pollSecret),
                accountId,
                sessionId,
                category,
                matchNumber,
                matchChoicesAround(matchNumber),
                userAgent,
                country,
                expiresAt);
        return new CreatedRequest(code, pollSecret, matchNumber, expiresAt);
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
                                NameParts.of(account).greeting(),
                                request.requestedUserAgent(),
                                request.requestedCountry(),
                                mailLocaleService.forAccount(account.id()));
                    } catch (Exception e) {
                        log.warn("Failed to enqueue the vouched sign-in notice for account {}", account.id(), e);
                    }
                });
    }

    /**
     * @param matchNumber the number the requesting screen shows, which the approving screen asks
     *         for. It is the one part of the handshake that never travels in the QR
     */
    public record CreatedRequest(String code, String pollSecret, int matchNumber, Instant expiresAt) {}

    /**
     * How an approval went, which the screen says three different things about.
     *
     * <p>A wrong number is told apart from an unknown code on purpose. The reader holding the phone
     * has the code in front of them and needs to know that the numbers were the problem, while
     * somebody fishing for a code learns nothing from it: reaching this answer at all means already
     * holding a code raised for their own account.
     */
    public enum ApprovalResult {
        APPROVED,
        WRONG_NUMBER,
        UNKNOWN
    }

    public enum PollStatus {
        PENDING,
        APPROVED,
        EXPIRED,
        UNKNOWN,
        /** Somebody picked the wrong number, which ends the request rather than costing a guess. */
        REJECTED
    }

    /**
     * @param claimToken the one-time token, present exactly once: on the poll that found the
     *         approval first
     * @param purpose what that token buys, so the asking device knows which ceremony follows.
     *         {@code null} where there is nothing yet to buy
     */
    public record PollResult(PollStatus status, String claimToken, DeviceRequestPurpose purpose) {}
}
