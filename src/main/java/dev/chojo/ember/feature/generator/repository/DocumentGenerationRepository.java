/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import dev.chojo.ember.feature.generator.entity.GenerationLogEntry;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The log of documents generated from templates, and whose data went into each.
 */
@Singleton
public class DocumentGenerationRepository {

    private static final String COLUMNS = """
            id, station_id, template_id, template_version, member_id, generated_by, generated_at, self_service,
            document_id, file_sha256, pdf_original_id, event_id, event_date, issuer_id, issuer_function, issuer_fixed,
            issuer_signs""";

    /**
     * Writes one entry of the log with the people whose data went into the document.
     *
     * @param entry    what was generated, its id ignored
     * @param subjects every person whose data is in the document
     * @return the entry as written
     */
    public DocumentGeneration log(DocumentGeneration entry, List<DataSubject> subjects) {
        var written = SqlSupport.insertReturning(
                """
                        INSERT INTO document_generation(station_id, template_id, template_version, member_id,
                                                        generated_by, generated_at, self_service, document_id,
                                                        file_sha256, pdf_original_id, event_id, event_date,
                                                        issuer_id, issuer_function, issuer_fixed, issuer_signs)
                        VALUES (:station_id, :template_id, :template_version, :member_id,
                                :generated_by, :generated_at, :self_service, :document_id, :file_hash,
                                :pdf_original_id, :event_id, :event_date, :issuer_id, :issuer_function,
                                :issuer_fixed, :issuer_signs)
                        RETURNING %s;""",
                call().bind("station_id", entry.stationId())
                        .bind("generated_at", entry.generatedAt(), INSTANT_TIMESTAMP)
                        .bind("template_id", entry.templateId())
                        .bind("template_version", entry.templateVersion())
                        .bind("member_id", entry.memberId())
                        .bind("generated_by", entry.generatedBy())
                        .bind("self_service", entry.selfService())
                        .bind("document_id", entry.documentId())
                        .bind("file_hash", entry.fileSha256())
                        .bind("pdf_original_id", entry.pdfOriginalId())
                        .bind("event_id", entry.eventId())
                        .bind("event_date", entry.eventDate())
                        .bind("issuer_id", entry.issuerId())
                        .bind("issuer_function", entry.issuerFunction())
                        .bind("issuer_fixed", entry.issuerFixed())
                        .bind("issuer_signs", entry.issuerSigns()),
                DocumentGeneration.map(),
                COLUMNS);
        for (var subject : subjects) {
            query("""
                    INSERT INTO document_generation_subject(generation_id, member_id, role)
                    VALUES (:generation_id, :member_id, :role)
                    ON CONFLICT DO NOTHING;""")
                    .single(call().bind("generation_id", written.id())
                            .bind("member_id", subject.memberId())
                            .bind("role", subject.role()))
                    .insert();
        }
        return written;
    }

    /**
     * @param generationId the entry
     * @return the entry, or empty where there is none
     */
    public Optional<DocumentGeneration> findById(int generationId) {
        return SqlSupport.findById("document_generation", COLUMNS, generationId, DocumentGeneration.map());
    }

    /**
     * @param generationId the entry
     * @return the people whose data went into the document, the member first
     */
    public List<DataSubject> subjects(int generationId) {
        return query("""
                SELECT member_id, role
                FROM document_generation_subject
                WHERE generation_id = :id
                ORDER BY role DESC, member_id;""")
                .single(call().bind("id", generationId))
                .map(DataSubject.map())
                .all();
    }

    /**
     * One page of the documents generated at a station, with the template each came from.
     *
     * @param stationId the station the documents were filed at
     * @param limit     how many entries at most
     * @param offset    how many of the newest entries to pass over
     * @return the entries, the newest first
     */
    public List<GenerationLogEntry> forStation(int stationId, int limit, int offset) {
        return query("""
                SELECT g.id, g.generated_at, g.template_id, t.name AS template_name, g.template_version,
                       t.cluster_id IS NOT NULL AS of_association, g.member_id, g.generated_by, g.self_service,
                       g.document_id, g.issuer_id, g.issuer_function
                FROM document_generation g
                JOIN document_template t ON t.id = g.template_id
                WHERE g.station_id = :station_id
                ORDER BY g.generated_at DESC, g.id DESC
                LIMIT :limit OFFSET :offset;""")
                .single(call().bind("station_id", stationId)
                        .bind("limit", limit)
                        .bind("offset", offset))
                .map(GenerationLogEntry.map())
                .all();
    }

    /**
     * When a member last generated a template for one person through self service.
     *
     * @param templateId the template
     * @param memberId   the member the document is about
     * @return the moment, or null where it never happened
     */
    public @Nullable Instant lastSelfService(int templateId, int memberId) {
        return query("""
                SELECT max(generated_at) AS last
                FROM document_generation
                WHERE template_id = :template_id
                  AND member_id = :member_id
                  AND self_service;""")
                .single(call().bind("template_id", templateId).bind("member_id", memberId))
                .map(row -> row.get("last", INSTANT_TIMESTAMP))
                .first()
                .orElse(null);
    }

    /**
     * When several templates were last generated for one person through self service, in one read.
     *
     * @param templateIds the templates
     * @param memberId    the member the documents are about
     * @return the moment by template; a template never generated for them is absent
     */
    public Map<Integer, Instant> lastSelfService(Collection<Integer> templateIds, int memberId) {
        if (templateIds.isEmpty()) return Map.of();
        var last = new HashMap<Integer, Instant>();
        query("""
                SELECT template_id, max(generated_at) AS last
                FROM document_generation
                WHERE template_id = ANY(:template_ids)
                  AND member_id = :member_id
                  AND self_service
                GROUP BY template_id;""")
                .single(call().bind("template_ids", List.copyOf(templateIds), PostgreSqlTypes.INTEGER)
                        .bind("member_id", memberId))
                .map(row -> Map.entry(row.getInt("template_id"), row.get("last", INSTANT_TIMESTAMP)))
                .all()
                .forEach(entry -> last.put(entry.getKey(), entry.getValue()));
        return last;
    }
}
