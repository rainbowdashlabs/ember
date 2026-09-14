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
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.devicerequest.service.DeviceRequestService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The two proofs D8 added beside the existing second-factor step-up: the password, for the
 * accounts that have nothing else, and the passkey, on its own relying-party view with user
 * verification required. The existing WebAuthn step-up cannot serve the passkey: its allow list
 * excludes passkeys by construction and it asks for user verification only as a preference.
 */
@Singleton
public class StepUpRoutes implements Routes {
    private final TwoFactorService twoFactorService;
    private final PasskeyService passkeyService;
    private final AuthService authService;
    private final TwoFactorAuditService auditService;
    private final AuthRateLimiter rateLimiter;
    private final DeviceRequestService deviceRequestService;
    private final Network network;

    @Inject
    public StepUpRoutes(
            TwoFactorService twoFactorService,
            PasskeyService passkeyService,
            AuthService authService,
            TwoFactorAuditService auditService,
            AuthRateLimiter rateLimiter,
            DeviceRequestService deviceRequestService,
            Network network) {
        this.twoFactorService = twoFactorService;
        this.passkeyService = passkeyService;
        this.authService = authService;
        this.auditService = auditService;
        this.rateLimiter = rateLimiter;
        this.deviceRequestService = deviceRequestService;
        this.network = network;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/auth/stepup/password", this::passwordStepUp, StationPermission.LOGIN);
        routes.post(prefix + "/auth/stepup/passkey/begin", this::beginPasskeyStepUp, StationPermission.LOGIN);
        routes.post(prefix + "/auth/stepup/passkey/finish", this::finishPasskeyStepUp, StationPermission.LOGIN);

        // Confirming on a device the reader is already signed in on. The answer for somebody with
        // no password and no passkey on the machine in front of them.
        routes.post(prefix + "/auth/stepup/device/begin", this::beginDeviceStepUp, StationPermission.LOGIN);
        routes.post(prefix + "/auth/stepup/device/poll", this::pollDeviceStepUp, StationPermission.LOGIN);
    }

    /**
     * Raises the request and shows its code. The category travels with it so the approving device can
     * say what it is confirming rather than asking somebody to trust a blank.
     */
    private void beginDeviceStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (!twoFactorService
                .availableProofs(session.accountId(), session.sessionId())
                .contains(StepUpProof.ANOTHER_DEVICE)) {
            throw new ForbiddenResponse("No other device of this account could confirm");
        }
        var request = ctx.bodyAsClass(DeviceStepUpBeginRequest.class);
        StepUpCategory category = parseCategory(request.category());
        var created = deviceRequestService.createStepUpRequest(
                session.accountId(),
                session.sessionId(),
                category,
                request.operation(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new DeviceStepUpBeginResponse(created.code(), created.pollSecret(), created.expiresAt()));
    }

