/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.entity;

import org.jspecify.annotations.Nullable;

import java.time.Instant;

/** Steps ticked by hand, for tests that build them without saying they ask for no document. */
public final class ProcedureSteps {
    private ProcedureSteps() {}

    /** A step of a procedure ticked by hand. */
    public static ProcedureItem check(
            int id,
            int procedureId,
            String title,
            @Nullable String description,
            @Nullable String note,
            boolean isPublic,
            boolean userAssigned,
            int position,
            boolean checked,
            @Nullable Instant checkedAt,
            @Nullable Integer checkedBy) {
        return new ProcedureItem(
                id,
                procedureId,
                title,
                description,
                note,
                isPublic,
                userAssigned,
                position,
                checked,
                checkedAt,
                checkedBy,
                ProcedureStepKind.CHECK,
                null,
                null);
    }

    /** A step of a template ticked by hand. */
    public static ProcedureTemplateItem templateCheck(
            int id,
            int templateId,
            String title,
            @Nullable String description,
            boolean isPublic,
            boolean userAssigned,
            int position) {
        return new ProcedureTemplateItem(
                id, templateId, title, description, isPublic, userAssigned, position, ProcedureStepKind.CHECK, null);
    }
}
