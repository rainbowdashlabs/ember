/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.inventory.service;

import dev.chojo.ember.api.ApiException;
import dev.chojo.ember.feature.inventory.entity.FlowProblem;
import io.javalin.http.HttpStatus;

/**
 * Refuses a change to a chain, naming the rule that stands in the way.
 *
 * <p>Carries the fault as a code rather than as a sentence, so a refusal reads in the same words as
 * the same fault shown on the chain itself, and in the reader's language rather than in English.
 */
public class FlowRefusedException extends ApiException {

    private final transient FlowProblem problem;

    public FlowRefusedException(FlowProblem problem) {
        super(HttpStatus.BAD_REQUEST, problem.code().name());
        this.problem = problem;
    }

    public FlowRefusedException(FlowProblem.Code code) {
        this(FlowProblem.of(code));
    }

    /** What is wrong with the chain the change would have left behind. */
    public FlowProblem problem() {
        return problem;
    }
}
