/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.feature.beacon.repository.BeaconReadRepository;
import dev.chojo.ember.feature.beacon.service.BeaconSettings;
import dev.chojo.ember.feature.system.repository.ProblemReportRepository;
import dev.chojo.ember.feature.system.service.ProblemLogAppender;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * What is waiting for whoever runs the instance.
 *
 * <p>Everything an operator has to look at otherwise has to be found by opening the page and seeing
 * whether anything is on it. A count in the sidebar is what turns that round: nothing shown means nothing
 * to do, and a number means somebody should look.
 */
@Singleton
public class AdminMonitoringCountRoutes implements Routes {

    private final ProblemReportRepository reportRepository;
    private final BeaconReadRepository beaconRepository;
    private final BeaconSettings beaconSettings;

    @Inject
    public AdminMonitoringCountRoutes(
            ProblemReportRepository reportRepository,
            BeaconReadRepository beaconRepository,
            BeaconSettings beaconSettings) {
        this.reportRepository = reportRepository;
        this.beaconRepository = beaconRepository;
        this.beaconSettings = beaconSettings;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/monitoring-counts", this::counts, InstancePermission.ADMINISTRATOR);
    }

    /**
     * The counts, and whether this instance is a beacon at all.
     *
     * <p>The beacon numbers are only asked for where the instance receives, because an instance that does
     * not has no tables worth querying and no sidebar entry to put a number on.
     */
    @OpenApi(
            path = "/api/v1/admin/monitoring-counts",
            methods = HttpMethod.GET,
            summary = "What is waiting for the operator",
            tags = {"Monitoring"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MonitoringCounts.class)))
    private void counts(Context ctx) {
        boolean receiving = beaconSettings.receiving();
        var waiting = receiving ? beaconRepository.countWaiting() : new BeaconReadRepository.Waiting(0, 0);
        var log = ProblemLogAppender.instance();
        ctx.json(new MonitoringCounts(
                log != null ? log.countUnacknowledged() : 0,
                reportRepository.countUnacknowledged(),
                receiving,
                waiting.faults(),
                waiting.reports()));
    }

    /**
     * @param problemLog     entries in the fault log nobody has acknowledged
     * @param problemReports reports from this instance's own members that nobody has looked at
     * @param beaconActive   whether this instance accepts what other instances report
     * @param beaconFaults   faults gathered from other instances, unacknowledged
     * @param beaconReports  forwarded reports from other instances, unacknowledged
     */
    public record MonitoringCounts(
            int problemLog, int problemReports, boolean beaconActive, int beaconFaults, int beaconReports) {}
}
