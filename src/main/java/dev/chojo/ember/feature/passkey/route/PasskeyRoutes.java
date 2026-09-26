/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.passkey.route;

import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.auth.StepUpGuard;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.AccountCredential;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.route.AuthRoutes.LoginResponse;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequest;
import dev.chojo.ember.feature.devicerequest.entity.DeviceRequestPurpose;
import dev.chojo.ember.feature.devicerequest.service.DeviceRequestService;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.service.ManagedAccessService;
import dev.chojo.ember.feature.passkey.service.PasskeyAccountService;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
import dev.chojo.ember.feature.passkey.service.PasskeyService;
import dev.chojo.ember.feature.twofactor.service.RelyingParties;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
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
import java.util.Set;
import java.util.stream.Stream;

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
    private final DeviceRequestService deviceService;
    private final PasskeyEnrollmentService enrollmentService;
    private final TotpService totpService;
    private final StepUpGuard stepUpGuard;
    private final TwoFactorService twoFactorService;
    private final ManagedAccessService managedAccessService;
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
            DeviceRequestService deviceService,
            PasskeyEnrollmentService enrollmentService,
            TotpService totpService,
            StepUpGuard stepUpGuard,
            TwoFactorService twoFactorService,
            ManagedAccessService managedAccessService,
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
        this.stepUpGuard = stepUpGuard;
        this.twoFactorService = twoFactorService;
        this.managedAccessService = managedAccessService;
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

        routes.post(prefix + "/auth/device/sign-in-request", this::createSignInRequest);
        routes.post(prefix + "/auth/device/sign-in-claim", this::claimSignIn);

        routes.post(prefix + "/auth/passkey/enroll/begin", this::beginDeviceEnrollment);
        routes.post(prefix + "/auth/passkey/enroll/finish", this::finishDeviceEnrollment);

        // The token doors: a mail link, a QR in the room or a console line carries a bearer
        // that may create one passkey. The lookup names whose account it is before the device
        // asks for a fingerprint.
        routes.post(prefix + "/auth/passkey/token-enroll/lookup", this::lookupTokenEnrollment);
        routes.post(prefix + "/auth/passkey/token-enroll/begin", this::beginTokenEnrollment);
        routes.post(prefix + "/auth/passkey/token-enroll/finish", this::finishTokenEnrollment);
        routes.post(prefix + "/account/passkeys/device-lookup", this::lookupDeviceRequest, StationPermission.LOGIN);
        routes.post(prefix + "/account/passkeys/device-approve", this::approveDeviceRequest, StationPermission.LOGIN);
    }

    // -- The device handshake --

    private void createDeviceRequest(Context ctx) {
        requirePasskeysOn();
        var identifier = ctx.bodyAsClass(DeviceIdentifierRequest.class);
        RateLimits.enforce(rateLimiter.tryDeviceRequest(clientIp(ctx), identifier.identifier()));
        var request = deviceService.createRequest(
                DeviceRequestPurpose.ENROL_PASSKEY,
                identifier.identifier(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(deviceRequestResponse(request));
    }

    /**
     * The answer either request gives, code and all.
     *
     * <p>The QR carries the code, so scanning it is the whole of the reader's work. That makes it a
     * thing which grants a session to whoever holds it, which is why the number exists: it is shown
     * here and asked for there, and a picture forwarded to somebody cannot carry the screen it was
     * taken from.
     */
    private DeviceRequestResponse deviceRequestResponse(DeviceRequestService.CreatedRequest request) {
        String approvalUrl = api.baseUrl() + "/account/unlock-device?code=" + request.code();
        String qrPng = Base64.getEncoder().encodeToString(totpService.generateQrPng(approvalUrl, 240));
        return new DeviceRequestResponse(
                request.code(), request.pollSecret(), request.matchNumber(), request.expiresAt(), qrPng);
    }

    /**
     * A device asking to be signed in rather than given a credential. Unlike the enrolment request
     * this does not need passkeys to be on at all: not needing them is the point.
     */
    private void createSignInRequest(Context ctx) {
        var identifier = ctx.bodyAsClass(DeviceIdentifierRequest.class);
        RateLimits.enforce(rateLimiter.tryDeviceRequest(clientIp(ctx), identifier.identifier()));
        var request = deviceService.createRequest(
                DeviceRequestPurpose.SIGN_IN, identifier.identifier(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(deviceRequestResponse(request));
    }

    /**
     * Spends the claim the poll handed over and answers with the session. Throttled on the address
     * like every other way into an account, because this one ends in a session as surely as a
     * password does.
     */
    private void claimSignIn(Context ctx) {
        RateLimits.enforce(rateLimiter.tryDeviceClaim(clientIp(ctx)));
        var request = ctx.bodyAsClass(SignInClaimRequest.class);
        if (isBlank(request.claimToken())) {
            throw Refusal.DEVICE_SIGN_IN_CLAIM_MISSING.raise();
        }
        var result = deviceService
                .claimSignIn(request.claimToken(), ctx.userAgent(), ctx.header("CF-IPCountry"))
                .orElseThrow(Refusal.DEVICE_SIGN_IN_NOT_GRANTED::raise);
        if (!result.success()) {
            throw Refusal.DEVICE_SIGN_IN_NOT_GRANTED.raise();
        }
        if (result.passwordChangeRequired()) {
            ctx.json(LoginResponse.passwordChange(result.token(), result.expiresAt()));
            return;
        }
        ctx.json(LoginResponse.session(result.token(), result.expiresAt()));
    }

    private void pollDeviceRequest(Context ctx) {
        var request = ctx.bodyAsClass(DevicePollRequest.class);
        if (isBlank(request.pollSecret())) {
            throw Refusal.DEVICE_POLL_SECRET_MISSING.raise();
        }
        RateLimits.enforce(rateLimiter.tryDevicePoll(clientIp(ctx), request.pollSecret()));
        var result = deviceService.poll(
                request.pollSecret(), Set.of(DeviceRequestPurpose.ENROL_PASSKEY, DeviceRequestPurpose.SIGN_IN));
        ctx.json(new DevicePollResponse(
                result.status().name(),
                result.claimToken(),
                result.purpose() == null ? null : result.purpose().name()));
    }

    private void beginDeviceEnrollment(Context ctx) {
        requirePasskeysOn();
        RateLimits.enforce(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(DeviceEnrollBeginRequest.class);
        if (isBlank(request.enrollToken())) {
            throw Refusal.DEVICE_ENROLMENT_TOKEN_MISSING.raise();
        }
        var start = deviceService
                .beginEnrollment(request.enrollToken())
                .orElseThrow(Refusal.DEVICE_ENROLMENT_NOT_BEGUN::raise);
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishDeviceEnrollment(Context ctx) {
        requirePasskeysOn();
        RateLimits.enforce(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(DeviceEnrollFinishRequest.class);
        if (isBlank(request.enrollToken()) || isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw Refusal.DEVICE_ENROLMENT_DETAILS_MISSING.raise();
        }
        boolean created = deviceService.finishEnrollment(
                request.enrollToken(), request.challengeToken(), request.credentialJson(), ctx.header("CF-IPCountry"));
        if (!created) {
            throw Refusal.DEVICE_ENROLMENT_NOT_FINISHED.raise();
        }
        ctx.json(Map.of("message", "Passkey created"));
    }

    // -- The token doors --

    private void lookupTokenEnrollment(Context ctx) {
        requirePasskeysOn();
        RateLimits.enforce(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenEnrollRequest.class);
        if (isBlank(request.token())) {
            throw Refusal.ENROLMENT_LINK_TOKEN_MISSING.raise();
        }
        var account = enrollmentService.lookup(request.token()).orElseThrow(Refusal.ENROLMENT_LINK_UNKNOWN::raise);
        ctx.json(new TokenEnrollLookupResponse(account.firstName(), account.lastName()));
    }

    private void beginTokenEnrollment(Context ctx) {
        requirePasskeysOn();
        RateLimits.enforce(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenEnrollRequest.class);
        if (isBlank(request.token())) {
            throw Refusal.ENROLMENT_LINK_TOKEN_MISSING_ON_BEGIN.raise();
        }
        var start = enrollmentService.begin(request.token()).orElseThrow(Refusal.ENROLMENT_LINK_NOT_BEGUN::raise);
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishTokenEnrollment(Context ctx) {
        requirePasskeysOn();
        RateLimits.enforce(rateLimiter.tryDeviceEnroll(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenEnrollFinishRequest.class);
        if (isBlank(request.token()) || isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw Refusal.ENROLMENT_LINK_DETAILS_MISSING.raise();
        }
        if (!enrollmentService.finish(
                request.token(), request.challengeToken(), request.credentialJson(), ctx.header("CF-IPCountry"))) {
            throw Refusal.ENROLMENT_LINK_NOT_FINISHED.raise();
        }
        ctx.json(Map.of("message", "Passkey created"));
    }

    private void lookupDeviceRequest(Context ctx) {
        UserSession session = UserSession.from(ctx);
        RateLimits.enforce(rateLimiter.tryDeviceCodeEntry(session.sessionId(), session.accountId()));
        var request = ctx.bodyAsClass(DeviceCodeRequest.class);
        if (isBlank(request.code())) {
            throw Refusal.DEVICE_CODE_MISSING_ON_LOOKUP.raise();
        }
        var open = deviceService.lookup(request.code()).orElseThrow(Refusal.DEVICE_CODE_NOT_YOURS_ON_LOOKUP::raise);
        if (!mayConfirm(session, open)) {
            throw Refusal.DEVICE_CODE_NOT_YOURS_ON_LOOKUP.raise();
        }
        ctx.json(new DeviceLookupResponse(
                open.requestedUserAgent(),
                open.requestedCountry(),
                open.createdAt(),
                open.purpose().name(),
                open.stepUpCategory() == null ? null : open.stepUpCategory().name(),
                stepUpSubject(session, open),
                open.matchChoices(),
                managedCandidates(session, open)));
    }

    /**
     * Whose step-up this is, where it is not the reader's own.
     *
     * <p>A guardian may be answering for themselves or for a child, and the two look identical
     * otherwise: same screen, same category, and a browser and country that are the child's. Naming
     * them is what lets a guardian refuse a code that is not the one somebody beside them just asked
     * for. Their own request says nothing, because there is nothing to tell apart.
     */
    private String stepUpSubject(UserSession session, DeviceRequest open) {
        if (!open.is(DeviceRequestPurpose.STEP_UP)) return null;
        Integer requester = open.requestingAccountId();
        if (requester == null || requester == session.accountId()) return null;
        return accountRepository.findById(requester).map(Account::fullName).orElse(null);
    }

    /**
     * Whether this reader is allowed to see the request at all, let alone answer it.
     *
     * <p>Their own, or one raised by somebody in their care: a member signed in by their guardian has
     * no password and no passkey, so the guardian is the only one who can answer a demand made of
     * them. Everybody else is told nothing, which is exactly what a wrong code already earns, so the
     * answer cannot be read as "this code exists".
     *
     * <p>A sign-in and an enrolment are read against the account the requesting device named, a
     * step-up against the one whose session raised it. The first two used to be waved through,
     * because a request named nobody until it was approved: a code could then be handed to a crowd
     * and whoever answered it gave away their own account, without whoever raised it ever having to
     * know whose it would be.
     *
     * <p>A request whose identifier matched no account names nobody and so passes for nobody, which
     * is how an address that does not exist here comes to be answered like one that does.
     */
    private boolean mayConfirm(UserSession session, DeviceRequest open) {
        if (!open.is(DeviceRequestPurpose.STEP_UP)) {
            if (open.namesAccount(session.accountId())) return true;
            return open.namedAccountId() != null
                    && accountRepository.isGuardianOf(session.accountId(), open.namedAccountId());
        }
        if (Integer.valueOf(session.accountId()).equals(open.requestingAccountId())) return true;
        return open.requestingAccountId() != null
                && accountRepository.isGuardianOf(session.accountId(), open.requestingAccountId());
    }

    private boolean manages(UserSession session, Integer accountId) {
        if (accountId == null) return false;
        return session.memberOpt()
                .map(member -> managedAccessService.signInCandidates(member.id()).stream()
                        .anyMatch(candidate -> candidate.accountId() == accountId.intValue()))
                .orElse(false);
    }

    /**
     * Whose account the grant is for.
     *
     * <p>A step-up always stamps the session that raised it, so the subject is the account that
     * asked, whoever answers for it. A guardian answering for a member in their care is the point of
     * the exercise, and the choice the screen offered has no say in it.
     *
     * <p>Otherwise the approver, unless this is a sign-in they are making for somebody in their care.
     * The guardianship is checked here and not only where the screen offered the choice, so a
     * guardianship that ended in between cannot be spent on a list drawn before it did.
     */
    private int subjectFor(UserSession session, DeviceRequest open, Integer requested) {
        if (open.is(DeviceRequestPurpose.STEP_UP)) return open.requestingAccountId();
        if (requested == null || requested == session.accountId()) return session.accountId();
        if (!open.is(DeviceRequestPurpose.SIGN_IN)) {
            throw Refusal.APPROVAL_ONLY_FOR_SIGN_IN.raise();
        }
        if (!manages(session, requested)) {
            throw Refusal.APPROVAL_MEMBER_NOT_YOURS.raise();
        }
        return requested;
    }

    private List<ApprovalCandidate> managedCandidates(UserSession session, DeviceRequest open) {
        if (!open.is(DeviceRequestPurpose.SIGN_IN)) return List.of();
        var self = new ApprovalCandidate(
                session.accountId(),
                accountRepository
                        .findById(session.accountId())
                        .map(Account::fullName)
                        .orElse(""));
        if (session.memberOpt().isEmpty()) return List.of(self);
        var managed = managedAccessService
                .signInCandidates(session.memberOpt().get().id())
                .stream()
                .map(candidate -> new ApprovalCandidate(candidate.accountId(), candidate.name()));
        return Stream.concat(Stream.of(self), managed).toList();
    }

    /**
     * Vouching for another device, which is the one action a session can take that lets somebody else
     * in.
     *
     * <p>It carries no {@code StepUpCategory} route role, deliberately. That role is a freshness
     * check, and a session already fresh would pass it without anybody being asked anything. This is
     * the one action that has to rest on a proof given at this keyboard now, so the handler demands
     * one itself and spends it rather than riding a window somebody opened earlier.
     *
     * <p>The demand comes after the code has been read and the subject settled, so a reader who typed
     * a wrong code is told so without being asked to prove themselves first.
     */
    private void approveDeviceRequest(Context ctx) {
        UserSession session = UserSession.from(ctx);
        RateLimits.enforce(rateLimiter.tryDeviceCodeEntry(session.sessionId(), session.accountId()));
        var request = ctx.bodyAsClass(DeviceCodeRequest.class);
        if (isBlank(request.code())) {
            throw Refusal.DEVICE_CODE_MISSING_ON_APPROVAL.raise();
        }
        var open = deviceService.lookup(request.code()).orElseThrow(Refusal.DEVICE_CODE_NOT_YOURS_ON_APPROVAL::raise);
        if (!mayConfirm(session, open)) {
            throw Refusal.DEVICE_CODE_NOT_YOURS_ON_APPROVAL.raise();
        }
        if (request.pickedNumber() == null) {
            throw Refusal.DEVICE_MATCH_NUMBER_MISSING.raise();
        }
        int subject = subjectFor(session, open, request.forAccountId());
        stepUpGuard.spendLocalProof(session, StepUpCategory.ACCOUNT_SECURITY);
        var result = deviceService.approve(session.accountId(), subject, request.code(), request.pickedNumber());
        if (result == DeviceRequestService.ApprovalResult.WRONG_NUMBER) {
            throw Refusal.DEVICE_MATCH_NUMBER_WRONG.raise();
        }
        if (result != DeviceRequestService.ApprovalResult.APPROVED) {
            throw Refusal.DEVICE_APPROVAL_NOT_TAKEN.raise();
        }
        ctx.json(Map.of("message", "Device approved"));
    }

    private String clientIp(Context ctx) {
        return ClientIp.resolve(ctx, network).getHostAddress();
    }

    private void requirePasskeysOn() {
        if (modeService.effectiveMode() == PasskeySettings.Mode.OFF) {
            throw Refusal.PASSKEYS_SWITCHED_OFF.raise();
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
        RateLimits.enforce(rateLimiter.tryPasskeySignIn(clientIp(ctx)));
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
        RateLimits.enforce(rateLimiter.tryPasskeySignIn(clientIp(ctx)));
        var request = ctx.bodyAsClass(SignInFinishRequest.class);
        if (isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw Refusal.PASSKEY_SIGN_IN_DETAILS_MISSING.raise();
        }

        Optional<Integer> accountId = passkeyService.finishSignIn(
                request.challengeToken(), request.credentialJson(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (accountId.isEmpty()) {
            throw Refusal.PASSKEY_SIGN_IN_REFUSED.raise();
        }

        var result = authService.admitPasskeyAccount(
                accountId.get(), ctx.userAgent(), ctx.header("CF-IPCountry"), request.trustedDevice());
        if (!result.success()) {
            throw Refusal.PASSKEY_SIGN_IN_REFUSED.raise();
        }
        if (result.passwordChangeRequired()) {
            ctx.json(LoginResponse.passwordChange(result.token(), result.expiresAt()));
            return;
        }
        ctx.json(LoginResponse.session(result.token(), result.expiresAt()));
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
        var account = accountRepository
                .findById(session.accountId())
                .orElseThrow(Refusal.ACCOUNT_NOT_HERE_ON_PASSKEY_CREATION::raise);
        String displayName = NameParts.of(account).official();
        var start = passkeyService.startCreation(
                session.accountId(), account.email(), displayName.isBlank() ? account.email() : displayName);
        ctx.json(new CeremonyResponse(start.challengeToken(), start.optionsJson()));
    }

    private void finishCreation(Context ctx) {
        requirePasskeysOn();
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreationFinishRequest.class);
        if (isBlank(request.challengeToken()) || isBlank(request.credentialJson())) {
            throw Refusal.PASSKEY_CREATION_DETAILS_MISSING.raise();
        }
        var factor = passkeyService.finishCreation(
                session.accountId(),
                request.challengeToken(),
                request.credentialJson(),
                request.label(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        if (factor.isEmpty()) {
            throw Refusal.PASSKEY_NOT_CREATED.raise();
        }
        ctx.status(HttpStatus.CREATED)
                .json(new PasskeyEntryResponse(
                        factor.get().id(), factor.get().label(), factor.get().createdAt(), null, null, false, null));
    }

    private void rename(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(RenameRequest.class);
        if (!accountService.rename(session.accountId(), pathInt(ctx, "id"), request.label())) {
            throw Refusal.PASSKEY_NOT_HERE_ON_RENAME.raise();
        }
        ctx.json(Map.of("message", "Passkey renamed"));
    }

    private void remove(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var outcome = accountService.remove(
                session.accountId(), pathInt(ctx, "id"), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (outcome) {
            case NOT_FOUND -> throw Refusal.PASSKEY_NOT_HERE_ON_REMOVAL.raise();
            case REFUSED_NO_PASSWORD -> throw Refusal.LAST_WAY_INTO_ACCOUNT.raise();
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
            case MODE_FORBIDS -> throw Refusal.PASSWORD_SIGN_IN_LOCKED_BY_INSTANCE.raise();
            case NO_REACHABLE_ADDRESS -> throw Refusal.PASSWORD_SIGN_IN_NEEDS_REACHABLE_ADDRESS.raise();
            case NO_TRIED_PASSKEY -> throw Refusal.PASSWORD_SIGN_IN_NEEDS_TRIED_PASSKEY.raise();
            case NO_PASSWORD -> throw Refusal.ACCOUNT_HOLDS_NO_PASSWORD_ON_SWITCH.raise();
        }
    }

    private void setAskWithPassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(SwitchRequest.class);
        accountService.setAskWithPassword(session.accountId(), request.enabled());
        ctx.json(Map.of("message", "Updated"));
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
                    default -> throw Refusal.PASSKEY_OFFER_ANSWER_UNKNOWN.raise();
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
            throw Refusal.PASSKEY_TRIAL_DETAILS_MISSING.raise();
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
     * @param qrPng PNG of a QR code opening the approval screen with the code already in it, so
     *         scanning it is the whole of the reader's work
     * @param matchNumber the number this screen shows and the approving screen asks for. It is the
     *         one part of the handshake the QR does not carry, which is what a forwarded picture
     *         cannot supply
     */
    public record DeviceRequestResponse(
            String code, String pollSecret, int matchNumber, Instant expiresAt, String qrPng) {}

    /**
     * @param identifier the address or username the device is asking to be signed in as. Answered
     *         the same way whether or not it matches an account
     */
    public record DeviceIdentifierRequest(String identifier) {}

    public record DevicePollRequest(String pollSecret) {}

    /**
     * @param purpose what the waiting claim buys, so the asking device knows which ceremony follows.
     *         Absent until there is something to claim.
     */
    public record DevicePollResponse(String status, String enrollToken, String purpose) {}

    public record SignInClaimRequest(String claimToken) {}

    public record DeviceEnrollBeginRequest(String enrollToken) {}

    public record DeviceEnrollFinishRequest(String enrollToken, String challengeToken, String credentialJson) {}

    /**
     * @param forAccountId whom the sign-in is for, where a guardian is signing in somebody in their
     *         care. Absent means the approver themselves
     * @param pickedNumber which of the six offered numbers the reader chose. Absent on the lookup,
     *         which only reads; the approval refuses without it
     */
    public record DeviceCodeRequest(String code, Integer forAccountId, Integer pickedNumber) {}

    /**
     * @param purpose what approving this buys, so the screen can say it in the reader's terms
     * @param stepUpCategory what a step-up was demanded for, absent for the other purposes
     * @param stepUpSubject whose step-up it is, where that is somebody in the reader's care rather
     *         than the reader themselves
     * @param numberChoices the six numbers to offer, in the order to offer them, one of which is
     *         the one the requesting screen shows. Which is never said here
     * @param candidates whom this reader may sign in, for a sign-in. Themselves first
     */
    public record DeviceLookupResponse(
            String userAgent,
            String country,
            Instant createdAt,
            String purpose,
            String stepUpCategory,
            String stepUpSubject,
            List<Integer> numberChoices,
            List<ApprovalCandidate> candidates) {}

    public record ApprovalCandidate(int accountId, String name) {}

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
