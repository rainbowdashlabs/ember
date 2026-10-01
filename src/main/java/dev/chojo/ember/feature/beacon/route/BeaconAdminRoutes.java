/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.beacon.entity.BeaconFault;
import dev.chojo.ember.feature.beacon.entity.BeaconMetricsRow;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads.MetricsBatch;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads.ProblemPayload;
import dev.chojo.ember.feature.beacon.entity.BeaconPayloads.ReportPayload;
import dev.chojo.ember.feature.beacon.entity.BeaconReport;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.BeaconSettingsRequest;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.BeaconStatus;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SendReportRequest;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SendResult;
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

import java.util.List;

/**
 * What an operator does with their own beacon: look at what would be sent, and send it, and go
 * through what other instances sent this one.
 */
@Singleton
public class BeaconAdminRoutes implements Routes {

    private final BeaconAdminService beacon;

    @Inject
    public BeaconAdminRoutes(BeaconAdminService beacon) {
        this.beacon = beacon;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        String base = prefix + "/admin/beacon";
        routes.get(base, this::status, InstancePermission.ADMINISTRATOR);
        routes.put(base, this::updateSettings, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/problems/{id}/preview", this::previewProblem, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/problems/{id}/send", this::sendProblem, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/problems/send", this::sendProblems, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/reports/{id}/preview", this::previewReport, InstancePermission.ADMINISTRATOR);
        routes.post(base + "/reports/{id}/send", this::sendReport, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/figures/preview", this::previewMetrics, InstancePermission.ADMINISTRATOR);

        routes.get(base + "/collected/faults", this::faults, InstancePermission.ADMINISTRATOR);
        routes.put(base + "/collected/faults/{id}", this::resolveFault, InstancePermission.ADMINISTRATOR);
        routes.get(base + "/collected/reports", this::collectedReports, InstancePermission.ADMINISTRATOR);
        routes.post(
                base + "/collected/reports/{id}/acknowledge",
                this::acknowledgeReport,
                InstancePermission.ADMINISTRATOR);
        routes.get(
                base + "/collected/reports/{id}/screenshot",
                this::collectedReportScreenshot,
                InstancePermission.ADMINISTRATOR);
        routes.get(base + "/collected/figures", this::collectedMetrics, InstancePermission.ADMINISTRATOR);
    }

    private static boolean includeAcknowledged(Context ctx) {
        return Boolean.parseBoolean(ctx.queryParam("includeAcknowledged"));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/collected/faults",
            methods = HttpMethod.GET,
            summary = "The faults this beacon gathered, most widely met first",
            tags = {"Beacon"},
            queryParams = @OpenApiParam(name = "includeAcknowledged", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconFault[].class)))
    private void faults(Context ctx) {
        ctx.json(beacon.faults(includeAcknowledged(ctx)));
    }

