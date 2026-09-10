/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.documents;

import dev.chojo.ember.api.auth.StationPermission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the document permissions reach, and what they deliberately do not.
 */
class DocumentPermissionTest {

    /**
     * Reading the store used to mean holding the permission to read members, so the new permissions
     * are granted by implication rather than by migration. Without this a custom role holding only
     * {@code MEMBER_READ} would have lost sight of the documents it can see today, which is a
     * regression nobody asked for, and putting it right would have needed a migration over every
     * station.
     */
    @Test
    void readingMembersStillReachesTheStore() {
        assertTrue(StationPermission.MEMBER_READ.allChildren().contains(StationPermission.DOCUMENT_READ));
    }

    /** Editing members still files documents, for the same reason. */
    @Test
    void editingMembersStillFilesDocuments() {
        assertTrue(StationPermission.MEMBER_EDIT.allChildren().contains(StationPermission.DOCUMENT_EDIT));
        assertTrue(StationPermission.MEMBER_EDIT.allChildren().contains(StationPermission.DOCUMENT_READ));
    }

    /**
     * The implication runs one way only. Letting the equipment officer at the test certificates is
     * the whole point of a permission of its own, and it must not hand them the member list.
     */
    @Test
    void readingDocumentsDoesNotReachMembers() {
        assertFalse(StationPermission.DOCUMENT_READ.allChildren().contains(StationPermission.MEMBER_READ));
        assertFalse(StationPermission.DOCUMENT_EDIT.allChildren().contains(StationPermission.MEMBER_READ));
        assertFalse(StationPermission.DOCUMENT_EDIT.allChildren().contains(StationPermission.MEMBER_EDIT));
    }

    /** Filing implies reading, because nobody should be able to write into a store they cannot see. */
    @Test
    void filingImpliesReading() {
        assertTrue(StationPermission.DOCUMENT_EDIT.allChildren().contains(StationPermission.DOCUMENT_READ));
    }
}
