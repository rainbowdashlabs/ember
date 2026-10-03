/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import dev.chojo.ember.feature.generator.entity.DataSubject;
import dev.chojo.ember.feature.generator.entity.DocumentGeneration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
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
            document_id, file_sha256, pdf_original_id, event_id, event_date""";

    /**
     * Writes one entry of the log with the people whose data went into the document.
     *
     * @param entry    what was generated, its id ignored
     * @param subjects every person whose data is in the document
     * @return the entry as written
     */
    public DocumentGeneration log(DocumentGeneration entry, List<DataSubject> subjects) {
        var written = query("""
                        INSERT INTO document_generation(station_id, template_id, template_version, member_id,
                                                        generated_by, generated_at, self_service, document_id,
                                                        file_sha256, pdf_original_id, event_id, event_date)
                        VALUES (:station_id, :template_id, :template_version, :member_id,
                                :generated_by, :generated_at, :self_service, :document_id, :file_hash,
                                :pdf_original_id, :event_id, :event_date)
                        RETURNING %s;""", COLUMNS)
                .single(call().bind("station_id", entry.stationId())
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
                        .bind("event_date", entry.eventDate()))
                .map(DocumentGeneration.map())
                .first()
                .orElseThrow();
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
        return query("SELECT %s FROM document_generation WHERE id = :id;", COLUMNS)
                .single(call().bind("id", generationId))
                .map(DocumentGeneration.map())
                .first();
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
}
