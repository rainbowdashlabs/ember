/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

/**
 * Where one participant stands with one document an appointment asks them to bring.
 */
public enum RequirementStatus {
    /** No copy was generated for them yet, or the one generated was deleted. */
    NOT_GENERATED,
    /** A copy filled with their data was generated and filed with them. */
    GENERATED
}
