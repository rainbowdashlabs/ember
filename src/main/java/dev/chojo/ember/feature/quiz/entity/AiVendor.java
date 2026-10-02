/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The AI services a station or a person can generate quiz content with.
 *
 * <p>The API sends and reads a vendor by its constant name. The database keeps the lower-case
 * {@link #key()} that rows were written with before the vendor travelled as an enum, and a station
 * transfer carries that key too, so instances of either age understand each other. A stored key
 * that names no vendor of this instance is read as no vendor at all, never as a failure.
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
     * @param key the provider key as the database and a station transfer carry it
     * @return the vendor, or empty when the key names none
     */
    public static Optional<AiVendor> fromKey(String key) {
        return Arrays.stream(values()).filter(v -> v.key.equals(key)).findFirst();
    }

    /**
     * The vendor of this constant name, as an address names it.
     *
     * @param name the constant name, for example {@code OPENAI}
     * @return the vendor, or empty when the name is none of them
     */
    public static Optional<AiVendor> fromName(String name) {
        return Arrays.stream(values()).filter(v -> v.name().equals(name)).findFirst();
    }

    /** The key the database and a station transfer store this vendor by. */
    public String key() {
        return key;
    }

    /** The model used when neither the request nor the station's settings name one. */
    public String defaultModel() {
        return defaultModel;
    }
}
