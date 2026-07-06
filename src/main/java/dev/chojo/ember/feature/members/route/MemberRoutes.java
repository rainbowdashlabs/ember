/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ConflictResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for member account management including inviting new members,
 * updating account details, and email change confirmation.
 */
@Singleton
public class MemberRoutes implements Routes {
    private final AuthService authService;
    private final AccountRepository accountRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public MemberRoutes(
            AuthService authService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository) {
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Asserts the given account belongs to a member of the caller's station. Answers 404
     * when the caller has no resolved station or the account is not a member of it, so an
     * account id from another station cannot be updated or reset through these routes.
     */
    private void requireStationAccount(int accountId, UserSession session) {
        Integer stationId = session.stationId();
        if (stationId == null
                || stationMemberRepository
                        .findByStationAndAccount(stationId, accountId)
                        .isEmpty()) {
            throw new NotFoundResponse();
        }
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/members/invite", this::invite, StationPermission.MEMBER_EDIT);
        routes.put(prefix + "/members/{accountId}", this::updateAccount, StationPermission.LOGIN);
        routes.post(prefix + "/members/reset-password", this::resetPassword, StationPermission.MEMBER_EDIT);
    }

    @OpenApi(
            path = "/api/v1/members/{accountId}",
            methods = HttpMethod.PUT,
            summary = "Update account name and email",
            description =
                    "Every signed-in user may update their own account. Updating another account requires the MEMBER_EDIT permission and the target being a member of the caller's station.",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "accountId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = UpdateAccountRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateAccount(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int accountId = pathInt(ctx, "accountId");
        if (session.accountId() != accountId) {
            if (!session.hasPermission(StationPermission.MEMBER_EDIT)) {
                throw new ForbiddenResponse("Updating another account requires the member edit permission");
            }
            requireStationAccount(accountId, session);
        }
        var request = ctx.bodyAsClass(UpdateAccountRequest.class);
        var existing = accountRepository.findById(accountId).orElseThrow(NotFoundResponse::new);

        // If email changed, send confirmation to the new address instead of applying immediately
        boolean emailChanged = request.email() != null
                && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(existing.email());

        // Update name fields immediately
        if (!accountRepository.update(accountId, existing.email(), request.firstName(), request.lastName())) {
            throw new NotFoundResponse();
        }

        if (emailChanged) {
            authService.requestEmailChange(accountId, request.email());
            ctx.json(new MessageResponse("Name updated. A confirmation email has been sent to the new address."));
        } else {
            ctx.json(new MessageResponse("Account updated"));
        }
    }

    @OpenApi(
            path = "/api/v1/members/invite",
            methods = HttpMethod.POST,
            summary = "Invite a new user to a station",
            description =
                    "Creates a pre-verified account associated with the station from X-Station-Id and sends a password setup email. Requires MEMBER_MANAGER role.",
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
                    "Sends a password reset email to the member. Optionally forces them to change password on next login. Requires MEMBER_MANAGER role.",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ResetPasswordRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void resetPassword(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(ResetPasswordRequest.class);
        if (request.accountId() == null) {
            throw new BadRequestResponse("accountId is required");
        }
        requireStationAccount(request.accountId(), session);

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
