/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.repository;

import dev.chojo.ember.feature.comment.entity.EntityNote;
import dev.chojo.ember.feature.comment.entity.NoteEntityType;
import dev.chojo.ember.feature.comment.entity.NoteVersion;
import dev.chojo.ember.util.sql.SqlSupport;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Repository for managing entity notes and their version history.
 */
@Singleton
public class NoteRepository {

    private static final String NOTE_COLUMNS =
            "id, entity_type, entity_id, station_id, content, updated_by, updated_at";
    private static final String VERSION_COLUMNS = "id, note_id, diff_patch, author_id, created_at";

    /**
     * Finds a note by entity type and entity ID.
     *
     * @param entityType the entity type (e.g. "event", "news")
     * @param entityId   the entity ID
     * @return the note, if found
     */
    public Optional<EntityNote> findNote(NoteEntityType entityType, int entityId, int stationId) {
        return query("""
                SELECT %s
                FROM entity_note
                WHERE entity_type = :entity_type
                  AND entity_id = :entity_id
                  AND station_id = :station_id;""", NOTE_COLUMNS)
                .single(call().bind("entity_type", entityType)
                        .bind("entity_id", entityId)
                        .bind("station_id", stationId))
                .map(EntityNote.map())
                .first();
    }

    /**
     * Creates or updates a note for an entity. Uses INSERT ON CONFLICT DO UPDATE.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @param stationId  the station ID
     * @param content    the note content
     * @param updatedBy  the member ID who is updating
     * @return the created or updated note
     */
    public EntityNote createOrUpdate(
            NoteEntityType entityType, int entityId, int stationId, String content, int updatedBy) {
        return SqlSupport.insertReturning(
                """
                INSERT
                INTO
                    entity_note
                    (entity_type, entity_id, station_id, content, updated_by)
                VALUES
                    (:entity_type, :entity_id, :station_id, :content, :updated_by)
                ON CONFLICT (station_id, entity_type, entity_id) DO UPDATE
                    SET
                        content    = :content,
                        updated_by = :updated_by,
                        updated_at = now()
                RETURNING %s;""".formatted(NOTE_COLUMNS),
                call().bind("entity_type", entityType)
                        .bind("entity_id", entityId)
                        .bind("station_id", stationId)
                        .bind("content", content)
                        .bind("updated_by", updatedBy),
                EntityNote.map());
    }

    /**
     * Finds all versions for a note, ordered by creation time descending.
     *
     * @param noteId the note ID
     * @return the list of note versions
     */
    public List<NoteVersion> findVersions(int noteId) {
        return query("""
                SELECT %s
                FROM entity_note_version
                WHERE note_id = :note_id
                ORDER BY created_at DESC;""", VERSION_COLUMNS)
                .single(call().bind("note_id", noteId))
                .map(NoteVersion.map())
                .all();
    }

    /**
     * Creates a new version entry for a note.
     *
     * @param noteId    the note ID
     * @param diffPatch the unified diff patch
     * @param authorId  the member ID who made the change
     */
    public void createVersion(int noteId, String diffPatch, int authorId) {
        query("""
                INSERT
                INTO
                    entity_note_version
                    (note_id, diff_patch, author_id)
                VALUES
                    (:note_id, :diff_patch, :author_id);""")
                .single(call().bind("note_id", noteId)
                        .bind("diff_patch", diffPatch)
                        .bind("author_id", authorId))
                .update();
    }
}
