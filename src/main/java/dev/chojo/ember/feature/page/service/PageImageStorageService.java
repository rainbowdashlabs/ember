/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.page.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Singleton
public class PageImageStorageService {
    private static final Logger log = LoggerFactory.getLogger(PageImageStorageService.class);
    private final Path baseDir = Path.of("data", "page-images");

    public void store(int pageId, int imageId, byte[] data, String contentType) throws IOException {
        Path dir = baseDir.resolve(String.valueOf(pageId));
        Files.createDirectories(dir);
        Files.write(dir.resolve(String.valueOf(imageId)), data);
        if (contentType != null) {
            Files.writeString(dir.resolve(imageId + ".content-type"), contentType);
        }
    }

    public Optional<FileData> read(int pageId, int imageId) {
        Path file = baseDir.resolve(String.valueOf(pageId)).resolve(String.valueOf(imageId));
        if (!Files.exists(file)) return Optional.empty();
        try {
            byte[] data = Files.readAllBytes(file);
            Path ctFile = baseDir.resolve(String.valueOf(pageId)).resolve(imageId + ".content-type");
            String contentType = Files.exists(ctFile) ? Files.readString(ctFile).trim() : "application/octet-stream";
            return Optional.of(new FileData(data, contentType));
        } catch (IOException e) {
            log.error("Failed to read page image {}/{}", pageId, imageId, e);
            return Optional.empty();
        }
    }

    public void delete(int pageId, int imageId) {
        deleteQuietly(baseDir.resolve(String.valueOf(pageId)).resolve(String.valueOf(imageId)));
        deleteQuietly(baseDir.resolve(String.valueOf(pageId)).resolve(imageId + ".content-type"));
    }

    public void deleteAllForPage(int pageId) {
        Path dir = baseDir.resolve(String.valueOf(pageId));
        if (!Files.exists(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("Failed to list page image dir {}", dir, e);
        }
        deleteQuietly(dir);
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
        }
    }

    public record FileData(byte[] data, String contentType) {}
}
