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
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StepUpChallenge;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.SessionCookies;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.api.refusal.MemberRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.conf.file.elements.PasskeySettings;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.entity.LoginResult;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.passkey.service.PasskeyModeService;
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
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Routes for authentication operations including registration, login, email verification,
 * password management, and email change confirmation.
 *
 * <p>The outcome switches of the password and email-change flows end in a throwing default: an
 * outcome added later would otherwise fall out of the switch with nothing written, and an empty 200
 * reads as success. The address-setup flow maps its outcomes in a switch expression instead, which
 * the compiler holds to every outcome.
 */
@Singleton
public class AuthRoutes implements Routes {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final Demo demo;
    private final PasskeyModeService passkeyModeService;
    private final SessionCookies sessionCookies;

    @Inject
    public AuthRoutes(
            AuthService authService,
            AuthRateLimiter rateLimiter,
            Demo demo,
            PasskeyModeService passkeyModeService,
            SessionCookies sessionCookies) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.demo = demo;
        this.passkeyModeService = passkeyModeService;
        this.sessionCookies = sessionCookies;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Answers a sign-in: the session goes into the cookie, and the body says what was decided. */
    private void answerSignIn(Context ctx, @Nullable LoginResult login) {
        sessionCookies.issue(ctx, login);
        ctx.status(HttpStatus.OK).json(LoginResponse.of(login));
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
        routes.post(prefix + "/auth/logout", this::logout);
        routes.post(
                prefix + "/auth/change-password",
                this::changePassword,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(prefix + "/auth/confirm-email-change", this::confirmEmailChange);
    }

    /**
     * Registers an account. A passwordless instance asks for no password: the verification mail's
     * link is where the passkey is made.
     */
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
        RateLimits.enforce(MemberRefusal.REGISTERING_TOO_OFTEN, rateLimiter.tryRegister(ctx.ip()));
        var request = ctx.bodyAsClass(RegisterRequest.class);
        boolean passwordless = passkeyModeService.effectiveMode() == PasskeySettings.Mode.PASSWORDLESS;
        if (isBlank(request.email())
                || isBlank(request.firstName())
                || isBlank(request.lastName())
                || (!passwordless && isBlank(request.password()))) {
            throw MemberRefusal.REGISTRATION_DETAILS_MISSING.raise();
        }

        var result = authService.registerSelf(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password(),
                request.registrationCode());
        Account account = result.account();
        if (!result.success() || account == null) {
            throw MemberRefusal.REGISTRATION_REFUSED.raise();
        }

        ctx.status(HttpStatus.CREATED)
                .json(new RegisterResponse(
                        account.id(),
                        account.email(),
                        account.firstName(),
                        account.lastName(),
                        account.emailVerified()));
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
        RateLimits.enforce(MemberRefusal.EMAIL_VERIFYING_TOO_OFTEN, rateLimiter.tryVerifyEmail(ctx.ip()));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw MemberRefusal.EMAIL_VERIFICATION_TOKEN_MISSING.raise();
        }

        if (authService.verifyEmail(request.token())) {
            ctx.status(HttpStatus.OK).json(new MessageResponse("Email verified"));
        } else {
            throw MemberRefusal.EMAIL_VERIFICATION_LINK_NOT_GOOD.raise();
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
            throw MemberRefusal.RESEND_VERIFICATION_ADDRESS_MISSING.raise();
        }
        RateLimits.enforce(
                MemberRefusal.VERIFICATION_MAIL_TOO_OFTEN,
                rateLimiter.tryResendVerification(ctx.ip(), request.email()));

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
        RateLimits.enforce(MemberRefusal.PASSWORD_SETTING_TOO_OFTEN, rateLimiter.trySetPassword(ctx.ip()));
        var request = ctx.bodyAsClass(SetPasswordRequest.class);
        if (isBlank(request.token()) || isBlank(request.password())) {
            throw MemberRefusal.PASSWORD_SETUP_DETAILS_MISSING.raise();
        }

