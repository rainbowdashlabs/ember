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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedBoardRepositoryTest extends RepositoryTestBase {
    private static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE_MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REMOTE_AUTHOR_1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID REMOTE_CREATOR_1 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID REMOTE_WATCHER_1 = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID REMOTE_WATCHER_2 = UUID.fromString("00000000-0000-0000-0000-000000000006");

    private static Station owningStation;
    private static Station partnerStation;
    private static Account account;
    private static StationMember member;
    private static int boardId;
    private static UUID boardUid;
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
        boardUid = board.uid();

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
                memberIdentityFactory.local(owningStation.id(), member.id()));
        ticketId = ticket.id();

        BoardComment comment = boardTicketRepo.createComment(
                ticketId, null, memberIdentityFactory.local(owningStation.id(), member.id()), "Fed comment");
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
        federatedBoardRepo.setShareTarget(shareId, partnerId, BoardShareMode.READ_ONLY, "USER");
        var targets = federatedBoardRepo.findShareTargets(shareId);
        assertEquals(1, targets.size());
        assertEquals(partnerId, targets.getFirst().partnerId());
        assertEquals(BoardShareMode.READ_ONLY, targets.getFirst().shareMode());
    }

    @Test
    @Order(11)
    void setShareTargetUpsert() {
        federatedBoardRepo.setShareTarget(shareId, partnerId, BoardShareMode.FULL, "USER");
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
        federatedBoardRepo.setShareTarget(shareId, partnerId, BoardShareMode.READ_ONLY, "USER");
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
    void setFederatedEditUserTypes() {
        federatedBoardRepo.setFederatedEditUserTypes(boardId, List.of("MEMBER", "GUARDIAN", "TEAM"));
        var userTypes = federatedBoardRepo.findFederatedEditUserTypes(boardId);
        assertEquals(3, userTypes.size());
        assertTrue(userTypes.containsAll(List.of("MEMBER", "GUARDIAN", "TEAM")));
    }

    @Test
    @Order(21)
    void hasFederatedEditUserTypes() {
        assertTrue(federatedBoardRepo.hasFederatedEditUserTypes(boardId));
    }

    @Test
    @Order(22)
    void setFederatedEditUserTypesReplace() {
        federatedBoardRepo.setFederatedEditUserTypes(boardId, List.of("MANAGER"));
        var userTypes = federatedBoardRepo.findFederatedEditUserTypes(boardId);
        assertEquals(1, userTypes.size());
        assertEquals("MANAGER", userTypes.getFirst());
    }

    @Test
    @Order(23)
    void setFederatedEditUserTypesEmpty() {
        federatedBoardRepo.setFederatedEditUserTypes(boardId, List.of());
        assertFalse(federatedBoardRepo.hasFederatedEditUserTypes(boardId));
        assertTrue(federatedBoardRepo.findFederatedEditUserTypes(boardId).isEmpty());
    }

    @Test
    @Order(24)
    void hasFederatedEditUserTypesWhenNone() {
        assertFalse(federatedBoardRepo.hasFederatedEditUserTypes(-999));
    }

    // Satellite table tests removed — identity is now inline in board_ticket columns

    // -- Bookmarks --

    @Test
    @Order(70)
    void createBookmark() {
        var remoteBoardUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var bookmark = federatedBoardRepo.createBookmark(
                member.id(), partnerId, remoteBoardUid, "Remote Board", "RB", BoardShareMode.READ_ONLY);
        assertNotNull(bookmark);
        assertEquals(member.id(), bookmark.memberId());
        assertEquals(partnerId, bookmark.partnerId());
        assertEquals(remoteBoardUid, bookmark.remoteBoardUid());
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
        var remoteBoardUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        federatedBoardRepo.updateBookmarkName(partnerId, remoteBoardUid, "Renamed Board", "RN");
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertEquals("Renamed Board", bookmarks.getFirst().remoteBoardName());
        assertEquals("RN", bookmarks.getFirst().remoteBoardShortKey());
    }

    @Test
    @Order(73)
    void updateBookmarkShareMode() {
        var remoteBoardUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        federatedBoardRepo.updateBookmarkShareMode(partnerId, remoteBoardUid, BoardShareMode.FULL);
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertEquals(BoardShareMode.FULL, bookmarks.getFirst().shareMode());
    }

    @Test
    @Order(74)
    void deleteBookmarkByBoard() {
        var remoteBoardUid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        federatedBoardRepo.deleteBookmarkByBoard(member.id(), partnerId, remoteBoardUid);
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertTrue(bookmarks.isEmpty());
    }

    @Test
    @Order(75)
    void deleteBookmark() {
        var remoteUid2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        var bookmark = federatedBoardRepo.createBookmark(
                member.id(), partnerId, remoteUid2, "Board 2", "B2", BoardShareMode.FULL);
        federatedBoardRepo.deleteBookmark(bookmark.id());
        var bookmarks = federatedBoardRepo.findBookmarks(member.id());
        assertTrue(bookmarks.isEmpty());
    }

    @Test
    @Order(76)
    void deleteBookmarksByBoard() {
        var remoteUid3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
        federatedBoardRepo.createBookmark(
                member.id(), partnerId, remoteUid3, "Board 3", "B3", BoardShareMode.READ_ONLY);
        federatedBoardRepo.deleteBookmarksByBoard(partnerId, remoteUid3);
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
        var access = new AccessData(List.of("MEMBER", "GUARDIAN"), List.of(10), List.of(20));
        federatedBoardRepo.setLocalViewOverride(partnerId, boardUid, access);
        assertTrue(federatedBoardRepo.hasLocalViewOverride(partnerId, boardUid));
    }

    @Test
    @Order(81)
    void findLocalViewOverride() {
        var access = federatedBoardRepo.findLocalViewOverride(partnerId, boardUid);
        assertEquals(2, access.userTypes().size());
        assertTrue(access.userTypes().containsAll(List.of("MEMBER", "GUARDIAN")));
        assertEquals(List.of(10), access.groupIds());
        assertEquals(List.of(20), access.tagIds());
    }

    @Test
    @Order(82)
    void setLocalViewOverrideReplace() {
        var access = new AccessData(List.of("TEAM"), List.of(), List.of());
        federatedBoardRepo.setLocalViewOverride(partnerId, boardUid, access);
        var result = federatedBoardRepo.findLocalViewOverride(partnerId, boardUid);
        assertEquals(List.of("TEAM"), result.userTypes());
        assertTrue(result.groupIds().isEmpty());
        assertTrue(result.tagIds().isEmpty());
    }

    @Test
    @Order(83)
    void setLocalViewOverrideEmpty() {
        var access = new AccessData(List.of(), List.of(), List.of());
        federatedBoardRepo.setLocalViewOverride(partnerId, boardUid, access);
        assertFalse(federatedBoardRepo.hasLocalViewOverride(partnerId, boardUid));
    }

    @Test
    @Order(84)
    void hasLocalViewOverrideWhenNone() {
        assertFalse(
                federatedBoardRepo.hasLocalViewOverride(-999, UUID.fromString("99999999-9999-9999-9999-999999999999")));
    }

    // -- Local Edit Overrides --

    @Test
    @Order(85)
    void setLocalEditOverride() {
        var access = new AccessData(List.of("GUARDIAN"), List.of(11), List.of(21));
        federatedBoardRepo.setLocalEditOverride(partnerId, boardUid, access);
        assertTrue(federatedBoardRepo.hasLocalEditOverride(partnerId, boardUid));
    }

    @Test
    @Order(86)
    void findLocalEditOverride() {
        var access = federatedBoardRepo.findLocalEditOverride(partnerId, boardUid);
        assertEquals(List.of("GUARDIAN"), access.userTypes());
        assertEquals(List.of(11), access.groupIds());
        assertEquals(List.of(21), access.tagIds());
    }

    @Test
    @Order(87)
    void setLocalEditOverrideReplace() {
        var access = new AccessData(List.of(), List.of(99), List.of());
        federatedBoardRepo.setLocalEditOverride(partnerId, boardUid, access);
        var result = federatedBoardRepo.findLocalEditOverride(partnerId, boardUid);
        assertTrue(result.userTypes().isEmpty());
        assertEquals(List.of(99), result.groupIds());
        assertTrue(result.tagIds().isEmpty());
    }

    @Test
    @Order(88)
    void setLocalEditOverrideEmpty() {
        var access = new AccessData(List.of(), List.of(), List.of());
        federatedBoardRepo.setLocalEditOverride(partnerId, boardUid, access);
        assertFalse(federatedBoardRepo.hasLocalEditOverride(partnerId, boardUid));
    }

    @Test
    @Order(89)
    void hasLocalEditOverrideWhenNone() {
        assertFalse(
                federatedBoardRepo.hasLocalEditOverride(-999, UUID.fromString("99999999-9999-9999-9999-999999999999")));
    }

    @Test
    @Order(90)
    void findLocalViewOverrideEmpty() {
        var access =
                federatedBoardRepo.findLocalViewOverride(-999, UUID.fromString("99999999-9999-9999-9999-999999999999"));
        assertTrue(access.userTypes().isEmpty());
        assertTrue(access.groupIds().isEmpty());
        assertTrue(access.tagIds().isEmpty());
    }

    @Test
    @Order(91)
    void findLocalEditOverrideEmpty() {
        var access =
                federatedBoardRepo.findLocalEditOverride(-999, UUID.fromString("99999999-9999-9999-9999-999999999999"));
        assertTrue(access.userTypes().isEmpty());
        assertTrue(access.groupIds().isEmpty());
        assertTrue(access.tagIds().isEmpty());
    }
}
