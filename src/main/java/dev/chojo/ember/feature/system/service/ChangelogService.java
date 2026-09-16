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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What every version of this instance brought, in the language that asks for it.
 *
 * <p>The changelog travels in the jar rather than being fetched from GitHub by whoever is reading
 * it. An installation with no way out can still say what it changed, the rate limit of a public API
 * is not spent per reader, and nobody has to leave an address somewhere else to read what their own
 * instance does.
 *
 * <p>German is what the product speaks and English is what it falls back to, because the English
 * file is the one every release is cut from and the German one begins where this started.
 */
@Singleton
public class ChangelogService {
    private static final Logger log = LoggerFactory.getLogger(ChangelogService.class);
    private static final String DEFAULT_LOCALE = "de";
    private static final String FALLBACK_LOCALE = "en";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** A version heading, as {@code ## v26.17.0}. The {@code v} is part of how they are written. */
    private static final Pattern VERSION_HEADING = Pattern.compile("^##\\s+v?(\\d+(?:\\.\\d+)*)\\s*$");

    private final Map<String, Map<String, String>> byLocale = new LinkedHashMap<>();
    private final Map<String, Release> releases;
    private final String repository;

    @Inject
    public ChangelogService(Updates updates) {
        this(updates, readResource("changelog/releases.json"));
    }

    /**
     * Lets a test say which versions were released and when.
     *
     * <p>The shipped record is written by the build out of the tags of whatever checkout it ran in,
     * so a test that read it would assert something different on a machine with the tags than on one
     * without them, and nothing at all in a shallow clone.
     *
     * @param updates     the operator's settings, which name the repository the links point at
     * @param releasesJson the record of tags, as the build writes it; null where none was shipped
     */
    ChangelogService(Updates updates, String releasesJson) {
        for (String locale : List.of(DEFAULT_LOCALE, FALLBACK_LOCALE)) {
            byLocale.put(locale, read(locale));
        }
        releases = readReleases(releasesJson);
        repository = updates.repository();
    }

    /**
     * Every version the changelog names, newest first, as it is written.
     *
     * @param locale the language to read it in; anything else falls back to English
     */
    public List<ChangelogEntry> all(String locale) {
        var versions = sectionsFor(locale);
        var fallback = byLocale.getOrDefault(FALLBACK_LOCALE, Map.of());
        List<ChangelogEntry> out = new ArrayList<>();
        for (var entry : versions.entrySet()) {
            out.add(entryOf(entry.getKey(), entry.getValue()));
        }
        // A version the German file has not reached yet is still worth reading, so the English text
        // stands in for it rather than the list stopping where the translation stops.
        for (var entry : fallback.entrySet()) {
            if (!versions.containsKey(entry.getKey())) {
                out.add(entryOf(entry.getKey(), entry.getValue()));
            }
        }
        out.sort((left, right) -> compareVersions(right.version(), left.version()));
        return out;
    }

    /**
     * What one version brought, or empty where the changelog does not name it.
     *
     * <p>A development build has no section of its own, and an announcement with an empty body says
     * less than nothing, so the absence is the answer rather than a heading with nothing under it.
     *
     * @param locale the language to read it in; anything else falls back to English
     */
    public Optional<ChangelogEntry> forVersion(String locale, String version) {
        String wanted = normalise(version);
        if (wanted.isEmpty()) return Optional.empty();
        String body = sectionsFor(locale).get(wanted);
        if (body == null)
            body = byLocale.getOrDefault(FALLBACK_LOCALE, Map.of()).get(wanted);
        return Optional.ofNullable(body).map(text -> entryOf(wanted, text));
    }

    /**
     * Compares two versions by their numbers rather than as text, so that 26.9.0 is older than
     * 26.17.0 instead of later in the alphabet.
     *
     * @return negative where the left one is older, positive where it is newer, zero where they are
     *     the same version
     */
    public static int compareVersions(String left, String right) {
        String[] leftParts = normalise(left).split("\\.");
        String[] rightParts = normalise(right).split("\\.");
        for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
            int compared = Integer.compare(segment(leftParts, i), segment(rightParts, i));
            if (compared != 0) return compared;
        }
        return 0;
    }

    /**
     * The version as the changelog writes it: the numbers alone.
     *
     * <p>What the instance reports about itself carries more than that. A build off a branch says
     * {@code 26.17.0 main-1a2b3c4 @ 2026-09-15 10:00}, and the part a changelog can be asked about
     * is the first word of it, without its {@code v}.
     */
    public static String normalise(String version) {
        if (version == null) return "";
        String trimmed = version.strip();
        int space = trimmed.indexOf(' ');
        if (space > 0) trimmed = trimmed.substring(0, space);
        if (trimmed.startsWith("v")) trimmed = trimmed.substring(1);
        return trimmed.matches("\\d+(\\.\\d+)*") ? trimmed : "";
    }

