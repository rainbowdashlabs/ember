/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.auth.BreachCheckWorker;
import dev.chojo.ember.auth.HibpClient;
import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.auth.PasswordPolicy;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.entity.RegistrationResult;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.RegistrationCodeRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service handling authentication, registration, session management, password operations,
 * email changes, and station deletion confirmations.
 */
@Singleton
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final RegistrationCodeRepository registrationCodeRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final Auth authConfig;
    private final Demo demo;
    private final HibpClient hibpClient;
    private final BreachCheckWorker breachCheckWorker;
    private final TwoFactorRepository twoFactorRepository;
    private final TrustedDeviceService trustedDeviceService;
    /**
     * Lazily-computed hash of a random throwaway password, used by {@link #login} when the
     * supplied email does not resolve to an account or has no stored credential. Running the
     * verifier against this placeholder keeps the wall-clock cost of every login attempt
     * within the same order of magnitude as a real hash check, denying a timing oracle for
     * account enumeration.
     */
    private volatile String dummyPasswordHash;

    @Inject
    public AuthService(
            AccountRepository accountRepository,
            RegistrationCodeRepository registrationCodeRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            PasswordHasher passwordHasher,
            EmailService emailService,
            Auth authConfig,
            Demo demo,
            HibpClient hibpClient,
            BreachCheckWorker breachCheckWorker,
            TwoFactorRepository twoFactorRepository,
            TrustedDeviceService trustedDeviceService) {
        this.accountRepository = accountRepository;
        this.registrationCodeRepository = registrationCodeRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.authConfig = authConfig;
        this.demo = demo;
        this.hibpClient = hibpClient;
        this.breachCheckWorker = breachCheckWorker;
        this.twoFactorRepository = twoFactorRepository;
        this.trustedDeviceService = trustedDeviceService;
    }

    /**
     * Registers a new account via self-registration. Optionally validates a registration code to create
     * a station membership and assign groups. Sends a verification email upon success.
     *
     * @param email            the email address
     * @param firstName        the first name
     * @param lastName         the last name
     * @param password         the plaintext password
     * @param registrationCode optional station registration code
     * @return the registration result indicating success or failure with a message
     */
    public RegistrationResult registerSelf(
            String email, String firstName, String lastName, String password, String registrationCode) {
        var policy = validateNewPassword(password);
        if (policy != PasswordPolicy.Result.OK) {
            return RegistrationResult.failure(policy.message());
        }
        RegistrationCode code = null;
        if (registrationCode != null && !registrationCode.isBlank()) {
            Optional<RegistrationCode> codeOpt = registrationCodeRepository.findByCode(registrationCode);
            if (codeOpt.isEmpty()) {
                return RegistrationResult.failure("Invalid registration code");
            }
            code = codeOpt.get();
            if (!code.hasUsesLeft()) {
                return RegistrationResult.failure("Registration code has been exhausted");
            }
        }

        var existing = accountRepository.findByEmail(email);
        if (existing.isPresent()) {
            emailService.sendDuplicateRegistrationNotice(
                    existing.get().email(),
                    existing.get().firstName(),
                    accountRepository.findMailLocale(existing.get().id()));
            return RegistrationResult.maskedSuccess(email, firstName, lastName);
        }

        String hash = passwordHasher.hash(password);
        var account = accountRepository.create(email, firstName, lastName, false);
        int accountId = account.id();

        accountRepository.createCredential(accountId, hash);

        if (code != null) {
            // Create station membership from code's station
            var member = stationMemberRepository.create(code.stationId(), accountId);
            int memberId = member.id();

            // Assign groups from registration code
            List<Integer> groupIds = registrationCodeRepository.findGroupIds(code.id());
            for (int groupId : groupIds) {
                memberGroupRepository.addMember(groupId, memberId);
            }

            registrationCodeRepository.incrementUses(code.id());
        }

        // Send verification email
        String token = generateToken();
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.VERIFY_EMAIL);
        accountRepository.createToken(
                accountId,
                token,
                TokenType.VERIFY_EMAIL,
                Instant.now().plus(authConfig.verifyTokenHours(), ChronoUnit.HOURS));
        emailService.sendVerificationEmail(email, firstName, token, accountRepository.findMailLocale(accountId));

        log.info("Account registered via self-registration: account {} ({})", accountId, email);
        return RegistrationResult.success(accountRepository.findById(accountId).orElseThrow());
    }

    /**
     * Creates an account for an invited user. The email is pre-verified, and a password setup token
     * is sent via email. A station membership is created automatically.
     *
     * @param email     the email address
     * @param firstName the first name
     * @param lastName  the last name
     * @param stationId the station to create a membership for
     * @return the registration result indicating success or failure
     */
    public RegistrationResult createInvitedAccount(String email, String firstName, String lastName, int stationId) {
        if (accountRepository.findByEmail(email).isPresent()) {
            return RegistrationResult.failure("Email already in use");
        }

        var account = accountRepository.create(email, firstName, lastName, true, stationId);
        int accountId = account.id();

        stationMemberRepository.create(stationId, accountId);

        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.SET_PASSWORD,
                Instant.now().plus(authConfig.passwordTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordSetupEmail(email, firstName, token, accountRepository.findMailLocale(accountId));

        log.info("Invited account created: account {} ({}) for station {}", accountId, email, stationId);
        return RegistrationResult.success(accountRepository.findById(accountId).orElseThrow());
    }

    /**
     * Sends a password setup email to an existing account that has no credentials yet.
     * Creates a SET_PASSWORD token and sends the setup email.
     *
     * @param accountId the account identifier
     * @param email     the email address
     * @param firstName the first name for the email greeting
     */
    public void sendPasswordSetup(int accountId, String email, String firstName) {
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.SET_PASSWORD);
        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.SET_PASSWORD,
                Instant.now().plus(authConfig.passwordTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordSetupEmail(email, firstName, token, accountRepository.findMailLocale(accountId));
    }

    /**
     * Verifies an email address using the provided token. The token is consumed on success or deleted if expired.
     *
     * @param token the verification token
     * @return {@code true} if the email was successfully verified
     */
    public boolean verifyEmail(String token) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        AccountToken accountToken = tokenOpt.get();
        if (accountToken.isExpired() || accountToken.tokenType() != TokenType.VERIFY_EMAIL) {
            accountRepository.deleteToken(token);
            return false;
        }

        accountRepository.setEmailVerified(accountToken.accountId());
        accountRepository.deleteToken(token);
        log.info("Email verified for account {}", accountToken.accountId());
        return true;
    }

    /**
     * Sets a password using a token. Accepts SET_PASSWORD, RESET_PASSWORD, and FORCE_PASSWORD_CHANGE tokens.
     * Creates credentials if none exist, otherwise updates the existing password hash.
     *
     * @param token    the password setup or reset token
     * @param password the new plaintext password
     * @return {@code true} if the password was successfully set
     */
    public SetPasswordOutcome setPassword(String token, String password) {
        PasswordPolicy.Result policy = validateNewPassword(password);
        if (policy == PasswordPolicy.Result.TOO_SHORT) {
            log.info("[set-password] rejected: password too short");
            return SetPasswordOutcome.PASSWORD_TOO_SHORT;
        }
        if (policy == PasswordPolicy.Result.BREACHED) {
            log.info("[set-password] rejected: password found in breach corpus");
            return SetPasswordOutcome.PASSWORD_BREACHED;
        }
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) {
            log.info("[set-password] rejected: token not found");
            return SetPasswordOutcome.TOKEN_INVALID;
        }

        AccountToken accountToken = tokenOpt.get();
        TokenType type = accountToken.tokenType();
        if (accountToken.isExpired()
                || (type != TokenType.SET_PASSWORD
                        && type != TokenType.RESET_PASSWORD
                        && type != TokenType.FORCE_PASSWORD_CHANGE)) {
            log.info(
                    "[set-password] rejected: token type {} expired={} account={}",
                    type,
                    accountToken.isExpired(),
                    accountToken.accountId());
            accountRepository.deleteToken(token);
            return SetPasswordOutcome.TOKEN_INVALID;
        }

        String hash = passwordHasher.hash(password);
        Optional<Account> account = accountRepository.findById(accountToken.accountId());
        if (account.isEmpty()) {
            log.warn("[set-password] rejected: account {} for valid token is gone", accountToken.accountId());
            return SetPasswordOutcome.TOKEN_INVALID;
        }

        if (accountRepository.findCredential(accountToken.accountId()).isPresent()) {
            accountRepository.updateCredential(accountToken.accountId(), hash);
        } else {
            accountRepository.createCredential(accountToken.accountId(), hash);
        }

        invalidateAfterPasswordRotation(accountToken.accountId(), null);
        notifyPasswordChanged(account.get());
        // The endpoint accepts SET_PASSWORD (invite), RESET_PASSWORD (forgot),
        // and FORCE_PASSWORD_CHANGE (post-login forced rotation) interchangeably.
        // Logging which one triggered the rotation lets operators correlate the
        // flow without a dedicated audit table.
        log.info("Password set via {} for account {}", type, accountToken.accountId());
        return SetPasswordOutcome.OK;
    }

    /**
     * Outcome of {@link #setPassword(String, String)}. Surfaces distinct rejection reasons so
     * the route can return a precise error message instead of the same opaque "Invalid or
     * expired token" string for every failure mode.
     */
    public enum SetPasswordOutcome {
        /** Password was accepted and the credential was rotated. */
        OK,
        /** Submitted password is shorter than {@link PasswordPolicy#MIN_LENGTH}. */
        PASSWORD_TOO_SHORT,
        /** Submitted password is on the Have-I-Been-Pwned breach corpus. */
        PASSWORD_BREACHED,
        /** Token does not exist, has expired, has the wrong type, or its account is gone. */
        TOKEN_INVALID
    }

    /**
     * Initiates a password reset by sending a reset email. Silently does nothing if the email is not found,
     * to prevent email enumeration.
     *
     * @param email the email address to send the reset link to
     */
    public void requestPasswordReset(String email) {
        Optional<Account> accountOpt = accountRepository.findByEmail(email);
        if (accountOpt.isEmpty()) {
            return;
        }

        Account account = accountOpt.get();
        String token = generateToken();
        accountRepository.deleteTokensByAccountAndType(account.id(), TokenType.RESET_PASSWORD);
        accountRepository.createToken(
                account.id(),
                token,
                TokenType.RESET_PASSWORD,
                Instant.now().plus(authConfig.resetTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordResetEmail(
                account.email(), account.firstName(), token, accountRepository.findMailLocale(account.id()));
        log.info("Password reset requested for account {} ({})", account.id(), email);
    }

    /**
     * Resets a password as an administrator. Optionally sets a force-password-change flag and sends a reset email.
     *
     * @param accountId   the account identifier
     * @param forceChange if {@code true}, the user will be required to change their password on next login
     * @return {@code true} if the account was found and the reset email was sent
     */
    public boolean adminResetPassword(int accountId, boolean forceChange) {
        Optional<Account> accountOpt = accountRepository.findById(accountId);
        if (accountOpt.isEmpty()) {
            return false;
        }

        Account account = accountOpt.get();

        if (forceChange) {
            log.info("Admin reset for account {} ({}) with forced password change", accountId, account.email());
            accountRepository.setForcePasswordChange(accountId, true);
        }

        // Kill every live session and outstanding recovery token for the account so
        // an attacker who triggered this reset cannot continue with the previous
        // credentials. The freshly-issued RESET_PASSWORD token below is created
        // *after* the wipe so it is not collateral.
        invalidateAfterPasswordRotation(accountId, null);

        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.RESET_PASSWORD,
                Instant.now().plus(authConfig.passwordTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordResetEmail(
                account.email(), account.firstName(), token, accountRepository.findMailLocale(account.id()));
        return true;
    }

    /**
     * Resends the verification email for an unverified account. Does nothing if the email is not found
     * or already verified.
     *
     * @param email the email address
     * @return {@code true} if the verification email was sent
     */
    public boolean resendVerification(String email) {
        Optional<Account> accountOpt = accountRepository.findByEmail(email);
        if (accountOpt.isEmpty() || accountOpt.get().emailVerified()) {
            return false;
        }

        Account account = accountOpt.get();
        String token = generateToken();
        accountRepository.deleteTokensByAccountAndType(account.id(), TokenType.VERIFY_EMAIL);
        accountRepository.createToken(
                account.id(),
                token,
                TokenType.VERIFY_EMAIL,
                Instant.now().plus(authConfig.verifyTokenHours(), ChronoUnit.HOURS));
        emailService.sendVerificationEmail(
                account.email(), account.firstName(), token, accountRepository.findMailLocale(account.id()));
        return true;
    }

    /**
     * Authenticates a user with email and password. Validates email verification, credentials, and login
     * authorization. If a forced password change is required, returns a password change token instead of
     * a session. Rehashes the password if the hashing algorithm has changed.
     *
     * @param email     the email address
     * @param password  the plaintext password
     * @param userAgent the client's user agent string
     * @param location  the client's location (e.g. country code)
     * @return the login result containing a session token or password change token, or a failure message
     */
    public LoginResult login(String email, String password, String userAgent, String location) {
        return login(email, password, userAgent, location, null);
    }

    /**
     * Logs in a demo / dev account by email only. Used by the one-click quick-login UI so the
     * dev or demo operator can keep clicking a seeded user even after their password has been
     * rotated. Bypasses password verification, the {@code force_password_change} branch, and
     * the 2FA challenge — none of those make sense for a click-to-impersonate flow whose only
     * caller is the dev/demo login page.
     *
     * <p>Refuses (with a failure result, never a partial session) when neither
     * {@code demo.dev()} nor {@code demo.enabled()} is set. This is a defence-in-depth check on
     * top of the route-level gate so the path stays inert if a future code change ever exposes
     * it outside the dev / demo origin.
     */
    public LoginResult loginAsDemo(String email, String userAgent, String location) {
        if (!demo.dev() && !demo.enabled()) {
            return LoginResult.failure("Quick login is only available in dev or demo mode");
        }
        Optional<Account> accountOpt = accountRepository.findByEmail(email);
        if (accountOpt.isEmpty()) {
            return LoginResult.failure("Invalid email or password");
        }
        Account account = accountOpt.get();
        log.info(
                "[demo] quick login: signing in as account {} ({}) without password verification", account.id(), email);
        return createSession(account.id(), userAgent, location);
    }

    /**
     * Variant of {@link #login(String, String, String, String)} that additionally accepts the
     * {@code ember_2fa_trust} cookie value. If the cookie resolves to a valid trusted-device row
     * for the authenticated account, the 2FA challenge is skipped and a fully-verified session
     * is minted immediately.
     */
    public LoginResult login(
            String email, String password, String userAgent, String location, String trustedDeviceCookie) {
        Optional<Account> accountOpt = accountRepository.findByEmail(email);
        Optional<AccountCredential> credOpt = accountOpt.flatMap(a -> accountRepository.findCredential(a.id()));

        String storedHash = credOpt.map(AccountCredential::passwordHash).orElseGet(this::dummyPasswordHash);
        boolean passwordValid = passwordHasher.verify(password, storedHash);

        if (accountOpt.isEmpty() || credOpt.isEmpty() || !passwordValid) {
            log.info("Login failed for '{}': invalid credentials", email);
            return LoginResult.failure("Invalid email or password");
        }

        Account account = accountOpt.get();
        if (!account.emailVerified()) {
            log.info("Login failed for account {} ({}): email not verified", account.id(), email);
            return LoginResult.failure("Email not verified");
        }

        // Rehash if algorithm changed
        if (passwordHasher.needsRehash(credOpt.get().passwordHash())) {
            accountRepository.updateCredential(account.id(), passwordHasher.hash(password));
        }

        // Async HIBP breach check — gated by staleness window inside the worker, fail-open.
        if (!demo.enabled() && !demo.dev()) {
            breachCheckWorker.enqueueCheck(account.id(), password);
        }

        // Force password change — issue a one-time token instead of a session
        if (credOpt.get().forcePasswordChange()) {
            log.info(
                    "Login for account {} ({}) requires password change — issuing password-change token",
                    account.id(),
                    email);
            String token = generateToken();
            accountRepository.deleteTokensByAccountAndType(account.id(), TokenType.FORCE_PASSWORD_CHANGE);
            accountRepository.createToken(
                    account.id(),
                    token,
                    TokenType.FORCE_PASSWORD_CHANGE,
                    Instant.now().plus(authConfig.sessionMinutes(), ChronoUnit.MINUTES));
            return LoginResult.passwordChangeRequired(
                    token, Instant.now().plus(authConfig.sessionMinutes(), ChronoUnit.MINUTES));
        }

        // Two-factor authentication — issue a pre-auth token if enrolled
        if (twoFactorEnrolled(account.id())) {
            // Trusted-device cookie bypass: the cookie was issued after a previous successful
            // 2FA verification, so we trust it for as long as it's not expired or revoked. The
            // cookie scope is the account behind the token, so validate first then verify it
            // belongs to *this* account before honouring it.
            if (trustedDeviceService != null && trustedDeviceCookie != null && !trustedDeviceCookie.isBlank()) {
                var trusted = trustedDeviceService.validate(trustedDeviceCookie);
                if (trusted.isPresent() && trusted.get().accountId() == account.id()) {
                    log.info(
                            "Login for account {} ({}) bypassing 2FA via trusted device {}",
                            account.id(),
                            email,
                            trusted.get().id());
                    return createSession(
                            account.id(),
                            userAgent,
                            location,
                            Instant.now(),
                            trusted.get().id());
                }
            }
            log.info("Login for account {} ({}) requires 2FA verification", account.id(), email);
            String token = generateToken();
            accountRepository.deleteTokensByAccountAndType(account.id(), TokenType.TWO_FACTOR_PENDING);
            Instant expiresAt = Instant.now().plus(5, ChronoUnit.MINUTES);
            accountRepository.createToken(account.id(), token, TokenType.TWO_FACTOR_PENDING, expiresAt);
            return LoginResult.twoFactorRequired(token, expiresAt);
        }

        return createSession(account.id(), userAgent, location);
    }

    /**
     * Refreshes a session by invalidating the old token and creating a new session.
     *
     * @param token     the current session token
     * @param userAgent the client's user agent string
     * @param location  the client's location
     * @return a new login result with a fresh token, or failure if the session is invalid or expired
     */
    public LoginResult refreshSession(String token, String userAgent, String location) {
        Optional<AccountSession> sessionOpt = accountRepository.findSession(token);
        if (sessionOpt.isEmpty()) {
            log.debug("Session refresh failed: invalid token");
            return LoginResult.failure("Invalid session");
        }

        AccountSession session = sessionOpt.get();
        if (session.isExpired()) {
            accountRepository.deleteSession(token);
            log.info("Session refresh failed for account {}: session expired", session.accountId());
            return LoginResult.failure("Session expired");
        }

        accountRepository.deleteSession(token);
        log.debug("Session refreshed for account {}", session.accountId());
        return createSession(session.accountId(), userAgent, location);
    }

    /**
     * Logs out by invalidating the session token.
     *
     * @param token the session token to invalidate
     * @return {@code true} if the session was found and deleted
     */
    public boolean logout(String token) {
        var session = accountRepository.findSession(token);
        boolean deleted = accountRepository.deleteSession(token);
        session.ifPresent(s -> log.info("Logout for account {}", s.accountId()));
        return deleted;
    }

    /**
     * Retrieves all active sessions for an account.
     *
     * @param accountId the account identifier
     * @return list of active sessions
     */
    public List<AccountSession> findSessionsByAccount(int accountId) {
        return accountRepository.findSessionsByAccount(accountId);
    }

    /**
     * Invalidates all sessions for an account, forcing re-authentication on all devices.
     *
     * @param accountId the account identifier
     * @return {@code true} if any sessions were invalidated
     */
    public boolean invalidateAllSessions(int accountId) {
        log.info("All sessions invalidated for account {}", accountId);
        return accountRepository.deleteSessionsByAccount(accountId);
    }

    /**
     * Changes a user's password after verifying the current password.
     *
     * @param accountId       the account identifier
     * @param currentPassword the current plaintext password for verification
     * @param newPassword     the new plaintext password
     * @return {@code true} if the password was changed successfully
     */
    public boolean changePassword(
            int accountId, String currentSessionToken, String currentPassword, String newPassword) {
        if (validateNewPassword(newPassword) != PasswordPolicy.Result.OK) {
            return false;
        }
        var credOpt = accountRepository.findCredential(accountId);
        if (credOpt.isEmpty()) return false;
        if (!passwordHasher.verify(currentPassword, credOpt.get().passwordHash())) return false;
        accountRepository.updateCredential(accountId, passwordHasher.hash(newPassword));
        invalidateAfterPasswordRotation(accountId, currentSessionToken);
        accountRepository.findById(accountId).ifPresent(this::notifyPasswordChanged);
        log.info("Password changed by account {}", accountId);
        return true;
    }

    // -- Login / Session --

    /**
     * Initiates an email change by sending a confirmation email to the new address.
     * The new email is stored as token metadata and applied upon confirmation.
     *
     * @param accountId the account identifier
     * @param newEmail  the new email address to change to
     */
    public void requestEmailChange(int accountId, String newEmail) {
        var account = accountRepository.findById(accountId).orElse(null);
        if (account == null) return;

        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.EMAIL_CHANGE);
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.EMAIL_CHANGE_RELEASE);
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.EMAIL_CHANGE_CLAIM);

        String requestId = UUID.randomUUID().toString();
        String metadata = requestId + "|" + newEmail;
        Instant expiresAt = Instant.now().plus(authConfig.verifyTokenHours(), ChronoUnit.HOURS);

        String releaseToken = generateToken();
        String claimToken = generateToken();
        accountRepository.createToken(accountId, releaseToken, TokenType.EMAIL_CHANGE_RELEASE, metadata, expiresAt);
        accountRepository.createToken(accountId, claimToken, TokenType.EMAIL_CHANGE_CLAIM, metadata, expiresAt);

        String mailLocale = accountRepository.findMailLocale(accountId);
        emailService.sendEmailChangeReleaseRequest(
                account.email(), account.firstName(), newEmail, releaseToken, mailLocale);
        emailService.sendEmailChangeClaimRequest(
                newEmail, account.firstName(), account.email(), claimToken, mailLocale);
        log.info("Email change requested for account {}: {} -> {}", accountId, account.email(), newEmail);
    }

    /**
     * Two-step email-change confirmation. The change only commits once both the
     * EMAIL_CHANGE_RELEASE token (sent to the old address) and the EMAIL_CHANGE_CLAIM
     * token (sent to the new address) have been clicked; the first click marks
     * the token as awaiting its partner, the second click commits.
     */
    public EmailChangeResult confirmEmailChange(String token) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) return EmailChangeResult.INVALID;
        AccountToken self = tokenOpt.get();
        if (self.isExpired()) {
            accountRepository.deleteToken(token);
            return EmailChangeResult.INVALID;
        }
        if (self.tokenType() != TokenType.EMAIL_CHANGE_RELEASE && self.tokenType() != TokenType.EMAIL_CHANGE_CLAIM) {
            return EmailChangeResult.INVALID;
        }

        String metadata = self.metadata();
        if (metadata == null || metadata.isBlank()) return EmailChangeResult.INVALID;
        int sep = metadata.indexOf('|');
        if (sep <= 0 || sep >= metadata.length() - 1) return EmailChangeResult.INVALID;
        String requestId = metadata.substring(0, sep);
        String newEmail = metadata.substring(sep + 1);

        var partnerOpt = accountRepository.findEmailChangePartner(self.accountId(), requestId, self.id());
        if (partnerOpt.isEmpty() || partnerOpt.get().isExpired()) {
            // First click of the pair, or the other half has already expired.
            accountRepository.markTokenConfirmed(self.id());
            return EmailChangeResult.WAITING;
        }
        AccountToken partner = partnerOpt.get();
        if (partner.confirmedAt() == null) {
            accountRepository.markTokenConfirmed(self.id());
            return EmailChangeResult.WAITING;
        }

        var accountOpt = accountRepository.findById(self.accountId());
        if (accountOpt.isEmpty()) return EmailChangeResult.INVALID;
        Account account = accountOpt.get();

        var existing = accountRepository.findByEmail(newEmail);
        if (existing.isPresent() && existing.get().id() != account.id()) {
            accountRepository.deleteToken(token);
            accountRepository.deleteTokensByAccountAndType(account.id(), TokenType.EMAIL_CHANGE_RELEASE);
            accountRepository.deleteTokensByAccountAndType(account.id(), TokenType.EMAIL_CHANGE_CLAIM);
            return EmailChangeResult.DUPLICATE;
        }

        String oldEmail = account.email();
        accountRepository.updateEmail(account.id(), newEmail);
        invalidateAfterPasswordRotation(account.id(), null);
        String mailLocale = accountRepository.findMailLocale(account.id());
        emailService.sendEmailChangedNotice(oldEmail, account.firstName(), oldEmail, newEmail, mailLocale);
        emailService.sendEmailChangedNotice(newEmail, account.firstName(), oldEmail, newEmail, mailLocale);
        log.info("Email changed for account {}: {} -> {}", account.id(), oldEmail, newEmail);
        return EmailChangeResult.COMMITTED;
    }

    /**
     * Initiates a station deletion by sending a confirmation email to the account owner.
     * The station ID is stored as token metadata.
     *
     * @param accountId the account identifier of the station owner
     * @param stationId the station to be deleted
     */
    public void requestStationDeletion(int accountId, int stationId) {
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.STATION_DELETE);
        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.STATION_DELETE,
                String.valueOf(stationId),
                Instant.now().plus(1, ChronoUnit.HOURS));
        accountRepository
                .findById(accountId)
                .ifPresent(account -> emailService.sendStationDeletionConfirmation(
                        account.email(), account.firstName(), token, accountRepository.findMailLocale(account.id())));
        log.info("Station deletion requested by account {} for station {}", accountId, stationId);
    }

    /**
     * Confirms a station deletion using the provided token. Returns the station ID to be deleted.
     *
     * @param token the station deletion confirmation token
     * @return the station ID to delete, or empty if the token is invalid or expired
     */
    public Optional<Integer> confirmStationDeletion(String token) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) return Optional.empty();
        AccountToken accountToken = tokenOpt.get();
        if (accountToken.isExpired() || accountToken.tokenType() != TokenType.STATION_DELETE) return Optional.empty();
        String stationIdStr = accountToken.metadata();
        if (stationIdStr == null) return Optional.empty();
        int stationId;
        try {
            stationId = Integer.parseInt(stationIdStr);
        } catch (NumberFormatException e) {
            // Token row is corrupt or attacker-crafted; clear it so it stops
            // generating noise on retries and surface the same "invalid /
            // expired token" shape every other token endpoint uses.
            accountRepository.deleteToken(token);
            return Optional.empty();
        }
        accountRepository.deleteToken(token);
        log.info("Station deletion confirmed by account {} for station {}", accountToken.accountId(), stationId);
        return Optional.of(stationId);
    }

    /**
     * Creates a session for the given account, callable from the 2FA verification
     * flow after the user has proven their second factor.
     */
    public LoginResult createSessionForAccount(int accountId, String userAgent, String location) {
        return createSession(accountId, userAgent, location);
    }

    /**
     * Creates a session that is already marked as 2FA-verified and (optionally) linked to a
     * trusted-device row. Used by the {@code /auth/2fa} verify path so the freshly-minted
     * session passes step-up freshness checks without a second prompt.
     */
    public LoginResult createVerifiedSessionForAccount(
            int accountId, String userAgent, String location, Integer deviceTrustId) {
        return createSession(accountId, userAgent, location, Instant.now(), deviceTrustId);
    }

    // -- Email change --

    private boolean twoFactorEnrolled(int accountId) {
        return twoFactorRepository != null && twoFactorRepository.isEnrolled(accountId);
    }

    /**
     * Combined length + HIBP validation applied before persisting any new password.
     * Returns {@link PasswordPolicy.Result#OK} when the password meets the length
     * requirement and is not known to HIBP; otherwise returns the specific reason.
     * HIBP failures are fail-open inside {@link HibpClient}, so a third-party outage
     * does not block legitimate password changes.
     */
    private PasswordPolicy.Result validateNewPassword(String plaintext) {
        // Dev / demo runs skip the minimum-length + breach check so seeded test accounts can
        // rotate to short, well-known passwords like "test". The route still rejects an empty
        // value before reaching this method, so the bypass cannot create a credential with no
        // password at all. Production deployments (demo.enabled() == false && demo.dev() == false)
        // always enforce both checks.
        if (demo.dev() || demo.enabled()) return PasswordPolicy.Result.OK;
        var policy = PasswordPolicy.validate(plaintext);
        if (policy != PasswordPolicy.Result.OK) return policy;
        if (hibpClient.isPwned(plaintext)) return PasswordPolicy.Result.BREACHED;
        return PasswordPolicy.Result.OK;
    }

    /**
     * Kills every other live session and clears every outstanding recovery /
     * verification token for the account. Used after any password rotation so an
     * attacker who already obtained credentials cannot continue with previously
     * minted sessions or alternate recovery tokens.
     *
     * @param accountId        the rotating account
     * @param keepSessionToken the raw bearer of the session that triggered the
     *                         rotation, retained so a self-service password change
     *                         does not log the user out of their own browser;
     *                         {@code null} to kill every session
     */
    private void invalidateAfterPasswordRotation(int accountId, String keepSessionToken) {
        if (keepSessionToken == null || keepSessionToken.isBlank()) {
            accountRepository.deleteSessionsByAccount(accountId);
        } else {
            accountRepository.deleteSessionsExceptToken(accountId, keepSessionToken);
        }
        accountRepository.deleteAllTokens(accountId);
        // A password reset is a security event; a captured "remember this device" cookie is a
        // standing 2FA bypass, so revoke every trusted device alongside sessions and tokens.
        trustedDeviceService.revokeAll(accountId);
    }

    // -- Station deletion --

    private void notifyPasswordChanged(Account account) {
        try {
            emailService.sendPasswordChangedNotice(
                    account.email(), account.firstName(), accountRepository.findMailLocale(account.id()));
        } catch (Exception e) {
            log.warn("Failed to enqueue password-changed notice for account {}", account.id(), e);
        }
    }

    private String dummyPasswordHash() {
        String local = dummyPasswordHash;
        if (local != null) return local;
        synchronized (this) {
            if (dummyPasswordHash == null) {
                byte[] random = new byte[32];
                RANDOM.nextBytes(random);
                dummyPasswordHash = passwordHasher.hash(Base64.getEncoder().encodeToString(random));
            }
            return dummyPasswordHash;
        }
    }

    /**
     * Creates a new session for the given account and returns a successful login result.
     *
     * @param accountId the account identifier
     * @param userAgent the client's user agent string
     * @param location  the client's location
     * @return a successful login result with the session token
     */
    private LoginResult createSession(int accountId, String userAgent, String location) {
        return createSession(accountId, userAgent, location, null, null);
    }

    /**
     * Issues a session, optionally tagging it with a 2FA-verification timestamp and a trusted-device
     * link so step-up freshness checks pass without an immediate prompt.
     */
    private LoginResult createSession(
            int accountId, String userAgent, String location, Instant twoFactorVerifiedAt, Integer deviceTrustId) {
        String token;
        Instant expiresAt;
        if (demo.dev() || demo.enabled()) {
            // In dev/demo mode, use the email as a stable session token so sessions survive restarts
            token = accountRepository.findById(accountId).map(Account::email).orElseGet(this::generateToken);
            expiresAt = Instant.now().plus(365, ChronoUnit.DAYS);
            // Delete any existing session with this token to avoid duplicates
            accountRepository.deleteSession(token);
        } else {
            token = generateToken();
            expiresAt = Instant.now().plus(authConfig.sessionMinutes(), ChronoUnit.MINUTES);
        }
        accountRepository.createSession(
                accountId, token, expiresAt, userAgent, location, twoFactorVerifiedAt, deviceTrustId);
        accountRepository.markSetupCompleted(accountId);
        log.info("Session created for account {}", accountId);
        return LoginResult.success(token, expiresAt);
    }

    /**
     * Generates a cryptographically secure random token encoded as URL-safe Base64.
     *
     * @return the generated token string
     */
    private String generateToken() {
        byte[] bytes = new byte[authConfig.tokenBytes()];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Outcome of a {@link #confirmEmailChange(String)} click. {@link #WAITING} means
     * this side has been confirmed but the partner side still has to click;
     * {@link #COMMITTED} means both halves are in and the email was updated.
     */
    public enum EmailChangeResult {
        /**
         * Token unknown, of the wrong type, or expired.
         */
        INVALID,
        /**
         * This side accepted; the other half still has to confirm.
         */
        WAITING,
        /**
         * Both halves confirmed; the account email has been updated.
         */
        COMMITTED,
        /**
         * Both halves confirmed but the requested email is now taken by another account.
         */
        DUPLICATE
    }
}