    /**
     * The asking device waiting for the other one. A confirmed request stamps this session, and the
     * caller then retries whatever it was refused for.
     */
    private void pollDeviceStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(DeviceStepUpPollRequest.class);
        if (request.pollSecret() == null || request.pollSecret().isBlank()) {
            throw new BadRequestResponse("pollSecret is required");
        }
        var result = deviceRequestService.poll(request.pollSecret());
        if (result.claimToken() != null && deviceRequestService.claimStepUp(result.claimToken())) {
            auditService.record(
                    session.accountId(),
                    null,
                    TwoFactorEvent.STEPUP_VERIFIED,
                    null,
                    ctx.userAgent(),
                    ctx.header("CF-IPCountry"));
            ctx.json(new DeviceStepUpPollResponse("CONFIRMED"));
            return;
        }
        ctx.json(new DeviceStepUpPollResponse(result.status().name()));
    }

    private static StepUpCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) return StepUpCategory.ACCOUNT_SECURITY;
        try {
            return StepUpCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown step-up category");
        }
    }

    private String clientIp(Context ctx) {
        return ClientIp.resolve(ctx, network).getHostAddress();
    }

    private static void enforceLimit(Optional<Long> retryAfter) {
        if (retryAfter.isEmpty()) return;
        throw new HttpResponseException(
                HttpStatus.TOO_MANY_REQUESTS.getCode(),
                "Too many requests, please try again later",
                Map.of("Retry-After", Long.toString(retryAfter.get())));
    }

    /**
     * The password proof. A password oracle reachable with any live session, so it is throttled
     * per account and per client address before anything else happens, and every failure is
     * written down: a log with only the successes in it says nothing about the endpoint an
     * attacker would grind.
     */
    private void passwordStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        enforceLimit(rateLimiter.tryPasswordStepUp(clientIp(ctx), session.accountId()));

        var request = ctx.bodyAsClass(PasswordStepUpRequest.class);
        if (request.password() == null || request.password().isBlank()) {
            throw new BadRequestResponse("password is required");
        }

        Set<StepUpProof> proofs = twoFactorService.availableProofs(session.accountId());
        if (!proofs.contains(StepUpProof.PASSWORD)) {
            // An account with a second factor is asked for the factor; accepting the password
            // there would hand somebody holding a phished password a way past it.
            throw new ForbiddenResponse("A password is not a proof for this account");
        }

        if (!authService.verifyPassword(session.accountId(), request.password())) {
            auditService.record(
                    session.accountId(),
                    null,
                    TwoFactorEvent.STEPUP_FAILED,
                    null,
                    ctx.userAgent(),
                    ctx.header("CF-IPCountry"));
            throw new UnauthorizedResponse("Password verification failed");
        }

        twoFactorService.markSessionTwoFactorVerified(session.sessionId(), StepUpProof.PASSWORD);
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.STEPUP_VERIFIED,
                null,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new StepUpVerifiedResponse(Instant.now()));
    }

    private void beginPasskeyStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (!twoFactorService.availableProofs(session.accountId()).contains(StepUpProof.PASSKEY)) {
            throw new ForbiddenResponse("This account holds no passkey");
        }
        var start = passkeyService.startStepUp(session.accountId());
        ctx.json(new PasskeyStepUpBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishPasskeyStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(PasskeyStepUpFinishRequest.class);
        if (request.challengeToken() == null || request.credentialJson() == null) {
            throw new BadRequestResponse("challengeToken and credentialJson are required");
        }
        enforceLimit(rateLimiter.tryTwoFactor(clientIp(ctx), session.accountId()));
        if (!passkeyService.finishStepUp(session.accountId(), request.challengeToken(), request.credentialJson())) {
            auditService.record(
                    session.accountId(),
                    null,
                    TwoFactorEvent.STEPUP_FAILED,
                    TwoFactorKind.WEBAUTHN,
                    ctx.userAgent(),
                    ctx.header("CF-IPCountry"));
            throw new UnauthorizedResponse("Passkey verification failed");
        }
        twoFactorService.markSessionTwoFactorVerified(session.sessionId(), StepUpProof.PASSKEY);
        auditService.record(
                session.accountId(),
                null,
                TwoFactorEvent.STEPUP_VERIFIED,
                TwoFactorKind.WEBAUTHN,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new StepUpVerifiedResponse(Instant.now()));
    }

    public record PasswordStepUpRequest(String password) {}

    public record PasskeyStepUpBeginResponse(String challengeToken, String optionsJson) {}

    public record PasskeyStepUpFinishRequest(String challengeToken, String credentialJson) {}

    public record StepUpVerifiedResponse(Instant verifiedAt) {}

    /**
     * @param category what the step-up was demanded for
     * @param operation the action in a sentence, where the caller knows one. Shown to whoever confirms
     */
    public record DeviceStepUpBeginRequest(String category, String operation) {}

    public record DeviceStepUpBeginResponse(String code, String pollSecret, Instant expiresAt) {}

    public record DeviceStepUpPollRequest(String pollSecret) {}

    public record DeviceStepUpPollResponse(String status) {}
}
