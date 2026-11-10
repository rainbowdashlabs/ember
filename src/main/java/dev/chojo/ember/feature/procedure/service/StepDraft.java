/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.service;

import dev.chojo.ember.feature.procedure.entity.ProcedureStepKind;
import org.jspecify.annotations.Nullable;

/**
 * A step of a template or a procedure as it is added or changed.
 *
 * @param title              what the step is called
 * @param description        what the step asks for, or {@code null} for nothing
 * @param isPublic           whether the party and the member the procedure is about see it
 * @param userAssigned       whether the party ticks it
 * @param position           where it stands among the steps
 * @param kind               a step ticked by hand or a document to sign; {@code null} leaves a step as it is
 *                           and adds a new one ticked by hand
 * @param documentTemplateId the document template a document step asks to sign
 */
public record StepDraft(
        String title,
        @Nullable String description,
        boolean isPublic,
        boolean userAssigned,
        int position,
        @Nullable ProcedureStepKind kind,
        @Nullable Integer documentTemplateId) {}
