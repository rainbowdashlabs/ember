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
 * Remembers, per legal document folder, the hash consents were recorded under before a document's
 * version left out the stored browser keys: the hash of the whole document as it read when the new
 * scheme first started, together with the new version it stood for.
 *
 * <p>Those consents stay current for as long as the new version is the same. Without the pin the
 * legacy hash would be taken from the document as it reads now, and the first key added to a known
 * category afterwards would move it and ask everybody who has not consented since. The pin lives in
 * {@value #FILE_NAME} beside the folder's version file and is written once; a folder that is not
 * kept between starts simply pins the document as it reads at each start.
 */
final class LegacyVersionPin {
    private static final Logger log = LoggerFactory.getLogger(LegacyVersionPin.class);
    private static final String FILE_NAME = "legacy-version.txt";

    private LegacyVersionPin() {}

    /**
     * Pins the legacy hash to the version, unless the folder already holds a pin.
     *
     * @param baseDir       the folder of the document
     * @param legacyVersion the hash of the whole document, every stored key included
     * @param version       the version over the text and the storage categories
     */
    static void pinOnce(Path baseDir, String legacyVersion, String version) {
        Path file = baseDir.resolve(FILE_NAME);
        if (!Files.isDirectory(baseDir) || Files.exists(file)) return;
        try {
            Files.writeString(file, legacyVersion + " " + version + "\n", StandardCharsets.UTF_8);
            log.info("Pinned legacy version {} to version {} in {}", legacyVersion, version, baseDir);
        } catch (IOException e) {
            log.error("Failed to pin the legacy version in {}", file, e);
        }
    }

    /**
     * The pinned legacy hash, if it was pinned to the given version.
     *
     * @param baseDir the folder of the document
     * @param version the version in force
     * @return the legacy hash still covering the version, or empty when nothing is pinned or the
     *         text or its categories changed since
     */
    static Optional<String> pinnedFor(Path baseDir, String version) {
        return read(baseDir).filter(pin -> pin.version().equals(version)).map(Pin::legacyVersion);
    }

    /**
     * The version a legacy hash was pinned to.
     *
     * @param baseDir       the folder of the document
     * @param legacyVersion a hash a consent was recorded under
     * @return the version the hash stood for, or empty when it is not the pinned one
     */
    static Optional<String> versionPinnedTo(Path baseDir, String legacyVersion) {
        return read(baseDir)
                .filter(pin -> pin.legacyVersion().equals(legacyVersion))
                .map(Pin::version);
    }

    private static Optional<Pin> read(Path baseDir) {
        Path file = baseDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) return Optional.empty();
        try {
            String[] parts =
                    Files.readString(file, StandardCharsets.UTF_8).strip().split(" ");
            if (parts.length != 2) return Optional.empty();
            return Optional.of(new Pin(parts[0], parts[1]));
        } catch (IOException e) {
            log.error("Failed to read the legacy version in {}", file, e);
            return Optional.empty();
        }
    }

    /**
     * One pin as it lies in the file.
     *
     * @param legacyVersion the hash of the whole document
     * @param version       the version it stood for
     */
    private record Pin(String legacyVersion, String version) {}
}
