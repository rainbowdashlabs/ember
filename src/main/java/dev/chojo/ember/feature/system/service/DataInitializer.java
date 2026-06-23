/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Initializes the data directory from bundled templates if files are not present.
 * Templates are stored as classpath resources under {@code /templates/data/}.
 */
@Singleton
public class DataInitializer {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private static final String[] TEMPLATE_FILES = {
        "documents/consent/de/01-consent.md",
        "documents/consent/en/01-consent.md",
        "documents/imprint/de/01-impressum.md",
        "documents/imprint/en/01-imprint.md",
        "documents/privacy/de/01-general.md",
        "documents/privacy/de/02-rights.md",
        "documents/privacy/en/01-general.md",
        "documents/privacy/en/02-rights.md",
        "documents/tos/de/01-nutzungsbedingungen.md",
        "documents/tos/en/01-terms.md",
    };

    /**
     * Copies template files from classpath resources to the data directory
     * if they don't already exist. Also ensures runtime directories exist.
     */
    public void initialize() {
        Path dataDir = Path.of("data");
        int copied = 0;

        for (String templateFile : TEMPLATE_FILES) {
            Path target = dataDir.resolve(templateFile);
            if (Files.exists(target)) {
                continue;
            }

            String resourcePath = "/templates/data/" + templateFile;
            try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
                if (in == null) {
                    log.warn("Template resource not found: {}", resourcePath);
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(in, target);
                copied++;
            } catch (IOException e) {
                log.error("Failed to copy template {} to {}", resourcePath, target, e);
            }
        }

        // Ensure runtime directories exist
        ensureDirectory(dataDir.resolve("images"));
        ensureDirectory(dataDir.resolve("kb-files"));

        if (copied > 0) {
            log.info("Initialized {} template files in data directory", copied);
        }
    }

    private void ensureDirectory(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("Failed to create directory {}", dir, e);
        }
    }
}
