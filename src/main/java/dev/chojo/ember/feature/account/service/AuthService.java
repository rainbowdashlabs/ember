/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.service;

import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.conf.file.elements.Auth;
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

    @Inject
    public AuthService(
            AccountRepository accountRepository,
            RegistrationCodeRepository registrationCodeRepository,
            StationMemberRepository stationMemberRepository,
            MemberGroupRepository memberGroupRepository,
            PasswordHasher passwordHasher,
            EmailService emailService,
            Auth authConfig) {
        this.accountRepository = accountRepository;
        this.registrationCodeRepository = registrationCodeRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberGroupRepository = memberGroupRepository;
        this.passwordHasher = passwordHasher;
        this.emailService = emailService;
        this.authConfig = authConfig;
    }

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

    public boolean logout(String token) {
        return accountRepository.deleteSession(token);
    }

    public List<AccountSession> findSessionsByAccount(int accountId) {
        return accountRepository.findSessionsByAccount(accountId);
    }

    public boolean invalidateAllSessions(int accountId) {
        return accountRepository.deleteSessionsByAccount(accountId);
    }

    public boolean changePassword(int accountId, String currentPassword, String newPassword) {
        var credOpt = accountRepository.findCredential(accountId);
        if (credOpt.isEmpty()) return false;
        if (!passwordHasher.verify(currentPassword, credOpt.get().passwordHash())) return false;
        accountRepository.updateCredential(accountId, passwordHasher.hash(newPassword));
        return true;
    }

    private LoginResult createSession(int accountId, String userAgent, String location) {
        String token = generateToken();
        Instant expiresAt = Instant.now().plus(authConfig.sessionMinutes(), ChronoUnit.MINUTES);
        accountRepository.createSession(accountId, token, expiresAt, userAgent, location);
        return LoginResult.success(token, expiresAt);
    }

    // -- Email change --

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

    // -- Station deletion --

    public void requestStationDeletion(int accountId, int stationId) {
        accountRepository.deleteTokensByAccountAndType(accountId, TokenType.STATION_DELETE);
        String token = generateToken();
        accountRepository.createToken(
                accountId,
                token,
                TokenType.STATION_DELETE,
                String.valueOf(stationId),
                Instant.now().plus(1, ChronoUnit.HOURS));
        var account = accountRepository.findById(accountId).orElse(null);
        if (account != null) {
            emailService.sendStationDeletionConfirmation(account.email(), account.firstName(), token);
        }
    }

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

    private String generateToken() {
        byte[] bytes = new byte[authConfig.tokenBytes()];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public record RegistrationResult(boolean success, String message, Account account) {
        public static RegistrationResult failure(String message) {
            return new RegistrationResult(false, message, null);
        }

        public static RegistrationResult success(Account account) {
            return new RegistrationResult(true, null, account);
        }
    }

    public record LoginResult(
            boolean success, String message, String token, Instant expiresAt, boolean passwordChangeRequired) {
        public static LoginResult failure(String message) {
            return new LoginResult(false, message, null, null, false);
        }

        public static LoginResult success(String token, Instant expiresAt) {
            return new LoginResult(true, null, token, expiresAt, false);
        }

        public static LoginResult passwordChangeRequired(String token, Instant expiresAt) {
            return new LoginResult(true, null, token, expiresAt, true);
        }
    }
}
