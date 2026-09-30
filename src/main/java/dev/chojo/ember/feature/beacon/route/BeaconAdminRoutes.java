/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.beacon.route;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SendReportRequest;
import dev.chojo.ember.feature.beacon.service.BeaconAdminService.SettingsRequest;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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

    private void faults(Context ctx) {
        ctx.json(beacon.faults(includeAcknowledged(ctx)));
    }

    /** Marking a fault as seen, or naming the version that put it right. */
    private void resolveFault(Context ctx) {
        var request = ctx.bodyAsClass(ResolveRequest.class);
        beacon.resolveFault(pathId(ctx), request.acknowledged(), request.resolvedIn());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void collectedReports(Context ctx) {
        ctx.json(beacon.collectedReports(includeAcknowledged(ctx)));
    }

    private void acknowledgeReport(Context ctx) {
        beacon.acknowledgeReport(pathId(ctx));
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void collectedReportScreenshot(Context ctx) {
        var picture = beacon.collectedPicture(pathId(ctx));
        ctx.contentType(picture.contentType()).result(picture.data());
    }

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
    public record ResolveRequest(boolean acknowledged, String resolvedIn) {}

    private void status(Context ctx) {
        ctx.json(beacon.status());
    }

    private void updateSettings(Context ctx) {
        ctx.json(beacon.update(ctx.bodyAsClass(SettingsRequest.class)));
    }

    private void previewProblem(Context ctx) {
        ctx.json(beacon.previewProblem(problemId(ctx)));
    }

    private void sendProblem(Context ctx) {
        ctx.json(beacon.sendProblem(problemId(ctx)));
    }

    private void sendProblems(Context ctx) {
        ctx.json(beacon.sendProblems(ctx.bodyAsClass(SendRequest.class).ids()));
    }

    private void previewReport(Context ctx) {
        ctx.json(beacon.previewReport(pathId(ctx)));
    }

    private void sendReport(Context ctx) {
        int id = pathId(ctx);
        var decision = ctx.body().isBlank() ? SendReportRequest.AS_IT_STANDS : ctx.bodyAsClass(SendReportRequest.class);
        ctx.json(beacon.sendReport(id, decision));
    }

    private void previewMetrics(Context ctx) {
        ctx.json(beacon.previewMetrics());
    }

    /** The problems an operator ticked. */
    public record SendRequest(List<Long> ids) {}
}
