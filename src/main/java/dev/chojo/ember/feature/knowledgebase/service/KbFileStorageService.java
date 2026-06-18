/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.conf.file.elements.Storage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Stores knowledge base binary files (PDFs, images, etc.) on disk instead of the database.
 * Files are stored at {@code data/kb-files/{fileId}/content} with a {@code .content-type}
 * marker. Plain-text uploads (text/*, application/json, application/xml, …) are gzipped on
 * disk under {@code content.gz} when {@link Storage#compressTextFiles()} is on (concept
 * §11.3) — the read path detects the suffix and transparently decompresses, so callers see
 * the same {@code FileData} shape regardless of how the bytes happened to land.
 */
@Singleton
public class KbFileStorageService {
    private static final Logger log = LoggerFactory.getLogger(KbFileStorageService.class);
    private static final String CONTENT = "content";
    private static final String CONTENT_GZ = "content.gz";
    private final Path baseDir = Path.of("data", "kb-files");
    private final Storage storageConfig;

    @Inject
    public KbFileStorageService(Storage storageConfig) {
        this.storageConfig = storageConfig;
    }

    public void store(int fileId, byte[] data, String contentType) throws IOException {
        Path dir = baseDir.resolve(String.valueOf(fileId));
        Files.createDirectories(dir);
        if (shouldGzip(contentType)) {
            Files.write(dir.resolve(CONTENT_GZ), gzip(data));
            Files.deleteIfExists(dir.resolve(CONTENT));
        } else {
            Files.write(dir.resolve(CONTENT), data);
            Files.deleteIfExists(dir.resolve(CONTENT_GZ));
        }
        if (contentType != null) {
            Files.writeString(dir.resolve(".content-type"), contentType);
        }
    }

    public Optional<FileData> read(int fileId) {
        Path dir = baseDir.resolve(String.valueOf(fileId));
        Path gz = dir.resolve(CONTENT_GZ);
        Path plain = dir.resolve(CONTENT);
        boolean isGzipped = Files.exists(gz);
        Path source = isGzipped ? gz : plain;
        if (!Files.exists(source)) {
            return Optional.empty();
        }
        try {
            byte[] data = isGzipped ? gunzip(Files.readAllBytes(source)) : Files.readAllBytes(source);
            Path ctFile = dir.resolve(".content-type");
            String contentType = Files.exists(ctFile) ? Files.readString(ctFile).trim() : "application/octet-stream";
            return Optional.of(new FileData(data, contentType));
        } catch (IOException e) {
            log.error("Failed to read KB file {}", fileId, e);
            return Optional.empty();
        }
    }

    public void delete(int fileId) {
        Path dir = baseDir.resolve(String.valueOf(fileId));
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Failed to delete {}", file, e);
                }
            });
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            log.warn("Failed to clean KB file directory {}", dir, e);
        }
    }

    /**
     * Store the converted PDF for a presentation file.
     */
    public void storePresentationPdf(int fileId, byte[] pdfData) throws IOException {
        Path dir = baseDir.resolve(String.valueOf(fileId));
        Files.createDirectories(dir);
        Files.write(dir.resolve("presentation.pdf"), pdfData);
    }

    /**
     * Read the converted PDF for a presentation file.
     */
    public Optional<FileData> readPresentationPdf(int fileId) {
        Path pdfPath = baseDir.resolve(String.valueOf(fileId)).resolve("presentation.pdf");
        if (!Files.exists(pdfPath)) return Optional.empty();
        try {
            return Optional.of(new FileData(Files.readAllBytes(pdfPath), "application/pdf"));
        } catch (IOException e) {
            log.error("Failed to read presentation PDF for file {}", fileId, e);
            return Optional.empty();
        }
    }

    private boolean shouldGzip(String contentType) {
        if (!storageConfig.compressTextFiles() || contentType == null) return false;
        String lower = contentType.toLowerCase(Locale.ROOT);
        if (lower.startsWith("text/")) return true;
        return switch (lower) {
            case "application/json",
                    "application/xml",
                    "application/yaml",
                    "application/x-yaml",
                    "application/javascript",
                    "application/x-www-form-urlencoded",
                    "image/svg+xml" -> true;
            default -> false;
        };
    }

    private static byte[] gzip(byte[] data) throws IOException {
        var out = new ByteArrayOutputStream(Math.max(64, data.length / 3));
        try (var gzip = new GZIPOutputStream(out)) {
            gzip.write(data);
        }
        return out.toByteArray();
    }

    private static byte[] gunzip(byte[] data) throws IOException {
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return gzip.readAllBytes();
        }
    }

    public record FileData(byte[] data, String contentType) {}
}
