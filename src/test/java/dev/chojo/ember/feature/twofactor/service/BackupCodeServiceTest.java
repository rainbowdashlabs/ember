/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackupCodeServiceTest {

    private final BackupCodeService service = new BackupCodeService(new TwoFactorSettings());

    @Test
    void generatesConfiguredCount() {
        var codes = service.generateCodes();
        assertEquals(10, codes.size());
        for (var code : codes) {
            assertTrue(code.matches("[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}"), code + " is the xxxx-xxxx-xxxx shape");
        }
    }

    @Test
    void hashAndVerifyRoundTrip() {
        String code = "ABCD-1234-EFGH";
        String hash = service.hashCode(code);
        assertNotEquals(code, hash);
        assertTrue(service.verifyCode(code, hash));
        // Dashes and case are normalised
        assertTrue(service.verifyCode("abcd1234efgh", hash));
        assertFalse(service.verifyCode("WRONG-CODE-HERE", hash));
    }
}
