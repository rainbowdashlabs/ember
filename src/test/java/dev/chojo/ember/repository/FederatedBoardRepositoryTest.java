/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.repository;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.Board;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedBoardRepositoryTest extends RepositoryTestBase {
    private static Station owningStation;
    private static Station partnerStation;
    private static Account account;
    private static StationMember member;
    private static int boardId;
    private static int laneId;
    private static int ticketId;
    private static int commentId;
    private static int partnerId;
    private static int shareId;
    private static int bookmarkId;

    @BeforeAll
    static void setup() {
        owningStation = stationRepo.create("Owning Station");
        partnerStation = stationRepo.create("Partner Station");
        account = accountRepo.create("fedboard@test.com", "Fed", "User");
        member = stationMemberRepo.create(owningStation.id(), account.id());

        Board board = boardRepo.create(owningStation.id(), "Fed Board", "For federation tests", "FED");
        boardId = board.id();

        BoardLane lane = boardRepo.createLane(boardId, "Open", null, 0);
        laneId = lane.id();

        int ticketNum = boardRepo.nextTicketNumber(boardId);
        BoardTicket ticket = boardTicketRepo.createTicket(
                boardId,
                laneId,
                ticketNum,
                "Fed Ticket",
                "Description",
                null,
                TicketPriority.MEDIUM,
                null,
                0,
                member.id());
        ticketId = ticket.id();

        BoardComment comment = boardTicketRepo.createComment(ticketId, null, member.id(), "Fed comment");
        commentId = comment.id();

        partnerId = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p::uuid, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of()
                        .bind("s", owningStation.id())
                        .bind("p", partnerStation.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        boardRepo.delete(boardId);
        stationRepo.delete(owningStation.id());
        stationRepo.delete(partnerStation.id());
        accountRepo.delete(account.id());
    }

    // -- Share CRUD --

    @Test
    @Order(1)
    void createShare() {
        var share = federatedBoardRepo.createShare(boardId);
        assertNotNull(share);
        assertEquals(boardId, share.boardId());
        shareId = share.id();
    }

    @Test
    @Order(2)
    void findShare() {
        var share = federatedBoardRepo.findShare(boardId);
        assertTrue(share.isPresent());
        assertEquals(shareId, share.get().id());
        assertEquals(boardId, share.get().boardId());
    }

    @Test
    @Order(3)
    void findShareEmpty() {
        var share = federatedBoardRepo.findShare(-999);
        assertFalse(share.isPresent());
    }

    // -- Share Targets --

    @Test
    @Order(10)
    void setShareTarget() {
        federatedBoardRepo.setShareTarget(shareId, partnerId, BoardShareMode.READ_ONLY);
        var targets = federatedBoardRepo.findShareTargets(shareId);
        assertEquals(1, targets.size());
        assertEquals(partnerId, targets.getFirst().partnerId());
        assertEquals(BoardShareMode.READ_ONLY, targets.getFirst().shareMode());
    }

    @Test
    @Order(11)
    void setShareTargetUpsert() {
        federatedBoardRepo.setShareTarget(shareId, partnerId, BoardShareMode.FULL);
        var targets = federatedBoardRepo.findShareTargets(shareId);
        assertEquals(1, targets.size());
        assertEquals(BoardShareMode.FULL, targets.getFirst().shareMode());
    }

    @Test
    @Order(12)
    void findShareMode() {
        var mode = federatedBoardRepo.findShareMode(boardId, partnerId);
        assertTrue(mode.isPresent());
        assertEquals(BoardShareMode.FULL, mode.get());
    }

    @Test
    @Order(13)
    void findShareModeNotFound() {
        var mode = federatedBoardRepo.findShareMode(boardId, -999);
        assertFalse(mode.isPresent());
    }

    @Test
    @Order(14)
    void findSharedBoardIds() {
        var ids = federatedBoardRepo.findSharedBoardIds(partnerId);
        assertEquals(1, ids.size());
        assertEquals(boardId, ids.getFirst());
    }

    @Test
    @Order(15)
    void removeShareTarget() {
        federatedBoardRepo.removeShareTarget(shareId, partnerId);
        var targets = federatedBoardRepo.findShareTargets(shareId);
        assertTrue(targets.isEmpty());
    }

    @Test
    @Order(16)
    void clearShareTargets() {
        federatedBoardRepo.setShareTarget(shareId, partnerId, BoardShareMode.READ_ONLY);
        federatedBoardRepo.clearShareTargets(shareId);
        var targets = federatedBoardRepo.findShareTargets(shareId);
        assertTrue(targets.isEmpty());
    }

    @Test
    @Order(17)
    void deleteShare() {
        federatedBoardRepo.deleteShare(boardId);
        var share = federatedBoardRepo.findShare(boardId);
        assertFalse(share.isPresent());
    }

    // -- Federated Edit Roles --

    @Test
    @Order(20)
    void setFederatedEditRoles() {
        federatedBoardRepo.setFederatedEditRoles(boardId, List.of(1, 2, 3));
        var roles = federatedBoardRepo.findFederatedEditRoles(boardId);
        assertEquals(3, roles.size());
        assertTrue(roles.containsAll(List.of(1, 2, 3)));
    }

    @Test
    @Order(21)
    void hasFederatedEditRoles() {
        assertTrue(federatedBoardRepo.hasFederatedEditRoles(boardId));
    }

    @Test
    @Order(22)
    void setFederatedEditRolesReplace() {
        federatedBoardRepo.setFederatedEditRoles(boardId, List.of(5));
        var roles = federatedBoardRepo.findFederatedEditRoles(boardId);
        assertEquals(1, roles.size());
        assertEquals(5, roles.getFirst());
    }

    @Test
    @Order(23)
    void setFederatedEditRolesEmpty() {
        federatedBoardRepo.setFederatedEditRoles(boardId, List.of());
        assertFalse(federatedBoardRepo.hasFederatedEditRoles(boardId));
        assertTrue(federatedBoardRepo.findFederatedEditRoles(boardId).isEmpty());
    }

    @Test
    @Order(24)
    void hasFederatedEditRolesWhenNone() {
        assertFalse(federatedBoardRepo.hasFederatedEditRoles(-999));
    }

    // -- Federated Assignees --

    @Test
    @Order(30)
    void setFederatedAssignee() {
        federatedBoardRepo.setFederatedAssignee(ticketId, partnerId, "remote-member-1");
        var assignee = federatedBoardRepo.findFederatedAssignee(ticketId);
        assertTrue(assignee.isPresent());
        assertEquals(ticketId, assignee.get().ticketId());
        assertEquals(partnerId, assignee.get().partnerId());
        assertEquals("remote-member-1", assignee.get().remoteMemberId());
    }

    @Test
    @Order(31)
    void setFederatedAssigneeUpsert() {
        federatedBoardRepo.setFederatedAssignee(ticketId, partnerId, "remote-member-2");
        var assignee = federatedBoardRepo.findFederatedAssignee(ticketId);
        assertTrue(assignee.isPresent());
        assertEquals("remote-member-2", assignee.get().remoteMemberId());
    }

    @Test
    @Order(32)
    void findFederatedAssigneeNotFound() {
        var assignee = federatedBoardRepo.findFederatedAssignee(-999);
        assertFalse(assignee.isPresent());
    }

    @Test
    @Order(33)
    void removeFederatedAssignee() {
        federatedBoardRepo.removeFederatedAssignee(ticketId);
        var assignee = federatedBoardRepo.findFederatedAssignee(ticketId);
        assertFalse(assignee.isPresent());
    }

    // -- Federated Comment Authors --

    @Test
    @Order(40)
    void setFederatedCommentAuthor() {
        federatedBoardRepo.setFederatedCommentAuthor(commentId, partnerId, "remote-author-1");
        var author = federatedBoardRepo.findFederatedCommentAuthor(commentId);
        assertTrue(author.isPresent());
        assertEquals(commentId, author.get().commentId());
        assertEquals(partnerId, author.get().partnerId());
        assertEquals("remote-author-1", author.get().remoteMemberId());
    }

    @Test
    @Order(41)
    void findFederatedCommentAuthorNotFound() {
        var author = federatedBoardRepo.findFederatedCommentAuthor(-999);
        assertFalse(author.isPresent());
    }

    // -- Federated Creators --

    @Test
    @Order(50)
    void setFederatedCreator() {
        federatedBoardRepo.setFederatedCreator(ticketId, partnerId, "remote-creator-1");
        var creator = federatedBoardRepo.findFederatedCreator(ticketId);
        assertTrue(creator.isPresent());
        assertEquals(ticketId, creator.get().ticketId());
        assertEquals(partnerId, creator.get().partnerId());
        assertEquals("remote-creator-1", creator.get().remoteMemberId());
    }

    @Test
    @Order(51)
    void findFederatedCreatorNotFound() {
        var creator = federatedBoardRepo.findFederatedCreator(-999);
        assertFalse(creator.isPresent());
    }

    // -- Federated Watchers --

    @Test
    @Order(60)
    void addFederatedWatcher() {
        federatedBoardRepo.addFederatedWatcher(ticketId, partnerId, "remote-watcher-1");
        var watchers = federatedBoardRepo.findFederatedWatchers(ticketId);
        assertEquals(1, watchers.size());
        assertEquals(ticketId, watchers.getFirst().ticketId());
        assertEquals(partnerId, watchers.getFirst().partnerId());
        assertEquals("remote-watcher-1", watchers.getFirst().remoteMemberId());
    }

    @Test
    @Order(61)
    void addFederatedWatcherDuplicate() {
        // ON CONFLICT DO NOTHING — should not throw or add duplicate
        federatedBoardRepo.addFederatedWatcher(ticketId, partnerId, "remote-watcher-1");
        var watchers = federatedBoardRepo.findFederatedWatchers(ticketId);
        assertEquals(1, watchers.size());
    }

    @Test
    @Order(62)
    void addSecondFederatedWatcher() {
        federatedBoardRepo.addFederatedWatcher(ticketId, partnerId, "remote-watcher-2");
        var watchers = federatedBoardRepo.findFederatedWatchers(ticketId);
        assertEquals(2, watchers.size());
    }

    @Test
    @Order(63)
    void isFederatedWatching() {
        assertTrue(federatedBoardRepo.isFederatedWatching(ticketId, partnerId, "remote-watcher-1"));
        assertFalse(federatedBoardRepo.isFederatedWatching(ticketId, partnerId, "nonexistent"));
    }

    @Test
    @Order(64)
    void removeFederatedWatcher() {
        federatedBoardRepo.removeFederatedWatcher(ticketId, partnerId, "remote-watcher-1");
        assertFalse(federatedBoardRepo.isFederatedWatching(ticketId, partnerId, "remote-watcher-1"));
        assertTrue(federatedBoardRepo.isFederatedWatching(ticketId, partnerId, "remote-watcher-2"));
    }

    @Test
    @Order(65)
    void findFederatedWatchersEmpty() {
        federatedBoardRepo.removeFederatedWatcher(ticketId, partnerId, "remote-watcher-2");
        var watchers = federatedBoardRepo.findFederatedWatchers(ticketId);
        assertTrue(watchers.isEmpty());
    }

    // -- Bookmarks --

    @Test
    @Order(70)
    void createBookmark() {
        var bookmark = federatedBoardRepo.createBookmark(
                member.id(), partnerId, 100, "Remote Board", "RB", BoardShareMode.READ_ONLY);
        assertNotNull(bookmark);
        assertEquals(member.id(), bookmark.memberId());
        assertEquals(partnerId, bookmark.partnerId());
        assertEquals(100, bookmark.remoteBoardId());
        assertEquals("Remote Board", bookmark.remoteBoardName());
        assertEquals("RB", bookmark.remoteBoardShortKey());
        assertEquals(BoardShareMode.READ_ONLY, bookmark.shareMode());
        assertNotNull(bookmark.createdAt());
        bookmarkId = bookmark.id();
    }

    @Test
    @Order(71)
    void findBookmarks() {
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertEquals(1, bookmarks.size());
        assertEquals(bookmarkId, bookmarks.getFirst().id());
    }

    @Test
    @Order(72)
    void updateBookmarkName() {
        federatedBoardRepo.updateBookmarkName(partnerId, 100, "Renamed Board", "RN");
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertEquals("Renamed Board", bookmarks.getFirst().remoteBoardName());
        assertEquals("RN", bookmarks.getFirst().remoteBoardShortKey());
    }

    @Test
    @Order(73)
    void updateBookmarkShareMode() {
        federatedBoardRepo.updateBookmarkShareMode(partnerId, 100, BoardShareMode.FULL);
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertEquals(BoardShareMode.FULL, bookmarks.getFirst().shareMode());
    }

    @Test
    @Order(74)
    void deleteBookmarkByBoard() {
        federatedBoardRepo.deleteBookmarkByBoard(member.id(), partnerId, 100);
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertTrue(bookmarks.isEmpty());
    }

    @Test
    @Order(75)
    void deleteBookmark() {
        var bookmark =
                federatedBoardRepo.createBookmark(member.id(), partnerId, 200, "Board 2", "B2", BoardShareMode.FULL);
        federatedBoardRepo.deleteBookmark(bookmark.id());
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertTrue(bookmarks.isEmpty());
    }

    @Test
    @Order(76)
    void deleteBookmarksByBoard() {
        federatedBoardRepo.createBookmark(member.id(), partnerId, 300, "Board 3", "B3", BoardShareMode.READ_ONLY);
        federatedBoardRepo.deleteBookmarksByBoard(partnerId, 300);
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertTrue(bookmarks.isEmpty());
    }

    @Test
    @Order(77)
    void findBookmarksEmpty() {
        var bookmarks = federatedBoardRepo.findBookmarks(-999);
        assertTrue(bookmarks.isEmpty());
    }

    // -- Local View Overrides --

    @Test
    @Order(80)
    void setLocalViewOverride() {
        var access = new AccessData(List.of(1, 2), List.of(10), List.of(20));
        federatedBoardRepo.setLocalViewOverride(partnerId, boardId, access);
        assertTrue(federatedBoardRepo.hasLocalViewOverride(partnerId, boardId));
    }

    @Test
    @Order(81)
    void findLocalViewOverride() {
        var access = federatedBoardRepo.findLocalViewOverride(partnerId, boardId);
        assertEquals(List.of(1, 2), access.roleIds());
        assertEquals(List.of(10), access.groupIds());
        assertEquals(List.of(20), access.tagIds());
    }

    @Test
    @Order(82)
    void setLocalViewOverrideReplace() {
        var access = new AccessData(List.of(5), List.of(), List.of());
        federatedBoardRepo.setLocalViewOverride(partnerId, boardId, access);
        var result = federatedBoardRepo.findLocalViewOverride(partnerId, boardId);
        assertEquals(List.of(5), result.roleIds());
        assertTrue(result.groupIds().isEmpty());
        assertTrue(result.tagIds().isEmpty());
    }

    @Test
    @Order(83)
    void setLocalViewOverrideEmpty() {
        var access = new AccessData(List.of(), List.of(), List.of());
        federatedBoardRepo.setLocalViewOverride(partnerId, boardId, access);
        assertFalse(federatedBoardRepo.hasLocalViewOverride(partnerId, boardId));
    }

    @Test
    @Order(84)
    void hasLocalViewOverrideWhenNone() {
        assertFalse(federatedBoardRepo.hasLocalViewOverride(-999, -999));
    }

    // -- Local Edit Overrides --

    @Test
    @Order(85)
    void setLocalEditOverride() {
        var access = new AccessData(List.of(3), List.of(11), List.of(21));
        federatedBoardRepo.setLocalEditOverride(partnerId, boardId, access);
        assertTrue(federatedBoardRepo.hasLocalEditOverride(partnerId, boardId));
    }

    @Test
    @Order(86)
    void findLocalEditOverride() {
        var access = federatedBoardRepo.findLocalEditOverride(partnerId, boardId);
        assertEquals(List.of(3), access.roleIds());
        assertEquals(List.of(11), access.groupIds());
        assertEquals(List.of(21), access.tagIds());
    }

    @Test
    @Order(87)
    void setLocalEditOverrideReplace() {
        var access = new AccessData(List.of(), List.of(99), List.of());
        federatedBoardRepo.setLocalEditOverride(partnerId, boardId, access);
        var result = federatedBoardRepo.findLocalEditOverride(partnerId, boardId);
        assertTrue(result.roleIds().isEmpty());
        assertEquals(List.of(99), result.groupIds());
        assertTrue(result.tagIds().isEmpty());
    }

    @Test
    @Order(88)
    void setLocalEditOverrideEmpty() {
        var access = new AccessData(List.of(), List.of(), List.of());
        federatedBoardRepo.setLocalEditOverride(partnerId, boardId, access);
        assertFalse(federatedBoardRepo.hasLocalEditOverride(partnerId, boardId));
    }

    @Test
    @Order(89)
    void hasLocalEditOverrideWhenNone() {
        assertFalse(federatedBoardRepo.hasLocalEditOverride(-999, -999));
    }

    @Test
    @Order(90)
    void findLocalViewOverrideEmpty() {
        var access = federatedBoardRepo.findLocalViewOverride(-999, -999);
        assertTrue(access.roleIds().isEmpty());
        assertTrue(access.groupIds().isEmpty());
        assertTrue(access.tagIds().isEmpty());
    }

    @Test
    @Order(91)
    void findLocalEditOverrideEmpty() {
        var access = federatedBoardRepo.findLocalEditOverride(-999, -999);
        assertTrue(access.roleIds().isEmpty());
        assertTrue(access.groupIds().isEmpty());
        assertTrue(access.tagIds().isEmpty());
    }
}
