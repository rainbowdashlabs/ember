/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.content.entity.ContentRows;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import dev.chojo.ember.owner.Owner;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * The document templates of a station or an association, and the content of their letters.
 *
 * <p>Nothing here deletes a template. The generation log points at every template a document came
 * from, so a template its owner is done with is archived instead.
 *
 * <p>An owner is a station or an association; the two columns are compared with
 * {@code IS NOT DISTINCT FROM}, so one query finds the templates of either.
 */
@Singleton
public class DocumentTemplateRepository {
    private static final String OWNED_BY = """
            station_id IS NOT DISTINCT FROM :station_id::int
            AND cluster_id IS NOT DISTINCT FROM :cluster_id::int""";

    /**
     * Writes a new template, without its content.
     *
     * @param owner    the station or the association that keeps it
     * @param draft    what it says
     * @param authorId the account that creates it
     * @return the template as written
     */
    public DocumentTemplate create(Owner owner, DocumentTemplateDraft draft, int authorId) {
        return query("""
                        INSERT INTO document_template(station_id, cluster_id, kind, name, title_pattern,
                                                      file_name_pattern, tags, hidden, keep_on_archive, legal,
                                                      for_appointments, self_service, self_service_cooldown_days,
                                                      restriction_mode, language, created_by, updated_by)
                        VALUES (:station_id, :cluster_id, :kind, :name, :title_pattern,
                                :file_name_pattern, :tags, :hidden, :keep_on_archive, :legal,
                                :for_appointments, :self_service, :cooldown_days, :restriction_mode,
                                :language, :author, :author)
                        RETURNING %s;""", DocumentTemplate.COLUMNS)
                .single(bindDraft(owned(owner).bind("kind", draft.kind()), draft)
                        .bind("author", authorId))
                .map(DocumentTemplate.map())
                .first()
                .orElseThrow();
    }

    /**
     * Rewrites a template, without its content, counting its version up.
     *
     * @param templateId the template
     * @param draft      what it says now
     * @param authorId   the account that changes it
     * @return the template as written, or empty where it does not exist
     */
    public Optional<DocumentTemplate> update(int templateId, DocumentTemplateDraft draft, int authorId) {
        return query("""
                        UPDATE document_template
                        SET name                       = :name,
                            title_pattern              = :title_pattern,
                            file_name_pattern          = :file_name_pattern,
                            tags                       = :tags,
                            hidden                     = :hidden,
                            keep_on_archive            = :keep_on_archive,
                            legal                      = :legal,
                            for_appointments           = :for_appointments,
                            self_service               = :self_service,
                            self_service_cooldown_days = :cooldown_days,
                            restriction_mode           = :restriction_mode,
                            language                   = :language,
                            version                   = version + 1,
                            updated_at                 = now(),
                            updated_by                 = :author
                        WHERE id = :id
                        RETURNING %s;""", DocumentTemplate.COLUMNS)
                .single(bindDraft(call().bind("id", templateId), draft).bind("author", authorId))
                .map(DocumentTemplate.map())
                .first();
    }

    /**
     * Counts a template's version up for a change that is not written through {@link #update}, such as
     * a new upload of its PDF.
     *
     * @param templateId the template
     * @param authorId   the account that changed it
     * @return the template as it now stands, or empty where it does not exist
     */
    public Optional<DocumentTemplate> countVersion(int templateId, int authorId) {
        return query("""
                        UPDATE document_template
                        SET version    = version + 1,
                            updated_at = now(),
                            updated_by = :author
                        WHERE id = :id
                        RETURNING %s;""", DocumentTemplate.COLUMNS)
                .single(call().bind("id", templateId).bind("author", authorId))
                .map(DocumentTemplate.map())
                .first();
    }

    private static Call bindDraft(Call call, DocumentTemplateDraft draft) {
        return call.bind("name", draft.name())
                .bind("title_pattern", draft.titlePattern())
                .bind("file_name_pattern", draft.fileNamePattern())
                .bind("tags", draft.tags(), PostgreSqlTypes.TEXT)
                .bind("hidden", draft.hidden())
                .bind("keep_on_archive", draft.keepOnArchive())
                .bind("legal", draft.legal())
                .bind("for_appointments", draft.forAppointments())
                .bind("self_service", draft.selfService())
                .bind("cooldown_days", draft.cooldownDays())
                .bind("restriction_mode", draft.restrictionMode())
                .bind("language", draft.language());
    }

    /**
     * Writes what a letter template says, replacing what it said before.
     *
     * @param templateId the template
     * @param letter     the letter
     */
    public void writeLetter(int templateId, LetterContent letter) {
        query("""
                INSERT INTO document_template_letter(template_id, header, footer, body, page)
                VALUES (:template_id, :header::jsonb, :footer::jsonb, :body::jsonb, :page::jsonb)
                ON CONFLICT (template_id) DO UPDATE
                    SET header = excluded.header,
                        footer = excluded.footer,
                        body   = excluded.body,
                        page   = excluded.page;""")
                .single(call().bind("template_id", templateId)
                        .bind("header", ContentRows.toJson(letter.header()))
                        .bind("footer", ContentRows.toJson(letter.footer()))
                        .bind("body", ContentRows.toJson(letter.body()))
                        .bind("page", letter.page().toJson()))
                .insert();
    }

