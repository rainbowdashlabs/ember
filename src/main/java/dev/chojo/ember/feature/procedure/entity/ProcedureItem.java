/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.procedure.entity;

import de.chojo.sadu.mapper.annotation.MappingProvider;
import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

public record ProcedureItem(
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
        @Nullable Integer checkedBy,
        ProcedureStepKind kind,
        @Nullable Integer documentTemplateId,
        @Nullable Integer generationId) {

    @MappingProvider("")
    public static RowMapping<ProcedureItem> map() {
        return row -> new ProcedureItem(
                row.getInt("id"),
                row.getInt("procedure_id"),
                row.getString("title"),
                row.getString("description"),
                row.getString("note"),
                row.getBoolean("public"),
                row.getBoolean("user_assigned"),
                row.getInt("position"),
                row.getBoolean("checked"),
                row.get("checked_at", INSTANT_TIMESTAMP),
                row.getObject("checked_by", Integer.class),
                ProcedureStepKind.valueOf(row.getString("kind")),
                row.getObject("document_template_id", Integer.class),
                row.getObject("generation_id", Integer.class));
    }

    /** @return whether the step asks to sign a document rather than to be ticked by hand */
    public boolean asksToSign() {
        return kind == ProcedureStepKind.DOCUMENT;
    }

    /** @return whether the step made its document and waits for its signatures, not ticked yet */
    public boolean awaitsSignatures() {
        return asksToSign() && generationId != null && !checked;
    }

    /** @return the step without the note of those who run it, as the member it is about reads it */
    public ProcedureItem withoutNote() {
        return new ProcedureItem(
                id,
                procedureId,
                title,
                description,
                null,
                isPublic,
                userAssigned,
                position,
                checked,
                checkedAt,
                checkedBy,
                kind,
                documentTemplateId,
                generationId);
    }
}
