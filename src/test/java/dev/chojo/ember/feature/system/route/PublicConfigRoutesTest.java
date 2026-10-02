/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.conf.file.elements.Demo;
import org.junit.jupiter.api.Test;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Anybody learns where the demo is, whether this is one and which version runs, without signing in. */
class PublicConfigRoutesTest {

    @Test
    void anybodyLearnsTheDemoAndTheVersion() {
        Api api = mock(Api.class);
        when(api.demoUrl()).thenReturn("https://demo.example.org");
        var harness = RouteHarness.serving(new PublicConfigRoutes(api, demo(false, true)));

        var config = json(harness.request(client -> client.get(PREFIX + "/public/config")));

        assertEquals("https://demo.example.org", config.path("demoUrl").asString());
        assertTrue(config.path("demo").asBoolean(), "a development instance counts as a demo");
        assertFalse(config.path("version").asString().isBlank());
    }

    @Test
    void anInstanceNamingNoDemoAnswersAnEmptyAddress() {
        var harness = RouteHarness.serving(new PublicConfigRoutes(mock(Api.class), demo(false, false)));

        var config = json(harness.request(client -> client.get(PREFIX + "/public/config")));

        assertEquals("", config.path("demoUrl").asString());
        assertFalse(config.path("demo").asBoolean());
    }

    private static Demo demo(boolean enabled, boolean dev) {
        Demo demo = mock(Demo.class);
        when(demo.enabled()).thenReturn(enabled);
        when(demo.dev()).thenReturn(dev);
        return demo;
    }
}
