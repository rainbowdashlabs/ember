/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskStatus;
import io.javalin.http.Context;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * The state of the instance's background tasks, for the admin monitoring page: when each sweep last
 * ran, how long it took and whether it failed. Read from memory, so it covers the time since the last
 * start.
 */
@Singleton
public class TaskStatusRoutes implements Routes {
    private final TaskScheduler scheduler;

    @Inject
    public TaskStatusRoutes(TaskScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/admin/tasks", this::listTasks, InstancePermission.ADMINISTRATOR);
    }

    @OpenApi(
            path = "/api/v1/admin/tasks",
            methods = HttpMethod.GET,
            summary = "List the scheduled background tasks with their last run, duration and last failure",
            tags = {"Admin"},
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = TaskStatus[].class)))
    private void listTasks(Context ctx) {
        ctx.json(scheduler.statuses());
    }
}
