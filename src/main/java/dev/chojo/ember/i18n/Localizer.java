/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple localizer that loads JSON translation files from the classpath.
 * Files are expected at {@code i18n/<prefix>_<locale>.json} with a flat or nested structure.
 */
public final class Localizer {
    private static final Logger log = LoggerFactory.getLogger(Localizer.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Map<String, Map<String, Map<String, String>>> cache = new ConcurrentHashMap<>();

    /**
     * Loads a JSON file from {@code i18n/<prefix>_<locale>.json} and returns the map for the given section.
     * Results are cached.
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> get(String prefix, String locale, String section) {
        String key = prefix + "_" + locale;
        var fileMap = cache.computeIfAbsent(key, k -> loadFile(prefix, locale));
        return fileMap.getOrDefault(section, Map.of());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> loadFile(String prefix, String locale) {
        String path = "i18n/" + prefix + "_" + locale + ".json";
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Locale file not found: {}", path);
                return Map.of();
            }
            String json = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return MAPPER.readValue(json, Map.class);
        } catch (IOException e) {
            log.error("Failed to load locale file: {}", path, e);
            return Map.of();
        }
    }
}
