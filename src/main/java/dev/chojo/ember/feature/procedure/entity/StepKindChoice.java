/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.entity;

import org.jspecify.annotations.Nullable;

/**
 * What a step asks for: a tick by hand, or a signature on a document of the given template.
 *
 * @param kind               the kind of step
 * @param documentTemplateId the document template of a document step, null for a step ticked by hand
 */
public record StepKindChoice(
        ProcedureStepKind kind, @Nullable Integer documentTemplateId) {
    /** A step ticked by hand. */
    public static final StepKindChoice CHECK = new StepKindChoice(ProcedureStepKind.CHECK, null);

    /**
     * @param documentTemplateId the document template
     * @return a step asking to sign a document of that template
     */
    public static StepKindChoice document(int documentTemplateId) {
        return new StepKindChoice(ProcedureStepKind.DOCUMENT, documentTemplateId);
    }

    /**
     * @param item a template step
     * @return what it asks for
     */
    public static StepKindChoice of(ProcedureTemplateItem item) {
        return new StepKindChoice(item.kind(), item.documentTemplateId());
    }

    /**
     * @param item a step of a procedure
     * @return what it asks for
     */
    public static StepKindChoice of(ProcedureItem item) {
        return new StepKindChoice(item.kind(), item.documentTemplateId());
    }

    /** @return whether the step asks to sign a document rather than to be ticked by hand */
    public boolean asksToSign() {
        return kind == ProcedureStepKind.DOCUMENT;
    }
}
