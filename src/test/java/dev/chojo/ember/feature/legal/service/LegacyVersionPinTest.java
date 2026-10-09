/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.legal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LegacyVersionPinTest {
    @TempDir
    Path dir;

    @Test
    void thePinHoldsWhileTheVersionIsTheOneItWasPinnedTo() {
        LegacyVersionPin.pinOnce(dir, "legacy", "v1");

        assertEquals(Optional.of("legacy"), LegacyVersionPin.pinnedFor(dir, "v1"));
        assertEquals(Optional.empty(), LegacyVersionPin.pinnedFor(dir, "v2"));
    }

    @Test
    void aLegacyHashLeadsBackToItsVersion() {
        LegacyVersionPin.pinOnce(dir, "legacy", "v1");

        assertEquals(Optional.of("v1"), LegacyVersionPin.versionPinnedTo(dir, "legacy"));
        assertEquals(Optional.empty(), LegacyVersionPin.versionPinnedTo(dir, "other"));
    }

    @Test
    void aPinIsWrittenOnceAndNeverMoved() {
        LegacyVersionPin.pinOnce(dir, "legacy", "v1");
        LegacyVersionPin.pinOnce(dir, "later", "v1");

        assertEquals(Optional.of("legacy"), LegacyVersionPin.pinnedFor(dir, "v1"));
    }

    @Test
    void nothingIsPinnedWhereNothingWasWrittenOrTheFileIsUnreadable() throws IOException {
        assertEquals(Optional.empty(), LegacyVersionPin.pinnedFor(dir, "v1"));

        Files.writeString(dir.resolve("legacy-version.txt"), "garbage");
        assertEquals(Optional.empty(), LegacyVersionPin.pinnedFor(dir, "v1"));
    }

    @Test
    void aMissingFolderGetsNoPin() {
        Path missing = dir.resolve("missing");

        LegacyVersionPin.pinOnce(missing, "legacy", "v1");

        assertFalse(Files.exists(missing));
    }
}
