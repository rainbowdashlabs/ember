/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A vendor is found again under the key it is stored by and the name an address carries, and a key
 * or name this instance does not know finds nothing rather than failing.
 */
class AiVendorTest {

    @ParameterizedTest
    @EnumSource(AiVendor.class)
    void everyVendorIsFoundByItsKeyAndItsName(AiVendor vendor) {
        assertEquals(Optional.of(vendor), AiVendor.fromKey(vendor.key()));
        assertEquals(Optional.of(vendor), AiVendor.fromName(vendor.name()));
        assertTrue(!vendor.defaultModel().isBlank());
    }

    @Test
    void theStoredKeysAreTheOnesRowsWereAlwaysWrittenWith() {
        assertEquals(Optional.of(AiVendor.OPENAI), AiVendor.fromKey("openai"));
        assertEquals(Optional.of(AiVendor.GEMINI), AiVendor.fromKey("gemini"));
        assertEquals(Optional.of(AiVendor.CLAUDE), AiVendor.fromKey("claude"));
        assertEquals(Optional.of(AiVendor.DEEPSEEK), AiVendor.fromKey("deepseek"));
    }

    @Test
    void deepSeekIsSpokenToThroughTheOpenAiApiAtItsOwnAddress() {
        assertEquals(AiVendor.Protocol.OPENAI, AiVendor.DEEPSEEK.protocol());
        assertEquals(Optional.of("https://api.deepseek.com"), AiVendor.DEEPSEEK.baseUrl());
        assertEquals("deepseek-chat", AiVendor.DEEPSEEK.defaultModel());
    }

    @Test
    void theOriginalVendorsKeepTheirClientsOwnAddress() {
        assertEquals(AiVendor.Protocol.OPENAI, AiVendor.OPENAI.protocol());
        assertEquals(AiVendor.Protocol.GEMINI, AiVendor.GEMINI.protocol());
        assertEquals(AiVendor.Protocol.ANTHROPIC, AiVendor.CLAUDE.protocol());
        assertTrue(AiVendor.OPENAI.baseUrl().isEmpty());
        assertTrue(AiVendor.GEMINI.baseUrl().isEmpty());
        assertTrue(AiVendor.CLAUDE.baseUrl().isEmpty());
    }

    @Test
    void theKeysAreDistinct() {
        assertEquals(
                AiVendor.values().length,
                Arrays.stream(AiVendor.values()).map(AiVendor::key).distinct().count());
    }

    @Test
    void anUnknownKeyOrNameFindsNothing() {
        assertTrue(AiVendor.fromKey("mystery").isEmpty());
        assertTrue(AiVendor.fromKey("OPENAI").isEmpty());
        assertTrue(AiVendor.fromName("openai").isEmpty());
        assertTrue(AiVendor.fromName("MYSTERY").isEmpty());
    }
}
