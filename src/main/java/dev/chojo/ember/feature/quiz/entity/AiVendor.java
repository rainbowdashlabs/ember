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
 *
 * <p>A vendor that speaks the OpenAI chat API under an address of its own, such as DeepSeek, is
 * reached through the OpenAI client and code paths with its {@link #baseUrl()}, so adding one is a
 * constant here and nothing else.
 */
public enum AiVendor {
    OPENAI("openai", "gpt-4o-mini", Protocol.OPENAI),
    GEMINI("gemini", "gemini-2.0-flash", Protocol.GEMINI),
    CLAUDE("claude", "claude-sonnet-4-20250514", Protocol.ANTHROPIC),
    DEEPSEEK("deepseek", "deepseek-chat", "https://api.deepseek.com");

    private final String key;
    private final String defaultModel;
    private final Protocol protocol;
    private final Optional<String> baseUrl;

    /** A vendor spoken to through its own client at the client's own address. */
    AiVendor(String key, String defaultModel, Protocol protocol) {
        this(key, defaultModel, protocol, Optional.empty());
    }

    /** A vendor that speaks the OpenAI API at an address of its own. */
    AiVendor(String key, String defaultModel, String openAiCompatibleBaseUrl) {
        this(key, defaultModel, Protocol.OPENAI, Optional.of(openAiCompatibleBaseUrl));
    }

    AiVendor(String key, String defaultModel, Protocol protocol, Optional<String> baseUrl) {
        this.key = key;
        this.defaultModel = defaultModel;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
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

    /** The API this vendor is spoken to through. */
    public Protocol protocol() {
        return protocol;
    }

    /**
     * Where the vendor answers, for one that speaks another vendor's API under its own address.
     *
     * @return the base address the client is pointed at, or empty for the client's own default
     */
    public Optional<String> baseUrl() {
        return baseUrl;
    }

    /** The APIs the vendors are spoken to through, one client library each. */
    public enum Protocol {
        /** The OpenAI chat completions API, through the OpenAI client. */
        OPENAI,
        /** The Google GenAI API, through the Google client. */
        GEMINI,
        /** The Anthropic messages API, through the Anthropic client. */
        ANTHROPIC
    }
}
