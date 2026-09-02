/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.conf.file.elements.Updates;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Asks GitHub whether a release newer than the running one exists.
 *
 * <p>Answers from memory and never from the request that asks, because a page that waits on
 * github.com is a page that hangs when github.com does. The poll runs on its own schedule and the
 * endpoint reads whatever the last one found; an instance that has never managed a check simply
 * reports no update, which is the same thing it reports when it is up to date.
 *
 * <p>Releases are read rather than tags: a tag is pushed when the work is done and a release when
 * it is meant to be installed, and only the second is news to an operator. Failures are logged once
 * at warn and change nothing, so an instance with no outbound access is merely quiet.
 */
@Singleton
public class UpdateCheckService {
    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String GITHUB_API = "https://api.github.com";

    private final Updates config;
    private final AtomicReference<String> latestVersion = new AtomicReference<>();
    private final String currentVersion;
    private final String apiBase;

    @Inject
    public UpdateCheckService(Updates config) {
        this(config, GITHUB_API);
    }

    /**
     * Lets a test answer for GitHub. The host is the one thing about this service that cannot be
     * exercised against the real thing: a test that asked api.github.com would be slow, would fail
     * offline, and would say something different every release.
     *
     * @param config  the operator's settings
     * @param apiBase the root the releases are read from, without a trailing slash
     */
    UpdateCheckService(Updates config, String apiBase) {
        this.config = config;
        this.apiBase = apiBase;
        this.currentVersion = readCurrentVersion();
    }

    /**
     * Starts the periodic check, unless the operator switched it off.
     *
     * <p>The first run is delayed by a minute so that starting up is never held behind an outbound
     * call, and so that an instance restarted in a loop does not hammer the API.
     */
    public void start() {
        if (!config.enabled()) {
            log.debug("Update check disabled");
            return;
        }
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                runnable -> Thread.ofVirtual().name("update-check").unstarted(runnable));
        scheduler.scheduleAtFixedRate(this::check, 1, config.checkIntervalHours() * 60L, TimeUnit.MINUTES);
    }

    /**
     * Performs one check. Never throws; a failure leaves the previous answer in place.
     */
    public void check() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiBase + "/repos/" + config.repository() + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "ember")
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Update check answered {} for {}", response.statusCode(), config.repository());
                return;
            }
            String tag = JSON.readTree(response.body()).path("tag_name").asString();
            if (tag == null || tag.isBlank()) {
                log.warn("Update check found no tag name in the latest release of {}", config.repository());
                return;
            }
            latestVersion.set(normalise(tag));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Update check against {} failed: {}", config.repository(), e.toString());
        }
    }

    /**
     * What the instance runs, what the newest release is, and whether the second is ahead of the
     * first.
     *
     * @return the state of the last completed check
     */
    public UpdateStatus status() {
        String latest = latestVersion.get();
        return new UpdateStatus(currentVersion, latest, latest != null && isNewer(latest, currentVersion));
    }

    /**
     * Whether the first version is ahead of the second.
     *
     * <p>Compared number by number rather than as text, because "26.9.0" is ahead of "26.10.0" on
     * every string comparison and behind it on every sensible one. A part that is not a number
     * stops the comparison and counts as equal, so a release named in some other scheme is never
     * announced as an update.
     *
     * @param candidate the version being offered
     * @param running   the version in use
     * @return true where the candidate is genuinely newer
     */
    static boolean isNewer(String candidate, String running) {
        int[] left = numbersOf(candidate);
        int[] right = numbersOf(running);
        if (left == null || right == null) return false;
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int a = i < left.length ? left[i] : 0;
            int b = i < right.length ? right[i] : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    /**
     * The version read as numbers, or null where any part of it is not one.
     *
     * <p>Checked as a whole before anything is compared, so a release candidate of a higher version
     * is refused rather than announced: reaching the higher number first would otherwise decide the
     * answer before the part that says it is not a finished release is ever looked at.
     */
    private static int[] numbersOf(String version) {
        String[] parts = version.split("\\.");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                numbers[i] = Integer.parseInt(parts[i].trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return numbers;
    }

    /**
     * Strips a leading {@code v} and anything the build appended, leaving the bare numbers.
     */
    private static String normalise(String version) {
        String stripped = version.strip();
        if (stripped.startsWith("v") || stripped.startsWith("V")) {
            stripped = stripped.substring(1);
        }
        int cut = stripped.indexOf(' ');
        return cut < 0 ? stripped : stripped.substring(0, cut);
    }

    private String readCurrentVersion() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("version")) {
            if (is != null) {
                return normalise(new String(is.readAllBytes(), StandardCharsets.UTF_8).strip());
            }
        } catch (Exception e) {
            log.warn("Failed to read version resource for the update check", e);
        }
        return "unknown";
    }

    /**
     * @param currentVersion  the version this instance runs
     * @param latestVersion   the newest release found, null where no check has succeeded
     * @param updateAvailable whether the newest release is ahead of the running one
     */
    public record UpdateStatus(String currentVersion, String latestVersion, boolean updateAvailable) {}
}
