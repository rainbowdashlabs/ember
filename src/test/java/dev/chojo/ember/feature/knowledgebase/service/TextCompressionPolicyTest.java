/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

class TextCompressionPolicyTest {

    private Storage config;
    private TextCompressionPolicy policy;

    @BeforeEach
    void setup() {
        config = Mockito.mock(Storage.class);
        Mockito.when(config.compressTextFiles()).thenReturn(true);
        policy = new TextCompressionPolicy(config);
    }

    @Test
    void textMimeTypesAreGzipped() {
        assertTrue(policy.shouldGzip("text/plain"));
        assertTrue(policy.shouldGzip("text/markdown"));
        assertTrue(policy.shouldGzip("application/json"));
        assertTrue(policy.shouldGzip("application/xml"));
        assertTrue(policy.shouldGzip("application/yaml"));
        assertTrue(policy.shouldGzip("application/x-yaml"));
        assertTrue(policy.shouldGzip("application/javascript"));
        assertTrue(policy.shouldGzip("application/x-www-form-urlencoded"));
        assertTrue(policy.shouldGzip("image/svg+xml"));
    }

    @Test
    void binaryMimeTypesAreNotGzipped() {
        assertFalse(policy.shouldGzip("application/pdf"));
        assertFalse(policy.shouldGzip("image/png"));
        assertFalse(policy.shouldGzip(null));
    }

    @Test
    void configFlagDisablesCompression() {
        Mockito.when(config.compressTextFiles()).thenReturn(false);
        assertFalse(policy.shouldGzip("text/plain"));
    }

    @Test
    void gzipAndGunzipRoundTrip() {
        byte[] data = "hello world ".repeat(100).getBytes();
        byte[] gz = policy.gzip(data);
        assertTrue(gz.length < data.length);
        byte[] decoded = policy.gunzip(gz);
        assertArrayEquals(data, decoded);
    }

    @Test
    void gunzipRejectsInvalidPayload() {
        assertThrows(IllegalStateException.class, () -> policy.gunzip(new byte[] {1, 2, 3, 4}));
    }
}
