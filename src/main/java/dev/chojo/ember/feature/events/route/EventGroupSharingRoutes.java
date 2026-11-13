/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.route.EventGroupRoutes.EventGroupDecisionRequest;
import dev.chojo.ember.feature.events.route.EventSharingRoutes.FederationShareResponse;
import dev.chojo.ember.feature.events.route.EventSharingRoutes.SetFederationShareRequest;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Sharing an event group with partner stations, and the sign-ups their members send for it.
 */
@Singleton
public class EventGroupSharingRoutes implements Routes {
    private static final String EVENT_GROUP = "/events/groups/{eventGroupId}";

    private final EventGroupPartnerService partners;

    @Inject
    public EventGroupSharingRoutes(EventGroupPartnerService partners) {
        this.partners = partners;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + EVENT_GROUP + "/federation", this::getShare, StationPermission.EVENTS_FEDERATE);
        routes.put(prefix + EVENT_GROUP + "/federation", this::setShare, StationPermission.EVENTS_FEDERATE);
        routes.delete(prefix + EVENT_GROUP + "/federation", this::removeShare, StationPermission.EVENTS_FEDERATE);
        routes.get(prefix + EVENT_GROUP + "/partner-sign-ups", this::listSignUps, StationPermission.EVENT_REGISTRATION);
        routes.put(
                prefix + EVENT_GROUP + "/partner-sign-ups/{signUpId}/status",
                this::decide,
                StationPermission.EVENT_REGISTRATION);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/federation",
            methods = HttpMethod.GET,
            summary = "How an event group is shared with partner stations",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShareResponse.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void getShare(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(partners.findShare(session.stationId(), pathInt(ctx, "eventGroupId"))
                .map(share -> new FederationShareResponse(true, share.scope(), share.partnerIds()))
                .orElseGet(() -> new FederationShareResponse(false, null, null)));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/federation",
            methods = HttpMethod.PUT,
            summary = "Share an event group and every part with partner stations",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetFederationShareRequest.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShareResponse.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void setShare(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(SetFederationShareRequest.class);
        var targets = Objects.requireNonNullElse(request.partnerIds(), List.<Integer>of());
        partners.share(session.stationId(), pathInt(ctx, "eventGroupId"), request.scope(), targets);
        ctx.json(new FederationShareResponse(true, request.scope(), targets));
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/federation",
            methods = HttpMethod.DELETE,
            summary = "Stop sharing an event group and its parts with partner stations",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void removeShare(Context ctx) {
        StationSession session = StationSession.from(ctx);
        partners.unshare(session.stationId(), pathInt(ctx, "eventGroupId"));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/partner-sign-ups",
            methods = HttpMethod.GET,
            summary = "The sign-ups partner stations' members sent for an event group",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = EventGroupPartnerSignUp[].class)))
    private void listSignUps(Context ctx) {
        StationSession session = StationSession.from(ctx);
        ctx.json(partners.signUps(session.stationId(), pathInt(ctx, "eventGroupId")).stream()
                .map(EventGroupPartnerSignUp::of)
                .toList());
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/partner-sign-ups/{signUpId}/status",
            methods = HttpMethod.PUT,
            summary = "Accept or turn down a partner member's event group sign-up and every part chosen with it",
            tags = {"Events"},
            pathParams = {
                @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
                @OpenApiParam(name = "signUpId", type = Integer.class, required = true)
            },
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = EventGroupDecisionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void decide(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var request = ctx.bodyAsClass(EventGroupDecisionRequest.class);
        partners.decide(session.stationId(), pathInt(ctx, "eventGroupId"), pathInt(ctx, "signUpId"), request.status());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * A partner member's event group sign-up as the event group page lists it.
     *
     * @param id           the sign-up, which decisions name
     * @param member       the member, or null where the partnership is gone
     * @param status       where the sign-up stands
     * @param registeredAt when they signed up
     * @param parts        the parts that count for the rules
     * @param sets         how those parts meet every set
     */
    public record EventGroupPartnerSignUp(
            int id,
            @Nullable MemberIdentity member,
            @Nullable RegistrationStatus status,
            @Nullable Instant registeredAt,
            List<Integer> parts,
            List<SetResult> sets) {
        static EventGroupPartnerSignUp of(EventGroupPartnerService.PartnerSignUp signUp) {
            var standing = signUp.standing();
            return new EventGroupPartnerSignUp(
                    signUp.id(),
                    signUp.member(),
                    standing.status(),
                    standing.registeredAt(),
                    standing.parts(),
                    standing.sets());
        }
    }
}
