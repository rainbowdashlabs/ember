/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.route.AuthRoutes.LoginResponse;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.passkey.service.PasskeyAccountService;
import dev.chojo.ember.feature.passkey.service.PasskeyDeviceService;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * The passkey endpoints: the passwordless sign-in under {@code /auth}, and the member's own
 * management under {@code /account} beside the existing security routes. Creating, removing and
 * renaming a passkey and both switches carry {@code ACCOUNT_SECURITY}: those three actions are
 * what D8 was written for, and leaving them open would harden every old route around the hole.
 */
@Singleton
public class PasskeyRoutes implements Routes {
    private final PasskeyService passkeyService;
    private final PasskeyAccountService accountService;
    private final PasskeyModeService modeService;
    private final AuthService authService;
    private final AccountRepository accountRepository;
    private final AuthRateLimiter rateLimiter;
    private final RelyingParties relyingParties;
    private final PasskeyDeviceService deviceService;
    private final PasskeyEnrollmentService enrollmentService;
    private final TotpService totpService;
    private final Api api;
    private final Network network;

    @Inject
    public PasskeyRoutes(
            PasskeyService passkeyService,
            PasskeyAccountService accountService,
            PasskeyModeService modeService,
            AuthService authService,
            AccountRepository accountRepository,
            AuthRateLimiter rateLimiter,
            RelyingParties relyingParties,
            PasskeyDeviceService deviceService,
            PasskeyEnrollmentService enrollmentService,
            TotpService totpService,
            Api api,
            Network network) {
        this.passkeyService = passkeyService;
        this.accountService = accountService;
        this.modeService = modeService;
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.rateLimiter = rateLimiter;
        this.relyingParties = relyingParties;
        this.deviceService = deviceService;
        this.enrollmentService = enrollmentService;
        this.totpService = totpService;
        this.api = api;
        this.network = network;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Which mode the instance is in, for the login screen. It reveals only that, which is
        // not sensitive: the login screen shows or hides the passkey path with it.
        routes.get(prefix + "/public/settings/passkeys", this::publicMode);

        // The passwordless sign-in - unauthenticated, throttled by IP on begin and finish both.
        routes.post(prefix + "/auth/passkey/begin", this::beginSignIn);
        routes.post(prefix + "/auth/passkey/finish", this::finishSignIn);

        routes.get(prefix + "/account/passkeys", this::status, StationPermission.LOGIN);
        routes.post(
                prefix + "/account/passkeys/begin",
                this::beginCreation,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/passkeys/finish",
                this::finishCreation,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/passkeys/{id}/rename",
                this::rename,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.delete(
                prefix + "/account/passkeys/{id}",
                this::remove,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/passkeys/password-login",
                this::setPasswordLogin,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/account/passkeys/second-factor",
                this::setAskWithPassword,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);

        // The offer and its answer are a preference, not a security operation.
        routes.get(prefix + "/account/passkeys/offer", this::offerState, StationPermission.LOGIN);
        routes.post(prefix + "/account/passkeys/offer-answer", this::answerOffer, StationPermission.LOGIN);

        // The trial: authenticated, its own challenge kind, mints nothing.
        routes.post(prefix + "/account/passkeys/trial/begin", this::beginTrial, StationPermission.LOGIN);
        routes.post(prefix + "/account/passkeys/trial/finish", this::finishTrial, StationPermission.LOGIN);

        // The device handshake: the new device asks (unauthenticated), a signed-in device
        // approves, and the enrolment token the poll returns may create exactly one credential.
        routes.post(prefix + "/auth/passkey/device-request", this::createDeviceRequest);
        routes.post(prefix + "/auth/passkey/device-request/poll", this::pollDeviceRequest);
        routes.post(prefix + "/auth/passkey/enroll/begin", this::beginDeviceEnrollment);
        routes.post(prefix + "/auth/passkey/enroll/finish", this::finishDeviceEnrollment);

        // The token doors: a mail link, a QR in the room or a console line carries a bearer
        // that may create one passkey. The lookup names whose account it is before the device
        // asks for a fingerprint.
        routes.post(prefix + "/auth/passkey/token-enroll/lookup", this::lookupTokenEnrollment);
        routes.post(prefix + "/auth/passkey/token-enroll/begin", this::beginTokenEnrollment);
        routes.post(prefix + "/auth/passkey/token-enroll/finish", this::finishTokenEnrollment);
        routes.post(prefix + "/account/passkeys/device-lookup", this::lookupDeviceRequest, StationPermission.LOGIN);
        routes.post(
                prefix + "/account/passkeys/device-approve",
                this::approveDeviceRequest,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
    }

    // -- The device handshake --

    private void createDeviceRequest(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryDeviceRequest(clientIp(ctx)));
        var request = deviceService.createRequest(ctx.userAgent(), ctx.header("CF-IPCountry"));
        // The QR opens the approval screen and nothing more. A link that arrives with the code
        // already filled in would be exactly the relayable artifact this flow is shaped to avoid.
        String approvalUrl = api.baseUrl() + "/account/unlock-device";
        String qrPng = Base64.getEncoder().encodeToString(totpService.generateQrPng(approvalUrl, 240));
        ctx.json(new DeviceRequestResponse(request.code(), request.pollSecret(), request.expiresAt(), qrPng));
    }

    private void pollDeviceRequest(Context ctx) {
        enforceLimit(rateLimiter.tryDevicePoll(clientIp(ctx)));
        var request = ctx.bodyAsClass(DevicePollRequest.class);
        if (isBlank(request.pollSecret())) {
            throw new BadRequestResponse("pollSecret is required");
        }
        var result = deviceService.poll(request.pollSecret());
        ctx.json(new DevicePollResponse(result.status().name(), result.enrollToken()));
    }

    private void beginDeviceEnrollment(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(DeviceEnrollBeginRequest.class);
        if (isBlank(request.enrollToken())) {
            throw new BadRequestResponse("enrollToken is required");
        }
        var start = deviceService
                .beginEnrollment(request.enrollToken())
                .orElseThrow(() -> new UnauthorizedResponse("Enrolment failed"));
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishDeviceEnrollment(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(DeviceEnrollFinishRequest.class);
        if (isBlank(request.enrollToken()) || isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw new BadRequestResponse("enrollToken, challengeToken and credentialJson are required");
        }
        boolean created = deviceService.finishEnrollment(
                request.enrollToken(), request.challengeToken(), request.credentialJson(), ctx.header("CF-IPCountry"));
        if (!created) {
            throw new UnauthorizedResponse("Enrolment failed");
        }
        ctx.json(Map.of("message", "Passkey created"));
    }

    // -- The token doors --

    private void lookupTokenEnrollment(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenEnrollRequest.class);
        if (isBlank(request.token())) {
            throw new BadRequestResponse("token is required");
        }
        var account = enrollmentService.lookup(request.token()).orElseThrow(NotFoundResponse::new);
        ctx.json(new TokenEnrollLookupResponse(account.firstName(), account.lastName()));
    }

    private void beginTokenEnrollment(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenEnrollRequest.class);
        if (isBlank(request.token())) {
            throw new BadRequestResponse("token is required");
        }
        var start = enrollmentService
                .begin(request.token())
                .orElseThrow(() -> new UnauthorizedResponse("Enrolment failed"));
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishTokenEnrollment(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenEnrollFinishRequest.class);
        if (isBlank(request.token()) || isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw new BadRequestResponse("token, challengeToken and credentialJson are required");
        }
        if (!enrollmentService.finish(
                request.token(), request.challengeToken(), request.credentialJson(), ctx.header("CF-IPCountry"))) {
            throw new UnauthorizedResponse("Enrolment failed");
        }
        ctx.json(Map.of("message", "Passkey created"));
    }

