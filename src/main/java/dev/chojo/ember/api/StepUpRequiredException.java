/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.api;

import dev.chojo.ember.api.auth.StepUpCategory;

/**
 * Raised when a route requires a fresh second-factor verification but the session's
 * last 2FA verification is outside the configured freshness window. The exception is
 * translated by {@link ApiServer} into a {@code 401} response with body
 * {@code {error:"step_up_required", category:"..."}} and header {@code X-StepUp-Required}.
 */
public class StepUpRequiredException extends RuntimeException {
    private final StepUpCategory category;

    public StepUpRequiredException(StepUpCategory category) {
        super("Step-up required: " + category.name());
        this.category = category;
    }

    public StepUpCategory category() {
        return category;
    }
}
