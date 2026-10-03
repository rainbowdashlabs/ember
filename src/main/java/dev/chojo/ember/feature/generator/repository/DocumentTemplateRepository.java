/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.generator.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;
import de.chojo.sadu.queries.api.call.Call;
import dev.chojo.ember.feature.generator.entity.DocumentTemplate;
import dev.chojo.ember.feature.generator.entity.DocumentTemplateDraft;
import dev.chojo.ember.feature.generator.entity.LetterContent;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The document templates of a station and the content of their letters.
 *
 * <p>Nothing here deletes a template. The generation log points at every template a document came
 * from, so a template a station is done with is archived instead.
 */
@Singleton
public class DocumentTemplateRepository {

    /**
     * Writes a new template, without its content.
     *
     * @param stationId the station that owns it
     * @param draft     what it says
     * @param authorId  the member who creates it
     * @return the template as written
     */
    public DocumentTemplate create(int stationId, DocumentTemplateDraft draft, int authorId) {
        return query("""
                        INSERT INTO document_template(station_id, kind, name, title_pattern, file_name_pattern, tags,
                                                      hidden, keep_on_archive, legal, self_service,
                                                      self_service_cooldown_days, restriction_mode, pronoun_field_id,
                                                      pronoun_mapping,
                                                      created_by, updated_by)
                        VALUES (:station_id, :kind, :name, :title_pattern, :file_name_pattern, :tags,
                                :hidden, :keep_on_archive, :legal, :self_service,
                                :cooldown_days, :restriction_mode, :pronoun_field_id, :pronoun_mapping::jsonb,
                                :author, :author)
                        RETURNING %s;""", DocumentTemplate.COLUMNS)
                .single(bindDraft(call().bind("station_id", stationId).bind("kind", draft.kind()), draft)
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
     * @param authorId   the member who changes it
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
                            self_service               = :self_service,
                            self_service_cooldown_days = :cooldown_days,
                            restriction_mode           = :restriction_mode,
                            pronoun_field_id           = :pronoun_field_id,
                            pronoun_mapping            = :pronoun_mapping::jsonb,
                            version                    = version + 1,
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
     * @param authorId   the member who changed it
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
        var pronouns = draft.pronounSource();
        return call.bind("name", draft.name())
                .bind("title_pattern", draft.titlePattern())
                .bind("file_name_pattern", draft.fileNamePattern())
                .bind("tags", draft.tags(), PostgreSqlTypes.TEXT)
                .bind("hidden", draft.hidden())
                .bind("keep_on_archive", draft.keepOnArchive())
                .bind("legal", draft.legal())
                .bind("self_service", draft.selfService())
                .bind("cooldown_days", draft.cooldownDays())
                .bind("restriction_mode", draft.restrictionMode())
                .bind("pronoun_field_id", pronouns == null ? null : pronouns.fieldId())
                .bind("pronoun_mapping", pronouns == null ? null : pronouns.mappingJson());
    }

    /**
     * Writes what a letter template says, replacing what it said before.
     *
     * @param templateId the template
     * @param letter     the letter
     */
    public void writeLetter(int templateId, LetterContent letter) {
        query("""
                INSERT INTO document_template_letter(template_id, letterhead, body_markdown, page)
                VALUES (:template_id, :letterhead::jsonb, :body, :page::jsonb)
                ON CONFLICT (template_id) DO UPDATE
                    SET letterhead    = excluded.letterhead,
                        body_markdown = excluded.body_markdown,
                        page          = excluded.page;""")
                .single(call().bind("template_id", templateId)
                        .bind("letterhead", letter.letterhead().toJson())
                        .bind("body", letter.bodyMarkdown())
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
     * The templates of a station by name.
     *
     * @param stationId the station
     * @param archived  whether to list the archived ones instead of those in use
     * @return the templates
     */
    public List<DocumentTemplate> findByStation(int stationId, boolean archived) {
        return query("""
                SELECT %s
                FROM document_template
                WHERE station_id = :station_id
                  AND (archived_at IS NOT NULL) = :archived
                ORDER BY lower(name), id;""", DocumentTemplate.COLUMNS)
                .single(call().bind("station_id", stationId).bind("archived", archived))
                .map(DocumentTemplate.map())
                .all();
    }

    /**
     * @param templateId the template
     * @return what its letter says, or empty where it has none
     */
    public Optional<LetterContent> findLetter(int templateId) {
        return query("""
                SELECT letterhead::text AS letterhead, body_markdown, page::text AS page
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
     * @param authorId   the member who does it
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
     * Whether a station has another template in use by this name, ignoring case.
     *
     * @param stationId the station
     * @param name      the name
     * @param exceptId  a template to leave out, the one being renamed, or null
     * @return whether the name is taken
     */
    public boolean nameTaken(int stationId, String name, @Nullable Integer exceptId) {
        return query("""
                SELECT EXISTS (SELECT 1
                               FROM document_template
                               WHERE station_id = :station_id
                                 AND archived_at IS NULL
                                 AND lower(name) = lower(:name)
                                 AND id IS DISTINCT FROM :except::int) AS taken;""")
                .single(call().bind("station_id", stationId).bind("name", name).bind("except", exceptId))
                .map(row -> row.getBoolean("taken"))
                .first()
                .orElse(false);
    }
}
