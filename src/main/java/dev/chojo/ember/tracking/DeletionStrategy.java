/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.tracking;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Strategy applied to an identity column when an account or member is deleted.
 *
 * @param column     column name this strategy applies to
 * @param strategy   deletion behaviour
 * @param reason     human-readable explanation
 * @param legalBasis legal justification — required when {@code strategy = RETAIN}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DeletionStrategy(String column, Strategy strategy, String reason, String legalBasis) {
    public DeletionStrategy(String column, Strategy strategy, String reason) {
        this(column, strategy, reason, null);
    }
}
