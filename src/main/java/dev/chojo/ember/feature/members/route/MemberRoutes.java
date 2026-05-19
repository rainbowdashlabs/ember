/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class MemberRoutes implements Routes {
    private final AuthService authService;
    private final AccountRepository accountRepository;

    @Inject
    public MemberRoutes(AuthService authService, AccountRepository accountRepository) {
        this.authService = authService;
        this.accountRepository = accountRepository;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/members/invite", this::invite, Roles.MEMBER_MANAGEMENT);
        routes.put(prefix + "/members/{accountId}", this::updateAccount, Roles.MEMBER_MANAGEMENT);
        routes.post(prefix + "/members/reset-password", this::resetPassword, Roles.MEMBER_MANAGEMENT);
    }

    @OpenApi(
            path = "/api/v1/members/{accountId}",
            methods = HttpMethod.PUT,
            summary = "Update account name and email",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateAccountRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateAccount(Context ctx) {
        int accountId = ctx.pathParamAsClass("accountId", Integer.class).get();
        var request = ctx.bodyAsClass(UpdateAccountRequest.class);
        if (!accountRepository.update(accountId, request.email(), request.firstName(), request.lastName())) {
            throw new NotFoundResponse();
        }
        ctx.json(new MessageResponse("Account updated"));
    }

    @OpenApi(
            path = "/api/v1/members/invite",
            methods = HttpMethod.POST,
            summary = "Invite a new user to a station",
            description =
                    "Creates a pre-verified account associated with the station from X-Station-Id and sends a password setup email. Requires MEMBER_MANAGEMENT role.",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InviteRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = InviteResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void invite(Context ctx) {
        var request = ctx.bodyAsClass(InviteRequest.class);
        if (isBlank(request.email()) || isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw new BadRequestResponse("email, firstName, and lastName are required");
        }

        UserSession session = UserSession.from(ctx);
        var result = authService.createInvitedAccount(
                request.email(), request.firstName(), request.lastName(), session.stationId());
        if (!result.success()) {
            throw new ConflictResponse(result.message());
        }

        ctx.status(HttpStatus.CREATED)
                .json(new InviteResponse(
                        result.account().id(),
                        result.account().email(),
                        result.account().firstName(),
                        result.account().lastName()));
    }

    @OpenApi(
            path = "/api/v1/members/reset-password",
            methods = HttpMethod.POST,
            summary = "Reset a member's password",
            description =
                    "Sends a password reset email to the member. Optionally forces them to change password on next login. Requires MEMBER_MANAGEMENT role.",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ResetPasswordRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resetPassword(Context ctx) {
        var request = ctx.bodyAsClass(ResetPasswordRequest.class);
        if (request.accountId() == null) {
            throw new BadRequestResponse("accountId is required");
        }

        boolean forceChange = request.forceChange() != null && request.forceChange();
        if (authService.adminResetPassword(request.accountId(), forceChange)) {
            ctx.status(HttpStatus.OK).json(new MessageResponse("Password reset email sent"));
        } else {
            throw new NotFoundResponse("Account not found");
        }
    }

    // -- Request/Response records --

    public record InviteRequest(String email, String firstName, String lastName) {}

    public record ResetPasswordRequest(Integer accountId, Boolean forceChange) {}

    public record InviteResponse(int id, String email, String firstName, String lastName) {}

    public record UpdateAccountRequest(String email, String firstName, String lastName) {}
}
