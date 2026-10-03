/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * One entry of a station's generation log together with the template it came from, as the list of
 * generated documents reads it.
 *
 * @param id              the entry identifier
 * @param generatedAt     when the document was generated
 * @param templateId      the template it came from
 * @param templateName    what the template is called now
 * @param templateVersion the version of the template at the time
 * @param ofAssociation   whether the template is one of the station's association
 * @param memberId        the member it is about, or null once they were deleted
 * @param generatedBy     who generated it, or null once they are gone
 * @param selfService     whether it was generated through self service
 * @param documentId      the member document it was filed as, or null once that was deleted
 * @param issuerId        the member who issued it, or null where it names none or they are gone
 * @param issuerFunction  what the issuer does at the station, as printed, or null
 */
public record GenerationLogEntry(
        int id,
        Instant generatedAt,
        int templateId,
        String templateName,
        int templateVersion,
        boolean ofAssociation,
        @Nullable Integer memberId,
        @Nullable Integer generatedBy,
        boolean selfService,
        @Nullable Integer documentId,
        @Nullable Integer issuerId,
        @Nullable String issuerFunction) {

    public static RowMapping<GenerationLogEntry> map() {
        return row -> new GenerationLogEntry(
                row.getInt("id"),
                row.get("generated_at", INSTANT_TIMESTAMP),
                row.getInt("template_id"),
                row.getString("template_name"),
                row.getInt("template_version"),
                row.getBoolean("of_association"),
                row.getObject("member_id", Integer.class),
                row.getObject("generated_by", Integer.class),
                row.getBoolean("self_service"),
                row.getObject("document_id", Integer.class),
                row.getObject("issuer_id", Integer.class),
                row.getString("issuer_function"));
    }
}
