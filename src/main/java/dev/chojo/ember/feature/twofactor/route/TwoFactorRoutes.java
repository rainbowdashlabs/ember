/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.AccountToken;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.entity.TokenType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Singleton
public class TwoFactorRoutes implements Routes {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorRoutes.class);

    private final TwoFactorService twoFactorService;
    private final TwoFactorAuditService auditService;
    private final AccountRepository accountRepository;
    private final AuthService authService;
    private final Auth authConfig;
    private final TokenHasher tokenHasher;
    private final WebAuthnService webAuthnService;
    private final Demo demoConfig;

    @Inject
    public TwoFactorRoutes(
            TwoFactorService twoFactorService,
            TwoFactorAuditService auditService,
            AccountRepository accountRepository,
            AuthService authService,
            Auth authConfig,
            TokenHasher tokenHasher,
            WebAuthnService webAuthnService,
            Demo demoConfig) {
        this.twoFactorService = twoFactorService;
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.authConfig = authConfig;
        this.tokenHasher = tokenHasher;
        this.webAuthnService = webAuthnService;
        this.demoConfig = demoConfig;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/account/2fa/status", this::getStatus, StationPermission.LOGIN);
        // First-time enrollment cannot require step-up because no factor exists yet.
        // Confirm/remove/regenerate are gated because they mutate an already-enrolled factor.
        routes.post(prefix + "/account/2fa/totp/begin", this::beginTotp, StationPermission.LOGIN);
        routes.post(
                prefix + "/account/2fa/totp/confirm",
                this::confirmTotp,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/account/2fa/totp/remove",
                this::removeTotp,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/2fa/backup-codes/regenerate",
                this::regenerateBackupCodes,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(prefix + "/auth/2fa", this::verify2fa);
        routes.post(prefix + "/auth/2fa/stepup", this::stepUp, StationPermission.LOGIN);

        // WebAuthn enrollment — step-up only applies when the account is already enrolled
        // in something else (the middleware exempts unenrolled accounts so the first
        // factor can be added without a chicken-and-egg).
        routes.post(
                prefix + "/account/2fa/webauthn/register/begin",
                this::beginWebAuthnRegistration,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/2fa/webauthn/register/finish",
                this::finishWebAuthnRegistration,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/2fa/factors/{id}/remove",
                this::removeFactor,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(prefix + "/account/2fa/factors/{id}/rename", this::renameFactor, StationPermission.LOGIN);

        // WebAuthn assertion at login — public, gated by the pre-auth token issued at password login.
        routes.post(prefix + "/auth/2fa/webauthn/begin", this::beginWebAuthnLogin);
        routes.post(prefix + "/auth/2fa/webauthn/finish", this::finishWebAuthnLogin);

        // WebAuthn assertion for step-up — authenticated, updates session.two_factor_verified_at.
        routes.post(
                prefix + "/auth/2fa/stepup/webauthn/begin",
                this::beginWebAuthnStepUp,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/auth/2fa/stepup/webauthn/finish",
                this::finishWebAuthnStepUp,
                StationPermission.LOGIN);
    }

    private void getStatus(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var factors = twoFactorService.getActiveFactors(session.accountId());
        int backupCodes = twoFactorService.countUnusedBackupCodes(session.accountId());
        boolean enrolled = twoFactorService.isEnrolled(session.accountId());
        ctx.json(new TwoFactorStatusResponse(
                enrolled,
                factors.stream()
                        .map(f -> new FactorInfo(f.id(), f.kind().name(), f.label(), f.createdAt(), f.lastUsedAt()))
                        .toList(),
                backupCodes,
                !demoConfig.enabled()));
    }

    private void beginTotp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (twoFactorService.isEnrolled(session.accountId())) {
            throw new BadRequestResponse("Already enrolled in 2FA");
        }
        var enrollment = twoFactorService.beginTotpEnrollment(
                session.accountId(), session.account().email());
        ctx.json(new TotpBeginResponse(
                enrollment.secret(),
                enrollment.otpauthUri(),
                Base64.getEncoder().encodeToString(enrollment.qrPng()),
                enrollment.recoveryCodes()));
    }

    private void confirmTotp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(TotpConfirmRequest.class);
        if (request.secret() == null || request.code() == null || request.recoveryCodes() == null) {
            throw new BadRequestResponse("secret, code, and recoveryCodes are required");
        }
        boolean confirmed = twoFactorService.confirmTotpEnrollment(
                session.accountId(),
                request.secret(),
                request.code(),
                request.recoveryCodes(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        if (!confirmed) {
            throw new BadRequestResponse("Invalid verification code");
        }
        ctx.json(new MessageResponse("TOTP enrolled"));
    }

    private void removeTotp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        boolean removed =
                twoFactorService.removeTotpFactor(session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (!removed) {
            throw new BadRequestResponse("No active TOTP factor");
        }
        ctx.json(new MessageResponse("TOTP removed"));
    }

    private void regenerateBackupCodes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (!twoFactorService.isEnrolled(session.accountId())) {
            throw new BadRequestResponse("Not enrolled in 2FA");
        }
        List<String> codes = twoFactorService.regenerateBackupCodes(
                session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new BackupCodesResponse(codes));
    }

    private void verify2fa(Context ctx) {
        var request = ctx.bodyAsClass(Verify2faRequest.class);
        if (request.preAuthToken() == null || request.proof() == null) {
            throw new BadRequestResponse("preAuthToken and proof are required");
        }

        Optional<AccountToken> tokenOpt = accountRepository.findToken(request.preAuthToken());
        if (tokenOpt.isEmpty()) {
            throw new UnauthorizedResponse("Invalid or expired pre-auth token");
        }
        AccountToken preAuth = tokenOpt.get();
        if (preAuth.isExpired() || preAuth.tokenType() != TokenType.TWO_FACTOR_PENDING) {
            accountRepository.deleteToken(request.preAuthToken());
            throw new UnauthorizedResponse("Invalid or expired pre-auth token");
        }

        int accountId = preAuth.accountId();
        boolean verified;

        if ("BACKUP_CODE".equals(request.factor())) {
            var result = twoFactorService.verifyBackupCode(accountId, request.proof(), ctx.ip());
            verified = result.valid();
            if (verified) {
                auditService.record(
                        accountId,
                        null,
                        TwoFactorEvent.BACKUP_CODE_USED,
                        TwoFactorKind.BACKUP_CODES,
                        ctx.userAgent(),
                        ctx.header("CF-IPCountry"));
            }
        } else {
            verified = twoFactorService.verifyTotp(accountId, request.proof());
            if (verified) {
                auditService.record(
                        accountId,
                        null,
                        TwoFactorEvent.LOGIN_VERIFIED,
                        TwoFactorKind.TOTP,
                        ctx.userAgent(),
                        ctx.header("CF-IPCountry"));
            }
        }

        if (!verified) {
            throw new UnauthorizedResponse("Invalid verification code");
        }

        accountRepository.deleteToken(request.preAuthToken());
        LoginResult session =
                authService.createSessionForAccount(accountId, ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new LoginResultResponse(session.token(), session.expiresAt()));
    }

    private void stepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(StepUpRequest.class);
        if (request.factor() == null || request.proof() == null) {
            throw new BadRequestResponse("factor and proof are required");
        }
        if (!twoFactorService.isEnrolled(session.accountId())) {
            throw new BadRequestResponse("Not enrolled in 2FA");
        }

        boolean verified;
        TwoFactorKind kind;
        if ("BACKUP_CODE".equals(request.factor())) {
            kind = TwoFactorKind.BACKUP_CODES;
            verified = twoFactorService
                    .verifyBackupCode(session.accountId(), request.proof(), ctx.ip())
                    .valid();
        } else {
            kind = TwoFactorKind.TOTP;
            verified = twoFactorService.verifyTotp(session.accountId(), request.proof());
        }

        if (!verified) {
            throw new UnauthorizedResponse("Invalid verification code");
        }

        twoFactorService.markSessionTwoFactorVerified(session.sessionId());
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.STEPUP_VERIFIED,
                kind,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new StepUpResponse(Instant.now()));
    }

    private void beginWebAuthnRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var account = session.account();
        String displayName = (account.firstName() + " " + account.lastName()).trim();
        var start = webAuthnService.startRegistration(
                session.accountId(), account.email(), displayName.isBlank() ? account.email() : displayName);
        ctx.json(new WebAuthnBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishWebAuthnRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(WebAuthnRegisterFinishRequest.class);
        if (request.challengeToken() == null || request.credentialJson() == null) {
            throw new BadRequestResponse("challengeToken and credentialJson are required");
        }
        var factor = webAuthnService.finishRegistration(
                session.accountId(),
                request.challengeToken(),
                request.credentialJson(),
                request.label(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        if (factor.isEmpty()) {
            throw new BadRequestResponse("WebAuthn registration failed");
        }
        // First-factor enrollment also seeds backup codes — the client must surface these once.
        List<String> issuedCodes = twoFactorService.issueInitialBackupCodesIfMissing(
                session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        var f = factor.get();
        ctx.json(new WebAuthnRegisterFinishResponse(
                new FactorInfo(f.id(), f.kind().name(), f.label(), f.createdAt(), f.lastUsedAt()),
                issuedCodes));
    }

    private void removeFactor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int factorId = ctx.pathParamAsClass("id", Integer.class).get();
        if (!twoFactorService.removeFactor(
                session.accountId(), factorId, ctx.userAgent(), ctx.header("CF-IPCountry"))) {
            throw new BadRequestResponse("Factor not found");
        }
        ctx.json(new MessageResponse("Factor removed"));
    }

    private void renameFactor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int factorId = ctx.pathParamAsClass("id", Integer.class).get();
        var request = ctx.bodyAsClass(RenameFactorRequest.class);
        if (!twoFactorService.renameFactor(session.accountId(), factorId, request.label())) {
            throw new BadRequestResponse("Invalid label or factor not found");
        }
        ctx.json(new MessageResponse("Factor renamed"));
    }

    private void beginWebAuthnLogin(Context ctx) {
        var request = ctx.bodyAsClass(WebAuthnLoginBeginRequest.class);
        if (request.preAuthToken() == null) {
            throw new BadRequestResponse("preAuthToken is required");
        }
        int accountId = consumeReadOnlyPreAuth(request.preAuthToken());
        var start = webAuthnService.startAssertion(accountId);
        ctx.json(new WebAuthnBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishWebAuthnLogin(Context ctx) {
        var request = ctx.bodyAsClass(WebAuthnLoginFinishRequest.class);
        if (request.preAuthToken() == null || request.challengeToken() == null || request.credentialJson() == null) {
            throw new BadRequestResponse("preAuthToken, challengeToken, and credentialJson are required");
        }
        int accountId = consumeReadOnlyPreAuth(request.preAuthToken());
        if (!webAuthnService.finishAssertion(accountId, request.challengeToken(), request.credentialJson())) {
            throw new UnauthorizedResponse("WebAuthn verification failed");
        }
        // Pre-auth is single-use — consume only after successful verification so users can retry.
        accountRepository.deleteToken(request.preAuthToken());
        auditService.record(
                accountId,
                null,
                TwoFactorEvent.LOGIN_VERIFIED,
                TwoFactorKind.WEBAUTHN,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        LoginResult session =
                authService.createSessionForAccount(accountId, ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new LoginResultResponse(session.token(), session.expiresAt()));
    }

    private void beginWebAuthnStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var start = webAuthnService.startAssertion(session.accountId());
        ctx.json(new WebAuthnBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishWebAuthnStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(WebAuthnStepUpFinishRequest.class);
        if (request.challengeToken() == null || request.credentialJson() == null) {
            throw new BadRequestResponse("challengeToken and credentialJson are required");
        }
        if (!webAuthnService.finishAssertion(
                session.accountId(), request.challengeToken(), request.credentialJson())) {
            throw new UnauthorizedResponse("WebAuthn verification failed");
        }
        twoFactorService.markSessionTwoFactorVerified(session.sessionId());
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.STEPUP_VERIFIED,
                TwoFactorKind.WEBAUTHN,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new StepUpResponse(Instant.now()));
    }

    /**
     * Resolves a TWO_FACTOR_PENDING token to an account id without deleting it — used between
     * begin/finish calls of the WebAuthn login ceremony so a failed assertion can be retried
     * with the same pre-auth token.
     */
    private int consumeReadOnlyPreAuth(String preAuthToken) {
        Optional<AccountToken> tokenOpt = accountRepository.findToken(preAuthToken);
        if (tokenOpt.isEmpty()) {
            throw new UnauthorizedResponse("Invalid or expired pre-auth token");
        }
        AccountToken preAuth = tokenOpt.get();
        if (preAuth.isExpired() || preAuth.tokenType() != TokenType.TWO_FACTOR_PENDING) {
            accountRepository.deleteToken(preAuthToken);
            throw new UnauthorizedResponse("Invalid or expired pre-auth token");
        }
        return preAuth.accountId();
    }

    // -- Request / Response records --

    public record TwoFactorStatusResponse(
            boolean enrolled, List<FactorInfo> factors, int unusedBackupCodes, boolean webauthnAvailable) {}

    public record FactorInfo(int id, String kind, String label, Instant createdAt, Instant lastUsedAt) {}

    public record TotpBeginResponse(String secret, String otpauthUri, String qrPng, List<String> recoveryCodes) {}

    public record TotpConfirmRequest(String secret, String code, List<String> recoveryCodes) {}

    public record BackupCodesResponse(List<String> codes) {}

    public record Verify2faRequest(String preAuthToken, String factor, String proof) {}

    public record StepUpRequest(String factor, String proof) {}

    public record StepUpResponse(Instant verifiedAt) {}

    public record LoginResultResponse(String token, Instant expiresAt) {}

    public record MessageResponse(String message) {}

    public record WebAuthnBeginResponse(String challengeToken, String optionsJson) {}

    public record WebAuthnRegisterFinishRequest(String challengeToken, String credentialJson, String label) {}

    public record WebAuthnRegisterFinishResponse(FactorInfo factor, List<String> recoveryCodes) {}

    public record WebAuthnLoginBeginRequest(String preAuthToken) {}

    public record WebAuthnLoginFinishRequest(String preAuthToken, String challengeToken, String credentialJson) {}

    public record WebAuthnStepUpFinishRequest(String challengeToken, String credentialJson) {}

    public record RenameFactorRequest(String label) {}
}
