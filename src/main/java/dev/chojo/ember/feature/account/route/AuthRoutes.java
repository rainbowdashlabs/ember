/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.account.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.conf.file.elements.Network;
import dev.chojo.ember.feature.account.service.AuthRateLimiter;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.util.ClientIp;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.HttpStatus;
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
import java.util.Map;
import java.util.Optional;

/**
 * Routes for authentication operations including registration, login, email verification,
 * password management, and email change confirmation.
 */
@Singleton
public class AuthRoutes implements Routes {
    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final Network network;

    @Inject
    public AuthRoutes(AuthService authService, AuthRateLimiter rateLimiter, Network network) {
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.network = network;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String clientIp(Context ctx) {
        return ClientIp.resolve(ctx, network).getHostAddress();
    }

    private static void enforceLimit(Optional<Long> retryAfter) {
        if (retryAfter.isEmpty()) return;
        long seconds = retryAfter.get();
        throw new HttpResponseException(
                HttpStatus.TOO_MANY_REQUESTS.getCode(),
                "Too many requests, please try again later",
                Map.of("Retry-After", Long.toString(seconds)));
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/auth/register", this::register);
        routes.post(prefix + "/auth/verify-email", this::verifyEmail);
        routes.post(prefix + "/auth/resend-verification", this::resendVerification);
        routes.post(prefix + "/auth/set-password", this::setPassword);
        routes.post(prefix + "/auth/forgot-password", this::forgotPassword);
        routes.post(prefix + "/auth/login", this::login);
        routes.post(prefix + "/auth/refresh", this::refresh);
        routes.post(prefix + "/auth/logout", this::logout);
        routes.post(
                prefix + "/auth/change-password",
                this::changePassword,
                StationPermission.LOGIN,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(prefix + "/auth/confirm-email-change", this::confirmEmailChange);
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
        enforceLimit(rateLimiter.tryRegister(clientIp(ctx)));
        var request = ctx.bodyAsClass(RegisterRequest.class);
        if (isBlank(request.email())
                || isBlank(request.firstName())
                || isBlank(request.lastName())
                || isBlank(request.password())) {
            throw new BadRequestResponse("email, firstName, lastName, and password are required");
        }

        var result = authService.registerSelf(
                request.email(),
                request.firstName(),
                request.lastName(),
                request.password(),
                request.registrationCode());
        if (!result.success()) {
            throw new ConflictResponse(result.message());
        }

        ctx.status(HttpStatus.CREATED)
                .json(new RegisterResponse(
                        result.account().id(),
                        result.account().email(),
                        result.account().firstName(),
                        result.account().lastName(),
                        false));
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
        enforceLimit(rateLimiter.tryVerifyEmail(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw new BadRequestResponse("token is required");
        }

        if (authService.verifyEmail(request.token())) {
            ctx.status(HttpStatus.OK).json(new MessageResponse("Email verified"));
        } else {
            throw new BadRequestResponse("Invalid or expired token");
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
            throw new BadRequestResponse("email is required");
        }
        enforceLimit(rateLimiter.tryResendVerification(clientIp(ctx), request.email()));

        authService.resendVerification(request.email());
        ctx.status(HttpStatus.OK)
                .json(new MessageResponse(
                        "If the email exists and is unverified, a new verification email has been sent"));
    }

    @OpenApi(
            path = "/api/v1/auth/set-password",
            methods = HttpMethod.POST,
            summary = "Set password for invited account",
            description = "Sets the initial password using the token from the invite email.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetPasswordRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setPassword(Context ctx) {
        enforceLimit(rateLimiter.trySetPassword(clientIp(ctx)));
        var request = ctx.bodyAsClass(SetPasswordRequest.class);
        if (isBlank(request.token()) || isBlank(request.password())) {
            throw new BadRequestResponse("token and password are required");
        }

        if (authService.setPassword(request.token(), request.password())) {
            ctx.status(HttpStatus.OK).json(new MessageResponse("Password set successfully"));
        } else {
            throw new BadRequestResponse("Invalid or expired token");
        }
    }

    @OpenApi(
            path = "/api/v1/auth/forgot-password",
            methods = HttpMethod.POST,
            summary = "Request password reset",
            description = "Sends a password reset email. Always returns OK to prevent email enumeration.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EmailRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void forgotPassword(Context ctx) {
        var request = ctx.bodyAsClass(EmailRequest.class);
        if (isBlank(request.email())) {
            throw new BadRequestResponse("email is required");
        }
        enforceLimit(rateLimiter.tryForgotPassword(clientIp(ctx), request.email()));

        authService.requestPasswordReset(request.email());
        ctx.status(HttpStatus.OK).json(new MessageResponse("If the email exists, a password reset link has been sent"));
    }

    @OpenApi(
            path = "/api/v1/auth/login",
            methods = HttpMethod.POST,
            summary = "Log in",
            description =
                    "Authenticates with email and password. Returns a session token, or a password change token if a password change is required.",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = LoginRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = LoginResponse.class)),
                @OpenApiResponse(status = "401", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void login(Context ctx) {
        var request = ctx.bodyAsClass(LoginRequest.class);
        if (isBlank(request.email()) || isBlank(request.password())) {
            throw new BadRequestResponse("email and password are required");
        }
        enforceLimit(rateLimiter.tryLogin(clientIp(ctx), request.email()));

        var result = authService.login(
                request.email(),
                request.password(),
                ctx.userAgent(),
                ctx.header("CF-IPCountry"),
                ctx.cookie("ember_2fa_trust"));
        if (!result.success()) {
            throw new UnauthorizedResponse(result.message());
        }

        if (result.passwordChangeRequired()) {
            ctx.status(HttpStatus.OK)
                    .json(new LoginResponse(null, null, true, result.token(), result.expiresAt(), false, null, null));
        } else if (result.twoFactorRequired()) {
            ctx.status(HttpStatus.OK)
                    .json(new LoginResponse(
                            null,
                            null,
                            false,
                            null,
                            null,
                            true,
                            result.preAuthToken(),
                            result.preAuthTokenExpiresAt()));
        } else {
            ctx.status(HttpStatus.OK)
                    .json(new LoginResponse(result.token(), result.expiresAt(), false, null, null, false, null, null));
        }
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
        enforceLimit(rateLimiter.tryRefresh(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw new BadRequestResponse("token is required");
        }

        var result = authService.refreshSession(request.token(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        if (!result.success()) {
            throw new UnauthorizedResponse(result.message());
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
            throw new BadRequestResponse("token is required");
        }

        authService.logout(request.token());
        ctx.status(HttpStatus.OK).json(new MessageResponse("Logged out"));
    }

    private void changePassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        enforceLimit(rateLimiter.tryChangePassword(session.accountId()));
        var request = ctx.bodyAsClass(ChangePasswordRequest.class);
        if (isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw new BadRequestResponse("currentPassword and newPassword are required");
        }
        String currentSessionToken = extractBearerToken(ctx);
        if (!authService.changePassword(
                session.accountId(), currentSessionToken, request.currentPassword(), request.newPassword())) {
            throw new BadRequestResponse("Current password is incorrect");
        }
        ctx.json(new MessageResponse("Password changed"));
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

    @OpenApi(
            path = "/api/v1/auth/confirm-email-change",
            methods = HttpMethod.POST,
            summary = "Confirm an email change",
            tags = {"Auth"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TokenRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "400")
            })
    private void confirmEmailChange(Context ctx) {
        enforceLimit(rateLimiter.tryConfirmEmailChange(clientIp(ctx)));
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) throw new BadRequestResponse("token is required");
        var result = authService.confirmEmailChange(request.token());
        switch (result) {
            case COMMITTED -> ctx.json(new MessageResponse("Email address updated"));
            case WAITING ->
                ctx.json(
                        new MessageResponse(
                                "Confirmation received. Waiting for the other address to confirm before the change takes effect."));
            case DUPLICATE -> throw new BadRequestResponse("Email already in use");
            case INVALID -> throw new BadRequestResponse("Invalid or expired token");
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
    public record LoginRequest(String email, String password) {}

    /**
     * Request body containing a one-time token for verification, password set, refresh, or logout.
     */
    public record TokenRequest(String token) {}

    /**
     * Request body for changing a password while authenticated.
     */
    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    /**
     * Request body containing only an email address (used for password reset and resend verification).
     */
    public record EmailRequest(String email) {}

    /**
     * Request body for setting a password using an invite or reset token.
     */
    public record SetPasswordRequest(String token, String password) {}

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
            boolean twoFactorRequired,
            String preAuthToken,
            Instant preAuthTokenExpiresAt) {}

    /**
     * Response body for a refreshed session with the new token and expiration.
     */
    public record SessionResponse(String token, Instant expiresAt) {}
}
