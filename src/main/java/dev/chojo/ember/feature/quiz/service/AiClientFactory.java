/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.google.genai.Client;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import dev.chojo.ember.feature.quiz.entity.AiVendor;
import jakarta.inject.Singleton;

/**
 * Builds the vendor SDK clients for one call each.
 *
 * <p>Every client owns an HTTP connection pool and dispatcher threads, so whoever asks for one
 * closes it when the call is over. The keys differ per station and per request, which is why no
 * client is kept between calls.
 */
@Singleton
public class AiClientFactory {

    /**
     * An OpenAI client for this key, pointed at the vendor's own address where it speaks the OpenAI
     * API under one; the caller closes it.
     *
     * @param vendor the vendor to call, one whose protocol is the OpenAI API
     * @param apiKey the key to authenticate with
     * @return a new client
     */
    public OpenAIClient openAi(AiVendor vendor, String apiKey) {
        var builder = OpenAIOkHttpClient.builder().apiKey(apiKey);
        vendor.baseUrl().ifPresent(builder::baseUrl);
        return builder.build();
    }

    /**
     * An Anthropic client for this key; the caller closes it.
     *
     * @param apiKey the key to authenticate with
     * @return a new client
     */
    public AnthropicClient anthropic(String apiKey) {
        return AnthropicOkHttpClient.builder().apiKey(apiKey).build();
    }

    /**
     * A Google GenAI client for this key; the caller closes it.
     *
     * @param apiKey the key to authenticate with
     * @return a new client
     */
    public Client gemini(String apiKey) {
        return Client.builder().apiKey(apiKey).build();
    }
}
