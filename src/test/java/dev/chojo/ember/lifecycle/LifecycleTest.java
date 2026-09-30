/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.lifecycle;

import dev.chojo.ember.MovableClock;
import dev.chojo.ember.api.ApiServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

class LifecycleTest {
    private final List<String> stages = new ArrayList<>();
    private final MovableClock clock = new MovableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final ApiServer apiServer = mock(ApiServer.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final DataSource dataSource = mock(DataSource.class, withSettings().extraInterfaces(AutoCloseable.class));

    @Test
    void stagesRunInOrderWithTheLogWriterLast() throws Exception {
        record(apiServer, "http");
        doAnswer(invocation -> stages.add("tasks")).when(scheduler).stop(any());
        doAnswer(invocation -> stages.add("pool"))
                .when((AutoCloseable) dataSource)
                .close();

        lifecycle(flush("log", Integer.MAX_VALUE), flush("hits", 0), flush("traffic", 0))
                .shutdown();

        assertEquals(List.of("http", "tasks", "flush hits", "flush traffic", "flush log", "pool"), stages);
    }

    @Test
    void tasksGetWhatIsLeftOfTheBudgetMinusTheReserve() {
        doAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(8));
                    return null;
                })
                .when(apiServer)
                .stop();
        var grace = new Duration[1];
        doAnswer(invocation -> grace[0] = invocation.getArgument(0))
                .when(scheduler)
                .stop(any());

        lifecycle().shutdown();

        assertEquals(Lifecycle.DRAIN_BUDGET.minusSeconds(8).minus(Lifecycle.FLUSH_RESERVE), grace[0]);
    }

    @Test
    void tasksGetNoGraceWhenTheHttpStopUsedTheBudget() {
        doAnswer(invocation -> {
                    clock.advance(Duration.ofSeconds(40));
                    return null;
                })
                .when(apiServer)
                .stop();
        var grace = new Duration[1];
        doAnswer(invocation -> grace[0] = invocation.getArgument(0))
                .when(scheduler)
                .stop(any());

        lifecycle().shutdown();

        assertEquals(Duration.ZERO, grace[0]);
    }

    @Test
    void failingStageDoesNotStopTheRest() throws Exception {
        doThrow(new IllegalStateException("expected by the test"))
                .when(apiServer)
                .stop();
        doAnswer(invocation -> stages.add("pool"))
                .when((AutoCloseable) dataSource)
                .close();

        lifecycle(new ShutdownFlush() {
                    @Override
                    public String name() {
                        return "broken";
                    }

                    @Override
                    public void flushAll() {
                        throw new IllegalStateException("expected by the test");
                    }
                })
                .shutdown();

        assertEquals(List.of("pool"), stages);
    }

    @Test
    void poolThatCannotCloseIsLeftAlone() {
        new Lifecycle(apiServer, scheduler, Set.of(), mock(DataSource.class), clock).shutdown();
    }

    private void record(ApiServer server, String stage) {
        doAnswer(invocation -> stages.add(stage)).when(server).stop();
    }

    private Lifecycle lifecycle(ShutdownFlush... flushes) {
        return new Lifecycle(apiServer, scheduler, Set.of(flushes), dataSource, clock);
    }

    private ShutdownFlush flush(String name, int order) {
        return new ShutdownFlush() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void flushAll() {
                stages.add("flush " + name);
            }

            @Override
            public int order() {
                return order;
            }
        };
    }
}
