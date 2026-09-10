/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScannerProbesTest {
    @Test
    void everySpellingOfTheCredentialsFileIsAProbe() {
        assertTrue(ScannerProbes.looksLikeAProbe("/api/.env"));
        assertTrue(ScannerProbes.looksLikeAProbe("/api/v1/.env"));
        assertTrue(ScannerProbes.looksLikeAProbe("/api/v3/.env"));
        assertTrue(ScannerProbes.looksLikeAProbe("/api/staging/.env"));
        assertTrue(ScannerProbes.looksLikeAProbe("/api/dev/.env"));
    }

    @Test
    void anythingUnderADottedNameIsAProbe() {
        assertTrue(ScannerProbes.looksLikeAProbe("/.git/config"));
        assertTrue(ScannerProbes.looksLikeAProbe("/.aws/credentials"));
        assertTrue(ScannerProbes.looksLikeAProbe("/api/v1/../../etc/passwd"));
    }

    /** The web asks for this one by agreement, so it is an address and not a guess at one. */
    @Test
    void theAgreedDottedDirectoryIsNotAProbe() {
        assertFalse(ScannerProbes.looksLikeAProbe("/.well-known/security.txt"));
        assertFalse(ScannerProbes.looksLikeAProbe("/.well-known/webfinger"));
    }

    @Test
    void softwareThisIsNotIsAProbeByNameAndByFileType() {
        assertTrue(ScannerProbes.looksLikeAProbe("/wp-login.php"));
        assertTrue(ScannerProbes.looksLikeAProbe("/wp-admin/setup-config"));
        assertTrue(ScannerProbes.looksLikeAProbe("/PhpMyAdmin/index"));
        assertTrue(ScannerProbes.looksLikeAProbe("/actuator/health"));
        assertTrue(ScannerProbes.looksLikeAProbe("/config.yml"));
    }

    /**
     * The addresses worth an operator's attention: a client asking for something that should be there
     * and is not. Quietening these is the failure this list has to avoid.
     */
    @Test
    void anAddressOfThisApplicationIsNeverAProbe() {
        assertFalse(ScannerProbes.looksLikeAProbe("/api/v1/members/17/avatar"));
        assertFalse(ScannerProbes.looksLikeAProbe("/api/v1/exchanges/48/status"));
        assertFalse(ScannerProbes.looksLikeAProbe("/api/v2/stations"));
        assertFalse(ScannerProbes.looksLikeAProbe("/station/inventory/exchanges"));
        assertFalse(ScannerProbes.looksLikeAProbe("/"));
        assertFalse(ScannerProbes.looksLikeAProbe(""));
        assertFalse(ScannerProbes.looksLikeAProbe(null));
    }
}
