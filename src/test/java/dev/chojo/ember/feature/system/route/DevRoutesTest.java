/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.system.service.DemoService;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The frontend's error log and the reset the end-to-end suite starts from exist on a development
 * instance only.
 */
class DevRoutesTest {
    private final DemoService demoService = mock(DemoService.class);

    @Test
    void aDevelopmentInstanceTakesErrorReportsAndResets() {
        var harness = RouteHarness.serving(new DevRoutes(demo(true), () -> demoService));

        harness.run((server, client) -> {
            var reported = client.post(
                    PREFIX + "/dev/errors",
                    body("{\"source\": \"window\", \"message\": \"boom\", \"stack\": \"at DevRoutesTest\"}"));
            assertEquals(204, reported.code());
            assertEquals(204, client.post(PREFIX + "/dev/reset", null).code());
        });

        verify(demoService).resetAndSeed();
    }

    @Test
    void anyOtherInstanceHasNeither() {
        var harness = RouteHarness.serving(new DevRoutes(demo(false), () -> demoService));

        harness.run((server, client) -> {
            assertEquals(404, client.post(PREFIX + "/dev/errors", body("{}")).code());
            assertEquals(404, client.post(PREFIX + "/dev/reset", null).code());
        });

        verify(demoService, never()).resetAndSeed();
    }

    private static Demo demo(boolean dev) {
        Demo demo = mock(Demo.class);
        when(demo.dev()).thenReturn(dev);
        return demo;
    }
}
