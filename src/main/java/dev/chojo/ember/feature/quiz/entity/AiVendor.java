/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The AI services a station can generate quiz content with, under the key a station's provider
 * settings store them by.
 */
public enum AiVendor {
    OPENAI("openai", "gpt-4o-mini"),
    GEMINI("gemini", "gemini-2.0-flash"),
    CLAUDE("claude", "claude-sonnet-4-20250514");

    private final String key;
    private final String defaultModel;

    AiVendor(String key, String defaultModel) {
        this.key = key;
        this.defaultModel = defaultModel;
    }

    /**
     * The vendor stored under this key.
     *
     * @param key the provider key as the settings and the requests carry it
     * @return the vendor, or empty when the key names none
     */
    public static Optional<AiVendor> fromKey(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst();
    }

    /** The key the station's provider settings store this vendor by. */
    public String key() {
        return key;
    }

    /** The model used when neither the request nor the station's settings name one. */
    public String defaultModel() {
        return defaultModel;
    }
}