    /** Marking a fault as seen, or naming the version that put it right. */
    @OpenApi(
            path = "/api/v1/admin/beacon/collected/faults/{id}",
            methods = HttpMethod.PUT,
            summary = "Mark a gathered fault as seen or name the version that fixed it",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = ResolveRequest.class)),
            responses = @OpenApiResponse(status = "204"))
    private void resolveFault(Context ctx) {
        var request = ctx.bodyAsClass(ResolveRequest.class);
        beacon.resolveFault(pathId(ctx), request.acknowledged(), request.resolvedIn());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/collected/reports",
            methods = HttpMethod.GET,
            summary = "The problem reports forwarded to this beacon, newest first",
            tags = {"Beacon"},
            queryParams = @OpenApiParam(name = "includeAcknowledged", type = Boolean.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconReport[].class)))
    private void collectedReports(Context ctx) {
        ctx.json(beacon.collectedReports(includeAcknowledged(ctx)));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/collected/reports/{id}/acknowledge",
            methods = HttpMethod.POST,
            summary = "Mark a forwarded report as seen",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "204"))
    private void acknowledgeReport(Context ctx) {
        beacon.acknowledgeReport(pathId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/collected/reports/{id}/screenshot",
            methods = HttpMethod.GET,
            summary = "The picture a forwarded report came with",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(type = "image/*")))
    private void collectedReportScreenshot(Context ctx) {
        var picture = beacon.collectedPicture(pathId(ctx));
        ctx.contentType(picture.contentType()).result(picture.data());
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/collected/figures",
            methods = HttpMethod.GET,
            summary = "The bucketed figures this beacon collected over the last days",
            tags = {"Beacon"},
            queryParams = @OpenApiParam(name = "days", type = Integer.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconMetricsRow[].class)))
    private void collectedMetrics(Context ctx) {
        ctx.json(beacon.collectedMetrics(
                ctx.queryParamAsClass("days", Integer.class).getOrDefault(30)));
    }

    private static int pathId(Context ctx) {
        try {
            return Integer.parseInt(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw Refusal.BEACON_ID_NOT_A_NUMBER.raise();
        }
    }

    private static long problemId(Context ctx) {
        try {
            return Long.parseLong(ctx.pathParam("id"));
        } catch (NumberFormatException e) {
            throw Refusal.BEACON_PROBLEM_ID_NOT_A_NUMBER.raise();
        }
    }

    /** Whether a fault has been seen, and which version put it right. */
    public record ResolveRequest(
            boolean acknowledged, @Nullable String resolvedIn) {}

    @OpenApi(
            path = "/api/v1/admin/beacon",
            methods = HttpMethod.GET,
            summary = "What this instance sends to a beacon, and whether it is one",
            tags = {"Beacon"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconStatus.class)))
    private void status(Context ctx) {
        ctx.json(beacon.status());
    }

    @OpenApi(
            path = "/api/v1/admin/beacon",
            methods = HttpMethod.PUT,
            summary = "Update the beacon switches and the contact",
            tags = {"Beacon"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = BeaconSettingsRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = BeaconStatus.class)))
    private void updateSettings(Context ctx) {
        ctx.json(beacon.update(ctx.bodyAsClass(BeaconSettingsRequest.class)));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/problems/{id}/preview",
            methods = HttpMethod.GET,
            summary = "What one problem would travel as",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ProblemPayload.class)))
    private void previewProblem(Context ctx) {
        ctx.json(beacon.previewProblem(problemId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/problems/{id}/send",
            methods = HttpMethod.POST,
            summary = "Send one problem to the beacon",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Long.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SendResult.class)))
    private void sendProblem(Context ctx) {
        ctx.json(beacon.sendProblem(problemId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/problems/send",
            methods = HttpMethod.POST,
            summary = "Send the chosen problems to the beacon",
            tags = {"Beacon"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SendRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SendResult.class)))
    private void sendProblems(Context ctx) {
        ctx.json(beacon.sendProblems(ctx.bodyAsClass(SendRequest.class).ids()));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/reports/{id}/preview",
            methods = HttpMethod.GET,
            summary = "What one problem report would travel as",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = ReportPayload.class)))
    private void previewReport(Context ctx) {
        ctx.json(beacon.previewReport(pathId(ctx)));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/reports/{id}/send",
            methods = HttpMethod.POST,
            summary = "Pass one problem report on to the beacon",
            tags = {"Beacon"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SendReportRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = SendResult.class)))
    private void sendReport(Context ctx) {
        int id = pathId(ctx);
        var decision = ctx.body().isBlank() ? SendReportRequest.AS_IT_STANDS : ctx.bodyAsClass(SendReportRequest.class);
        ctx.json(beacon.sendReport(id, decision));
    }

    @OpenApi(
            path = "/api/v1/admin/beacon/figures/preview",
            methods = HttpMethod.GET,
            summary = "The day's figures as they would be sent",
            tags = {"Beacon"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MetricsBatch.class)))
    private void previewMetrics(Context ctx) {
        ctx.json(beacon.previewMetrics());
    }

    /** The problems an operator ticked. */
    public record SendRequest(List<Long> ids) {}
}
