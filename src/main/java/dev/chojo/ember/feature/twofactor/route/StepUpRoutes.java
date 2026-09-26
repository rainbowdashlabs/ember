/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.route;

import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import dev.chojo.ember.feature.devicerequest.service.DeviceRequestService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.Context;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
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

        routes.post(prefix + "/auth/stepup/device/begin", this::beginDeviceStepUp, StationPermission.LOGIN);
        routes.post(prefix + "/auth/stepup/device/poll", this::pollDeviceStepUp, StationPermission.LOGIN);
    }

    /**
     * Raises the request and shows its code.
     *
     * <p>The category travels with it so the approving device can say what it is confirming rather
     * than asking somebody to trust a blank, and nothing else does: whoever raises this may already
     * hold a stolen cookie, so every word on the approval screen is the product's own.
     */
    private void beginDeviceStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        RateLimits.enforce(rateLimiter.tryStepUpDeviceRequest(clientIp(ctx), session.accountId()));
        if (!twoFactorService
                .availableProofs(session.accountId(), session.sessionId())
                .contains(StepUpProof.ANOTHER_DEVICE)) {
            throw Refusal.NO_OTHER_DEVICE_TO_CONFIRM.raise();
        }
        var request = ctx.bodyAsClass(DeviceStepUpBeginRequest.class);
        var created = deviceRequestService.createStepUpRequest(
                session.accountId(),
                session.sessionId(),
                parseCategory(request.category()),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new DeviceStepUpBeginResponse(
                created.code(), created.pollSecret(), created.matchNumber(), created.expiresAt()));
    }

    /**
     * The asking device waiting for the other one. A confirmed request stamps this session, and the
     * caller then retries whatever it was refused for.
     */
    private void pollDeviceStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(DeviceStepUpPollRequest.class);
        if (request.pollSecret() == null || request.pollSecret().isBlank()) {
            throw Refusal.DEVICE_STEP_UP_POLL_SECRET_MISSING.raise();
        }
        RateLimits.enforce(rateLimiter.tryDevicePoll(clientIp(ctx), request.pollSecret()));
        var result = deviceRequestService.poll(request.pollSecret(), Set.of(DeviceRequestPurpose.STEP_UP));
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

    /**
     * The category is what the approver is shown and what their answer buys, so a blank one is a bad
     * request rather than a guess: defaulting it would let a caller who named nothing be handed the
     * gravest category the product has.
     */
    private static StepUpCategory parseCategory(String raw) {
        if (raw == null || raw.isBlank()) {
            throw Refusal.STEP_UP_CATEGORY_MISSING.raise();
        }
        try {
            return StepUpCategory.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw Refusal.STEP_UP_CATEGORY_UNKNOWN.raise();
        }
    }

    private String clientIp(Context ctx) {
        return ClientIp.resolve(ctx, network).getHostAddress();
    }

    /**
     * The password proof. A password oracle reachable with any live session, so it is throttled
     * per account and per client address before anything else happens, and every failure is
     * written down: a log with only the successes in it says nothing about the endpoint an
     * attacker would grind.
     */
    private void passwordStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        RateLimits.enforce(rateLimiter.tryPasswordStepUp(clientIp(ctx), session.accountId()));

        var request = ctx.bodyAsClass(PasswordStepUpRequest.class);
        if (request.password() == null || request.password().isBlank()) {
            throw Refusal.STEP_UP_PASSWORD_MISSING.raise();
        }

        Set<StepUpProof> proofs = twoFactorService.availableProofs(session.accountId());
        if (!proofs.contains(StepUpProof.PASSWORD)) {
            throw Refusal.PASSWORD_IS_NOT_A_PROOF_HERE.raise();
        }

        if (!authService.verifyPassword(session.accountId(), request.password())) {
            auditService.record(
                    session.accountId(),
                    null,
                    TwoFactorEvent.STEPUP_FAILED,
                    null,
                    ctx.userAgent(),
                    ctx.header("CF-IPCountry"));
            throw Refusal.STEP_UP_PASSWORD_WRONG.raise();
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
            throw Refusal.NO_PASSKEY_TO_CONFIRM_WITH.raise();
        }
        var start = passkeyService.startStepUp(session.accountId());
        ctx.json(new PasskeyStepUpBeginResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishPasskeyStepUp(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(PasskeyStepUpFinishRequest.class);
        if (request.challengeToken() == null || request.credentialJson() == null) {
            throw Refusal.PASSKEY_STEP_UP_DETAILS_MISSING.raise();
        }
        RateLimits.enforce(rateLimiter.tryTwoFactor(clientIp(ctx), session.accountId()));
        if (!passkeyService.finishStepUp(session.accountId(), request.challengeToken(), request.credentialJson())) {
            auditService.record(
                    session.accountId(),
                    null,
                    TwoFactorEvent.STEPUP_FAILED,
                    TwoFactorKind.WEBAUTHN,
                    ctx.userAgent(),
                    ctx.header("CF-IPCountry"));
            throw Refusal.PASSKEY_STEP_UP_REFUSED.raise();
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
     * @param category what the step-up was demanded for. The only thing the approval screen shows and
     *         the only thing a confirmation answers, so it is never text the caller composed
     */
    public record DeviceStepUpBeginRequest(String category) {}

    /**
     * @param matchNumber the number this screen shows and the confirming screen asks for, which the
     *         QR does not carry
     */
    public record DeviceStepUpBeginResponse(String code, String pollSecret, int matchNumber, Instant expiresAt) {}

    public record DeviceStepUpPollRequest(String pollSecret) {}

    public record DeviceStepUpPollResponse(String status) {}
}
