/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The latest copy of a document an appointment asks for, generated for one participant and still filed.
 *
 * @param templateId      the template
 * @param memberId        the participant
 * @param generationId    its entry in the generation log
 * @param templateVersion the version of the template it was generated from
 * @param generatedAt     when it was generated
 * @param documentId      the member document it was filed as
 */
public record RequirementGeneration(
        int templateId, int memberId, int generationId, int templateVersion, Instant generatedAt, int documentId) {

    public static RowMapping<RequirementGeneration> map() {
        return row -> new RequirementGeneration(
                row.getInt("template_id"),
                row.getInt("member_id"),
                row.getInt("id"),
                row.getInt("template_version"),
                row.get("generated_at", INSTANT_TIMESTAMP),
                row.getInt("document_id"));
    }
}
