/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.entity;

import java.util.List;
import java.util.Map;

/**
 * The declared inventory of everything this application stores in the browser, read from
 * {@code browser_storage.json}. It is the single source of truth behind the generated
 * storage disclosure; the frontend linter checks the running code against it.
 *
 * @param version the format version of the resource
 * @param text    the wording framing the generated section
 * @param entries every value the application may store, in the order they are disclosed
 */
public record BrowserStorageCatalog(int version, Text text, List<BrowserStorageEntry> entries) {

    /**
     * The path of the catalog resource on the classpath.
     */
    public static final String RESOURCE_PATH = "/browser_storage.json";

    /**
     * The wording framing the generated section.
     *
     * @param heading   the section heading
     * @param intro     the paragraph before the groups
     * @param closing   the paragraph after the groups
     * @param necessity heading and description per {@link BrowserStorageEntry.Necessity}
     * @param retention the label per {@link BrowserStorageEntry.Retention}
     */
    public record Text(
            LocalizedText heading,
            LocalizedText intro,
            LocalizedText closing,
            Map<BrowserStorageEntry.Necessity, Group> necessity,
            Map<BrowserStorageEntry.Retention, LocalizedText> retention) {

        /**
         * The wording introducing one necessity group.
         *
         * @param heading     the group heading
         * @param description what the group means for the reader
         */
        public record Group(LocalizedText heading, LocalizedText description) {}
    }
}
