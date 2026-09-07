/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import com.sun.net.httpserver.HttpServer;
import dev.chojo.ember.conf.file.elements.Updates;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateCheckServiceTest {

    /**
     * Runs the body against a stub standing in for GitHub, so the fetch itself is exercised without
     * reaching the network.
     */
    private static void withGitHubAnswering(int status, String responseBody, Consumer<UpdateCheckService> assertions)
            throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
        try {
            var service = new UpdateCheckService(
                    new Updates(), "http://127.0.0.1:" + server.getAddress().getPort());
            assertions.accept(service);
        } finally {
            server.stop(0);
        }
    }

    /**
     * The whole path: ask, read the tag, and report it as an update. The stub names a version far
     * ahead of anything this branch will carry, so the story does not go stale on the next release.
     */
    @Test
    void aNewerReleaseIsReportedAsAnUpdate() throws IOException {
        withGitHubAnswering(200, "{\"tag_name\":\"v99.1.0\"}", service -> {
            service.check();
            var status = service.status();
            assertEquals("99.1.0", status.latestVersion());
            assertTrue(status.updateAvailable());
        });
    }

    /**
     * An answer that is not a 200 leaves the previous state alone rather than recording a failure
     * as "no update", which would be indistinguishable from being up to date.
     */
    @Test
    void aRefusedAnswerChangesNothing() throws IOException {
        withGitHubAnswering(403, "rate limited", service -> {
            service.check();
            assertNull(service.status().latestVersion());
            assertFalse(service.status().updateAvailable());
        });
    }

    /**
     * A release with no tag name is not an update, and does not throw on the way to deciding that.
     */
    @Test
    void aReleaseWithoutATagIsNoUpdate() throws IOException {
        withGitHubAnswering(200, "{\"name\":\"no tag here\"}", service -> {
            service.check();
            assertNull(service.status().latestVersion());
        });
    }

    /**
     * Nothing has been asked yet, so nothing is claimed. This is also what an instance reports when
     * the operator has switched the check off.
     */
    @Test
    void beforeAnyCheckNoUpdateIsClaimed() {
        var service = new UpdateCheckService(new Updates());
        var status = service.status();
        assertNull(status.latestVersion());
        assertFalse(status.updateAvailable());
        assertFalse(status.currentVersion().isBlank());
    }

    /**
     * Switched off, starting the service does no work and schedules nothing.
     */
    @Test
    void aDisabledCheckStartsNothing() {
        var disabled = new Updates() {
            @Override
            public boolean enabled() {
                return false;
            }
        };
        new UpdateCheckService(disabled).start();
    }

    /**
     * Switched on, starting schedules the poll and returns at once. Nothing is asked while this
     * runs: the first check is a minute out, which is the point of the delay.
     */
    @Test
    void anEnabledCheckSchedulesWithoutAskingAnything() {
        var service = new UpdateCheckService(new Updates(), "http://127.0.0.1:1");
        service.start();
        assertNull(service.status().latestVersion());
    }

    /**
     * An instance with no way out reports no update rather than throwing. This is the ordinary case
     * for an installation behind a firewall, so it must be quiet rather than noisy.
     */
    @Test
    void anUnreachableGitHubIsNotAnError() {
        var service = new UpdateCheckService(new Updates(), "http://127.0.0.1:1");
        service.check();
        assertNull(service.status().latestVersion());
        assertFalse(service.status().updateAvailable());
    }

    /**
     * The comparison is the whole feature: get it wrong and every instance is either told to update
     * forever or never told at all.
     */
    @Test
    void aHigherNumberAnywhereInTheVersionIsNewer() {
        assertTrue(UpdateCheckService.isNewer("26.15.0", "26.14.0"));
        assertTrue(UpdateCheckService.isNewer("27.0.0", "26.14.9"));
        assertTrue(UpdateCheckService.isNewer("26.14.1", "26.14.0"));
    }

    /**
     * The same version is not an update, and neither is an older one. An instance running something
     * newer than the newest release, which is what a developer build is, is not told to downgrade.
     */
    @Test
    void theSameOrAnOlderVersionIsNoUpdate() {
        assertFalse(UpdateCheckService.isNewer("26.14.0", "26.14.0"));
        assertFalse(UpdateCheckService.isNewer("26.13.9", "26.14.0"));
        assertFalse(UpdateCheckService.isNewer("26.14.0", "27.0.0"));
    }

    /**
     * Compared as numbers and not as text. On a string comparison "26.9.0" sorts after "26.10.0",
     * which would hide every update for a whole minor series.
     */
    @Test
    void versionsAreComparedAsNumbersNotAsText() {
        assertTrue(UpdateCheckService.isNewer("26.10.0", "26.9.0"));
        assertFalse(UpdateCheckService.isNewer("26.9.0", "26.10.0"));
    }

    /**
     * A shorter version is padded with zeros rather than counted as smaller, so 26.14 and 26.14.0
     * are the same release and neither is offered as an update to the other.
     */
    @Test
    void aMissingPartCountsAsZero() {
        assertFalse(UpdateCheckService.isNewer("26.14", "26.14.0"));
        assertFalse(UpdateCheckService.isNewer("26.14.0", "26.14"));
        assertTrue(UpdateCheckService.isNewer("26.14.1", "26.14"));
    }

    /**
     * A release named in some other scheme is never announced. Reading a word as a number would
     * make the answer arbitrary, and announcing an update nobody can install is worse than saying
     * nothing.
     */
    @Test
    void aVersionThatIsNotNumbersIsNeverAnUpdate() {
        assertFalse(UpdateCheckService.isNewer("nightly", "26.14.0"));
        assertFalse(UpdateCheckService.isNewer("26.15.0", "unknown"));
        assertFalse(UpdateCheckService.isNewer("26.15.0-rc1", "26.14.0"));
    }
}
