/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import com.openai.client.OpenAIClient;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AiClientFactoryTest {
    private static final String KEY = "test-key";

    private final AiClientFactory factory = new AiClientFactory();

    /** The address a built client sends its requests to, read from the options it was built with. */
    private static String baseUrlOf(OpenAIClient client) {
        var seen = new AtomicReference<String>();
        client.withOptions(options -> seen.set(options.build().baseUrl()));
        return seen.get();
    }

    @Test
    void buildsAnOpenAiClientForTheKey() {
        var client = factory.openAi(AiVendor.OPENAI, KEY);
        assertEquals("https://api.openai.com/v1", baseUrlOf(client));
        client.close();
    }

    @Test
    void pointsTheOpenAiClientAtDeepSeek() {
        var client = factory.openAi(AiVendor.DEEPSEEK, KEY);
        assertEquals("https://api.deepseek.com", baseUrlOf(client));
        client.close();
    }

    @Test
    void pointsTheOpenAiClientAtMistralsVersionedAddress() {
        var client = factory.openAi(AiVendor.MISTRAL, KEY);
        assertEquals("https://api.mistral.ai/v1", baseUrlOf(client));
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
