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
import dev.chojo.ember.feature.members.entity.StationMemberInvite;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.AcceptException;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.GuardianRequest;
import dev.chojo.ember.feature.members.service.StationMemberInviteService.InviteRequest;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.GoneResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.util.List;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Routes for the per-recipient member invite flow added with the station setup wizard. Two
 * surfaces:
 * <ul>
 *   <li>Administrator endpoints under {@code /api/v1/station-members/invites} for creating,
 *       listing, and revoking invites. Gated by {@link StationPermission#MEMBER_EDIT} /
 *       {@link StationPermission#MEMBER_READ}.</li>
 *   <li>Public endpoints under {@code /api/v1/public/station-invite/{token}} for the recipient
 *       to look up their invite and submit a password to accept it.</li>
 * </ul>
 */
@Singleton
public class StationMemberInviteRoutes implements Routes {

    private final StationMemberInviteService service;
    private final StationRepository stationRepository;

    @Inject
    public StationMemberInviteRoutes(StationMemberInviteService service, StationRepository stationRepository) {
        this.service = service;
        this.stationRepository = stationRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.post(prefix + "/station-members/invites", this::createInvites, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/station-members/invites", this::listInvites, StationPermission.MEMBER_READ);
        routes.delete(prefix + "/station-members/invites/{id}", this::deleteInvite, StationPermission.MEMBER_EDIT);
        routes.get(prefix + "/public/station-invite/{token}", this::publicLookup);
        routes.post(prefix + "/public/station-invite/{token}/accept", this::publicAccept);
    }

    @OpenApi(
            path = "/api/v1/station-members/invites",
            methods = HttpMethod.POST,
            summary = "Create one or more station member invites and email each recipient",
            description = "Accepts a list of invites. Each entry may carry a nested guardians list; "
                    + "guardian rows are created as separate GUARDIAN invites linked to the parent.",
            tags = {"Station Member Invites"},
            responses =
                    @OpenApiResponse(
                            status = "201",
                            content = @OpenApiContent(from = StationMemberInviteResponse[].class)))
    private void createInvites(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var request = ctx.bodyAsClass(CreateInvitesRequest.class);
        if (request.invites() == null || request.invites().isEmpty()) {
            throw new BadRequestResponse("invites is required");
        }
        var serviceRequests = request.invites().stream()
                .map(StationMemberInviteRoutes::toServiceRequest)
                .toList();
        if (session.member() == null) throw new ForbiddenResponse("Not a station member");
        var created = service.createBatch(session.stationId(), session.member().id(), serviceRequests);
        ctx.status(HttpStatus.CREATED)
                .json(created.stream().map(StationMemberInviteResponse::from).toList());
    }

    @OpenApi(
            path = "/api/v1/station-members/invites",
            methods = HttpMethod.GET,
            summary = "List station member invites (pending and accepted)",
            tags = {"Station Member Invites"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = StationMemberInviteResponse[].class)))
    private void listInvites(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var invites = service.listForStation(session.stationId()).stream()
                .map(StationMemberInviteResponse::from)
                .toList();
        ctx.json(invites);
    }

    @OpenApi(
            path = "/api/v1/station-members/invites/{id}",
            methods = HttpMethod.DELETE,
            summary = "Revoke a pending station member invite",
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            tags = {"Station Member Invites"},
            responses = {@OpenApiResponse(status = "204"), @OpenApiResponse(status = "404")})
    private void deleteInvite(Context ctx) {
        UserSession session = UserSession.from(ctx);
        int id = pathInt(ctx, "id");
        if (!service.revoke(session.stationId(), id)) {
            throw new NotFoundResponse("Invite not found or already accepted");
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/public/station-invite/{token}",
            methods = HttpMethod.GET,
            summary = "Look up a station member invite by token (public)",
            pathParams = @OpenApiParam(name = "token", required = true),
            tags = {"Station Member Invites"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = PublicStationMemberInviteResponse.class)))
    private void publicLookup(Context ctx) {
        var token = ctx.pathParam("token");
        var invite = service.findByToken(token).orElseThrow(NotFoundResponse::new);
        String stationName = stationRepository
                .findById(invite.stationId())
                .map(Station::name)
                .orElse("");
        ctx.json(PublicStationMemberInviteResponse.from(invite, stationName));
    }

    @OpenApi(
            path = "/api/v1/public/station-invite/{token}/accept",
            methods = HttpMethod.POST,
            summary = "Accept a station member invite, creating the account and station membership",
            pathParams = @OpenApiParam(name = "token", required = true),
            tags = {"Station Member Invites"},
            responses = {
                @OpenApiResponse(status = "200"),
                @OpenApiResponse(status = "400"),
                @OpenApiResponse(status = "404"),
                @OpenApiResponse(status = "410")
            })
    private void publicAccept(Context ctx) {
        var token = ctx.pathParam("token");
        var body = ctx.bodyAsClass(AcceptRequest.class);
        if (body.password() == null || body.password().isBlank()) {
            throw new BadRequestResponse("password is required");
        }
        try {
            var result = service.accept(token, body.password());
            ctx.json(result);
        } catch (AcceptException e) {
            throw switch (e.reason()) {
                case NOT_FOUND -> new NotFoundResponse(e.getMessage());
                case ALREADY_ACCEPTED, EXPIRED -> new GoneResponse(e.getMessage());
                case WEAK_PASSWORD, EMAIL_IN_USE -> new BadRequestResponse(e.getMessage());
            };
        }
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

    /** Request body for {@link #createInvites(Context)}. */
    public record CreateInvitesRequest(List<InviteEntry> invites) {}

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

    /** Response body for {@link #createInvites} and {@link #listInvites}. */
    public record StationMemberInviteResponse(
            int id,
            int stationId,
            String email,
            String firstName,
            String lastName,
            StationUserType userType,
            Integer groupId,
            Integer guardianOfInviteId,
            int invitedByMemberId,
            Instant expiresAt,
            Instant acceptedAt,
            Instant createdAt) {
        public static StationMemberInviteResponse from(StationMemberInvite invite) {
            return new StationMemberInviteResponse(
                    invite.id(),
                    invite.stationId(),
                    invite.email(),
                    invite.firstName(),
                    invite.lastName(),
                    invite.userType(),
                    invite.groupId(),
                    invite.guardianOfInviteId(),
                    invite.invitedByMemberId(),
                    invite.expiresAt(),
                    invite.acceptedAt(),
                    invite.createdAt());
        }
    }

    /** Response body for the public lookup endpoint. */
    public record PublicStationMemberInviteResponse(
            String firstName,
            String lastName,
            String email,
            StationUserType userType,
            String stationName,
            boolean accepted,
            boolean expired) {
        public static PublicStationMemberInviteResponse from(StationMemberInvite invite, String stationName) {
            return new PublicStationMemberInviteResponse(
                    invite.firstName(),
                    invite.lastName(),
                    invite.email(),
                    invite.userType(),
                    stationName,
                    invite.isAccepted(),
                    invite.isExpired());
        }
    }

    /** Request body for the public accept endpoint. */
    public record AcceptRequest(String password) {}
}
