/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

/**
 * One member of a generation run and how it went for them.
 *
 * @param memberId      the member
 * @param status        how far the run got with them
 * @param documentId    the document filed for them, or null where none was or it is gone
 * @param refusalCode   the code of the refusal that kept a document from being filed, or null
 * @param refusalDetail what that refusal was about, or null where it named nothing
 */
public record GenerationJobMember(
        int memberId,
        JobMemberStatus status,
        @Nullable Integer documentId,
        @Nullable String refusalCode,
        @Nullable String refusalDetail) {

    public static RowMapping<GenerationJobMember> map() {
        return row -> new GenerationJobMember(
                row.getInt("member_id"),
                row.getEnum("status", JobMemberStatus.class),
                row.getObject("document_id", Integer.class),
                row.getString("refusal_code"),
                row.getString("refusal_detail"));
    }
}
