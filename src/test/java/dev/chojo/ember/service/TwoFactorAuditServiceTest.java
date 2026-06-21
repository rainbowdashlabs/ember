/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.service.TwoFactorAuditService;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TwoFactorAuditServiceTest extends RepositoryTestBase {

    @Test
    void recordAndQuery() {
        var account = accountRepo.create("audit-svc-" + UUID.randomUUID() + "@test.com", "Audit", "Svc", true);
        var service = new TwoFactorAuditService(twoFactorRepo);

        service.record(account.id(), null, TwoFactorEvent.ENROLLED, TwoFactorKind.TOTP, "ua", "DE");
        service.record(account.id(), null, TwoFactorEvent.LOGIN_VERIFIED, TwoFactorKind.TOTP, "ua", null);

        var entries = service.findByAccount(account.id(), 10, 0);
        assertEquals(2, entries.size());
    }
}
