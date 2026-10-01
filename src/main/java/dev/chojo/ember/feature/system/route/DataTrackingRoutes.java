/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.refusal.SystemRefusal;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.system.service.DataTrackingAdminService;
import dev.chojo.ember.feature.system.service.DataTrackingAdminService.Summary;
import dev.chojo.ember.feature.system.service.DataTrackingAdminService.TableUpdate;
import dev.chojo.ember.tracking.DataTracking;
import dev.chojo.ember.tracking.TableEntry;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev-mode-only routes for inspecting and editing {@code data_tracking.json} from a browser.
 *
 * <p>All handlers are registered only when {@link Demo#dev()} is true. In production no endpoints
 * are added to the router at all - there is no runtime opt-in path.
 */
@Singleton
public class DataTrackingRoutes implements Routes {

    private static final Logger log = LoggerFactory.getLogger(DataTrackingRoutes.class);

    private final Demo demoConfig;
    private final DataTrackingAdminService service;

    @Inject
    public DataTrackingRoutes(Demo demoConfig, DataTrackingAdminService service) {
        this.demoConfig = demoConfig;
        this.service = service;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        if (!demoConfig.dev()) return;
        log.info("Dev mode: registering /admin/data-tracking inspector endpoints");
        routes.get(prefix + "/admin/data-tracking", this::getTracking, InstancePermission.ADMINISTRATOR);
        routes.get(prefix + "/admin/data-tracking/summary", this::getSummary, InstancePermission.ADMINISTRATOR);
        routes.put(prefix + "/admin/data-tracking/tables/{table}", this::updateTable, InstancePermission.ADMINISTRATOR);
        routes.post(
                prefix + "/admin/data-tracking/tables/{table}/verify-columns",
                this::verifyColumns,
                InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/data-tracking",
            methods = HttpMethod.GET,
            summary = "The data tracking file as it stands",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = DataTracking.class)))
    private void getTracking(Context ctx) throws Exception {
        ctx.json(service.load());
    }

    @OpenApi(
            path = "/api/v1/admin/data-tracking/summary",
            methods = HttpMethod.GET,
            summary = "Counts of tracked, ignored and unverified tables",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = Summary.class)))
    private void getSummary(Context ctx) throws Exception {
        ctx.json(service.summarize());
    }

    @OpenApi(
            path = "/api/v1/admin/data-tracking/tables/{table}",
            methods = HttpMethod.PUT,
            summary = "Update the tracking of one table",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "table", type = String.class, required = true),
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = TableUpdate.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TableEntry.class)))
    private void updateTable(Context ctx) throws Exception {
        String table = ctx.pathParam("table");
        var payload = ctx.bodyAsClass(DataTrackingAdminService.TableUpdate.class);
        try {
            var updated = service.updateTable(table, payload);
            ctx.status(HttpStatus.OK).json(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Data tracking table {} could not be written", table, e);
            throw SystemRefusal.TRACKED_TABLE_NOT_HERE.raise();
        }
    }

    @OpenApi(
            path = "/api/v1/admin/data-tracking/tables/{table}/verify-columns",
            methods = HttpMethod.POST,
            summary = "Mark every column of one table as verified",
            tags = {"Admin"},
            pathParams = @OpenApiParam(name = "table", type = String.class, required = true),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TableEntry.class)))
    private void verifyColumns(Context ctx) throws Exception {
        String table = ctx.pathParam("table");
        try {
            var updated = service.verifyAllColumns(table);
            ctx.status(HttpStatus.OK).json(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Data tracking columns of table {} could not be checked", table, e);
            throw SystemRefusal.TRACKED_TABLE_NOT_HERE_ON_CHECK.raise();
        }
    }
}
