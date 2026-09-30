/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiClientFactoryTest {
    private static final String KEY = "test-key";

    private final AiClientFactory factory = new AiClientFactory();

    @Test
    void buildsAnOpenAiClientForTheKey() {
        var client = factory.openAi(KEY);
        assertNotNull(client);
        client.close();
    }

    @Test
    void buildsAnAnthropicClientForTheKey() {
        var client = factory.anthropic(KEY);
        assertNotNull(client);
        client.close();
    }

    @Test
    void buildsAGeminiClientForTheKey() {
        try (var client = factory.gemini(KEY)) {
            assertNotNull(client);
        }
    }
}
