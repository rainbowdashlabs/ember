/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.members.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.BatchResult;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.GuardianRequest;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.InviteRequest;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Routes for inviting station members. Inviting provisions the account and membership
 * immediately; the recipient receives a password-setup email to claim the account. Gated by
 * {@link StationPermission#MEMBER_EDIT}.
 */
@Singleton
public class StationMemberInviteRoutes implements Routes {

    private final StationMemberInviteService service;

    @Inject
    public StationMemberInviteRoutes(StationMemberInviteService service) {
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/station-members/invites", this::createInvites, StationPermission.MEMBER_EDIT);
    }

    @OpenApi(
            path = "/api/v1/station-members/invites",
            methods = HttpMethod.POST,
            summary = "Invite station members, creating account and membership immediately",
            description = "Accepts a list of invites. Each entry may carry a nested guardians list; "
                    + "guardians are provisioned as GUARDIAN members and linked as manager of the "
                    + "member they belong to. Every recipient receives a password-setup email to "
                    + "claim the created account.",
            tags = {"Station Member Invites"},
            responses = @OpenApiResponse(status = "201", content = @OpenApiContent(from = CreateInvitesResponse.class)))
    private void createInvites(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateInvitesRequest.class);
        if (request.invites() == null || request.invites().isEmpty()) {
            throw new BadRequestResponse("invites is required");
        }
        var serviceRequests = request.invites().stream()
                .map(StationMemberInviteRoutes::toServiceRequest)
                .toList();
        BatchResult result =
                service.createBatch(session.stationId(), serviceRequests, SetupMail.of(request.sendSetupMail()));
        ctx.status(HttpStatus.CREATED)
                .json(new CreateInvitesResponse(
                        result.provisioned().stream()
                                .map(ProvisionedMemberResponse::from)
                                .toList(),
                        result.failed().stream()
                                .map(f -> new FailedInviteResponse(f.email(), f.reason()))
                                .toList()));
    }

    private static InviteRequest toServiceRequest(InviteEntry entry) {
        StationUserType type;
        try {
            type = entry.userType() != null ? StationUserType.valueOf(entry.userType()) : StationUserType.MEMBER;
        } catch (IllegalArgumentException e) {
            throw new BadRequestResponse("Unknown userType: " + entry.userType());
        }
        if (entry.email() == null || entry.firstName() == null || entry.lastName() == null) {
            throw new BadRequestResponse("email, firstName and lastName are required on every invite");
        }
        List<GuardianRequest> guardians = entry.guardians() == null
                ? List.of()
                : entry.guardians().stream()
                        .map(g -> {
                            if (g.email() == null || g.firstName() == null || g.lastName() == null) {
                                throw new BadRequestResponse(
                                        "email, firstName and lastName are required on every guardian");
                            }
                            return new GuardianRequest(g.email(), g.firstName(), g.lastName());
                        })
                        .toList();
        return new InviteRequest(entry.email(), entry.firstName(), entry.lastName(), type, entry.groupId(), guardians);
    }

    /**
     * Request body for {@link #createInvites(Context)}.
     *
     * @param sendSetupMail whether the setup mails leave with the accounts. Absent means they do,
     *                      which is what the batch has always done.
     */
    public record CreateInvitesRequest(List<InviteEntry> invites, Boolean sendSetupMail) {}

    /** One row in the create-invites request. */
    public record InviteEntry(
            String email,
            String firstName,
            String lastName,
            String userType,
            Integer groupId,
            List<GuardianEntry> guardians) {}

    /** Guardian sub-row inside an {@link InviteEntry}. */
    public record GuardianEntry(String email, String firstName, String lastName) {}

    /** Response body for {@link #createInvites(Context)}. */
    public record CreateInvitesResponse(
            List<ProvisionedMemberResponse> provisioned, List<FailedInviteResponse> failed) {}

    /** One provisioned member in the create-invites response. */
    public record ProvisionedMemberResponse(
            int memberId,
            int accountId,
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            boolean accountCreated,
            boolean membershipCreated) {
        public static ProvisionedMemberResponse from(StationMemberInviteService.ProvisionedMember member) {
            return new ProvisionedMemberResponse(
                    member.memberId(),
                    member.accountId(),
                    member.email(),
                    member.firstName(),
                    member.lastName(),
                    member.userType(),
                    member.accountCreated(),
                    member.membershipCreated());
        }
    }

    /** One failed entry in the create-invites response. */
    public record FailedInviteResponse(String email, String reason) {}
}
