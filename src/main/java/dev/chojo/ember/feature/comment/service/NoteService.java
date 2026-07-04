/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.comment.service;

import dev.chojo.ember.feature.comment.entity.EntityNote;
import dev.chojo.ember.feature.comment.entity.NoteEntityType;
import dev.chojo.ember.feature.comment.entity.NoteVersion;
import dev.chojo.ember.feature.comment.repository.NoteRepository;
import dev.chojo.ember.util.TextDiff;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Service providing business logic for entity notes with diff-based version history.
 */
@Singleton
public class NoteService {
    private static final Logger log = LoggerFactory.getLogger(NoteService.class);

    private final NoteRepository noteRepository;

    @Inject
    public NoteService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }

    /**
     * Finds a note by entity type and entity ID.
     *
     * @param entityType the entity type (e.g. "event", "news")
     * @param entityId   the entity ID
     * @param stationId  the caller's station ID; notes are scoped to it
     * @return the note, if found
     */
    public Optional<EntityNote> findNote(NoteEntityType entityType, int entityId, int stationId) {
        return noteRepository.findNote(entityType, entityId, stationId);
    }

    /**
     * Updates or creates a note for an entity. If the note already exists and content changed,
     * a version entry with the diff patch is stored before updating.
     *
     * @param entityType the entity type
     * @param entityId   the entity ID
     * @param stationId  the station ID
     * @param newContent the new note content
     * @param authorId   the member ID who is making the change
     * @return the updated note
     */
    public EntityNote updateNote(
            NoteEntityType entityType, int entityId, int stationId, String newContent, int authorId) {
        var existing = noteRepository.findNote(entityType, entityId, stationId);

        var note = noteRepository.createOrUpdate(entityType, entityId, stationId, newContent, authorId);

        // If there was existing content, compute and store a version diff
        if (existing.isPresent()) {
            String oldContent = existing.get().content();
            String patch = TextDiff.createPatch(oldContent, newContent);
            if (!patch.isEmpty()) {
                noteRepository.createVersion(note.id(), patch, authorId);
            }
            log.info("Updated note {} on {} {} by member {}", note.id(), entityType, entityId, authorId);
        } else {
            log.info("Created note {} on {} {} by member {}", note.id(), entityType, entityId, authorId);
        }

        return note;
    }

    /**
     * Finds all versions for a note.
     *
     * @param noteId the note ID
     * @return the list of note versions, newest first
     */
    public List<NoteVersion> findVersions(int noteId) {
        return noteRepository.findVersions(noteId);
    }
}
