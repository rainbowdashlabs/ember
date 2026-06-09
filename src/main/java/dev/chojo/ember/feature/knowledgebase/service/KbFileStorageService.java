/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Stores knowledge base binary files (PDFs, images, etc.) on disk instead of the database.
 * Files are stored at {@code data/kb-files/{fileId}/content} with a {@code .content-type} marker.
 */
@Singleton
public class KbFileStorageService {
    private static final Logger log = LoggerFactory.getLogger(KbFileStorageService.class);
    private final Path baseDir = Path.of("data", "kb-files");

    public void store(int fileId, byte[] data, String contentType) throws IOException {
        Path dir = baseDir.resolve(String.valueOf(fileId));
        Files.createDirectories(dir);
        Files.write(dir.resolve("content"), data);
        if (contentType != null) {
            Files.writeString(dir.resolve(".content-type"), contentType);
        }
    }

    public Optional<FileData> read(int fileId) {
        Path dir = baseDir.resolve(String.valueOf(fileId));
        Path content = dir.resolve("content");
        if (!Files.exists(content)) {
            return Optional.empty();
        }
        try {
            byte[] data = Files.readAllBytes(content);
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

    public record FileData(byte[] data, String contentType) {}
}
