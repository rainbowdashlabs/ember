/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Remembers the consent version that consents were recorded under before the consent version left
 * out the stored keys: the hash of the whole consent document as it read when the new scheme first
 * started, together with the new version it stood for.
 *
 * <p>Those consents stay current for as long as the new version is the same. Without the pin the
 * legacy hash would be taken from the document as it reads now, and the first key added to a known
 * category afterwards would move it and ask everybody who has not consented since. The pin lives in
 * {@value #FILE_NAME} beside the document's own version file and is written once; a consent
 * directory that is not kept between starts simply pins the document as it reads at each start.
 */
final class LegacyConsentPin {
    private static final Logger log = LoggerFactory.getLogger(LegacyConsentPin.class);
    private static final String FILE_NAME = "legacy-version.txt";

    private LegacyConsentPin() {}

    /**
     * Pins the legacy hash to the consent version, unless a pin already exists.
     *
     * @param consentDir     the directory of the consent document
     * @param legacyVersion  the hash of the whole consent document, every stored key included
     * @param consentVersion the consent version over the text and the storage categories
     */
    static void pinOnce(Path consentDir, String legacyVersion, String consentVersion) {
        Path file = consentDir.resolve(FILE_NAME);
        if (!Files.isDirectory(consentDir) || Files.exists(file)) return;
        try {
            Files.writeString(file, legacyVersion + " " + consentVersion + "\n", StandardCharsets.UTF_8);
            log.info("Pinned legacy consent version {} to consent version {}", legacyVersion, consentVersion);
        } catch (IOException e) {
            log.error("Failed to pin the legacy consent version in {}", file, e);
        }
    }

    /**
     * The pinned legacy hash, if it was pinned to the given consent version.
     *
     * @param consentDir     the directory of the consent document
     * @param consentVersion the consent version in force
     * @return the legacy hash still covering the consent version, or empty when nothing is pinned
     *         or the consent text or its categories changed since
     */
    static Optional<String> pinnedFor(Path consentDir, String consentVersion) {
        Path file = consentDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            String[] parts =
                    Files.readString(file, StandardCharsets.UTF_8).strip().split(" ");
            if (parts.length != 2 || !parts[1].equals(consentVersion)) return Optional.empty();
            return Optional.of(parts[0]);
        } catch (IOException e) {
            log.error("Failed to read the legacy consent version in {}", file, e);
            return Optional.empty();
        }
    }
}
