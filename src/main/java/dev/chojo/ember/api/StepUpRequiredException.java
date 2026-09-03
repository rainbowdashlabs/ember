/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;

import java.util.Set;

/**
 * Raised when a route requires a fresh proof of presence but the session's last verification is
 * outside the configured freshness window. The exception is translated by {@link ApiServer}
 * into a {@code 401} response with body
 * {@code {error:"step_up_required", category:"...", proofs:[...]}} and header
 * {@code X-StepUp-Required}. The proofs are what this account can currently give, so the dialog
 * offers exactly those and never one nobody can produce.
 */
public class StepUpRequiredException extends RuntimeException {
    private final StepUpCategory category;
    private final Set<StepUpProof> proofs;

    public StepUpRequiredException(StepUpCategory category, Set<StepUpProof> proofs) {
        super("Step-up required: " + category.name());
        this.category = category;
        this.proofs = proofs;
    }

    public StepUpCategory category() {
        return category;
    }

    public Set<StepUpProof> proofs() {
        return proofs;
    }
}
