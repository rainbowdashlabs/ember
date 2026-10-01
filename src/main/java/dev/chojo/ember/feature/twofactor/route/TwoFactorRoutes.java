/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StepUpChallenge;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.TwoFactorRefusal;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TrustedDeviceService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorSignInService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorSignInService.Attempt;
import dev.chojo.ember.feature.twofactor.service.WebAuthnService;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

@Singleton
public class TwoFactorRoutes implements Routes {
    private final TwoFactorService twoFactorService;
    private final TwoFactorAuditService auditService;
    private final TwoFactorSignInService signIn;
    private final AuthService authService;
    private final WebAuthnService webAuthnService;
    private final Demo demoConfig;
    private final TrustedDeviceService trustedDeviceService;
    private final AuthRateLimiter rateLimiter;
    private final SessionCookies sessionCookies;

    @Inject
    public TwoFactorRoutes(
            TwoFactorService twoFactorService,
            TwoFactorAuditService auditService,
            TwoFactorSignInService signIn,
            AuthService authService,
            WebAuthnService webAuthnService,
            Demo demoConfig,
            TrustedDeviceService trustedDeviceService,
            AuthRateLimiter rateLimiter,
            SessionCookies sessionCookies) {
        this.twoFactorService = twoFactorService;
        this.auditService = auditService;
        this.signIn = signIn;
        this.authService = authService;
        this.webAuthnService = webAuthnService;
        this.demoConfig = demoConfig;
        this.trustedDeviceService = trustedDeviceService;
        this.rateLimiter = rateLimiter;
        this.sessionCookies = sessionCookies;
    }

    /**
     * Best-effort "is this the device the caller is currently signed in on?" check. We don't
     * have direct access to {@code account_session.device_trust_id} from {@link UserSession}
     * yet - the next session refresh will surface it. For now we always return false; the UI
     * still shows the list and lets the user revoke each one.
     */
    @SuppressWarnings("unused")
    private static boolean deviceMatchesSession(UserSession session, int deviceId) {
        return false;
    }

    /**
     * Registers the two-factor routes.
     *
     * <p>Enrollment, the first one included, carries the account security step-up category like
     * everything else on the screen: it is the only guard first enrollment has. The WebAuthn sign-in
     * routes are public and gated by the pre-auth token issued at password sign-in.
     */
    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/account/2fa/status", this::getStatus, StationPermission.LOGIN);
        routes.post(
                prefix + "/account/2fa/totp/begin",
                this::beginTotp,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/2fa/totp/confirm",
                this::confirmTotp,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
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

        routes.get(prefix + "/account/2fa/trusted-devices", this::listTrustedDevices, StationPermission.LOGIN);
        routes.post(
                prefix + "/account/2fa/trusted-devices/{id}/revoke",
                this::revokeTrustedDevice,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/2fa/trusted-devices/revoke-all",
                this::revokeAllTrustedDevices,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);

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

        routes.post(prefix + "/auth/2fa/webauthn/begin", this::beginWebAuthnLogin);
        routes.post(prefix + "/auth/2fa/webauthn/finish", this::finishWebAuthnLogin);

        routes.post(prefix + "/auth/2fa/stepup/webauthn/begin", this::beginWebAuthnStepUp, StationPermission.LOGIN);
        routes.post(prefix + "/auth/2fa/stepup/webauthn/finish", this::finishWebAuthnStepUp, StationPermission.LOGIN);
    }

