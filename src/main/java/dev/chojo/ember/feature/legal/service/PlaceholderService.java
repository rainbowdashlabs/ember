/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.DocumentPlaceholder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{ name }}} placeholders in the legal documents.
 *
 * <p>Placeholders are not declared anywhere: whatever an administrator writes into a document
 * becomes one, and the same name used in several documents is one placeholder with one value.
 * The values live in a small JSON file next to the documents so that everything a rendered
 * document depends on sits on disk together.
 *
 * <p>A token without a configured value is left standing rather than blanked, so a missing
 * value is visible instead of silently swallowing a sentence.
 */
public class PlaceholderService {
    private static final Logger log = LoggerFactory.getLogger(PlaceholderService.class);

    /**
     * Matches {@code {{ name }}} with optional surrounding whitespace.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.\\-]+)\\s*}}");

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final Path valueFile;

    private Map<String, String> cached = Map.of();
    private FileTime cachedAt;

    public PlaceholderService(Path valueFile) {
        this.valueFile = valueFile;
    }

    /**
     * Returns every placeholder name appearing in the given text.
     *
     * @param text the raw markdown to scan
     * @return the names found, in alphabetical order
     */
    public static Set<String> namesIn(String text) {
        if (text == null || text.isEmpty()) return Set.of();
        Set<String> names = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /**
     * Replaces every placeholder that has a value. Tokens without one are left untouched.
     *
     * @param text the raw markdown
     * @return the markdown with configured values substituted
     */
    public String apply(String text) {
        if (text == null || text.isEmpty() || text.indexOf('{') < 0) return text;
        Map<String, String> values = values();
        if (values.isEmpty()) return text;

        Matcher matcher = PLACEHOLDER.matcher(text);
        var out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(
                    out, value == null ? Matcher.quoteReplacement(matcher.group()) : Matcher.quoteReplacement(value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Returns the configured values, reloading them when the file changed since the last read.
     *
     * @return placeholder name to value, never null
     */
    public Map<String, String> values() {
        try {
            if (!Files.isRegularFile(valueFile)) {
                cached = Map.of();
                cachedAt = null;
                return cached;
            }
            FileTime modified = Files.getLastModifiedTime(valueFile);
            if (modified.equals(cachedAt)) return cached;
            cached = read();
            cachedAt = modified;
        } catch (IOException e) {
            log.error("Failed to read placeholder values from {}", valueFile, e);
        }
        return cached;
    }

    /**
     * Stores the given values, dropping the ones left empty.
     *
     * @param values placeholder name to value
     */
    public void save(Map<String, String> values) {
        Map<String, String> cleaned = new TreeMap<>();
        values.forEach((name, value) -> {
            if (name == null || name.isBlank()) return;
            if (value == null || value.isBlank()) return;
            cleaned.put(name.trim(), value);
        });
        try {
            if (valueFile.getParent() != null) Files.createDirectories(valueFile.getParent());
            Files.writeString(
                    valueFile, MAPPER.writeValueAsString(cleaned) + System.lineSeparator(), StandardCharsets.UTF_8);
            cached = cleaned;
            cachedAt = Files.getLastModifiedTime(valueFile);
        } catch (IOException e) {
            log.error("Failed to write placeholder values to {}", valueFile, e);
        }
    }

    /**
     * Scans a document directory for placeholders.
     *
     * @param baseDir  the base directory holding locale subdirectories
     * @param typeSlug the document type the directory belongs to
     * @return placeholder name to the sections it appears in
     */
    public Map<String, List<DocumentPlaceholder.Usage>> scan(Path baseDir, String typeSlug) {
        Map<String, List<DocumentPlaceholder.Usage>> found = new TreeMap<>();
        if (!Files.isDirectory(baseDir)) return found;

        try (DirectoryStream<Path> locales = Files.newDirectoryStream(baseDir, Files::isDirectory)) {
            for (Path localeDir : locales) {
                String locale = localeDir.getFileName().toString();
                if (locale.equals("history")) continue;
                scanLocale(localeDir, typeSlug, locale, found);
            }
        } catch (IOException e) {
            log.error("Failed to scan {} for placeholders", baseDir, e);
        }
        return found;
    }

    private void scanLocale(
            Path localeDir, String typeSlug, String locale, Map<String, List<DocumentPlaceholder.Usage>> found) {
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(localeDir, "*.md")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            log.error("Failed to list {} for placeholders", localeDir, e);
            return;
        }
        Collections.sort(files);

        for (Path file : files) {
            String name = file.getFileName().toString();
            String section = name.replaceFirst("^_?\\d+-", "").replaceFirst("\\.md$", "");
            try {
                for (String placeholder : namesIn(Files.readString(file, StandardCharsets.UTF_8))) {
                    found.computeIfAbsent(placeholder, _ -> new ArrayList<>())
                            .add(new DocumentPlaceholder.Usage(typeSlug, locale, section));
                }
            } catch (IOException e) {
                log.error("Failed to read {} for placeholders", file, e);
            }
        }
    }

    private Map<String, String> read() throws IOException {
        try (var in = Files.newInputStream(valueFile)) {
            Map<String, String> values = MAPPER.readValue(in, new TypeReference<LinkedHashMap<String, String>>() {});
            return values == null ? Map.of() : values;
        } catch (Exception e) {
            log.error("Failed to parse placeholder values in {}", valueFile, e);
            return Map.of();
        }
    }
}
