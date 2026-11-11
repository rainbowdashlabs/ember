/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.mail.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.mail.entity.FailedMailsUpTo;
import dev.chojo.ember.feature.mail.entity.StationFailedMails;
import dev.chojo.ember.feature.mail.service.FailedMailService;
import dev.chojo.ember.feature.mail.service.FailedMailService.AcknowledgedMails;
import dev.chojo.ember.feature.mail.service.FailedMailService.FailedMailPage;
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

import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * The instance administrator's list of mails that did not arrive: the instance's own in full, the
 * stations' counted per station, each taken note of so the warning ends, and the instance's own sent
 * again where the queue gave up on them.
 */
@Singleton
public class FailedMailRoutes implements Routes {
    private static final String BASE = "/admin/config/mailing/failed";
    private static final String UP_TO_MISSING =
            "A body without upTo is refused with ML-006; no body at all is refused as a request that cannot be read";

    private final FailedMailService failedMails;

    @Inject
    public FailedMailRoutes(FailedMailService failedMails) {
        this.failedMails = failedMails;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + BASE, this::instanceMails, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + BASE + "/stations", this::stationMails, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + BASE + "/{id}/acknowledge", this::acknowledge, InstancePermission.ADMINISTRATOR);
        routes.post(prefix + BASE + "/acknowledge-all", this::acknowledgeAll, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + BASE + "/stations/{stationUid}/acknowledge",
                this::acknowledgeStation,
                InstancePermission.ADMINISTRATOR);
        routes.post(prefix + BASE + "/{id}/requeue", this::requeue, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/failed",
            methods = HttpMethod.GET,
            summary = "The instance's own mails that did not arrive and nobody has taken note of, a page at a time",
            tags = {"Settings"},
            queryParams = {
                @OpenApiParam(name = "page", type = Integer.class, description = "The page, counted from zero"),
                @OpenApiParam(name = "size", type = Integer.class, description = "How many mails a page holds")
            },
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = FailedMailPage.class)))
    private void instanceMails(Context ctx) {
        int page = ctx.queryParamAsClass("page", Integer.class).getOrDefault(0);
        int size = ctx.queryParamAsClass("size", Integer.class).getOrDefault(FailedMailService.DEFAULT_SIZE);
        ctx.json(failedMails.instanceMails(page, size));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/failed/stations",
            methods = HttpMethod.GET,
            summary = "The stations with mails that did not arrive, counted per station without naming any mail",
            tags = {"Settings"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = StationFailedMails[].class)))
    private void stationMails(Context ctx) {
        ctx.json(failedMails.stationMails());
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/failed/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Take note of one of the instance's own mails that did not arrive",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledge(Context ctx) {
        failedMails.acknowledge(pathInt(ctx, "id"), UserSession.from(ctx).accountId());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/failed/acknowledge-all",
            methods = HttpMethod.POST,
            summary = "Take note of the instance's own mails that did not arrive, up to the latest failure seen",
            tags = {"Settings"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FailedMailsUpTo.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AcknowledgedMails.class)),
                @OpenApiResponse(status = "400", description = UP_TO_MISSING)
            })
    private void acknowledgeAll(Context ctx) {
        var request = ctx.bodyAsClass(FailedMailsUpTo.class);
        ctx.json(
                failedMails.acknowledgeAll(request.upTo(), UserSession.from(ctx).accountId()));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/failed/stations/{stationUid}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Take note of one station's mails that did not arrive, up to the latest failure seen",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "stationUid", type = UUID.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = FailedMailsUpTo.class)),
            responses = {
                @OpenApiResponse(status = "200", content = @OpenApiContent(from = AcknowledgedMails.class)),
                @OpenApiResponse(status = "400", description = UP_TO_MISSING)
            })
    private void acknowledgeStation(Context ctx) {
        var request = ctx.bodyAsClass(FailedMailsUpTo.class);
        ctx.json(failedMails.acknowledgeStation(
                pathUuid(ctx, "stationUid"),
                request.upTo(),
                UserSession.from(ctx).accountId()));
    }

    @OpenApi(
            path = "/api/v1/admin/config/mailing/failed/{id}/requeue",
            methods = HttpMethod.POST,
            summary = "Send one of the instance's own mails the queue gave up on again, from the first provider",
            tags = {"Settings"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void requeue(Context ctx) {
        failedMails.requeue(pathInt(ctx, "id"));
        ctx.status(HttpStatus.NO_CONTENT);
    }
}
