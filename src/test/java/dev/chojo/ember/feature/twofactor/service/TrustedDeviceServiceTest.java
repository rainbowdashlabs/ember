/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.auth.TokenHasher;
import dev.chojo.ember.conf.file.elements.TwoFactorSettings;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrustedDeviceServiceTest extends RepositoryTestBase {

    private static TrustedDeviceService service;

    @BeforeAll
    static void setupService() {
        service = new TrustedDeviceService(
                twoFactorRepo, TokenHasher.forTesting("trusted-test-pepper"), new TwoFactorSettings());
    }

    private int newAccount() {
        return accountRepo
                .create("td-svc-" + UUID.randomUUID() + "@test.com", "TD", "Svc", true)
                .id();
    }

    @Test
    void issueValidateRevoke() {
        int accountId = newAccount();
        var issued = service.issue(accountId, 7, "ua/test");
        assertNotNull(issued.token());
        assertEquals(accountId, issued.device().accountId());

        var validated = service.validate(issued.token()).orElseThrow();
        assertEquals(issued.device().id(), validated.id());

        assertTrue(service.validate(null).isEmpty());
        assertTrue(service.validate("").isEmpty());
        assertTrue(service.validate("garbage").isEmpty());

        assertEquals(1, service.list(accountId).size());
        assertTrue(service.revoke(issued.device().id(), accountId));
        assertFalse(service.revoke(issued.device().id(), accountId));
    }

    @Test
    void issueClampsDays() {
        int accountId = newAccount();
        // Default cap is 30 — asking for 999 clamps to 30.
        var capped = service.issue(accountId, 999, "ua");
        var seconds = capped.device().trustedUntil().getEpochSecond() - System.currentTimeMillis() / 1000;
        assertTrue(seconds <= 31L * 86400L, "trusted_until is capped at maxDays (30)");

        // Asking for 0 is clamped up to 1 (issue is always positive).
        var minimum = service.issue(accountId, 0, "ua");
        assertNotNull(minimum.token());
    }

    @Test
    void revokeAll() {
        int accountId = newAccount();
        service.issue(accountId, 1, "ua-1");
        service.issue(accountId, 1, "ua-2");
        assertEquals(2, service.list(accountId).size());

        service.revokeAll(accountId);
        assertEquals(0, service.list(accountId).size());
    }

    @Test
    void maxDaysReflectsSettings() {
        assertEquals(30, service.maxDays());
    }
}
