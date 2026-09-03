/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.service;

import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.mail.service.MailLocaleService;
import dev.chojo.ember.feature.mail.service.MailRecipientService;
import dev.chojo.ember.feature.passkey.repository.PasskeyRepository;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorFactor;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * The one mechanism behind every door to a passkey that is not a session: a bearer token that
 * may create exactly one credential, and nothing else. The doors differ in who opens them and
 * how long the token lives; the token itself is the same. The setup and reset tokens double as
 * doors on a passwordless instance, because that mail already holds exactly this power today.
 */
@Singleton
public class PasskeyEnrollmentService {
    private static final Logger log = LoggerFactory.getLogger(PasskeyEnrollmentService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** No 0/O, 1/I/L or U, so a code typed from a screen survives the typing. */
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int CODE_LENGTH = 8;

    /** Five minutes for the QR in the room: both people are standing there. */
    public static final Duration QR_TTL = Duration.ofMinutes(5);

    /** An hour for the console link and the re-onboarding mail: a person has to notice it. */
    public static final Duration LINK_TTL = Duration.ofHours(1);

    /** Which stored tokens open the enrolment ceremony everywhere. */
    private static final Set<TokenType> ENROLLMENT_DOORS =
            EnumSet.of(TokenType.PASSKEY_ENROLLMENT, TokenType.SET_PASSWORD, TokenType.RESET_PASSWORD);

    private final AccountRepository accountRepository;
    private final PasskeyRepository passkeyRepository;
    private final PasskeyService passkeyService;
    private final AuthService authService;
    private final TwoFactorAuditService auditService;
    private final MailRecipientService mailRecipientService;
    private final PasskeyModeService modeService;
    private final EmailService emailService;
    private final MailLocaleService mailLocaleService;
    private final TotpService totpService;
    private final Api api;

    @Inject
    public PasskeyEnrollmentService(
            AccountRepository accountRepository,
            PasskeyRepository passkeyRepository,
            PasskeyService passkeyService,
            AuthService authService,
            TwoFactorAuditService auditService,
            MailRecipientService mailRecipientService,
            PasskeyModeService modeService,
            EmailService emailService,
            MailLocaleService mailLocaleService,
            TotpService totpService,
            Api api) {
        this.accountRepository = accountRepository;
        this.passkeyRepository = passkeyRepository;
        this.passkeyService = passkeyService;
        this.authService = authService;
        this.auditService = auditService;
        this.mailRecipientService = mailRecipientService;
        this.modeService = modeService;
        this.emailService = emailService;
        this.mailLocaleService = mailLocaleService;
        this.totpService = totpService;
        this.api = api;
    }

    private static String newCode() {
        var code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return code.toString();
    }

    private static String normalize(String token) {
        return token == null ? "" : token.replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    /**
     * Issues a fresh enrolment code for the account, killing whatever one stood before it: an
     * abandoned attempt must not leave a photographed code alive for the rest of its window.
     */
    public String issueCode(int accountId, Duration ttl) {
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.PASSKEY_ENROLLMENT);
        String code = newCode();
        accountRepository.createToken(
                accountId, code, TokenType.PASSKEY_ENROLLMENT, Instant.now().plus(ttl));
        return code;
    }

    /** Kills the open code, for the guardian leaving the screen. */
    public void revokeCode(int accountId) {
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.PASSKEY_ENROLLMENT);
    }

    /**
     * The QR code held up in the room: issues a fresh code with its QR, tells whoever mail
     * about the account already goes to (the guardians, whoever pressed the button), and writes
     * the audit row. Without that, a credential appears on an account and the only record is a
     * log line.
     */
    public IssuedCode issueCodeWithQr(
            int targetAccountId, Integer actorAccountId, Duration ttl, String userAgent, String country) {
        String code = issueCode(targetAccountId, ttl);
        auditService.record(
                targetAccountId,
                actorAccountId,
                TwoFactorEvent.PASSKEY_CODE_ISSUED,
                TwoFactorKind.WEBAUTHN,
                userAgent,
                country);
        accountRepository.findById(targetAccountId).ifPresent(target -> {
            try {
                String locale = mailLocaleService.forAccount(targetAccountId);
                for (var recipient : mailRecipientService.forAccount(targetAccountId)) {
                    emailService.sendPasskeyCodeIssuedNotice(recipient.email(), target.firstName(), locale);
                }
            } catch (Exception e) {
                log.warn("Failed to enqueue the passkey-code notice for account {}", targetAccountId, e);
            }
        });
        String qrPng = java.util.Base64.getEncoder()
                .encodeToString(totpService.generateQrPng(api.baseUrl() + "/enroll?code=" + code, 240));
        return new IssuedCode(code, qrPng, Instant.now().plus(ttl));
    }