    @OpenApi(
            path = "/api/v1/account/2fa/status",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TwoFactorStatusResponse.class)))
    private void getStatus(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var factors = twoFactorService.getActiveFactors(session.accountId());
        int backupCodes = twoFactorService.countUnusedBackupCodes(session.accountId());
        boolean enrolled = twoFactorService.isEnrolled(session.accountId());
        ctx.json(new TwoFactorStatusResponse(
                enrolled,
                factors.stream()
                        .map(f -> new FactorInfo(f.id(), f.kind(), f.label(), f.createdAt(), f.lastUsedAt()))
                        .toList(),
                backupCodes,
                !demoConfig.enabled(),
                trustedDeviceService.maxDays()));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/totp/begin",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = TotpBeginResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void beginTotp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (twoFactorService.isEnrolled(session.accountId())) {
            throw TwoFactorRefusal.ALREADY_ENROLLED_ON_TOTP_SETUP.raise();
        }
        var enrollment = twoFactorService.beginTotpEnrollment(
                session.accountId(), session.account().email());
        ctx.json(new TotpBeginResponse(
                enrollment.secret(),
                enrollment.otpauthUri(),
                Base64.getEncoder().encodeToString(enrollment.qrPng()),
                enrollment.recoveryCodes()));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/totp/confirm",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TotpConfirmRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void confirmTotp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(TotpConfirmRequest.class);
        if (request.secret() == null || request.code() == null || request.recoveryCodes() == null) {
            throw TwoFactorRefusal.TOTP_CONFIRMATION_DETAILS_MISSING.raise();
        }
        boolean confirmed = twoFactorService.confirmTotpEnrollment(
                session.accountId(),
                request.secret(),
                request.code(),
                request.recoveryCodes(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        if (!confirmed) {
            throw TwoFactorRefusal.TOTP_SETUP_CODE_WRONG.raise();
        }
        ctx.json(new MessageResponse("TOTP enrolled"));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/totp/remove",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void removeTotp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        boolean removed =
                twoFactorService.removeTotpFactor(session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (!removed) {
            throw TwoFactorRefusal.NO_TOTP_TO_REMOVE.raise();
        }
        ctx.json(new MessageResponse("TOTP removed"));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/backup-codes/regenerate",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = BackupCodesResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void regenerateBackupCodes(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (!twoFactorService.isEnrolled(session.accountId())) {
            throw TwoFactorRefusal.NOT_ENROLLED_ON_BACKUP_CODES.raise();
        }
        List<String> codes = twoFactorService.regenerateBackupCodes(
                session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new BackupCodesResponse(codes));
    }

    @OpenApi(
            path = "/api/v1/auth/2fa",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = Verify2faRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResultResponse.class)))
    private void verify2fa(Context ctx) {
        var request = ctx.bodyAsClass(Verify2faRequest.class);
        if (request.preAuthToken() == null || request.proof() == null) {
            throw TwoFactorRefusal.TWO_FACTOR_CHECK_DETAILS_MISSING.raise();
        }

        int accountId = signIn.waitingAccount(request.preAuthToken(), TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_FACTOR);
        RateLimits.enforce(TwoFactorRefusal.TWO_FACTOR_CODE_TOO_OFTEN, rateLimiter.tryTwoFactor(ctx.ip(), accountId));
        signIn.verify(
                accountId,
                request.preAuthToken(),
                new Attempt(request.factor(), request.proof(), ctx.ip(), ctx.userAgent(), ctx.header("CF-IPCountry")));
        Integer deviceTrustId = issueTrustedDeviceIfRequested(ctx, accountId, request.rememberDeviceDays());
        LoginResult session = authService.createVerifiedSessionForAccount(
                accountId, ctx.userAgent(), ctx.header("CF-IPCountry"), deviceTrustId, request.trustedDevice());
        answerSession(ctx, session);
    }

    @OpenApi(
            path = "/api/v1/account/2fa/trusted-devices",
            methods = HttpMethod.GET,
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = TrustedDevicesResponse.class)))
    private void listTrustedDevices(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var devices = trustedDeviceService.list(session.accountId()).stream()
                .map(d -> new TrustedDeviceEntry(
                        d.id(),
                        d.userAgent(),
                        d.createdAt(),
                        d.lastSeenAt(),
                        d.trustedUntil(),
                        session.sessionId() != 0 && deviceMatchesSession(session, d.id())))
                .toList();
        ctx.json(new TrustedDevicesResponse(devices));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/trusted-devices/{id}/revoke",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void revokeTrustedDevice(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        if (!trustedDeviceService.revoke(id, session.accountId())) {
            throw TwoFactorRefusal.TRUSTED_DEVICE_NOT_HERE.raise();
        }
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.TRUSTED_DEVICE_REVOKED,
                null,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new MessageResponse("Trusted device revoked"));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/trusted-devices/revoke-all",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void revokeAllTrustedDevices(Context ctx) {
        UserSession session = UserSession.from(ctx);
        trustedDeviceService.revokeAll(session.accountId());
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.TRUSTED_DEVICE_REVOKED,
                null,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new MessageResponse("All trusted devices revoked"));
    }

    @OpenApi(
            path = "/api/v1/auth/2fa/stepup",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = StepUpRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StepUpResponse.class)))
    private void stepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(StepUpRequest.class);
        if (request.factor() == null || request.proof() == null) {
            throw TwoFactorRefusal.STEP_UP_DETAILS_MISSING.raise();
        }
        if (!twoFactorService.isEnrolled(session.accountId())) {
            throw TwoFactorRefusal.NOT_ENROLLED_ON_STEP_UP.raise();
        }
        RateLimits.enforce(
                TwoFactorRefusal.TWO_FACTOR_STEP_UP_TOO_OFTEN, rateLimiter.tryTwoFactor(ctx.ip(), session.accountId()));

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
            throw TwoFactorRefusal.STEP_UP_CODE_WRONG.raise();
        }

        twoFactorService.markSessionTwoFactorVerified(
                session.sessionId(), kind == TwoFactorKind.BACKUP_CODES ? StepUpProof.BACKUP_CODE : StepUpProof.TOTP);
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.STEPUP_VERIFIED,
                kind,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new StepUpResponse(Instant.now()));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/webauthn/register/begin",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebAuthnBeginResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void beginWebAuthnRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var account = session.account();
        String displayName = NameParts.of(account).official();
        var start = webAuthnService.startRegistration(
                session.accountId(), account.email(), displayName.isBlank() ? account.email() : displayName);
        ctx.json(new WebAuthnBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/webauthn/register/finish",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WebAuthnRegisterFinishRequest.class)),
            responses = {
                @OpenApiResponse(
                        status = "200",
                        content = @OpenApiContent(from = WebAuthnRegisterFinishResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void finishWebAuthnRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(WebAuthnRegisterFinishRequest.class);
        if (request.challengeToken() == null || request.credentialJson() == null) {
            throw TwoFactorRefusal.SECURITY_KEY_SETUP_DETAILS_MISSING.raise();
        }
        var factor = webAuthnService.finishRegistration(
                session.accountId(),
                request.challengeToken(),
                request.credentialJson(),
                request.label(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        if (factor.isEmpty()) {
            throw TwoFactorRefusal.SECURITY_KEY_NOT_REGISTERED.raise();
        }
        List<String> issuedCodes = twoFactorService.issueInitialBackupCodesIfMissing(
                session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        var f = factor.get();
        ctx.json(new WebAuthnRegisterFinishResponse(
                new FactorInfo(f.id(), f.kind(), f.label(), f.createdAt(), f.lastUsedAt()), issuedCodes));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/factors/{id}/remove",
            methods = HttpMethod.POST,
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void removeFactor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int factorId = pathInt(ctx, "id");
        if (!twoFactorService.removeFactor(
                session.accountId(), factorId, ctx.userAgent(), ctx.header("CF-IPCountry"))) {
            throw TwoFactorRefusal.FACTOR_NOT_HERE_ON_REMOVAL.raise();
        }
        ctx.json(new MessageResponse("Factor removed"));
    }

    @OpenApi(
            path = "/api/v1/account/2fa/factors/{id}/rename",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RenameFactorRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void renameFactor(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int factorId = pathInt(ctx, "id");
        var request = ctx.bodyAsClass(RenameFactorRequest.class);
        if (!twoFactorService.renameFactor(session.accountId(), factorId, request.label())) {
            throw TwoFactorRefusal.FACTOR_NOT_RENAMED.raise();
        }
        ctx.json(new MessageResponse("Factor renamed"));
    }

    @OpenApi(
            path = "/api/v1/auth/2fa/webauthn/begin",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WebAuthnLoginBeginRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebAuthnBeginResponse.class)))
    private void beginWebAuthnLogin(Context ctx) {
        var request = ctx.bodyAsClass(WebAuthnLoginBeginRequest.class);
        if (request.preAuthToken() == null) {
            throw TwoFactorRefusal.SECURITY_KEY_SIGN_IN_TOKEN_MISSING.raise();
        }
        int accountId = consumeReadOnlyPreAuth(request.preAuthToken());
        var start = webAuthnService.startAssertion(accountId);
        ctx.json(new WebAuthnBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    @OpenApi(
            path = "/api/v1/auth/2fa/webauthn/finish",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WebAuthnLoginFinishRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResultResponse.class)))
    private void finishWebAuthnLogin(Context ctx) {
        var request = ctx.bodyAsClass(WebAuthnLoginFinishRequest.class);
        if (request.preAuthToken() == null || request.challengeToken() == null || request.credentialJson() == null) {
            throw TwoFactorRefusal.SECURITY_KEY_SIGN_IN_DETAILS_MISSING.raise();
        }
        int accountId = consumeReadOnlyPreAuth(request.preAuthToken());
        RateLimits.enforce(
                TwoFactorRefusal.SECURITY_KEY_SIGN_IN_TOO_OFTEN, rateLimiter.tryTwoFactor(ctx.ip(), accountId));
        if (!webAuthnService.finishAssertion(accountId, request.challengeToken(), request.credentialJson())) {
            throw TwoFactorRefusal.SECURITY_KEY_SIGN_IN_REFUSED.raise();
        }
        signIn.finish(request.preAuthToken());
        auditService.record(
                accountId,
                null,
                TwoFactorEvent.LOGIN_VERIFIED,
                TwoFactorKind.WEBAUTHN,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        Integer deviceTrustId = issueTrustedDeviceIfRequested(ctx, accountId, request.rememberDeviceDays());
        LoginResult session = authService.createVerifiedSessionForAccount(
                accountId, ctx.userAgent(), ctx.header("CF-IPCountry"), deviceTrustId, request.trustedDevice());
        answerSession(ctx, session);
    }

    /** Puts the session the second factor earned into the cookie; the body only says how long it lasts. */
    private void answerSession(Context ctx, LoginResult session) {
        sessionCookies.issue(ctx, session);
        ctx.json(new LoginResultResponse(
                Objects.requireNonNull(session.expiresAt(), "a minted session carries its expiry")));
    }

    /**
     * Mints a trusted-device row when the client requested one. Emits a {@code Set-Cookie}
     * for {@code ember_2fa_trust} with {@code HttpOnly}, {@code SameSite=Strict}, path scoped
     * to the API origin, and {@code Secure} outside dev / demo mode. Returns the new row's id
     * so the caller can attach it to the freshly-minted session.
     */
    private @Nullable Integer issueTrustedDeviceIfRequested(
            Context ctx, int accountId, @Nullable Integer rememberDeviceDays) {
        if (rememberDeviceDays == null || rememberDeviceDays <= 0) return null;
        var issued = trustedDeviceService.issue(accountId, rememberDeviceDays, ctx.userAgent());
        long maxAge =
                Duration.between(Instant.now(), issued.device().trustedUntil()).getSeconds();
        StringBuilder cookie = new StringBuilder()
                .append(TrustedDeviceService.COOKIE_NAME)
                .append('=')
                .append(issued.token())
                .append("; Path=/; HttpOnly; SameSite=Strict; Max-Age=")
                .append(maxAge);
        if (!demoConfig.dev() && !demoConfig.enabled()) cookie.append("; Secure");
        ctx.res().addHeader("Set-Cookie", cookie.toString());
        auditService.record(
                accountId,
                null,
                TwoFactorEvent.TRUSTED_DEVICE_ADDED,
                null,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        return issued.device().id();
    }

    @OpenApi(
            path = "/api/v1/auth/2fa/stepup/webauthn/begin",
            methods = HttpMethod.POST,
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = WebAuthnBeginResponse.class)))
    private void beginWebAuthnStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var start = webAuthnService.startAssertion(session.accountId());
        ctx.json(new WebAuthnBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    @OpenApi(
            path = "/api/v1/auth/2fa/stepup/webauthn/finish",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = WebAuthnStepUpFinishRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StepUpResponse.class)))
    private void finishWebAuthnStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(WebAuthnStepUpFinishRequest.class);
        if (request.challengeToken() == null || request.credentialJson() == null) {
            throw TwoFactorRefusal.SECURITY_KEY_STEP_UP_DETAILS_MISSING.raise();
        }
        RateLimits.enforce(
                TwoFactorRefusal.SECURITY_KEY_STEP_UP_TOO_OFTEN,
                rateLimiter.tryTwoFactor(ctx.ip(), session.accountId()));
        if (!webAuthnService.finishAssertion(session.accountId(), request.challengeToken(), request.credentialJson())) {
            throw TwoFactorRefusal.SECURITY_KEY_STEP_UP_REFUSED.raise();
        }
        twoFactorService.markSessionTwoFactorVerified(session.sessionId(), StepUpProof.SECURITY_KEY);
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
     * Resolves a waiting sign-in to its account without spending it, so a failed security key
     * assertion can be retried with the same pre-auth token.
     */
    private int consumeReadOnlyPreAuth(String preAuthToken) {
        return signIn.waitingAccount(preAuthToken, TwoFactorRefusal.SIGN_IN_NOT_WAITING_ON_A_KEY);
    }

    public record TwoFactorStatusResponse(
            boolean enrolled,
            List<FactorInfo> factors,
            int unusedBackupCodes,
            boolean webauthnAvailable,
            int trustedDeviceMaxDays) {}

    public record FactorInfo(
            int id,
            TwoFactorKind kind,
            String label,
            Instant createdAt,
            @Nullable Instant lastUsedAt) {}

    public record TotpBeginResponse(String secret, String otpauthUri, String qrPng, List<String> recoveryCodes) {}

    /**
     * @param password no longer read: first enrollment answers step-up like everything else.
     *         The field stays so a client still sending it parses.
     */
    public record TotpConfirmRequest(String secret, String code, List<String> recoveryCodes, String password) {}

    public record BackupCodesResponse(List<String> codes) {}

    /**
     * @param trustedDevice the box from the login screen, carried through the second factor so
     *                      somebody who ticked it does not end up with the short session anyway.
     *                      Separate from {@code rememberDeviceDays}, which is about skipping the
     *                      second factor next time.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verify2faRequest(
            String preAuthToken, String factor, String proof, Integer rememberDeviceDays, boolean trustedDevice) {}

    public record StepUpRequest(String factor, String proof) {}

    public record StepUpResponse(Instant verifiedAt) {}

    /**
     * The answer to a finished second factor.
     *
     * <p>The session itself travels in its cookie, never in the body.
     *
     * @param expiresAt when the session ends
     */
    public record LoginResultResponse(Instant expiresAt) {}

    public record WebAuthnBeginResponse(String challengeToken, String optionsJson) {}

    /**
     * @param password no longer read: first enrollment answers step-up like everything else.
     *         The field stays so a client still sending it parses.
     */
    public record WebAuthnRegisterFinishRequest(
            String challengeToken, String credentialJson, String label, String password) {}

    public record WebAuthnRegisterFinishResponse(FactorInfo factor, List<String> recoveryCodes) {}

    public record WebAuthnLoginBeginRequest(String preAuthToken) {}

    public record WebAuthnLoginFinishRequest(
            String preAuthToken,
            String challengeToken,
            String credentialJson,
            Integer rememberDeviceDays,
            boolean trustedDevice) {}

    public record WebAuthnStepUpFinishRequest(String challengeToken, String credentialJson) {}

    public record RenameFactorRequest(String label) {}

    public record TrustedDeviceEntry(
            int id, String userAgent, Instant createdAt, Instant lastSeenAt, Instant trustedUntil, boolean current) {}

    public record TrustedDevicesResponse(List<TrustedDeviceEntry> devices) {}
}
