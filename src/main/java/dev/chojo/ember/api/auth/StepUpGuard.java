/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api.auth;

import dev.chojo.ember.api.StepUpRequiredException;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.conf.file.elements.Auth;
import dev.chojo.ember.conf.file.elements.Demo;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;

/**
 * Decides whether a session has proved itself recently enough for a sensitive operation. One
 * place rather than two, because the route middleware and the handlers that guard a single
 * branch of an otherwise harmless route have to agree on what "fresh" means.
 *
 * <p>There is no exemption for an account with no second factor: that used to wave through
 * exactly the accounts a passkey-only setup creates. What such an account answers with is the
 * password, which every password sign-in stamps; where even that is missing, the refusal names
 * what is left. Only the public demo instance stays exempt: a visitor there clicked a face and
 * never typed anything, so asking them for a password would end the demo. A dev run knows its
 * seeded passwords and answers like a member would, which keeps the end-to-end stories honest.
 */
@Singleton
public class StepUpGuard {
    private final Auth authConfig;
    private final Demo demoConfig;
    private final TwoFactorService twoFactorService;

    @Inject
    public StepUpGuard(Auth authConfig, Demo demoConfig, TwoFactorService twoFactorService) {
        this.authConfig = authConfig;
        this.demoConfig = demoConfig;
        this.twoFactorService = twoFactorService;
    }

    /**
     * Whether the session proved itself inside the freshness window.
     */
    public boolean isFresh(UserSession session) {
        if (demoConfig.enabled()) return true;
        Instant verifiedAt = session.twoFactorVerifiedAt();
        if (verifiedAt == null) return false;
        Duration freshness = Duration.ofSeconds(authConfig.twoFactor().stepUpFreshnessSeconds());
        return verifiedAt.isAfter(Instant.now().minus(freshness));
    }

    /**
     * Refuses with the step-up demand unless the session is fresh. The refusal names what the
     * account can currently prove itself with, so the dialog offers exactly those.
     */
    public void require(UserSession session, StepUpCategory category) {
        if (isFresh(session)) return;
        throw new StepUpRequiredException(category, twoFactorService.availableProofs(session.accountId()));
    }
}