    private void lookupDeviceRequest(Context ctx) {
        UserSession session = UserSession.from(ctx);
        enforceLimit(rateLimiter.tryDeviceCodeEntry(session.sessionId(), session.accountId()));
        var request = ctx.bodyAsClass(DeviceCodeRequest.class);
        if (isBlank(request.code())) {
            throw new BadRequestResponse("code is required");
        }
        var open = deviceService.lookup(request.code()).orElseThrow(NotFoundResponse::new);
        ctx.json(new DeviceLookupResponse(open.requestedUserAgent(), open.requestedCountry(), open.createdAt()));
    }

    private void approveDeviceRequest(Context ctx) {
        UserSession session = UserSession.from(ctx);
        enforceLimit(rateLimiter.tryDeviceCodeEntry(session.sessionId(), session.accountId()));
        var request = ctx.bodyAsClass(DeviceCodeRequest.class);
        if (isBlank(request.code())) {
            throw new BadRequestResponse("code is required");
        }
        if (!deviceService.approve(session.accountId(), request.code())) {
            throw new NotFoundResponse();
        }
        ctx.json(Map.of("message", "Device approved"));
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

    private void requirePasskeysOn() {
        if (modeService.effectiveMode() == PasskeySettings.Mode.OFF) {
            throw new ForbiddenResponse("Passkeys are not available on this instance");
        }
    }

    private void publicMode(Context ctx) {
        ctx.json(new PublicModeResponse(modeService.effectiveMode().name()));
    }

    // -- Sign-in --

    @OpenApi(
            path = "/api/v1/auth/passkey/begin",
            methods = HttpMethod.POST,
            summary = "Begin a passwordless sign-in",
            description = "Starts a WebAuthn assertion with no account named, so the browser offers whatever"
                    + " passkeys it holds for this instance. Returns the ceremony options and a challenge token.",
            tags = {"Auth"},
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = CeremonyResponse.class)),
                @OpenApiResponse(status = "403"),
                @OpenApiResponse(status = "429")
            })
    private void beginSignIn(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryPasskeySignIn(clientIp(ctx)));
        var start = passkeyService.startSignIn();
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    @OpenApi(
            path = "/api/v1/auth/passkey/finish",
            methods = HttpMethod.POST,
            summary = "Finish a passwordless sign-in",
            description = "Verifies the assertion, identifies the account from it and mints a session."
                    + " The refusal is deliberately the same for an unknown credential, a bad signature and a"
                    + " credential that may not sign in.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SignInFinishRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "401"),
                @OpenApiResponse(status = "429")
            })
    private void finishSignIn(Context ctx) {
        requirePasskeysOn();
        enforceLimit(rateLimiter.tryPasskeySignIn(clientIp(ctx)));
        var request = ctx.bodyAsClass(SignInFinishRequest.class);
        if (isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw new BadRequestResponse("challengeToken and credentialJson are required");
        }

        Optional<Integer> accountId = passkeyService.finishSignIn(
                request.challengeToken(), request.credentialJson(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (accountId.isEmpty()) {
            throw new UnauthorizedResponse("Sign-in failed");
        }

        var result = authService.admitPasskeyAccount(
                accountId.get(), ctx.userAgent(), ctx.header("CF-IPCountry"), request.trustedDevice());
        if (!result.success()) {
            throw new UnauthorizedResponse(result.message());
        }
        if (result.passwordChangeRequired()) {
            ctx.json(new LoginResponse(null, null, true, result.token(), result.expiresAt(), false, null, null));
            return;
        }
        ctx.json(new LoginResponse(result.token(), result.expiresAt(), false, null, null, false, null, null));
    }

    // -- The member's list and switches --

    private void status(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var entries = accountService.list(session.accountId());
        Optional<AccountCredential> credential = accountRepository.findCredential(session.accountId());
        var b64 = Base64.getUrlEncoder().withoutPadding();
        ctx.json(new PasskeysStatusResponse(
                entries.stream()
                        .map(e -> new PasskeyEntryResponse(
                                e.factorId(),
                                e.label(),
                                e.createdAt(),
                                e.lastUsedAt(),
                                e.aaguid() == null ? null : e.aaguid().toString(),
                                e.tried(),
                                b64.encodeToString(e.credentialId())))
                        .toList(),
                credential.isPresent(),
                credential.map(AccountCredential::passwordLoginEnabled).orElse(false),
                entries.stream().anyMatch(e -> e.secondFactor()),
                accountService.mayDisablePasswordLogin(session.accountId()),
                modeService.effectiveMode().name(),
                relyingParties.passkey().getIdentity().getId(),
                entries.isEmpty() ? null : b64.encodeToString(entries.getFirst().userHandle())));
    }

    private void beginCreation(Context ctx) {
        requirePasskeysOn();
        UserSession session = UserSession.from(ctx);
        var account = accountRepository.findById(session.accountId()).orElseThrow(NotFoundResponse::new);
        String displayName = (account.firstName() + " " + account.lastName()).trim();
        var start = passkeyService.startCreation(
                session.accountId(), account.email(), displayName.isBlank() ? account.email() : displayName);
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishCreation(Context ctx) {
        requirePasskeysOn();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreationFinishRequest.class);
        if (isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw new BadRequestResponse("challengeToken and credentialJson are required");
        }
        var factor = passkeyService.finishCreation(
                session.accountId(),
                request.challengeToken(),
                request.credentialJson(),
                request.label(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        if (factor.isEmpty()) {
            throw new BadRequestResponse("Passkey creation failed");
        }
        ctx.status(HttpStatus.CREATED)
                .json(new PasskeyEntryResponse(
                        factor.get().id(), factor.get().label(), factor.get().createdAt(), null, null, false, null));
    }

    private void rename(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(RenameRequest.class);
        if (!accountService.rename(session.accountId(), pathInt(ctx, "id"), request.label())) {
            throw new NotFoundResponse();
        }
        ctx.json(Map.of("message", "Passkey renamed"));
    }

    private void remove(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var outcome = accountService.remove(
                session.accountId(), pathInt(ctx, "id"), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (outcome) {
            case NOT_FOUND -> throw new NotFoundResponse();
            case REFUSED_NO_PASSWORD ->
                throw new HttpResponseException(
                        HttpStatus.CONFLICT.getCode(),
                        "This is the only way into the account. Be onboarded again to get a new passkey first.",
                        Map.of());
            case REMOVED -> ctx.json(new RemovalResponse(false));
            case REMOVED_PASSWORD_REENABLED -> ctx.json(new RemovalResponse(true));
        }
    }

    private void setPasswordLogin(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SwitchRequest.class);
        var outcome = accountService.setPasswordLogin(
                session.accountId(), request.enabled(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (outcome) {
            case OK -> ctx.json(Map.of("message", "Password sign-in updated"));
            case MODE_FORBIDS ->
                throw new ForbiddenResponse("This instance does not allow switching password sign-in off");
            case NO_REACHABLE_ADDRESS ->
                throw conflict("Switching password sign-in off needs an address a reset mail can reach");
            case NO_TRIED_PASSKEY ->
                throw conflict("Switching password sign-in off needs a passkey that has completed a sign-in");
            case NO_PASSWORD -> throw conflict("This account holds no password");
        }
    }

    private void setAskWithPassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SwitchRequest.class);
        accountService.setAskWithPassword(session.accountId(), request.enabled());
        ctx.json(Map.of("message", "Updated"));
    }

    private static HttpResponseException conflict(String message) {
        return new HttpResponseException(HttpStatus.CONFLICT.getCode(), message, Map.of());
    }

    // -- The offer --

    private void offerState(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(new OfferResponse(accountService.shouldOffer(session.accountId())));
    }

    private void answerOffer(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(OfferAnswerRequest.class);
        boolean declined =
                switch (request.answer() == null ? "" : request.answer()) {
                    case "DECLINED" -> true;
                    case "LATER" -> false;
                    default -> throw new BadRequestResponse("answer must be LATER or DECLINED");
                };
        accountService.answerOffer(session.accountId(), declined);
        ctx.json(Map.of("message", "Answer recorded"));
    }

    // -- The trial --

    private void beginTrial(Context ctx) {
        requirePasskeysOn();
        UserSession session = UserSession.from(ctx);
        var start = passkeyService.startTrial(session.accountId());
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishTrial(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SignInFinishRequest.class);
        if (isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw new BadRequestResponse("challengeToken and credentialJson are required");
        }
        var outcome =
                passkeyService.finishTrial(session.accountId(), request.challengeToken(), request.credentialJson());
        ctx.json(new TrialResponse(outcome.name()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record CeremonyResponse(String challengeToken, String optionsJson) {}

    public record PublicModeResponse(String mode) {}

    public record SignInFinishRequest(String challengeToken, String credentialJson, boolean trustedDevice) {}

    public record CreationFinishRequest(String challengeToken, String credentialJson, String label) {}

    public record RenameRequest(String label) {}

    public record SwitchRequest(boolean enabled) {}

    public record OfferAnswerRequest(String answer) {}

    public record OfferResponse(boolean offer) {}

    public record RemovalResponse(boolean passwordLoginReenabled) {}

    public record TrialResponse(String outcome) {}

    /**
     * @param qrPng PNG of a QR code opening the approval screen. It does not carry the code:
     *         both travel separately on purpose.
     */
    public record DeviceRequestResponse(String code, String pollSecret, Instant expiresAt, String qrPng) {}

    public record DevicePollRequest(String pollSecret) {}

    public record DevicePollResponse(String status, String enrollToken) {}

    public record DeviceEnrollBeginRequest(String enrollToken) {}

    public record DeviceEnrollFinishRequest(String enrollToken, String challengeToken, String credentialJson) {}

    public record DeviceCodeRequest(String code) {}

    public record DeviceLookupResponse(String userAgent, String country, Instant createdAt) {}

    public record TokenEnrollRequest(String token) {}

    public record TokenEnrollFinishRequest(String token, String challengeToken, String credentialJson) {}

    public record TokenEnrollLookupResponse(String firstName, String lastName) {}

    public record PasskeyEntryResponse(
            int id,
            String label,
            Instant createdAt,
            Instant lastUsedAt,
            String aaguid,
            boolean tried,
            String credentialId) {}

    /**
     * @param mayDisablePasswordLogin whether the switch-off is offered at all: the instance
     *         mode allows it, the address is reachable and a passkey has been shown to work
     * @param mode the effective passkey mode, so the screen knows what to show
     * @param rpId the effective relying-party id, which the browser's signal calls need
     * @param userHandle the account's user handle, for the same signals; null without passkeys
     */
    public record PasskeysStatusResponse(
            List<PasskeyEntryResponse> passkeys,
            boolean hasPassword,
            boolean passwordLoginEnabled,
            boolean askWithPassword,
            boolean mayDisablePasswordLogin,
            String mode,
            String rpId,
            String userHandle) {}
}
