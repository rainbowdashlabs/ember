/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import dev.chojo.ember.feature.legal.entity.BrowserStorageCatalog;
import dev.chojo.ember.feature.legal.entity.BrowserStorageEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the generated browser storage disclosure from {@code browser_storage.json}.
 *
 * <p>The result is a markdown section that the legal document renderer substitutes into the
 * privacy policy and the consent text wherever a section named {@value #SECTION_NAME} sits.
 * Administrators decide where that section appears and whether it is shown; its content is
 * never hand-written, so the disclosure cannot drift away from what the application does.
 *
 * <p>Tables are deliberately not used: the legal renderer sanitizes with a policy that drops
 * table markup, so the entries are rendered as a list instead.
 */
public class BrowserStorageService {
    private static final Logger log = LoggerFactory.getLogger(BrowserStorageService.class);

    /**
     * The reserved section name identifying a generated storage disclosure on disk.
     */
    public static final String SECTION_NAME = "browser-storage";

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private final BrowserStorageCatalog catalog;

    public BrowserStorageService() {
        this(load());
    }

    BrowserStorageService(BrowserStorageCatalog catalog) {
        this.catalog = catalog;
    }

    /**
     * Returns whether the given file name denotes the generated storage disclosure.
     * Both the disabled prefix and the ordering prefix are ignored, so {@code 03-browser-storage.md}
     * and {@code _01-browser-storage.md} are recognised alike.
     *
     * @param fileName the file name as it sits on disk
     * @return true if the file carries the generated section
     */
    public static boolean isGeneratedSection(String fileName) {
        String stem = fileName.replaceFirst("^_?\\d+-", "").replaceFirst("\\.md$", "");
        return SECTION_NAME.equals(stem);
    }

    /**
     * Returns the declared catalog.
     *
     * @return every value the application may store in the browser
     */
    public BrowserStorageCatalog catalog() {
        return catalog;
    }

    /**
     * Renders the disclosure as markdown for the given locale.
     *
     * @param locale the desired locale (e.g. "de", "en")
     * @return the markdown section, or an empty string if the catalog could not be read
     */
    public String toMarkdown(String locale) {
        if (catalog == null || catalog.entries() == null || catalog.entries().isEmpty()) {
            return "";
        }
        var text = catalog.text();
        var out = new StringBuilder();
        out.append("## ").append(text.heading().get(locale)).append("\n\n");
        out.append(text.intro().get(locale)).append("\n");

        for (var group : grouped().entrySet()) {
            var wording = text.necessity().get(group.getKey());
            out.append("\n### ").append(wording.heading().get(locale)).append("\n\n");
            out.append(wording.description().get(locale)).append("\n\n");
            for (var entry : group.getValue()) {
                out.append("- **`")
                        .append(entry.key())
                        .append("`** — ")
                        .append(entry.purpose().get(locale))
                        .append(" *(")
                        .append(text.retention().get(entry.retention()).get(locale))
                        .append(")*\n");
            }
        }

        out.append("\n").append(text.closing().get(locale)).append("\n");
        return out.toString();
    }

    private Map<BrowserStorageEntry.Necessity, List<BrowserStorageEntry>> grouped() {
        Map<BrowserStorageEntry.Necessity, List<BrowserStorageEntry>> groups = new LinkedHashMap<>();
        for (var necessity : BrowserStorageEntry.Necessity.values()) {
            List<BrowserStorageEntry> matching = new ArrayList<>();
            for (var entry : catalog.entries()) {
                if (entry.necessity() == necessity) matching.add(entry);
            }
            if (!matching.isEmpty()) groups.put(necessity, matching);
        }
        return groups;
    }

    private static BrowserStorageCatalog load() {
        try (InputStream in = BrowserStorageService.class.getResourceAsStream(BrowserStorageCatalog.RESOURCE_PATH)) {
            if (in == null) {
                log.error("Browser storage catalog not found on classpath: {}", BrowserStorageCatalog.RESOURCE_PATH);
                return null;
            }
            return MAPPER.readValue(in, BrowserStorageCatalog.class);
        } catch (Exception e) {
            log.error("Failed to read browser storage catalog", e);
            return null;
        }
    }
}
