/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.comment.entity.NoteEntityType;
import dev.chojo.ember.feature.comment.service.NoteService;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class NoteServiceTest extends RepositoryTestBase {
    private static NoteService service;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static int noteId;

    @BeforeAll
    static void setup() {
        service = new NoteService(noteRepo);
        station = stationRepo.create("NoteStation");
        account = accountRepo.create("note@test.com", "Note", "Author");
        member = stationMemberRepo.create(station.id(), account.id());
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        accountRepo.delete(account.id());
    }

    @Test
    @Order(1)
    void findNoteWhenNoneExists() {
        var note = service.findNote(NoteEntityType.EVENT, 9999);
        assertTrue(note.isEmpty());
    }

    @Test
    @Order(2)
    void createNote() {
        var note = service.updateNote(NoteEntityType.EVENT, 1, station.id(), "First content", member.id());
        assertNotNull(note);
        assertEquals("First content", note.content());
        assertEquals(NoteEntityType.EVENT, note.entityType());
        assertEquals(1, note.entityId());
        noteId = note.id();
    }

    @Test
    @Order(3)
    void findNote() {
        var note = service.findNote(NoteEntityType.EVENT, 1);
        assertTrue(note.isPresent());
        assertEquals("First content", note.get().content());
    }

    @Test
    @Order(4)
    void updateNoteCreatesVersion() {
        var note = service.updateNote(NoteEntityType.EVENT, 1, station.id(), "Updated content", member.id());
        assertEquals("Updated content", note.content());

        // Should have created a version with diff
        var versions = service.findVersions(noteId);
        assertEquals(1, versions.size());
        assertFalse(versions.getFirst().diffPatch().isEmpty());
    }

    @Test
    @Order(5)
    void updateWithSameContentNoVersion() {
        service.updateNote(NoteEntityType.EVENT, 1, station.id(), "Updated content", member.id());
        // No new version since content didn't change
        var versions = service.findVersions(noteId);
        assertEquals(1, versions.size());
    }

    @Test
    @Order(6)
    void multipleUpdatesCreateMultipleVersions() {
        service.updateNote(NoteEntityType.EVENT, 1, station.id(), "Third version", member.id());
        service.updateNote(NoteEntityType.EVENT, 1, station.id(), "Fourth version", member.id());
        var versions = service.findVersions(noteId);
        assertEquals(3, versions.size());
    }

    @Test
    @Order(7)
    void versionsOrderedNewestFirst() {
        var versions = service.findVersions(noteId);
        for (int i = 0; i < versions.size() - 1; i++) {
            assertTrue(versions.get(i).createdAt().compareTo(versions.get(i + 1).createdAt()) >= 0);
        }
    }

    @Test
    @Order(8)
    void findVersionsForNonExistentNote() {
        var versions = service.findVersions(-999);
        assertTrue(versions.isEmpty());
    }
}
