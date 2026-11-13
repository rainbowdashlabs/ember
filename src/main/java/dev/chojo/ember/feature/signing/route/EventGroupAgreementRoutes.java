/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.service.EventGroupService;
import dev.chojo.ember.feature.signing.entity.AgreementSigner;
import dev.chojo.ember.feature.signing.service.EventGroupAgreementSigners;
import dev.chojo.ember.feature.station.entity.StationModule;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static dev.chojo.ember.api.RouteSupport.pathInt;

/**
 * Who signed what an event group asks every part for, for whoever handles its sign-ups.
 */
@Singleton
public class EventGroupAgreementRoutes implements Routes {
    private final EventGroupService eventGroups;
    private final EventGroupAgreementSigners signers;

    @Inject
    public EventGroupAgreementRoutes(EventGroupService eventGroups, EventGroupAgreementSigners signers) {
        this.eventGroups = eventGroups;
        this.signers = signers;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(
                prefix + "/events/groups/{eventGroupId}/agreement-signers",
                this::signers,
                StationPermission.EVENT_REGISTRATION,
                StationModule.EVENTS);
    }

    @OpenApi(
            path = "/api/v1/events/groups/{eventGroupId}/agreement-signers",
            methods = HttpMethod.GET,
            summary = "Who signed the documents an event group asks every part for",
            tags = {"Signing"},
            pathParams = @OpenApiParam(name = "eventGroupId", type = Integer.class, required = true),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AgreementSigner[].class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void signers(Context ctx) {
        StationSession session = StationSession.from(ctx);
        var eventGroup = eventGroups.requireEventGroup(session.stationId(), pathInt(ctx, "eventGroupId"));
        ctx.json(signers.of(eventGroup.id()));
    }
}
