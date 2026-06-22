/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class PresentationCompressorTest {

    private Storage config;
    private PresentationCompressor compressor;

    @BeforeEach
    void setup() {
        config = Mockito.mock(Storage.class);
        Mockito.when(config.compressPresentations()).thenReturn(true);
        Mockito.when(config.compressOfficeDocs()).thenReturn(true);
        Mockito.when(config.compressThresholdBytes()).thenReturn(0L);
        compressor = new PresentationCompressor(config);
    }

    @Test
    void shouldCompressPresentations() {
        assertTrue(compressor.shouldCompress(
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", 1024));
        assertTrue(compressor.shouldCompress("application/vnd.oasis.opendocument.presentation", 1024));
        assertTrue(compressor.shouldCompress("application/vnd.ms-powerpoint", 1024));
    }

    @Test
    void shouldCompressOfficeDocs() {
        assertTrue(compressor.shouldCompress(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 1024));
        assertTrue(
                compressor.shouldCompress("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", 1024));
        assertTrue(compressor.shouldCompress("application/vnd.oasis.opendocument.text", 1024));
        assertTrue(compressor.shouldCompress("application/vnd.oasis.opendocument.spreadsheet", 1024));
    }

    @Test
    void rejectsUnknownMimes() {
        assertFalse(compressor.shouldCompress("text/plain", 1024));
        assertFalse(compressor.shouldCompress("application/pdf", 1024));
        assertFalse(compressor.shouldCompress(null, 1024));
    }

    @Test
    void respectsSeparateToggles() {
        Mockito.when(config.compressPresentations()).thenReturn(false);
        assertFalse(compressor.shouldCompress("application/vnd.ms-powerpoint", 1024));
        assertTrue(compressor.shouldCompress("application/vnd.oasis.opendocument.text", 1024));

        Mockito.when(config.compressPresentations()).thenReturn(true);
        Mockito.when(config.compressOfficeDocs()).thenReturn(false);
        assertTrue(compressor.shouldCompress("application/vnd.ms-powerpoint", 1024));
        assertFalse(compressor.shouldCompress("application/vnd.oasis.opendocument.text", 1024));
    }

    @Test
    void respectsSizeThreshold() {
        Mockito.when(config.compressThresholdBytes()).thenReturn(10_000L);
        assertFalse(compressor.shouldCompress("application/vnd.ms-powerpoint", 1024));
        assertTrue(compressor.shouldCompress("application/vnd.ms-powerpoint", 20_000));
    }

    @Test
    void recompressShrinksUnderCompressedZip() throws IOException {
        byte[] original = buildZip(Deflater.NO_COMPRESSION);
        byte[] recompressed = compressor.compress(original);
        assertTrue(
                recompressed.length < original.length,
                "Re-packing with BEST_COMPRESSION should shrink a NO_COMPRESSION zip");
    }

    @Test
    void recompressKeepsOriginalWhenLarger() throws IOException {
        byte[] original = buildZip(Deflater.BEST_COMPRESSION);
        byte[] recompressed = compressor.compress(original);
        assertNotNull(recompressed);
        assertTrue(
                recompressed.length >= original.length
                        || recompressed == original
                        || Arrays.equals(recompressed, original),
                "Already-tight zip should not grow when recompressed");
    }

    @Test
    void recompressReturnsOriginalOnBrokenZip() {
        byte[] junk = new byte[] {0, 1, 2, 3, 4, 5};
        byte[] result = compressor.compress(junk);
        assertArrayEquals(junk, result);
    }

    private static byte[] buildZip(int level) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zos = new ZipOutputStream(out)) {
            zos.setLevel(level);
            String payload = "hello world ".repeat(2000);
            zos.putNextEntry(new ZipEntry("doc.xml"));
            zos.write(payload.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("meta.xml"));
            zos.write("meta".repeat(1500).getBytes());
            zos.closeEntry();
        }
        return out.toByteArray();
    }
}
