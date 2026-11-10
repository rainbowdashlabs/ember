/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.entity;

/** What a step of a procedure asks for. */
public enum ProcedureStepKind {
    /** A step ticked by hand. */
    CHECK,
    /** A document about the procedure's member to sign, which ticks itself once it is settled. */
    DOCUMENT
}
