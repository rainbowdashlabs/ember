/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.v1;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.service.AuthService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
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

@Singleton
public class AuthRoutes implements Routes {
    private final AuthService authService;

    @Inject
    public AuthRoutes(AuthService authService) {
        this.authService = authService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
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
        routes.post(prefix + "/auth/change-password", this::changePassword, Roles.LOGIN);
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

        var result = authService.login(request.email(), request.password());
        if (!result.success()) {
            throw new UnauthorizedResponse(result.message());
        }

        if (result.passwordChangeRequired()) {
            ctx.status(HttpStatus.OK).json(new LoginResponse(null, null, true, result.token(), result.expiresAt()));
        } else {
            ctx.status(HttpStatus.OK).json(new LoginResponse(result.token(), result.expiresAt(), false, null, null));
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
        var request = ctx.bodyAsClass(TokenRequest.class);
        if (isBlank(request.token())) {
            throw new BadRequestResponse("token is required");
        }

        var result = authService.refreshSession(request.token());
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
        var request = ctx.bodyAsClass(ChangePasswordRequest.class);
        if (isBlank(request.currentPassword()) || isBlank(request.newPassword())) {
            throw new BadRequestResponse("currentPassword and newPassword are required");
        }
        if (!authService.changePassword(session.accountId(), request.currentPassword(), request.newPassword())) {
            throw new BadRequestResponse("Current password is incorrect");
        }
        ctx.json(new MessageResponse("Password changed"));
    }

    // -- Request/Response records --

    public record RegisterRequest(
            String email, String firstName, String lastName, String password, String registrationCode) {}

    public record LoginRequest(String email, String password) {}

    public record TokenRequest(String token) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    public record EmailRequest(String email) {}

    public record SetPasswordRequest(String token, String password) {}

    public record RegisterResponse(int id, String email, String firstName, String lastName, boolean emailVerified) {}

    public record LoginResponse(
            String token,
            Instant expiresAt,
            boolean passwordChangeRequired,
            String passwordChangeToken,
            Instant passwordChangeTokenExpiresAt) {}

    public record SessionResponse(String token, Instant expiresAt) {}
}
