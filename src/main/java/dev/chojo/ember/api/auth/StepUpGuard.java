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
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;
import dev.chojo.ember.feature.twofactor.service.TwoFactorService;
import io.javalin.http.ForbiddenResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

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
        throw new StepUpRequiredException(
                category, twoFactorService.availableProofs(session.accountId(), session.sessionId()));
    }

    /**
     * Refuses unless somebody proved themselves at this keyboard, just now.
     *
     * <p>For the one thing a session can do that vouches for another device. Two differences from
     * {@link #require}, and the feature rests on both.
     *
     * <p>The proof must be local. A stamp another device's approval produced is refused here and is
     * left out of what the refusal offers, because a session made fresh by being vouched for cannot
     * be the thing that vouches: two sessions would relay freshness to each other for ever and one
     * proof from months ago would keep a pair of stolen cookies approving new devices.
     *
     * <p>And the window is its own, far shorter than the one every other sensitive route shares, so
     * approving is an answer given now rather than a right earned earlier in the session. The caller
     * spends the stamp afterwards, which is what makes it once rather than once per window.
     */
    public void requireLocalProof(UserSession session, StepUpCategory category) {
        if (session.vouchedFor()) {
            // Not a step-up demand: no proof this session could give would change the answer, and a
            // dialog offering one would be a dialog that cannot be satisfied.
            throw new ForbiddenResponse("A session another device vouched for cannot vouch for one");
        }
        if (demoConfig.enabled()) return;
        if (isLocalAndRecent(session)) return;
        throw new StepUpRequiredException(category, localProofs(session.accountId()));
    }

    private boolean isLocalAndRecent(UserSession session) {
        StepUpProof proof = session.twoFactorProof();
        if (proof == null || !proof.isLocal()) return false;
        Instant verifiedAt = session.twoFactorVerifiedAt();
        if (verifiedAt == null) return false;
        Duration window = Duration.ofSeconds(authConfig.twoFactor().localProofFreshnessSeconds());
        return verifiedAt.isAfter(Instant.now().minus(window));
    }

    /** What the dialog may offer where being vouched for is not an answer. */
    private Set<StepUpProof> localProofs(int accountId) {
        Set<StepUpProof> proofs = twoFactorService.availableProofs(accountId);
        proofs.removeIf(proof -> !proof.isLocal());
        return proofs;
    }
}
