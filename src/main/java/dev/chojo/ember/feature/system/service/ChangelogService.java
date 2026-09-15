/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    /** A version heading, as {@code ## v26.17.0}. The {@code v} is part of how they are written. */
    private static final Pattern VERSION_HEADING = Pattern.compile("^##\\s+v?(\\d+(?:\\.\\d+)*)\\s*$");

    private final Map<String, Map<String, String>> byLocale = new LinkedHashMap<>();

    public ChangelogService() {
        for (String locale : List.of(DEFAULT_LOCALE, FALLBACK_LOCALE)) {
            byLocale.put(locale, read(locale));
        }
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
            out.add(new ChangelogEntry(entry.getKey(), entry.getValue()));
        }
        // A version the German file has not reached yet is still worth reading, so the English text
        // stands in for it rather than the list stopping where the translation stops.
        for (var entry : fallback.entrySet()) {
            if (!versions.containsKey(entry.getKey())) {
                out.add(new ChangelogEntry(entry.getKey(), entry.getValue()));
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
        return Optional.ofNullable(body).map(text -> new ChangelogEntry(wanted, text));
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
     * @param version the version it belongs to, as the numbers alone
     * @param body    what it brought, as the markdown somebody wrote
     */
    public record ChangelogEntry(String version, String body) {}
}
