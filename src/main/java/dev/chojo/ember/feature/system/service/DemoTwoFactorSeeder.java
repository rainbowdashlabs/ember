/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.feature.twofactor.entity.TwoFactorKind;
import dev.chojo.ember.feature.twofactor.repository.TwoFactorRepository;
import dev.chojo.ember.feature.twofactor.service.TotpService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Switches two-factor authentication on for one seeded team member.
 *
 * <p>An instance where nobody has it looks the same whether the feature works or not: the operator's
 * overview of who is protected has nothing to show, and the security settings of every account offer
 * only the enrolment. One enrolled account gives both a subject.
 *
 * <p>The secret is written the way the enrolment writes it - encrypted, with the configured digits
 * and period - rather than through the enrolment itself, which asks for a code from an authenticator
 * nobody is holding here.
 */
@Singleton
public class DemoTwoFactorSeeder implements DemoPerStationSeeder {
    private static final Logger log = LoggerFactory.getLogger(DemoTwoFactorSeeder.class);

    private final TwoFactorRepository twoFactorRepository;
    private final TotpService totpService;

    @Inject
    public DemoTwoFactorSeeder(TwoFactorRepository twoFactorRepository, TotpService totpService) {
        this.twoFactorRepository = twoFactorRepository;
        this.totpService = totpService;
    }

    @Override
    public int order() {
        return MODULES;
    }

    @Override
    public void seedStation(DemoRunContext run, DemoStationContext station) {
        var members = station.members();
        if (members == null || members.betreuer().isEmpty()) return;

        var member = members.betreuer().getFirst();
        Integer accountId = member.accountId();
        if (accountId == null) return;

        var factor = twoFactorRepository.createFactor(accountId, TwoFactorKind.TOTP, "Authenticator");
        var config = totpService.config();
        twoFactorRepository.createTotp(
                factor.id(),
                totpService.encryptSecret(totpService.generateSecret()),
                (short) 1,
                (short) config.digits(),
                (short) config.periodSeconds(),
                config.algorithm());

        log.info("Demo: Enabled two-factor for account {}", accountId);
    }
}
