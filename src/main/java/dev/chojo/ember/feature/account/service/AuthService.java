/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.entity.AccountSession;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.mail.service.EmailService;
import dev.chojo.ember.feature.members.entity.RegistrationCode;
import dev.chojo.ember.feature.members.repository.MemberGroupRepository;
import dev.chojo.ember.feature.members.repository.RegistrationCodeRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Service handling authentication, registration, session management, password operations,
 * email changes, and station deletion confirmations.
 */
@Singleton
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;
    private final RegistrationCodeRepository registrationCodeRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberGroupRepository memberGroupRepository;
    private final PasswordHasher passwordHasher;
    private final EmailService emailService;
    private final Auth authConfig;
    private final Demo demo;

    @Inject
    public AuthService(
            AccountRepository accountRepository,
            RegistrationCodeRepository registrationCodeRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            PasswordHasher passwordHasher,
            EmailService emailService,
            Auth authConfig,
            Demo demo) {
        this.accountRepository = accountRepository;
        this.registrationCodeRepository = registrationCodeRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.authConfig = authConfig;
        this.demo = demo;
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

        if (accountRepository.findByEmail(email).isPresent()) {
            return RegistrationResult.failure("Email already in use");
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
        emailService.sendVerificationEmail(email, firstName, token);

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

        var account = accountRepository.create(email, firstName, lastName, true);
        int accountId = account.id();

        // Create station membership for the invited user
        stationMemberRepository.create(stationId, accountId);

        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.SET_PASSWORD,
                Instant.now().plus(authConfig.passwordTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordSetupEmail(email, firstName, token);

        return RegistrationResult.success(accountRepository.findById(accountId).orElseThrow());
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
    public boolean setPassword(String token, String password) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        AccountToken accountToken = tokenOpt.get();
        TokenType type = accountToken.tokenType();
        if (accountToken.isExpired()
                || (type != TokenType.SET_PASSWORD
                        && type != TokenType.RESET_PASSWORD
                        && type != TokenType.FORCE_PASSWORD_CHANGE)) {
            accountRepository.deleteToken(token);
            return false;
        }

        String hash = passwordHasher.hash(password);
        Optional<Account> account = accountRepository.findById(accountToken.accountId());
        if (account.isEmpty()) {
            return false;
        }

        if (accountRepository.findCredential(accountToken.accountId()).isPresent()) {
            accountRepository.updateCredential(accountToken.accountId(), hash);
        } else {
            accountRepository.createCredential(accountToken.accountId(), hash);
        }

        accountRepository.deleteToken(token);
        return true;
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
                Instant.now().plus(authConfig.verifyTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordResetEmail(account.email(), account.firstName(), token);
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
            // Set flag so next login prompts password change
            accountRepository.setForcePasswordChange(accountId, true);
        }

        // Send reset email
        String token = generateToken();
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.RESET_PASSWORD);
        accountRepository.createToken(
                accountId,
                token,
                TokenType.RESET_PASSWORD,
                Instant.now().plus(authConfig.passwordTokenHours(), ChronoUnit.HOURS));
        emailService.sendPasswordResetEmail(account.email(), account.firstName(), token);
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
        emailService.sendVerificationEmail(account.email(), account.firstName(), token);
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
        Optional<Account> accountOpt = accountRepository.findByEmail(email);
        if (accountOpt.isEmpty()) {
            return LoginResult.failure("Invalid email or password");
        }

        Account account = accountOpt.get();
        if (!account.emailVerified()) {
            return LoginResult.failure("Email not verified");
        }

        Optional<AccountCredential> credOpt = accountRepository.findCredential(account.id());
        if (credOpt.isEmpty()) {
            return LoginResult.failure("Invalid email or password");
        }

        if (!passwordHasher.verify(password, credOpt.get().passwordHash())) {
            return LoginResult.failure("Invalid email or password");
        }

        if (!accountRepository.hasAccountRole(account.id(), "ADMIN")
                && !stationMemberRepository.hasLoginRole(account.id())) {
            return LoginResult.failure("Account is not authorized to log in");
        }

        // Rehash if algorithm changed
        if (passwordHasher.needsRehash(credOpt.get().passwordHash())) {
            accountRepository.updateCredential(account.id(), passwordHasher.hash(password));
        }

        // Force password change — issue a one-time token instead of a session
        if (credOpt.get().forcePasswordChange()) {
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

        return createSession(account.id(), userAgent, location);
    }

    // -- Login / Session --

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
            return LoginResult.failure("Invalid session");
        }

        AccountSession session = sessionOpt.get();
        if (session.isExpired()) {
            accountRepository.deleteSession(token);
            return LoginResult.failure("Session expired");
        }

        accountRepository.deleteSession(token);
        return createSession(session.accountId(), userAgent, location);
    }

    /**
     * Logs out by invalidating the session token.
     *
     * @param token the session token to invalidate
     * @return {@code true} if the session was found and deleted
     */
    public boolean logout(String token) {
        return accountRepository.deleteSession(token);
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
    public boolean changePassword(int accountId, String currentPassword, String newPassword) {
        var credOpt = accountRepository.findCredential(accountId);
        if (credOpt.isEmpty()) return false;
        if (!passwordHasher.verify(currentPassword, credOpt.get().passwordHash())) return false;
        accountRepository.updateCredential(accountId, passwordHasher.hash(newPassword));
        return true;
    }

    /**
     * Initiates an email change by sending a confirmation email to the new address.
     * The new email is stored as token metadata and applied upon confirmation.
     *
     * @param accountId the account identifier
     * @param newEmail  the new email address to change to
     */
    public void requestEmailChange(int accountId, String newEmail) {
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.EMAIL_CHANGE);
        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.EMAIL_CHANGE,
                newEmail,
                Instant.now().plus(authConfig.verifyTokenHours(), ChronoUnit.HOURS));
        var account = accountRepository.findById(accountId).orElse(null);
        String name = account != null ? account.firstName() : "";
        emailService.sendEmailChangeConfirmation(newEmail, name, token);
    }

    // -- Email change --

    /**
     * Confirms an email change using the provided token. Updates the account's email to the new address
     * stored in the token's metadata.
     *
     * @param token the email change confirmation token
     * @return {@code true} if the email was successfully changed
     */
    public boolean confirmEmailChange(String token) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(token);
        if (tokenOpt.isEmpty()) return false;
        AccountToken accountToken = tokenOpt.get();
        if (accountToken.isExpired() || accountToken.tokenType() != TokenType.EMAIL_CHANGE) return false;
        String newEmail = accountToken.metadata();
        if (newEmail == null || newEmail.isBlank()) return false;
        accountRepository.updateEmail(accountToken.accountId(), newEmail);
        accountRepository.deleteToken(token);
        return true;
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
                .ifPresent(account ->
                        emailService.sendStationDeletionConfirmation(account.email(), account.firstName(), token));
    }

    // -- Station deletion --

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
        accountRepository.deleteToken(token);
        return Optional.of(Integer.parseInt(stationIdStr));
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
        accountRepository.createSession(accountId, token, expiresAt, userAgent, location);
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
     * Result of a registration attempt.
     *
     * @param success whether the registration succeeded
     * @param message error message on failure, {@code null} on success
     * @param account the created account on success, {@code null} on failure
     */
    public record RegistrationResult(boolean success, String message, Account account) {
        /**
         * Creates a failed registration result with an error message.
         */
        public static RegistrationResult failure(String message) {
            return new RegistrationResult(false, message, null);
        }

        /**
         * Creates a successful registration result with the created account.
         */
        public static RegistrationResult success(Account account) {
            return new RegistrationResult(true, null, account);
        }
    }

    /**
     * Result of a login attempt.
     *
     * @param success                whether the login succeeded
     * @param message                error message on failure, {@code null} on success
     * @param token                  the session or password change token on success
     * @param expiresAt              when the token expires
     * @param passwordChangeRequired whether a password change is required before a session can be created
     */
    public record LoginResult(
            boolean success, String message, String token, Instant expiresAt, boolean passwordChangeRequired) {
        /**
         * Creates a failed login result with an error message.
         */
        public static LoginResult failure(String message) {
            return new LoginResult(false, message, null, null, false);
        }

        /**
         * Creates a successful login result with a session token.
         */
        public static LoginResult success(String token, Instant expiresAt) {
            return new LoginResult(true, null, token, expiresAt, false);
        }

        /**
         * Creates a login result indicating a forced password change is required.
         */
        public static LoginResult passwordChangeRequired(String token, Instant expiresAt) {
            return new LoginResult(true, null, token, expiresAt, true);
        }
    }
}
