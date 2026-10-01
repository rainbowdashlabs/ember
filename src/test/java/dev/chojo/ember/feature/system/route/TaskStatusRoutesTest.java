/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.lifecycle.Schedule;
import dev.chojo.ember.lifecycle.TaskOutcome;
import dev.chojo.ember.lifecycle.TaskScheduler;
import dev.chojo.ember.lifecycle.TaskStatus;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.javalin.router.JavalinDefaultRoutingApi;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The admin task page reads the scheduler's statuses as they are, and only an administrator may.
 */
class TaskStatusRoutesTest {

    @Test
    void theStatusesAreAnsweredAsTheSchedulerKeepsThem() throws Exception {
        var scheduler = mock(TaskScheduler.class);
        var statuses = List.of(new TaskStatus(
                "email-queue",
                Schedule.ScheduleMode.FIXED_DELAY,
                10,
                TaskOutcome.SUCCEEDED,
                Instant.parse("2026-05-01T08:00:00Z"),
                12L,
                3,
                0,
                null,
                null));
        when(scheduler.statuses()).thenReturn(statuses);
        var routes = new TaskStatusRoutes(scheduler);
        var handler = TaskStatusRoutes.class.getDeclaredMethod("listTasks", Context.class);
        handler.setAccessible(true);
        var ctx = mock(Context.class);

        handler.invoke(routes, ctx);

        verify(ctx).json(statuses);
    }

    @Test
    void onlyAnAdministratorMayAsk() {
        var routing = mock(JavalinDefaultRoutingApi.class);

        new TaskStatusRoutes(mock(TaskScheduler.class)).register(routing, "/api/v1");

        verify(routing).get(eq("/api/v1/admin/tasks"), any(Handler.class), eq(InstancePermission.ADMINISTRATOR));
    }
}