    /**
     * @param templateId the template
     * @return the template, or empty where it does not exist
     */
    public Optional<DocumentTemplate> findById(int templateId) {
        return query("SELECT %s FROM document_template WHERE id = :id;", DocumentTemplate.COLUMNS)
                .single(call().bind("id", templateId))
                .map(DocumentTemplate.map())
                .first();
    }

    /**
     * The templates of an owner by name.
     *
     * @param owner    the station or the association
     * @param archived whether to list the archived ones instead of those in use
     * @return the templates
     */
    public List<DocumentTemplate> findByOwner(Owner owner, boolean archived) {
        return query("""
                SELECT %s
                FROM document_template
                WHERE %s
                  AND (archived_at IS NOT NULL) = :archived
                ORDER BY lower(name), id;""", DocumentTemplate.COLUMNS, OWNED_BY)
                .single(owned(owner).bind("archived", archived))
                .map(DocumentTemplate.map())
                .all();
    }

    /**
     * The templates in use of the association a station belongs to, by name.
     *
     * @param stationId the station
     * @return the templates, none where the station belongs to no association
     */
    public List<DocumentTemplate> findOfAssociationOf(int stationId) {
        return query("""
                SELECT %s
                FROM document_template
                WHERE archived_at IS NULL
                  AND cluster_id = (SELECT cluster_id FROM station WHERE id = :station_id)
                ORDER BY lower(name), id;""", DocumentTemplate.COLUMNS)
                .single(call().bind("station_id", stationId))
                .map(DocumentTemplate.map())
                .all();
    }

    /**
     * @param templateId the template
     * @return what its letter says, or empty where it has none
     */
    public Optional<LetterContent> findLetter(int templateId) {
        return query("""
                SELECT header::text AS header, footer::text AS footer, body::text AS body, page::text AS page
                FROM document_template_letter
                WHERE template_id = :template_id;""")
                .single(call().bind("template_id", templateId))
                .map(LetterContent.map())
                .first();
    }

    /**
     * Archives a template or takes it back into use.
     *
     * @param templateId the template
     * @param archived   whether it is archived from now on
     * @param authorId   the account that does it
     * @return whether the template exists
     */
    public boolean setArchived(int templateId, boolean archived, int authorId) {
        return query("""
                UPDATE document_template
                SET archived_at = CASE WHEN :archived THEN coalesce(archived_at, now()) END,
                    updated_at  = now(),
                    updated_by  = :author
                WHERE id = :id;""")
                .single(call().bind("id", templateId).bind("archived", archived).bind("author", authorId))
                .update()
                .changed();
    }

    /**
     * When each template was last generated from, by anybody and in any way: at the station for a
     * station, which counts the templates of its association it uses there as well, and at any of its
     * stations for an association.
     *
     * @param owner the station or the association
     * @return the newest entry of the generation log by template, leaving out the templates never used
     */
    public Map<Integer, Instant> lastUsedAt(Owner owner) {
        return switch (owner) {
            case Owner.Station station -> lastUsed("""
                        SELECT template_id, max(generated_at) AS last_used_at
                        FROM document_generation
                        WHERE station_id = :station_id
                        GROUP BY template_id;""", call().bind("station_id", station.stationId()));
            case Owner.Association association -> lastUsed("""
                        SELECT g.template_id, max(g.generated_at) AS last_used_at
                        FROM document_generation g
                        JOIN document_template t ON t.id = g.template_id
                        WHERE t.cluster_id = :cluster_id
                        GROUP BY g.template_id;""", call().bind("cluster_id", association.clusterId()));
            case Owner.Instance ignored -> Map.of();
        };
    }

    private static Map<Integer, Instant> lastUsed(String sql, Call call) {
        return query(sql)
                .single(call)
                .map(row -> Map.entry(row.getInt("template_id"), row.get("last_used_at", INSTANT_TIMESTAMP)))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * Whether an appointment or an appointment template names the template as a document to bring.
     *
     * @param templateId the template
     * @return whether anything requires it
     */
    public boolean requiredByAppointments(int templateId) {
        return query("""
                SELECT EXISTS (SELECT 1
                               FROM event_document_requirement
                               WHERE template_id = :template_id) AS required;""")
                .single(call().bind("template_id", templateId))
                .map(row -> row.getBoolean("required"))
                .first()
                .orElse(false);
    }

    /**
     * Whether an owner has another template in use by this name, ignoring case.
     *
     * @param owner    the station or the association
     * @param name     the name
     * @param exceptId a template to leave out, the one being renamed, or null
     * @return whether the name is taken
     */
    public boolean nameTaken(Owner owner, String name, @Nullable Integer exceptId) {
        return query("""
                SELECT EXISTS (SELECT 1
                               FROM document_template
                               WHERE %s
                                 AND archived_at IS NULL
                                 AND lower(name) = lower(:name)
                                 AND id IS DISTINCT FROM :except::int) AS taken;""", OWNED_BY)
                .single(owned(owner).bind("name", name).bind("except", exceptId))
                .map(row -> row.getBoolean("taken"))
                .first()
                .orElse(false);
    }

    private static Call owned(Owner owner) {
        Integer stationId = owner instanceof Owner.Station station ? station.stationId() : null;
        Integer clusterId = owner instanceof Owner.Association association ? association.clusterId() : null;
        return call().bind("station_id", stationId).bind("cluster_id", clusterId);
    }
}