        var result = authService.setPasswordAndSignIn(
                request.token(), request.password(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        switch (result.outcome()) {
            case OK -> answerSignIn(ctx, result.login());
            case PASSWORD_TOO_SHORT -> throw MemberRefusal.NEW_PASSWORD_TOO_SHORT.raise();
            case PASSWORD_BREACHED -> throw MemberRefusal.NEW_PASSWORD_BREACHED.raise();
            case TOKEN_INVALID -> throw MemberRefusal.PASSWORD_SETUP_LINK_UNKNOWN.raise();
            case TOKEN_EXPIRED -> throw MemberRefusal.PASSWORD_SETUP_LINK_EXPIRED.raise();
            case PASSWORDLESS_MODE -> throw MemberRefusal.PASSWORDS_SWITCHED_OFF.raise();
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
        RateLimits.enforce(MemberRefusal.ADDRESS_SETTING_TOO_OFTEN, rateLimiter.trySetPassword(ctx.ip()));
        var request = ctx.bodyAsClass(SetAddressRequest.class);
        if (isBlank(request.token()) || isBlank(request.email())) {
            throw MemberRefusal.ADDRESS_SETUP_DETAILS_MISSING.raise();
        }

        var result = authService.setRequiredAddress(
                request.token(), request.email(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (result.outcome() != AuthService.AddressOutcome.OK)
            throw addressSetupRefusal(result.outcome()).raise();
        answerSignIn(ctx, result.login());
    }

    /**
     * The refusal for an address that was not put on the account. A switch expression, so an outcome
     * added later does not compile until it is answered here.
     *
     * @param outcome what became of the attempt, anything but {@code OK}
     * @return the refusal to answer with
     */
    static Refusal addressSetupRefusal(AuthService.AddressOutcome outcome) {
        return switch (outcome) {
            case TOKEN_INVALID -> MemberRefusal.ADDRESS_SETUP_LINK_UNKNOWN;
            case TOKEN_EXPIRED -> MemberRefusal.ADDRESS_SETUP_LINK_EXPIRED;
            case ADDRESS_MALFORMED -> MemberRefusal.ADDRESS_MALFORMED;
            case ADDRESS_UNREACHABLE -> MemberRefusal.ADDRESS_UNREACHABLE;
            case ADDRESS_TAKEN -> MemberRefusal.ADDRESS_TAKEN_ON_SETUP;
            case OK -> throw new IllegalArgumentException("An address that was set needs no refusal");
        };
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
        RateLimits.enforce(MemberRefusal.PASSWORD_LINK_CHECKED_TOO_OFTEN, rateLimiter.trySetPassword(ctx.ip()));
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
            throw MemberRefusal.FORGOTTEN_PASSWORD_ADDRESS_MISSING.raise();
        }
        RateLimits.enforce(
                MemberRefusal.PASSWORD_RESET_TOO_OFTEN, rateLimiter.tryForgotPassword(ctx.ip(), request.email()));

        authService.requestPasswordReset(request.email());
        ctx.status(HttpStatus.OK).json(new MessageResponse("If the email exists, a password reset link has been sent"));
    }

    @OpenApi(
            path = "/api/v1/auth/login",
            methods = HttpMethod.POST,
            summary = "Log in",
            description =
                    "Authenticates with an email address or a username, and a password. Sets the session cookie, or returns the token of the step still owed: a password change, an address or a second factor.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LoginRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void login(Context ctx) {
        var request = ctx.bodyAsClass(LoginRequest.class);
        if (isBlank(request.identifier()) || isBlank(request.password())) {
            throw MemberRefusal.SIGN_IN_DETAILS_MISSING.raise();
        }
        RateLimits.enforce(MemberRefusal.SIGN_IN_TOO_OFTEN, rateLimiter.tryLogin(ctx.ip(), request.identifier()));

        var result = authService.login(
                request.identifier(),
                request.password(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"),
                ctx.cookie("ember_2fa_trust"),
                request.trustedDevice());
        if (!result.success()) {
            throw MemberRefusal.SIGN_IN_REFUSED.raise();
        }

        answerSignIn(ctx, result);
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
            throw MemberRefusal.DEMO_SIGN_IN_ADDRESS_MISSING.raise();
        }
        var result = authService.loginAsDemo(request.email(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (!result.success()) {
            throw MemberRefusal.DEMO_SIGN_IN_REFUSED.raise();
        }
        answerSignIn(ctx, result);
    }

    @OpenApi(
            path = "/api/v1/auth/logout",
            methods = HttpMethod.POST,
            summary = "Log out",
            description =
                    "Ends the session the session cookie names and clears the cookie. Answers the same whether or not there was a session to end.",
            tags = {"Auth"},
            responses = {@OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class))})
    private void logout(Context ctx) {
        SessionCookies.token(ctx).ifPresent(authService::logout);
        sessionCookies.clear(ctx);
        ctx.status(HttpStatus.OK).json(new MessageResponse("Logged out"));
    }

    /**
     * Rotates the password and, with it, the token of the session asking, so the browser keeps its
     * sign-in on a token nobody could have read before the change.
     */
    @OpenApi(
            path = "/api/v1/auth/change-password",
            methods = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ChangePasswordRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = StepUpChallenge.class))
            })
    private void changePassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        RateLimits.enforce(MemberRefusal.PASSWORD_CHANGE_TOO_OFTEN, rateLimiter.tryChangePassword(session.accountId()));
        var request = ctx.bodyAsClass(ChangePasswordRequest.class);
        if (isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw MemberRefusal.PASSWORD_CHANGE_DETAILS_MISSING.raise();
        }
        String currentSessionToken = SessionCookies.token(ctx).orElse(null);
        var outcome = authService.changePassword(
                session.accountId(), currentSessionToken, request.currentPassword(), request.newPassword());
        switch (outcome) {
            case OK -> {
                sessionCookies.issue(
                        ctx,
                        authService.rotateSession(currentSessionToken, ctx.userAgent(), ctx.header("CF-IPCountry")));
                ctx.json(new MessageResponse("Password changed"));
            }
            case NEW_PASSWORD_TOO_SHORT -> throw MemberRefusal.CHANGED_PASSWORD_TOO_SHORT.raise();
            case NEW_PASSWORD_BREACHED -> throw MemberRefusal.CHANGED_PASSWORD_BREACHED.raise();
            case NO_PASSWORD_SET -> throw MemberRefusal.ACCOUNT_HAS_NO_PASSWORD.raise();
            case CURRENT_PASSWORD_WRONG -> throw MemberRefusal.CURRENT_PASSWORD_WRONG.raise();
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
        RateLimits.enforce(MemberRefusal.EMAIL_CHANGE_CONFIRMED_TOO_OFTEN, rateLimiter.tryConfirmEmailChange(ctx.ip()));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) throw MemberRefusal.EMAIL_CHANGE_TOKEN_MISSING.raise();
        var result = authService.confirmEmailChange(request.token());
        switch (result) {
            case COMMITTED -> ctx.json(new EmailChangeResponse(EmailChangeStatus.COMMITTED, "Email address updated"));
            case WAITING ->
                ctx.json(
                        new EmailChangeResponse(
                                EmailChangeStatus.WAITING,
                                "Confirmation received. Waiting for the other address to confirm before the change takes effect."));
            case DUPLICATE -> throw MemberRefusal.EMAIL_CHANGE_ADDRESS_TAKEN.raise();
            case INVALID -> throw MemberRefusal.EMAIL_CHANGE_LINK_NOT_GOOD.raise();
            default -> throw new IllegalStateException("Unhandled email-change outcome: " + result);
        }
    }

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
     * Request body containing a one-time token for verification or password set.
     */
    public record TokenRequest(String token) {}

    /**
     * Outcome of confirming one half of an email change.
     *
     * @param status COMMITTED once both addresses have confirmed, WAITING while the other one
     *               still has to. The page tells the two apart by this rather than by the wording.
     * @param message what to say about it
     */
    public record EmailChangeResponse(EmailChangeStatus status, String message) {}

    /** Where an email change stands once one of its two addresses has confirmed. */
    public enum EmailChangeStatus {
        /** Both addresses confirmed and the account carries the new one. */
        COMMITTED,
        /** This address confirmed; the other one still has to. */
        WAITING
    }

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
     * Response body for login: a finished sign-in, or the one step still standing in the way of one.
     *
     * <p>A finished sign-in travels in the session cookie, never in the body, so {@code expiresAt}
     * says only how long the session lasts.
     */
    public record LoginResponse(
            @Nullable Instant expiresAt,
            boolean passwordChangeRequired,
            @Nullable String passwordChangeToken,
            @Nullable Instant passwordChangeTokenExpiresAt,
            boolean addressRequired,
            @Nullable String addressToken,
            @Nullable Instant addressTokenExpiresAt,
            boolean twoFactorRequired,
            @Nullable String preAuthToken,
            @Nullable Instant preAuthTokenExpiresAt) {

        /** Neither a session nor a way to one: whoever asked has to sign in by hand. */
        public static LoginResponse none() {
            return new LoginResponse(null, false, null, null, false, null, null, false, null, null);
        }

        public static LoginResponse session(Instant expiresAt) {
            return new LoginResponse(expiresAt, false, null, null, false, null, null, false, null, null);
        }

        public static LoginResponse passwordChange(String token, Instant expiresAt) {
            return new LoginResponse(null, true, token, expiresAt, false, null, null, false, null, null);
        }

        public static LoginResponse address(String token, Instant expiresAt) {
            return new LoginResponse(null, false, null, null, true, token, expiresAt, false, null, null);
        }

        public static LoginResponse twoFactor(String preAuthToken, Instant expiresAt) {
            return new LoginResponse(null, false, null, null, false, null, null, true, preAuthToken, expiresAt);
        }

        /**
         * Whatever the sign-in decided, said the way the API says it: a session, or the one step
         * still standing in the way of one.
         */
        public static LoginResponse of(@Nullable LoginResult login) {
            if (login == null || !login.success()) return none();
            if (login.twoFactorRequired()) {
                return twoFactor(
                        Objects.requireNonNull(login.preAuthToken(), "a factor step carries its token"),
                        Objects.requireNonNull(login.preAuthTokenExpiresAt(), "a factor step carries its expiry"));
            }
            String token = Objects.requireNonNull(login.token(), "every other success carries a token");
            Instant expiresAt = Objects.requireNonNull(login.expiresAt(), "every other success carries an expiry");
            if (login.passwordChangeRequired()) return passwordChange(token, expiresAt);
            if (login.addressRequired()) return address(token, expiresAt);
            return session(expiresAt);
        }
    }
}
