/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.storage.service;

import dev.chojo.ember.conf.file.elements.Storage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Lossless recompression of presentation files (PPTX, ODP).
 * These formats are ZIP archives — re-packing with maximum deflate typically saves 10-30%.
 */
@Singleton
public class PresentationCompressor {
    private static final Logger log = LoggerFactory.getLogger(PresentationCompressor.class);

    private static final Set<String> COMPRESSIBLE_MIME_TYPES = Set.of(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.presentation",
            "application/vnd.ms-powerpoint");

    private final Storage storageConfig;

    @Inject
    public PresentationCompressor(Storage storageConfig) {
        this.storageConfig = storageConfig;
    }

    /**
     * Checks if a file should be compressed based on config, MIME type, and size.
     */
    public boolean shouldCompress(String mimeType, long fileSize) {
        return storageConfig.compressPresentations()
                && mimeType != null
                && COMPRESSIBLE_MIME_TYPES.contains(mimeType)
                && fileSize > storageConfig.compressThresholdBytes();
    }

    /**
     * Recompresses a ZIP-based file with maximum deflate level.
     * Returns the recompressed bytes, or the original if compression fails or doesn't save space.
     */
    public byte[] compress(byte[] data) {
        try {
            byte[] recompressed = recompress(data);
            if (recompressed.length < data.length) {
                long saved = data.length - recompressed.length;
                log.info(
                        "Presentation compressed: {} -> {} (saved {} bytes, {}%)",
                        data.length, recompressed.length, saved, String.format("%.1f", saved * 100.0 / data.length));
                return recompressed;
            }
            return data;
        } catch (IOException e) {
            log.warn("Failed to recompress presentation, using original", e);
            return data;
        }
    }

    private byte[] recompress(byte[] data) throws IOException {
        var out = new ByteArrayOutputStream(data.length);
        try (var zis = new ZipInputStream(new ByteArrayInputStream(data));
                var zos = new ZipOutputStream(out)) {
            zos.setLevel(Deflater.BEST_COMPRESSION);
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                var newEntry = new ZipEntry(entry.getName());
                if (entry.getExtra() != null) newEntry.setExtra(entry.getExtra());
                if (entry.getComment() != null) newEntry.setComment(entry.getComment());
                newEntry.setTime(entry.getTime());
                zos.putNextEntry(newEntry);
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
