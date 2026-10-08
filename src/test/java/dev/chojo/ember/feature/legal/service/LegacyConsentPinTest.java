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

class LegacyConsentPinTest {
    @TempDir
    Path dir;

    @Test
    void thePinHoldsWhileTheConsentVersionIsTheOneItWasPinnedTo() {
        LegacyConsentPin.pinOnce(dir, "legacy", "v1");

        assertEquals(Optional.of("legacy"), LegacyConsentPin.pinnedFor(dir, "v1"));
        assertEquals(Optional.empty(), LegacyConsentPin.pinnedFor(dir, "v2"));
    }

    @Test
    void aPinIsWrittenOnceAndNeverMoved() {
        LegacyConsentPin.pinOnce(dir, "legacy", "v1");
        LegacyConsentPin.pinOnce(dir, "later", "v1");

        assertEquals(Optional.of("legacy"), LegacyConsentPin.pinnedFor(dir, "v1"));
    }

    @Test
    void nothingIsPinnedWhereNothingWasWrittenOrTheFileIsUnreadable() throws IOException {
        assertEquals(Optional.empty(), LegacyConsentPin.pinnedFor(dir, "v1"));

        Files.writeString(dir.resolve("legacy-version.txt"), "garbage");
        assertEquals(Optional.empty(), LegacyConsentPin.pinnedFor(dir, "v1"));
    }

    @Test
    void aMissingConsentDirectoryGetsNoPin() {
        Path missing = dir.resolve("missing");

        LegacyConsentPin.pinOnce(missing, "legacy", "v1");

        assertFalse(Files.exists(missing));
    }
}