    /**
     * @param qrPng base64 PNG of a QR that carries the enrolment grant itself. A photograph of
     *         it is as good as the thing, which is why it lives five minutes and dies on use.
     */
    public record IssuedCode(String code, String qrPng, Instant expiresAt) {}

    /**
     * Whose account a token opens, so the member's device names it before asking for a
     * fingerprint. Empty is all an unknown or expired token earns.
     */
    public Optional<Account> lookup(String rawToken) {
        return findDoor(rawToken)
                .flatMap(door -> accountRepository.findById(door.token().accountId()));
    }

    /**
     * Opens the creation ceremony behind a token. The token is not spent yet; the finish
     * consumes it whatever happens.
     */
    public Optional<PasskeyService.CeremonyStart> begin(String rawToken) {
        return findDoor(rawToken)
                .flatMap(door -> accountRepository.findById(door.token().accountId()))
                .map(account -> {
                    String displayName = (account.firstName() + " " + account.lastName()).trim();
                    return passkeyService.startDeviceEnrollment(
                            account.id(), account.email(), displayName.isBlank() ? account.email() : displayName);
                });
    }

    /**
     * Spends the token and creates the credential: consumed first, fail-closed, so it cannot be
     * spent twice however the ceremony ends. A door that came through the verification mail also
     * verifies the address, because reaching it proved the same thing the mail was for.
     */
    public boolean finish(String rawToken, String challengeToken, String credentialJson, String country) {
        Optional<Door> doorOpt = findDoor(rawToken);
        if (doorOpt.isEmpty()) return false;
        Door door = doorOpt.get();
        accountRepository.deleteToken(door.matchedRaw());

        Optional<TwoFactorFactor> factor =
                passkeyService.finishTokenEnrollment(door.token().accountId(), challengeToken, credentialJson, country);
        if (factor.isEmpty()) return false;

        if (door.token().tokenType() == TokenType.VERIFY_EMAIL) {
            accountRepository.setEmailVerified(door.token().accountId());
        }
        log.info(
                "Passkey enrolled for account {} through a {} token",
                door.token().accountId(),
                door.token().tokenType());
        return true;
    }

    /**
     * Onboards a member again: every passkey disabled, every session and token ended, and a
     * fresh setup link sent where mail about the account already goes, which is the guardians
     * for a member with no address of their own. Not quite the button that resends a setup mail
     * today: that one refuses once somebody has ever signed in, and this one exists precisely
     * for somebody who did.
     *
     * @return whether anybody could be mailed; when not, the QR code in the room is the way
     */
    public boolean onboardAgain(int targetAccountId, int actorAccountId, String userAgent, String country) {
        int disabled = passkeyRepository.disableSignInPasskeys(targetAccountId);
        accountRepository.deleteSessionsByAccount(targetAccountId);
        accountRepository.deleteAllTokens(targetAccountId);
        auditService.record(
                targetAccountId,
                actorAccountId,
                TwoFactorEvent.ADMIN_RESET,
                TwoFactorKind.WEBAUTHN,
                userAgent,
                country);
        boolean mailed = authService.sendPasswordSetup(targetAccountId);
        log.info(
                "Account {} onboarded again by {}: {} passkey(s) disabled, setup mail {}",
                targetAccountId,
                actorAccountId,
                disabled,
                mailed ? "sent" : "undeliverable");
        return mailed;
    }

    /** Whether mail about the account reaches anybody, which decides between mail and QR. */
    public boolean isReachable(int accountId) {
        return mailRecipientService.isReachable(accountId);
    }

    private boolean isDoor(TokenType type) {
        if (ENROLLMENT_DOORS.contains(type)) return true;
        // The verification mail's link is where the passkey is made on a passwordless instance
        // (self-registration writes no credential row there); everywhere else it only verifies.
        return type == TokenType.VERIFY_EMAIL && modeService.effectiveMode() == PasskeySettings.Mode.PASSWORDLESS;
    }

    private Optional<Door> findDoor(String rawToken) {
        // A typed 8-char code arrives grouped and case-mangled; a link token arrives verbatim.
        // Whichever form matched is remembered, because consuming goes by the same raw value.
        Optional<Door> door = accountRepository.findToken(rawToken).map(token -> new Door(token, rawToken));
        if (door.isEmpty()) {
            String normalized = normalize(rawToken);
            door = accountRepository.findToken(normalized).map(token -> new Door(token, normalized));
        }
        return door.filter(
                found -> !found.token().isExpired() && isDoor(found.token().tokenType()));
    }

    private record Door(AccountToken token, String matchedRaw) {}
}
