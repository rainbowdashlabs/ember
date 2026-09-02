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
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountEmailService;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.LoginNameService;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.ProvisionException;
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
    private final StationMemberInviteService inviteService;
    private final LoginNameService loginNameService;
    private final AccountEmailService accountEmailService;

    @Inject
    public MemberRoutes(
            AuthService authService,
            AccountRepository accountRepository,
            StationMemberRepository stationMemberRepository,
            StationMemberInviteService inviteService,
            LoginNameService loginNameService,
            AccountEmailService accountEmailService) {
        this.authService = authService;
        this.accountRepository = accountRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.inviteService = inviteService;
        this.loginNameService = loginNameService;
        this.accountEmailService = accountEmailService;
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
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = UpdateAccountResponse.class)),
                @OpenApiResponse(status = "403", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void updateAccount(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int accountId = pathInt(ctx, "accountId");
        boolean actsForSomebodyElse = session.accountId() != accountId;
        // Whoever administers the instance reaches any account, and reaches it without being at the
        // same station. The account this exists for is another administrator: one whose address
        // cannot be written to has no way of correcting it, because the confirmation would be sent
        // to the address being corrected, and there is no reason the person who can help them
        // should have to be a member of their station first.
        boolean administersInstance = session.hasInstancePermission(InstancePermission.ADMINISTRATOR);
        if (actsForSomebodyElse && !administersInstance) {
            if (!session.hasPermission(StationPermission.MEMBER_EDIT)) {
                throw new ForbiddenResponse("Updating another account requires the member edit permission");
            }
            requireStationAccount(accountId, session);
        }
        var request = ctx.bodyAsClass(UpdateAccountRequest.class);
        var existing = accountRepository.findById(accountId).orElseThrow(NotFoundResponse::new);

        boolean emailChanged = request.email() != null
                && !request.email().isBlank()
                && !request.email().equalsIgnoreCase(existing.email());

        // Written with the address it already has, so that the two ways of changing one below are the
        // only things that ever move it
        if (!accountRepository.update(accountId, existing.email(), request.firstName(), request.lastName())) {
            throw new NotFoundResponse();
        }

        if (request.username() != null) {
            accountRepository.updateUsername(accountId, loginNameService.validatedFor(existing, request.username()));
        }

        if (!emailChanged) {
            ctx.json(new UpdateAccountResponse("Account updated", null));
            return;
        }

        // Somebody putting their own address right confirms it from both ends, which is what stops a
        // stolen session walking off with the account. An administrator is asked to do this precisely
        // where that cannot work: the address to be corrected is the wrong one, and it is the address
        // half of that confirmation would go to.
        if (actsForSomebodyElse) {
            accountEmailService.setEmailFor(session.accountId(), accountId, request.email());
            ctx.json(new UpdateAccountResponse("Account updated", AuthService.EmailChangeResult.COMMITTED));
            return;
        }
        var outcome = authService.requestEmailChange(accountId, request.email());
        if (outcome == AuthService.EmailChangeResult.DUPLICATE) {
            throw new BadRequestResponse("This email address already belongs to another account");
        }
        ctx.json(new UpdateAccountResponse("Account updated", outcome));
    }

    @OpenApi(
            path = "/api/v1/members/invite",
            methods = HttpMethod.POST,
            summary = "Invite a new user to a station",
            description =
                    "Provisions a pre-verified account and station membership immediately and sends a password setup email. An email that already belongs to an account attaches that account to the station instead.",
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
        try {
            var provisioned = inviteService.provision(
                    session.stationId(),
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    StationUserType.MEMBER,
                    null);
            ctx.status(HttpStatus.CREATED)
                    .json(new InviteResponse(
                            provisioned.accountId(),
                            provisioned.email(),
                            provisioned.firstName(),
                            provisioned.lastName()));
        } catch (ProvisionException e) {
            throw new ConflictResponse(e.getMessage());
        }
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

    /**
     * @param username the name this account signs in with beside its address. Absent leaves the name
     *                 as it is; empty clears it.
     */
    public record UpdateAccountRequest(String email, String username, String firstName, String lastName) {}

    /**
     * The answer to an account update.
     *
     * @param emailChange what became of an address given in the same call: {@code null} when the
     *                    address was left alone, COMMITTED when it is already the account's, and
     *                    WAITING when it becomes so once a link in the reader's mail is clicked
     */
    public record UpdateAccountResponse(String message, AuthService.EmailChangeResult emailChange) {}
}
