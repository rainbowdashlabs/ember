/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.route;

import dev.chojo.ember.api.RouteHarness;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository;
import dev.chojo.ember.feature.mail.repository.EmailQueueRepository.QueuedEmail;
import dev.chojo.ember.feature.system.service.DemoService;
import dev.chojo.ember.feature.system.service.DevMailService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static dev.chojo.ember.api.RouteHarness.PREFIX;
import static dev.chojo.ember.api.RouteHarness.body;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The frontend's error log, the reset the end-to-end suite starts from and the queued mail exist on a
 * development instance only.
 */
class DevRoutesTest {
    private final DemoService demoService = mock(DemoService.class);
    private final EmailQueueRepository mailQueue = mock(EmailQueueRepository.class);

    @Test
    void aDevelopmentInstanceTakesErrorReportsAndResets() {
        var harness = RouteHarness.serving(routes(true));

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
        var harness = RouteHarness.serving(routes(false));

        harness.run((server, client) -> {
            assertEquals(404, client.post(PREFIX + "/dev/errors", body("{}")).code());
            assertEquals(404, client.post(PREFIX + "/dev/reset", null).code());
            assertEquals(
                    404,
                    client.get(PREFIX + "/dev/mails/latest?recipient=erika@example.org")
                            .code());
        });

        verify(demoService, never()).resetAndSeed();
    }

    @Test
    void aDevelopmentInstanceShowsTheMailLastQueuedForAnAddress() {
        when(mailQueue.findLatestFor("erika@example.org", null, null))
                .thenReturn(Optional.of(new QueuedEmail(
                        1,
                        "erika@example.org",
                        "Bestätige",
                        "<a href=\"/apply/verify?token=abc\">",
                        null,
                        0,
                        0,
                        Instant.EPOCH)));
        var harness = RouteHarness.serving(routes(true));

        harness.run((server, client) -> {
            var mail = client.get(PREFIX + "/dev/mails/latest?recipient=erika@example.org");
            assertEquals(200, mail.code());
            assertTrue(mail.body().string().contains("/apply/verify?token=abc"));
            assertEquals(
                    404,
                    client.get(PREFIX + "/dev/mails/latest?recipient=nobody@example.org")
                            .code());
        });
    }

    private DevRoutes routes(boolean dev) {
        return new DevRoutes(demo(dev), () -> demoService, () -> new DevMailService(mailQueue));
    }

    private static Demo demo(boolean dev) {
        Demo demo = mock(Demo.class);
        when(demo.dev()).thenReturn(dev);
        return demo;
    }
}
