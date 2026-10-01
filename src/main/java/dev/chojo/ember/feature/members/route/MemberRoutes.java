/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.account.service.AuthService;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.members.service.MemberAccountService;
import dev.chojo.ember.feature.members.service.MemberAccountService.UpdateAccountRequest;
import dev.chojo.ember.feature.members.service.MemberAccountService.UpdateAccountResponse;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.ProvisionException;
import dev.chojo.ember.feature.passkey.service.PasskeyEnrollmentService;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for member account management including inviting new members,
 * updating account details, and email change confirmation.
 */
@Singleton
public class MemberRoutes implements Routes {
    private final AuthService authService;
    private final MemberAccountService memberAccounts;
    private final StationMemberInviteService inviteService;
    private final PasskeyEnrollmentService enrollmentService;

    @Inject
    public MemberRoutes(
            AuthService authService,
            MemberAccountService memberAccounts,
            StationMemberInviteService inviteService,
            PasskeyEnrollmentService enrollmentService) {
        this.authService = authService;
        this.memberAccounts = memberAccounts;
        this.inviteService = inviteService;
        this.enrollmentService = enrollmentService;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/members/invite", this::invite, StationPermission.MEMBER_EDIT);
        routes.put(prefix + "/members/{accountId}", this::updateAccount, StationPermission.LOGIN);
        routes.post(
                prefix + "/members/reset-password",
                this::resetPassword,
                StationPermission.MEMBER_EDIT,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/members/onboard-again",
                this::onboardAgain,
                StationPermission.MEMBER_EDIT,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.post(
                prefix + "/members/passkey-code",
                this::issuePasskeyCode,
                StationPermission.MEMBER_EDIT,
                StepUpCategory.ACCOUNT_SECURITY);
        routes.delete(
                prefix + "/members/passkey-code/{accountId}", this::revokePasskeyCode, StationPermission.MEMBER_EDIT);
    }

    /**
     * Onboards a member again: every passkey disabled, every session ended, a fresh setup link where
     * mail about the account already goes. Not a new power: whoever may press this can reset a
     * password today.
     */
    @OpenApi(
            path = "/api/v1/members/onboard-again",
            methods = HttpMethod.POST,
            summary = "Onboard a member again",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AccountActionRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = OnboardAgainResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void onboardAgain(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(AccountActionRequest.class);
        if (request.accountId() == null) {
            throw Refusal.ACCOUNT_NOT_NAMED_ON_ONBOARDING_AGAIN.raise();
        }
        memberAccounts.actionableAccount(request.accountId(), session, Refusal.ACCOUNT_NOT_HERE_ON_ONBOARDING_AGAIN);
        boolean mailed = enrollmentService.onboardAgain(
                request.accountId(), session.accountId(), ctx.userAgent(), ctx.header("CF-IPCountry"));
        ctx.json(new OnboardAgainResponse(mailed));
    }

    /**
     * The member manager's passkey code, for an addressless member with no guardian to hand it
     * over. Refused for anybody who has an address of their own: the mail path is right there and is
     * the one with a second party in it.
     */
    @OpenApi(
            path = "/api/v1/members/passkey-code",
            methods = HttpMethod.POST,
            summary = "Issue a passkey code for an addressless member",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = AccountActionRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = PasskeyCodeResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void issuePasskeyCode(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(AccountActionRequest.class);
        if (request.accountId() == null) {
            throw Refusal.ACCOUNT_NOT_NAMED_ON_PASSKEY_CODE.raise();
        }
        var target = memberAccounts.addresslessAccount(request.accountId(), session);
        var issued = enrollmentService.issueCodeWithQr(
                target.id(),
                session.accountId(),
                PasskeyEnrollmentService.QR_TTL,
                ctx.userAgent(),
                ctx.header("CF-IPCountry"));
        ctx.json(new PasskeyCodeResponse(issued.code(), issued.qrPng(), issued.expiresAt()));
    }

    @OpenApi(
            path = "/api/v1/members/passkey-code/{accountId}",
            methods = HttpMethod.DELETE,
            summary = "Revoke the passkey code of a member",
            tags = {"Members"},
            pathParams = @OpenApiParam(name = "accountId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void revokePasskeyCode(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int accountId = pathInt(ctx, "accountId");
        memberAccounts.requireStationAccount(accountId, session.stationId());
        enrollmentService.revokeCode(accountId);
        ctx.json(new MessageResponse("Code revoked"));
    }

    /**
     * Updates an account's name and address. No route-level step-up category: everybody edits their
     * own name here, and that is not sensitive. The one branch that is (moving somebody else's
     * address) asks by hand.
     */
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
        var request = ctx.bodyAsClass(UpdateAccountRequest.class);
        ctx.json(memberAccounts.update(session, session.stationId(), accountId, request));
    }

    @OpenApi(
            path = "/api/v1/members/invite",
            methods = HttpMethod.POST,
            summary = "Invite a new user to a station",
            description =
                    "Provisions a pre-verified account and station membership immediately and sends a password setup email. An email that already belongs to an account attaches that account to the station instead. Leaving the email out creates a member with no address of their own, who is reached through their guardians.",
            tags = {"Members"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = InviteRequest.class)),
            responses = {
                @OpenApiResponse(status = "201", content = @OpenApiContent(from = MemberInviteResponse.class)),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void invite(Context ctx) {
        var request = ctx.bodyAsClass(InviteRequest.class);
        if (isBlank(request.firstName()) || isBlank(request.lastName())) {
            throw Refusal.INVITE_NAME_MISSING.raise();
        }

        UserSession session = UserSession.from(ctx);
        try {
            var provisioned = inviteService.provision(
                    session.stationId(),
                    request.email(),
                    request.firstName(),
                    request.lastName(),
                    StationUserType.MEMBER,
                    null,
                    SetupMail.of(request.sendSetupMail()));
            ctx.status(HttpStatus.CREATED)
                    .json(new MemberInviteResponse(
                            provisioned.accountId(),
                            provisioned.email(),
                            provisioned.firstName(),
                            provisioned.lastName()));
        } catch (ProvisionException ignored) {
            throw Refusal.MEMBER_NOT_PROVISIONED.raise();
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
            throw Refusal.ACCOUNT_NOT_NAMED_ON_PASSWORD_RESET.raise();
        }
        memberAccounts.actionableAccount(request.accountId(), session, Refusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET);
        boolean forceChange = request.forceChange() != null && request.forceChange();
        if (authService.adminResetPassword(request.accountId(), forceChange)) {
            ctx.status(HttpStatus.OK).json(new MessageResponse("Password reset email sent"));
        } else {
            throw Refusal.ACCOUNT_NOT_HERE_ON_PASSWORD_RESET_MAIL.raise();
        }
    }

    /**
     * @param sendSetupMail whether the setup mail leaves with the account. Absent means it does,
     *                      which is what inviting somebody has always done.
     */
    public record InviteRequest(String email, String firstName, String lastName, Boolean sendSetupMail) {}

    public record ResetPasswordRequest(Integer accountId, Boolean forceChange) {}

    /**
     * The account an invitation provisioned.
     *
     * @param email its address, or null for a member reached through their guardians
     */
    public record MemberInviteResponse(int id, @Nullable String email, String firstName, String lastName) {}

    public record AccountActionRequest(Integer accountId) {}

    /**
     * @param mailed whether a setup mail could go out; when not, the QR code in the room is
     *         the way to the member
     */
    public record OnboardAgainResponse(boolean mailed) {}
}
