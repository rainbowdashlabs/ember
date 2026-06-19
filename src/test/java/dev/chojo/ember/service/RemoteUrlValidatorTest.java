/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.conf.file.elements.Federation;
import dev.chojo.ember.feature.federation.service.RemoteUrlValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteUrlValidatorTest {

    private static RemoteUrlValidator strict() {
        return new RemoteUrlValidator(new Federation());
    }

    private static RemoteUrlValidator permissive() {
        return new RemoteUrlValidator(new Federation() {
            @Override
            public boolean allowPrivateHosts() {
                return true;
            }
        });
    }

    @Test
    void rejectsHttpScheme() {
        assertFalse(strict().isAllowed("http://example.com"));
    }

    @Test
    void rejectsLoopbackLiteralV4() {
        assertFalse(strict().isAllowed("https://127.0.0.1"));
    }

    @Test
    void rejectsLoopbackLiteralV6() {
        assertFalse(strict().isAllowed("https://[::1]"));
    }

    @Test
    void rejectsPrivateRanges() {
        assertFalse(strict().isAllowed("https://10.0.0.1"));
        assertFalse(strict().isAllowed("https://192.168.1.1"));
        assertFalse(strict().isAllowed("https://172.16.0.1"));
    }

    @Test
    void rejectsLinkLocalAndMetadata() {
        assertFalse(strict().isAllowed("https://169.254.169.254"));
    }

    @Test
    void rejectsIpv6LinkLocal() {
        assertFalse(strict().isAllowed("https://[fe80::1]"));
    }

    @Test
    void rejectsMappedV4Loopback() {
        assertFalse(strict().isAllowed("https://[::ffff:127.0.0.1]"));
    }

    @Test
    void rejectsNullAndBlank() {
        assertFalse(strict().isAllowed(null));
        assertFalse(strict().isAllowed(""));
        assertFalse(strict().isAllowed("   "));
    }

    @Test
    void rejectsLocalhostHostname() {
        assertFalse(strict().isAllowed("https://localhost"));
    }

    @Test
    void permissiveModeAllowsHttpAndLoopback() {
        assertTrue(permissive().isAllowed("http://localhost"));
        assertTrue(permissive().isAllowed("https://127.0.0.1"));
    }

    @Test
    void validateThrowsForPrivateHost() {
        var ex = assertThrows(IllegalArgumentException.class, () -> strict().validate("http://example.com"));
        assertEquals(RemoteUrlValidator.rejectReason(), ex.getMessage());
    }

    @Test
    void validateThrowsForBlank() {
        assertThrows(IllegalArgumentException.class, () -> strict().validate(""));
    }
}
