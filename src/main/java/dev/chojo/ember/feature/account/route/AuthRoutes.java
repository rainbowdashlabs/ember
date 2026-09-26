/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.RateLimits;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
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

/**
 * Routes for authentication operations including registration, login, email verification,
 * password management, and email change confirmation.
 */
@Singleton
public class AuthRoutes implements Routes {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final Network network;
    private final Demo demo;
    private final PasskeyModeService passkeyModeService;

    @Inject
    public AuthRoutes(
            AuthService authService,
            AuthRateLimiter rateLimiter,
            Network network,
            Demo demo,
            PasskeyModeService passkeyModeService) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.network = network;
        this.demo = demo;
        this.passkeyModeService = passkeyModeService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String extractBearerToken(Context ctx) {
        String header = ctx.header("Authorization");
        if (header == null) return null;
        String prefix = "Bearer ";
        if (header.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return header.substring(prefix.length()).trim();
        }
        return null;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/auth/register", this::register);
        routes.post(prefix + "/auth/verify-email", this::verifyEmail);
        routes.post(prefix + "/auth/resend-verification", this::resendVerification);
        routes.post(prefix + "/auth/set-password", this::setPassword);
        routes.post(prefix + "/auth/set-address", this::setAddress);
        routes.post(prefix + "/auth/forgot-password", this::forgotPassword);
        routes.post(prefix + "/auth/password-link", this::passwordLinkStatus);
        routes.post(prefix + "/auth/login", this::login);
        if (demo.dev() || demo.enabled()) {
            routes.post(prefix + "/demo/login", this::demoLogin);
        }
        routes.post(prefix + "/auth/refresh", this::refresh);
        routes.post(prefix + "/auth/logout", this::logout);
        routes.post(
                prefix + "/auth/change-password",
                this::changePassword,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(prefix + "/auth/confirm-email-change", this::confirmEmailChange);
    }

    private String clientIp(Context ctx) {
        return ClientIp.resolve(ctx, network).getHostAddress();
    }

    @OpenApi(
            path = "/api/v1/auth/register",
            methods = HttpMethod.POST,
            summary = "Register a new account",
            description =
                    "Self-registration with email and password. Requires a station-specific registration code. Sends a verification email.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = RegisterRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = RegisterResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void register(Context ctx) {
        RateLimits.enforce(rateLimiter.tryRegister(clientIp(ctx)));
        var request = ctx.bodyAsClass(RegisterRequest.class);
        // On a passwordless instance no password is asked for: the account is created without
        // one, and the verification mail's link is where the passkey is made.
        boolean passwordless = passkeyModeService.effectiveMode() == PasskeySettings.Mode.PASSWORDLESS;
        if (isBlank(request.email())
                || isBlank(request.firstName())
                || isBlank(request.lastName())
                || (!passwordless && isBlank(request.password()))) {
            throw Refusal.REGISTRATION_DETAILS_MISSING.raise();
        }

        var result = authService.registerSelf(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password(),
                request.registrationCode());
        if (!result.success()) {
            throw Refusal.REGISTRATION_REFUSED.raise();
        }

        ctx.status(HttpStatus.CREATED)
                .json(new RegisterResponse(
                        result.account().id(),
                        result.account().email(),
                        result.account().firstName(),
                        result.account().lastName(),
                        result.account().emailVerified()));
    }

    @OpenApi(
            path = "/api/v1/auth/verify-email",
            methods = HttpMethod.POST,
            summary = "Verify email address",
            description = "Confirms email ownership using the token sent during registration.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokenRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void verifyEmail(Context ctx) {
        RateLimits.enforce(rateLimiter.tryVerifyEmail(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw Refusal.EMAIL_VERIFICATION_TOKEN_MISSING.raise();
        }

        if (authService.verifyEmail(request.token())) {
            ctx.status(HttpStatus.OK).json(new MessageResponse("Email verified"));
        } else {
            throw Refusal.EMAIL_VERIFICATION_LINK_NOT_GOOD.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/auth/resend-verification",
            methods = HttpMethod.POST,
            summary = "Resend verification email",
            description = "Resends the verification email. Always returns OK to prevent email enumeration.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EmailRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resendVerification(Context ctx) {
        var request = ctx.bodyAsClass(EmailRequest.class);
        if (isBlank(request.email())) {
            throw Refusal.RESEND_VERIFICATION_ADDRESS_MISSING.raise();
        }
        RateLimits.enforce(rateLimiter.tryResendVerification(clientIp(ctx), request.email()));

        authService.resendVerification(request.email());
        ctx.status(HttpStatus.OK)
                .json(new MessageResponse(
                        "If the email exists and is unverified, a new verification email has been sent"));
    }

    @OpenApi(
            path = "/api/v1/auth/set-password",
            methods = HttpMethod.POST,
            summary = "Set password for invited account",
            description =
                    "Sets the initial password using the token from the invite email, or a new one using a reset token, and signs the account in with it. Answers as the login endpoint does: a session, or the second factor still to be given. Where the account cannot be signed in yet, the session is absent and the sign-in form says why.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetPasswordRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setPassword(Context ctx) {
        RateLimits.enforce(rateLimiter.trySetPassword(clientIp(ctx)));
        var request = ctx.bodyAsClass(SetPasswordRequest.class);
        if (isBlank(request.token()) || isBlank(request.password())) {
            throw Refusal.PASSWORD_SETUP_DETAILS_MISSING.raise();
        }

        var result = authService.setPasswordAndSignIn(
                request.token(), request.password(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (result.outcome()) {
            case OK -> ctx.status(HttpStatus.OK).json(LoginResponse.of(result.login()));
            case PASSWORD_TOO_SHORT -> throw Refusal.NEW_PASSWORD_TOO_SHORT.raise();
            case PASSWORD_BREACHED -> throw Refusal.NEW_PASSWORD_BREACHED.raise();
            case TOKEN_INVALID -> throw Refusal.PASSWORD_SETUP_LINK_UNKNOWN.raise();
            case TOKEN_EXPIRED -> throw Refusal.PASSWORD_SETUP_LINK_EXPIRED.raise();
            case PASSWORDLESS_MODE -> throw Refusal.PASSWORDS_SWITCHED_OFF.raise();
            // An outcome added later and not answered here would otherwise fall out of the switch
            // with nothing written, and an empty 200 reads as a password that was set.
            default -> throw new IllegalStateException("Unhandled set-password outcome: " + result.outcome());
        }
    }

    @OpenApi(
            path = "/api/v1/auth/set-address",
            methods = HttpMethod.POST,
            summary = "Put a reachable address on an account that owes one",
            description =
                    "Spends the one-time token a sign-in hands out instead of a session when the account administers the instance and carries no address mail can reach. Writes the address and answers as the login endpoint does. A made-up address is refused: the point of the step is that the person can be written to.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetAddressRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setAddress(Context ctx) {
        RateLimits.enforce(rateLimiter.trySetPassword(clientIp(ctx)));
        var request = ctx.bodyAsClass(SetAddressRequest.class);
        if (isBlank(request.token()) || isBlank(request.email())) {
            throw Refusal.ADDRESS_SETUP_DETAILS_MISSING.raise();
        }

        var result = authService.setRequiredAddress(
                request.token(), request.email(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (result.outcome()) {
            case OK -> ctx.status(HttpStatus.OK).json(LoginResponse.of(result.login()));
            case TOKEN_INVALID -> throw Refusal.ADDRESS_SETUP_LINK_UNKNOWN.raise();
            case TOKEN_EXPIRED -> throw Refusal.ADDRESS_SETUP_LINK_EXPIRED.raise();
            case ADDRESS_MALFORMED -> throw Refusal.ADDRESS_MALFORMED.raise();
            case ADDRESS_UNREACHABLE -> throw Refusal.ADDRESS_UNREACHABLE.raise();
            case ADDRESS_TAKEN -> throw Refusal.ADDRESS_TAKEN_ON_SETUP.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/auth/password-link",
            methods = HttpMethod.POST,
            summary = "Ask what a password link is worth",
            description =
                    "Reports whether a setup or reset link is still good, has run out, or is unknown, and which of the two it is. Spends nothing: the page asks before it shows a form nobody could submit. The token travels in the body rather than the path so it stays out of logs.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokenRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = AuthService.TokenStatus.class)))
    private void passwordLinkStatus(Context ctx) {
        RateLimits.enforce(rateLimiter.trySetPassword(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        ctx.json(authService.checkPasswordToken(request.token()));
    }

    @OpenApi(
            path = "/api/v1/auth/forgot-password",
            methods = HttpMethod.POST,
            summary = "Request password reset",
            description =
                    "Sends a password reset email to whoever can be reached about the account, which for a member with no address of their own is their guardians. Takes an email address or a username. Always returns OK to prevent email enumeration.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EmailRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void forgotPassword(Context ctx) {
        var request = ctx.bodyAsClass(EmailRequest.class);
        if (isBlank(request.email())) {
            throw Refusal.FORGOTTEN_PASSWORD_ADDRESS_MISSING.raise();
        }
        RateLimits.enforce(rateLimiter.tryForgotPassword(clientIp(ctx), request.email()));

        authService.requestPasswordReset(request.email());
        ctx.status(HttpStatus.OK).json(new MessageResponse("If the email exists, a password reset link has been sent"));
    }

    @OpenApi(
            path = "/api/v1/auth/login",
            methods = HttpMethod.POST,
            summary = "Log in",
            description =
                    "Authenticates with an email address or a username, and a password. Returns a session token, or a password change token if a password change is required.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LoginRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void login(Context ctx) {
        var request = ctx.bodyAsClass(LoginRequest.class);
        if (isBlank(request.identifier()) || isBlank(request.password())) {
            throw Refusal.SIGN_IN_DETAILS_MISSING.raise();
        }
        RateLimits.enforce(rateLimiter.tryLogin(clientIp(ctx), request.identifier()));

        var result = authService.login(
                request.identifier(),
                request.password(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"),
                ctx.cookie("ember_2fa_trust"),
                request.trustedDevice());
        if (!result.success()) {
            throw Refusal.SIGN_IN_REFUSED.raise();
        }

        ctx.status(HttpStatus.OK).json(LoginResponse.of(result));
    }

    @OpenApi(
            path = "/api/v1/demo/login",
            methods = HttpMethod.POST,
            summary = "Quick login (dev / demo only) - sign in by email, no password check",
            description =
                    "Issues a session for the account behind the given email without verifying any password. Registered only when the backend runs with demo.dev or demo.enabled set; absent in production. Used by the dev / demo login UI so the click-to-impersonate buttons keep working after a seeded user has rotated their password.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = DemoLoginRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void demoLogin(Context ctx) {
        var request = ctx.bodyAsClass(DemoLoginRequest.class);
        if (isBlank(request.email())) {
            throw Refusal.DEMO_SIGN_IN_ADDRESS_MISSING.raise();
        }
        var result = authService.loginAsDemo(request.email(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (!result.success()) {
            throw Refusal.DEMO_SIGN_IN_REFUSED.raise();
        }
        ctx.status(HttpStatus.OK).json(LoginResponse.of(result));
    }

    @OpenApi(
            path = "/api/v1/auth/refresh",
            methods = HttpMethod.POST,
            summary = "Refresh session",
            description = "Exchanges a valid session token for a new one. The old token is invalidated.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokenRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = SessionResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void refresh(Context ctx) {
        RateLimits.enforce(rateLimiter.tryRefresh(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw Refusal.SESSION_RENEWAL_TOKEN_MISSING.raise();
        }

        var result = authService.refreshSession(request.token(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (!result.success()) {
            throw Refusal.SESSION_NOT_RENEWED.raise();
        }

        ctx.status(HttpStatus.OK).json(new SessionResponse(result.token(), result.expiresAt()));
    }

    @OpenApi(
            path = "/api/v1/auth/logout",
            methods = HttpMethod.POST,
            summary = "Log out",
            description = "Invalidates the session token.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokenRequest.class)),
            responses = {@OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class))})
    private void logout(Context ctx) {
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw Refusal.SIGN_OUT_TOKEN_MISSING.raise();
        }

        authService.logout(request.token());
        ctx.status(HttpStatus.OK).json(new MessageResponse("Logged out"));
    }

    private void changePassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        RateLimits.enforce(rateLimiter.tryChangePassword(session.accountId()));
        var request = ctx.bodyAsClass(ChangePasswordRequest.class);
        if (isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw Refusal.PASSWORD_CHANGE_DETAILS_MISSING.raise();
        }
        String currentSessionToken = extractBearerToken(ctx);
        var outcome = authService.changePassword(
                session.accountId(), currentSessionToken, request.currentPassword(), request.newPassword());
        switch (outcome) {
            case OK -> ctx.json(new MessageResponse("Password changed"));
            case NEW_PASSWORD_TOO_SHORT -> throw Refusal.CHANGED_PASSWORD_TOO_SHORT.raise();
            case NEW_PASSWORD_BREACHED -> throw Refusal.CHANGED_PASSWORD_BREACHED.raise();
            case NO_PASSWORD_SET -> throw Refusal.ACCOUNT_HAS_NO_PASSWORD.raise();
            case CURRENT_PASSWORD_WRONG -> throw Refusal.CURRENT_PASSWORD_WRONG.raise();
            // An outcome added later and not answered here would otherwise fall out of the switch
            // with nothing written, and an empty 200 reads as a password that changed.
            default -> throw new IllegalStateException("Unhandled change-password outcome: " + outcome);
        }
    }

    @OpenApi(
            path = "/api/v1/auth/confirm-email-change",
            methods = HttpMethod.POST,
            summary = "Confirm an email change",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokenRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = EmailChangeResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void confirmEmailChange(Context ctx) {
        RateLimits.enforce(rateLimiter.tryConfirmEmailChange(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) throw Refusal.EMAIL_CHANGE_TOKEN_MISSING.raise();
        var result = authService.confirmEmailChange(request.token());
        switch (result) {
            case COMMITTED -> ctx.json(new EmailChangeResponse("COMMITTED", "Email address updated"));
            case WAITING ->
                ctx.json(
                        new EmailChangeResponse(
                                "WAITING",
                                "Confirmation received. Waiting for the other address to confirm before the change takes effect."));
            case DUPLICATE -> throw Refusal.EMAIL_CHANGE_ADDRESS_TAKEN.raise();
            case INVALID -> throw Refusal.EMAIL_CHANGE_LINK_NOT_GOOD.raise();
            // An outcome added later and not answered here would otherwise fall out of the switch
            // with nothing written, and an empty 200 reads as an address that changed.
            default -> throw new IllegalStateException("Unhandled email-change outcome: " + result);
        }
    }

    // -- Request/Response records --

    /**
     * Request body for self-registration with optional station registration code.
     */
    public record RegisterRequest(
            String email, String firstName, String lastName, String password, String registrationCode) {}

    /**
     * Request body for login with email and password.
     */
    /**
     * @param identifier    what was typed in the first field: an email address, or the name an
     *                      account signs in with. Which of the two it is follows from the at sign,
     *                      because a username never holds one.
     * @param trustedDevice the box on the login screen. Ticked, the session lasts as long as the
     *                      instance allows; left alone it lasts the short duration, which is what a
     *                      borrowed or shared machine should get.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(String identifier, String password, boolean trustedDevice) {}

    /**
     * Request body for the dev / demo {@code POST /demo/login} quick-login endpoint. Only an
     * email is needed - the password field is intentionally absent because the backend skips
     * password verification on this path. Refer to {@link AuthService#loginAsDemo} for the
     * gating rules.
     */
    public record DemoLoginRequest(String email) {}

    /**
     * Request body containing a one-time token for verification, password set, refresh, or logout.
     */
    public record TokenRequest(String token) {}

    /**
     * Outcome of confirming one half of an email change.
     *
     * @param status COMMITTED once both addresses have confirmed, WAITING while the other one
     *               still has to. The page tells the two apart by this rather than by the wording.
     * @param message what to say about it
     */
    public record EmailChangeResponse(String status, String message) {}

    /**
     * Request body for changing a password while authenticated.
     */
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    /**
     * Request body containing only an email address (used for password reset and resend verification).
     */
    /**
     * @param email the address. The forgotten-password path also accepts the name an account signs
     *              in with, and then writes to whoever can be reached about that account.
     */
    public record EmailRequest(String email) {}

    /**
     * Request body for setting a password using an invite or reset token.
     */
    public record SetPasswordRequest(String token, String password) {}

    /**
     * Request body for putting a reachable address on an account that a sign-in stopped for one.
     */
    public record SetAddressRequest(String token, String email) {}

    /**
     * Response body returned after successful registration.
     */
    public record RegisterResponse(int id, String email, String firstName, String lastName, boolean emailVerified) {}

    /**
     * Response body for login. Contains either a session token or a password change token,
     * depending on whether a forced password change is required.
     */
    public record LoginResponse(
            String token,
            Instant expiresAt,
            boolean passwordChangeRequired,
            String passwordChangeToken,
            Instant passwordChangeTokenExpiresAt,
            boolean addressRequired,
            String addressToken,
            Instant addressTokenExpiresAt,
            boolean twoFactorRequired,
            String preAuthToken,
            Instant preAuthTokenExpiresAt) {

        /** Neither a session nor a way to one: whoever asked has to sign in by hand. */
        public static LoginResponse none() {
            return new LoginResponse(null, null, false, null, null, false, null, null, false, null, null);
        }

        public static LoginResponse session(String token, Instant expiresAt) {
            return new LoginResponse(token, expiresAt, false, null, null, false, null, null, false, null, null);
        }

        public static LoginResponse passwordChange(String token, Instant expiresAt) {
            return new LoginResponse(null, null, true, token, expiresAt, false, null, null, false, null, null);
        }

        public static LoginResponse address(String token, Instant expiresAt) {
            return new LoginResponse(null, null, false, null, null, true, token, expiresAt, false, null, null);
        }

        public static LoginResponse twoFactor(String preAuthToken, Instant expiresAt) {
            return new LoginResponse(null, null, false, null, null, false, null, null, true, preAuthToken, expiresAt);
        }

        /**
         * Whatever the sign-in decided, said the way the API says it: a session, or the one step
         * still standing in the way of one.
         */
        public static LoginResponse of(LoginResult login) {
            if (login == null || !login.success()) return none();
            if (login.passwordChangeRequired()) return passwordChange(login.token(), login.expiresAt());
            if (login.addressRequired()) return address(login.token(), login.expiresAt());
            if (login.twoFactorRequired()) return twoFactor(login.preAuthToken(), login.preAuthTokenExpiresAt());
            return session(login.token(), login.expiresAt());
        }
    }

    /**
     * Response body for a refreshed session with the new token and expiration.
     */
    public record SessionResponse(String token, Instant expiresAt) {}
}