    private static int segment(String[] parts, int index) {
        if (index >= parts.length) return 0;
        try {
            return Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * One version, with when it was released and where its changes can be read.
     *
     * <p>Both are absent for a version this build knows no tag of, which is what a checkout without
     * its history and a version never tagged both look like from here.
     */
    private ChangelogEntry entryOf(String version, String body) {
        var release = releases.get(version);
        if (release == null) return new ChangelogEntry(version, body, null, null);
        return new ChangelogEntry(version, body, release.releasedAt(), compareUrl(version, release.tag()));
    }

    /**
     * Where the changes of one version can be read, against the release before it.
     *
     * <p>The tag before this one rather than the entry above it in the changelog: a version can be
     * released without an entry of its own, and a comparison that skipped it would claim its changes
     * for its neighbour.
     */
    private String compareUrl(String version, String tag) {
        String previous = null;
        String previousTag = null;
        for (var candidate : releases.entrySet()) {
            if (compareVersions(candidate.getKey(), version) >= 0) continue;
            if (previous == null || compareVersions(candidate.getKey(), previous) > 0) {
                previous = candidate.getKey();
                previousTag = candidate.getValue().tag();
            }
        }
        if (previousTag == null) return null;
        return "https://github.com/" + repository + "/compare/" + previousTag + "..." + tag;
    }

    /**
     * When each version was tagged, as the build wrote it down.
     *
     * <p>Recorded while the sources still had their history beside them, because the image is built
     * from a context that does not carry it. A build that found no tags ships an empty record and
     * the page then shows its entries without dates, which is what a fork's own build does until it
     * tags anything.
     */
    private static Map<String, Release> readReleases(String json) {
        Map<String, Release> found = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            log.warn("No release dates shipped with the changelog");
            return found;
        }
        try {
            var tree = JSON.readTree(json);
            for (var name : tree.propertyNames()) {
                var node = tree.get(name);
                var tag = node.get("tag");
                var releasedAt = node.get("releasedAt");
                if (tag == null || releasedAt == null) continue;
                found.put(name, new Release(tag.asString(), Instant.parse(releasedAt.asString())));
            }
        } catch (RuntimeException e) {
            log.warn("Could not read when the versions were released", e);
        }
        return found;
    }

    /** Reads a resource that travels in the jar, or null where this build shipped none. */
    private static String readResource(String path) {
        try (InputStream is = ChangelogService.class.getClassLoader().getResourceAsStream(path)) {
            return is == null ? null : new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Could not read {}", path, e);
            return null;
        }
    }

    private Map<String, String> sectionsFor(String locale) {
        var sections = byLocale.get(locale == null ? DEFAULT_LOCALE : locale.toLowerCase());
        return sections != null ? sections : byLocale.getOrDefault(DEFAULT_LOCALE, Map.of());
    }

    /**
     * Splits one file into its versions, keeping everything under a heading as it was written:
     * these are entries somebody wrote to be read, and rewrapping them would only lose what they
     * say.
     */
    private Map<String, String> read(String locale) {
        Map<String, String> sections = new LinkedHashMap<>();
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("changelog/" + locale + ".md")) {
            if (is == null) {
                log.warn("No changelog shipped for {}", locale);
                return sections;
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            String version = null;
            StringBuilder body = new StringBuilder();
            for (String line : content.split("\r?\n", -1)) {
                var matcher = VERSION_HEADING.matcher(line);
                if (matcher.matches()) {
                    if (version != null) sections.put(version, body.toString().strip());
                    version = matcher.group(1);
                    body.setLength(0);
                    continue;
                }
                if (version != null) body.append(line).append('\n');
            }
            if (version != null) sections.put(version, body.toString().strip());
        } catch (IOException e) {
            log.warn("Could not read the changelog for {}", locale, e);
        }
        return sections;
    }

    /**
     * One version of the changelog.
     *
     * @param version    the version it belongs to, as the numbers alone
     * @param body       what it brought, as the markdown somebody wrote
     * @param releasedAt when it was tagged, or null where this build knows no tag of it
     * @param compareUrl where its changes can be read against the release before it, or null where
     *     there is no such pair of tags
     */
    public record ChangelogEntry(String version, String body, Instant releasedAt, String compareUrl) {}

    /**
     * A tag as the build found it.
     *
     * @param tag        the tag itself, which carries the {@code v} a version number does not
     * @param releasedAt when it was made
     */
    private record Release(String tag, Instant releasedAt) {}
}
