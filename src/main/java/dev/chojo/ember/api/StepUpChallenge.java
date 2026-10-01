/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.StepUpCategory;
import dev.chojo.ember.feature.twofactor.entity.StepUpProof;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The body of the {@code 401} a route answers when it wants a fresh proof before it acts.
 *
 * @param error    always {@value #ERROR}, which is how a client tells this refusal from a session that ended
 * @param category what the proof is demanded for
 * @param proofs   what this account can give right now, sorted by name, so the dialog offers exactly those
 */
public record StepUpChallenge(String error, StepUpCategory category, List<StepUpProof> proofs) {

    /** The error name every step-up challenge carries. */
    public static final String ERROR = "step_up_required";

    /**
     * The challenge for a category and the proofs an account can give.
     *
     * @param category what the proof is demanded for
     * @param proofs   what the account can give
     * @return the body to answer with
     */
    public static StepUpChallenge of(StepUpCategory category, Collection<StepUpProof> proofs) {
        return new StepUpChallenge(
                ERROR,
                category,
                proofs.stream().sorted(Comparator.comparing(Enum::name)).toList());
    }
}
