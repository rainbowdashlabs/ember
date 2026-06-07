/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class KbCommentRepositoryTest extends RepositoryTestBase {
    private static KbCommentRepository commentRepo;
    private static Station station;
    private static Account account;
    private static StationMember member;
    private static KbFile file;
    private static Station partnerStation;
    private static int partnerId;

    private static int topLevelCommentId;
    private static int replyCommentId;
    private static int standaloneCommentId;

    @BeforeAll
    static void setup() {
        commentRepo = new KbCommentRepository();
        station = stationRepo.create("KB Comment Station");
        account = accountRepo.create("kbcomment@test.com", "KbComment", "Tester");
        member = stationMemberRepo.create(station.id(), account.id());
        file = knowledgeBaseRepo.createFile(
                station.id(),
                null,
                "test-file.md",
                "A test file",
                KbFileType.MARKDOWN,
                "text/markdown",
                100,
                null,
                member.id());

        // Create a partner station for federated author tests
        partnerStation = stationRepo.create("Partner Station");
        partnerId = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p::uuid, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of()
                        .bind("s", station.id())
                        .bind("p", partnerStation.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        stationRepo.delete(station.id());
        stationRepo.delete(partnerStation.id());
        accountRepo.delete(account.id());
    }

    // -- create --

    @Test
    @Order(1)
    void createTopLevelComment() {
        var authorIdentity = memberIdentityFactory.local(station.id(), member.id());
        KbComment comment = commentRepo.create(file.id(), null, authorIdentity, "Top-level comment");
        assertNotNull(comment);
        assertTrue(comment.id() > 0);
        assertEquals(file.id(), comment.fileId());
        assertNull(comment.parentId());
        assertNotNull(comment.author());
        assertEquals(authorIdentity.memberUid(), comment.author().memberUid());
        assertEquals("Top-level comment", comment.content());
        assertFalse(comment.deleted());
        assertNotNull(comment.createdAt());
        assertNull(comment.updatedAt());
        topLevelCommentId = comment.id();
    }

    @Test
    @Order(2)
    void createReplyComment() {
        var authorIdentity = memberIdentityFactory.local(station.id(), member.id());
        KbComment reply = commentRepo.create(file.id(), topLevelCommentId, authorIdentity, "Reply to top-level");
        assertNotNull(reply);
        assertTrue(reply.id() > 0);
        assertEquals(file.id(), reply.fileId());
        assertEquals(topLevelCommentId, reply.parentId());
        assertEquals("Reply to top-level", reply.content());
        replyCommentId = reply.id();
    }

    @Test
    @Order(3)
    void createStandaloneComment() {
        var authorIdentity = memberIdentityFactory.local(station.id(), member.id());
        KbComment comment = commentRepo.create(file.id(), null, authorIdentity, "Standalone comment");
        assertNotNull(comment);
        standaloneCommentId = comment.id();
    }

    // -- findById --

    @Test
    @Order(10)
    void findByIdExisting() {
        var found = commentRepo.findById(topLevelCommentId);
        assertTrue(found.isPresent());
        assertEquals(topLevelCommentId, found.get().id());
        assertEquals("Top-level comment", found.get().content());
    }

    @Test
    @Order(11)
    void findByIdNotFound() {
        var found = commentRepo.findById(999999);
        assertTrue(found.isEmpty());
    }

    // -- findByFile --

    @Test
    @Order(20)
    void findByFileReturnsAllComments() {
        var comments = commentRepo.findByFile(file.id());
        assertEquals(3, comments.size());
    }

    @Test
    @Order(21)
    void findByFileOrderedByCreatedAt() {
        var comments = commentRepo.findByFile(file.id());
        for (int i = 1; i < comments.size(); i++) {
            assertTrue(
                    comments.get(i).createdAt().compareTo(comments.get(i - 1).createdAt()) >= 0,
                    "Comments should be ordered by created_at ascending");
        }
    }

    @Test
    @Order(22)
    void findByFileEmptyForNonExistentFile() {
        var comments = commentRepo.findByFile(999999);
        assertTrue(comments.isEmpty());
    }

    // -- hasChildren --

    @Test
    @Order(30)
    void hasChildrenTrueForParent() {
        assertTrue(commentRepo.hasChildren(topLevelCommentId));
    }

    @Test
    @Order(31)
    void hasChildrenFalseForLeaf() {
        assertFalse(commentRepo.hasChildren(replyCommentId));
    }

    @Test
    @Order(32)
    void hasChildrenFalseForNonExistent() {
        assertFalse(commentRepo.hasChildren(999999));
    }

    // -- update --

    @Test
    @Order(40)
    void updateExistingComment() {
        boolean updated = commentRepo.update(topLevelCommentId, "Updated top-level comment");
        assertTrue(updated);

        var found = commentRepo.findById(topLevelCommentId);
        assertTrue(found.isPresent());
        assertEquals("Updated top-level comment", found.get().content());
        assertNotNull(found.get().updatedAt());
    }

    @Test
    @Order(41)
    void updateNonExistentComment() {
        boolean updated = commentRepo.update(999999, "Should not update");
        assertFalse(updated);
    }

    // -- delete (soft-delete when has children) --

    @Test
    @Order(50)
    void deleteCommentWithChildrenSoftDeletes() {
        // topLevelCommentId has replyCommentId as a child
        boolean deleted = commentRepo.delete(topLevelCommentId);
        assertTrue(deleted);

        var found = commentRepo.findById(topLevelCommentId);
        assertTrue(found.isPresent(), "Soft-deleted comment should still be findable");
        assertTrue(found.get().deleted(), "Comment should be marked as deleted");
        assertEquals("", found.get().content(), "Content should be cleared on soft-delete");
    }

    @Test
    @Order(51)
    void softDeletedCommentStillInFileList() {
        // findByFile includes soft-deleted comments to preserve thread structure
        var comments = commentRepo.findByFile(file.id());
        assertTrue(comments.stream().anyMatch(c -> c.id() == topLevelCommentId && c.deleted()));
    }

    // -- delete (hard-delete when no children) --

    @Test
    @Order(60)
    void deleteCommentWithoutChildrenHardDeletes() {
        boolean deleted = commentRepo.delete(standaloneCommentId);
        assertTrue(deleted);

        var found = commentRepo.findById(standaloneCommentId);
        assertTrue(found.isEmpty(), "Hard-deleted comment should not be findable");
    }

    @Test
    @Order(61)
    void deleteNonExistentCommentReturnsFalse() {
        boolean deleted = commentRepo.delete(999999);
        assertFalse(deleted);
    }

    // -- Federated author inline --

    @Test
    @Order(70)
    void createCommentWithFederatedAuthor() {
        // Create a comment with federated author identity
        var federatedAuthor = new MemberIdentity(partnerStation.uid(), UUID.randomUUID());
        KbComment fedComment = commentRepo.create(file.id(), null, federatedAuthor, "Federated comment");
        assertNotNull(fedComment);
        assertNotNull(fedComment.author());
        assertEquals(federatedAuthor.stationUid(), fedComment.author().stationUid());
        assertEquals(federatedAuthor.memberUid(), fedComment.author().memberUid());
    }

    @Test
    @Order(71)
    void createCommentWithNullAuthor() {
        KbComment comment = commentRepo.create(file.id(), null, null, "Anonymous comment");
        assertNotNull(comment);
        assertNull(comment.author());
        commentRepo.delete(comment.id());
    }

    // -- delete reply then parent can be hard-deleted --

    @Test
    @Order(80)
    void deleteReplyThenParentCanBeHardDeleted() {
        // Delete the reply first
        boolean replyDeleted = commentRepo.delete(replyCommentId);
        assertTrue(replyDeleted);
        assertTrue(commentRepo.findById(replyCommentId).isEmpty(), "Reply should be hard-deleted (no children)");

        // Now the soft-deleted parent has no children, so deleting it again should hard-delete
        // But the parent is already soft-deleted; calling delete again should hard-delete it
        boolean parentDeleted = commentRepo.delete(topLevelCommentId);
        assertTrue(parentDeleted);
        assertTrue(
                commentRepo.findById(topLevelCommentId).isEmpty(), "Parent should now be hard-deleted (no children)");
    }
}
