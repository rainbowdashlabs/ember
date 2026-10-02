/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.twofactor.service;

import dev.chojo.ember.feature.twofactor.entity.TwoFactorAuditEntry;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorEvent;
import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class TwoFactorAuditService {
    private static final Logger log = LoggerFactory.getLogger(TwoFactorAuditService.class);

    private final TwoFactorRepository repository;

    @Inject
    public TwoFactorAuditService(TwoFactorRepository repository) {
        this.repository = repository;
    }

    public void record(
            int accountId,
            @Nullable Integer actorId,
            TwoFactorEvent event,
            @Nullable TwoFactorKind factorKind,
            @Nullable String userAgent,
            @Nullable String country) {
        repository.audit(accountId, actorId, event, factorKind, userAgent, country);
        log.info("2FA audit: account={}, event={}, kind={}, actor={}", accountId, event, factorKind, actorId);
    }

    public List<TwoFactorAuditEntry> findByAccount(int accountId, int limit, int offset) {
        return repository.findAuditLog(accountId, limit, offset);
    }
}
