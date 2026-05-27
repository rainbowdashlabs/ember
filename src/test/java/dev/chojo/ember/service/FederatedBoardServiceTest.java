/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.service.BoardTicketService;
import dev.chojo.ember.feature.board.service.FederatedBoardNotificationService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.board.service.FederatedBoardService.PartnerShareConfig;
import dev.chojo.ember.feature.federation.service.FederationWebhookService;
import dev.chojo.ember.feature.federation.service.FederationWebhookService.WebhookEvent;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedBoardServiceTest extends RepositoryTestBase {
    private static FederatedBoardService service;
    private static FederatedBoardNotificationService notificationService;
    private static FederationWebhookService webhookService;
    private static BoardTicketService ticketService;

    private static Station station;
    private static Station partnerStation;
    private static Account account;
    private static StationMember member;

    private static int boardId;
    private static int laneId;
    private static int ticketId;
    private static int commentId;
    private static int partnerId;
    private static int partner2Id;

    @BeforeAll
    static void setup() {
        service = new FederatedBoardService(federatedBoardRepo);
        webhookService = mock(FederationWebhookService.class);
        notificationService = new FederatedBoardNotificationService(webhookService, service);
        ticketService = new BoardTicketService(boardTicketRepo, boardRepo, new DomainEventBus(Set.of()));

        station = stationRepo.create("FedBoardStation");
        partnerStation = stationRepo.create("FedBoardPartner");
        account = accountRepo.create("fed-board@test.com", "Fed", "Board");
        member = stationMemberRepo.create(station.id(), account.id());

        // Create a board with a lane
        var board = boardRepo.create(station.id(), "Fed Test Board", "Desc", "FTB");
        boardId = board.id();
        var lane = boardRepo.createLane(boardId, "Open", null, 0);
        laneId = lane.id();

        // Create a ticket
        BoardTicket ticket = ticketService.createTicket(
                boardId, laneId, "Fed Ticket", "Desc", null, TicketPriority.MEDIUM, null, member.id());
        ticketId = ticket.id();

        // Create a comment
        var comment = ticketService.createComment(ticketId, null, member.id(), "Fed comment");
        commentId = comment.id();

        // Create federation partners via direct SQL
        partnerId = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of().bind("s", station.id()).bind("p", partnerStation.id()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();

        // Create second partner station and partner for multi-partner tests
        var partnerStation2 = stationRepo.create("FedBoardPartner2");
        partner2Id = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of().bind("s", station.id()).bind("p", partnerStation2.id()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        boardRepo.delete(boardId);
        stationRepo.delete(station.id());
        stationRepo.delete(partnerStation.id());
        accountRepo.delete(account.id());
    }

    // -- Sharing --

    @Test
    @Order(1)
    void shareBoardCreatesShareAndTargets() {
        service.shareBoard(boardId, List.of(new PartnerShareConfig(partnerId, BoardShareMode.FULL)));

        var share = service.findShare(boardId);
        assertTrue(share.isPresent());
        assertEquals(boardId, share.get().boardId());

        var targets = service.findShareTargets(boardId);
        assertEquals(1, targets.size());
        assertEquals(partnerId, targets.getFirst().partnerId());
        assertEquals(BoardShareMode.FULL, targets.getFirst().shareMode());
    }

    @Test
    @Order(2)
    void shareBoardReplacesExistingTargets() {
        service.shareBoard(
                boardId,
                List.of(
                        new PartnerShareConfig(partnerId, BoardShareMode.READ_ONLY),
                        new PartnerShareConfig(partner2Id, BoardShareMode.FULL)));

        var targets = service.findShareTargets(boardId);
        assertEquals(2, targets.size());
    }

    @Test
    @Order(3)
    void getShareMode() {
        var mode = service.getShareMode(boardId, partnerId);
        assertTrue(mode.isPresent());
        assertEquals(BoardShareMode.READ_ONLY, mode.get());

        var mode2 = service.getShareMode(boardId, partner2Id);
        assertTrue(mode2.isPresent());
        assertEquals(BoardShareMode.FULL, mode2.get());
    }

    @Test
    @Order(4)
    void findSharedBoardIds() {
        var ids = service.findSharedBoardIds(partnerId);
        assertTrue(ids.contains(boardId));
    }

    @Test
    @Order(5)
    void isSharedWith() {
        assertTrue(service.isSharedWith(boardId, partnerId));
        assertFalse(service.isSharedWith(boardId, 99999));
    }

    @Test
    @Order(6)
    void findShareTargetsForUnshareBoardReturnsEmpty() {
        var targets = service.findShareTargets(99999);
        assertTrue(targets.isEmpty());
    }

    // -- Access Control --

    @Test
    @Order(10)
    void canFederatedView() {
        assertTrue(service.canFederatedView(boardId, partnerId));
        assertFalse(service.canFederatedView(boardId, 99999));
    }

    @Test
    @Order(11)
    void canFederatedWriteFullMode() {
        // partner2 is FULL
        assertTrue(service.canFederatedWrite(boardId, partner2Id));
    }

    @Test
    @Order(12)
    void canFederatedWriteReadOnlyMode() {
        // partnerId is READ_ONLY
        assertFalse(service.canFederatedWrite(boardId, partnerId));
    }

    @Test
    @Order(13)
    void canFederatedWriteNotShared() {
        assertFalse(service.canFederatedWrite(boardId, 99999));
    }

    @Test
    @Order(14)
    void canFederatedEditNoRolesRestriction() {
        // partner2 is FULL, no edit roles set => any role can edit
        assertTrue(service.canFederatedEdit(boardId, partner2Id, List.of(1, 2, 3)));
    }

    @Test
    @Order(15)
    void canFederatedEditWithRolesRestriction() {
        service.setFederatedEditRoles(boardId, List.of(10, 20));
        // partner2 has FULL mode, role 10 is allowed
        assertTrue(service.canFederatedEdit(boardId, partner2Id, List.of(10)));
        // role 99 is not allowed
        assertFalse(service.canFederatedEdit(boardId, partner2Id, List.of(99)));
    }

    @Test
    @Order(16)
    void canFederatedEditReadOnlyDenied() {
        // partnerId is READ_ONLY, should be denied even with matching roles
        assertFalse(service.canFederatedEdit(boardId, partnerId, List.of(10)));
    }

    @Test
    @Order(17)
    void findFederatedEditRoles() {
        var roles = service.findFederatedEditRoles(boardId);
        assertEquals(2, roles.size());
        assertTrue(roles.contains(10));
        assertTrue(roles.contains(20));
    }

    @Test
    @Order(18)
    void clearFederatedEditRoles() {
        service.setFederatedEditRoles(boardId, List.of());
        var roles = service.findFederatedEditRoles(boardId);
        assertTrue(roles.isEmpty());
    }

    // -- Federated Assignees --

    @Test
    @Order(20)
    void setAndFindFederatedAssignee() {
        service.setFederatedAssignee(ticketId, partnerId, "remote-member-1");
        var assignee = service.findFederatedAssignee(ticketId);
        assertTrue(assignee.isPresent());
        assertEquals(partnerId, assignee.get().partnerId());
        assertEquals("remote-member-1", assignee.get().remoteMemberId());
    }

    @Test
    @Order(21)
    void setFederatedAssigneeOverwrites() {
        service.setFederatedAssignee(ticketId, partner2Id, "remote-member-2");
        var assignee = service.findFederatedAssignee(ticketId);
        assertTrue(assignee.isPresent());
        assertEquals(partner2Id, assignee.get().partnerId());
        assertEquals("remote-member-2", assignee.get().remoteMemberId());
    }

    @Test
    @Order(22)
    void removeFederatedAssignee() {
        service.removeFederatedAssignee(ticketId);
        assertTrue(service.findFederatedAssignee(ticketId).isEmpty());
    }

    // -- Federated Comment Authors --

    @Test
    @Order(30)
    void setAndFindFederatedCommentAuthor() {
        service.setFederatedCommentAuthor(commentId, partnerId, "remote-author-1");
        var author = service.findFederatedCommentAuthor(commentId);
        assertTrue(author.isPresent());
        assertEquals(partnerId, author.get().partnerId());
        assertEquals("remote-author-1", author.get().remoteMemberId());
    }

    // -- Federated Creators --

    @Test
    @Order(40)
    void setAndFindFederatedCreator() {
        service.setFederatedCreator(ticketId, partnerId, "remote-creator-1");
        var creator = service.findFederatedCreator(ticketId);
        assertTrue(creator.isPresent());
        assertEquals(partnerId, creator.get().partnerId());
        assertEquals("remote-creator-1", creator.get().remoteMemberId());
    }

    // -- Federated Watchers --

    @Test
    @Order(50)
    void addAndFindFederatedWatchers() {
        service.addFederatedWatcher(ticketId, partnerId, "remote-watcher-1");
        service.addFederatedWatcher(ticketId, partnerId, "remote-watcher-2");
        service.addFederatedWatcher(ticketId, partner2Id, "remote-watcher-3");

        var watchers = service.findFederatedWatchers(ticketId);
        assertEquals(3, watchers.size());
    }

    @Test
    @Order(51)
    void isFederatedWatching() {
        assertTrue(service.isFederatedWatching(ticketId, partnerId, "remote-watcher-1"));
        assertFalse(service.isFederatedWatching(ticketId, partnerId, "non-existent"));
    }

    @Test
    @Order(52)
    void removeFederatedWatcher() {
        service.removeFederatedWatcher(ticketId, partnerId, "remote-watcher-2");
        assertFalse(service.isFederatedWatching(ticketId, partnerId, "remote-watcher-2"));
        assertEquals(2, service.findFederatedWatchers(ticketId).size());
    }

    // -- Bookmarks --

    @Test
    @Order(60)
    void createAndFindBookmarks() {
        var bookmark = service.createBookmark(member.id(), partnerId, 100, "Remote Board", "RB", BoardShareMode.FULL);
        assertNotNull(bookmark);
        assertEquals("Remote Board", bookmark.remoteBoardName());
        assertEquals("RB", bookmark.remoteBoardShortKey());
        assertEquals(BoardShareMode.FULL, bookmark.shareMode());

        var bookmarks = service.findBookmarks(member.id());
        assertEquals(1, bookmarks.size());
    }

    @Test
    @Order(61)
    void updateBookmarkName() {
        service.updateBookmarkName(partnerId, 100, "Renamed Board", "RNB");
        var bookmarks = service.findBookmarks(member.id());
        assertEquals("Renamed Board", bookmarks.getFirst().remoteBoardName());
        assertEquals("RNB", bookmarks.getFirst().remoteBoardShortKey());
    }

    @Test
    @Order(62)
    void updateBookmarkShareMode() {
        service.updateBookmarkShareMode(partnerId, 100, BoardShareMode.READ_ONLY);
        var bookmarks = service.findBookmarks(member.id());
        assertEquals(BoardShareMode.READ_ONLY, bookmarks.getFirst().shareMode());
    }

    @Test
    @Order(63)
    void deleteBookmarkByBoard() {
        // Create another bookmark first
        service.createBookmark(member.id(), partnerId, 200, "Another Board", "AB", BoardShareMode.FULL);
        service.deleteBookmarkByBoard(member.id(), partnerId, 200);
        var bookmarks = service.findBookmarks(member.id());
        assertEquals(1, bookmarks.size());
    }

    @Test
    @Order(64)
    void deleteBookmark() {
        var bookmarks = service.findBookmarks(member.id());
        service.deleteBookmark(bookmarks.getFirst().id());
        assertTrue(service.findBookmarks(member.id()).isEmpty());
    }

    @Test
    @Order(65)
    void deleteBookmarksByBoard() {
        // Create bookmarks, then delete by board
        service.createBookmark(member.id(), partnerId, 300, "Board 300", "B3", BoardShareMode.FULL);
        service.deleteBookmarksByBoard(partnerId, 300);
        assertTrue(service.findBookmarks(member.id()).isEmpty());
    }

    // -- Local Overrides --

    @Test
    @Order(70)
    void setAndGetLocalViewOverride() {
        var access = new AccessData(List.of(1, 2), List.of(3), List.of(4, 5));
        service.setLocalViewOverride(partnerId, 100, access);

        assertTrue(service.hasLocalViewOverride(partnerId, 100));
        var result = service.getLocalViewOverride(partnerId, 100);
        assertEquals(List.of(1, 2), result.roleIds());
        assertEquals(List.of(3), result.groupIds());
        assertEquals(List.of(4, 5), result.tagIds());
    }

    @Test
    @Order(71)
    void noLocalViewOverride() {
        assertFalse(service.hasLocalViewOverride(partnerId, 99999));
        var result = service.getLocalViewOverride(partnerId, 99999);
        assertTrue(result.roleIds().isEmpty());
    }

    @Test
    @Order(72)
    void setAndGetLocalEditOverride() {
        var access = new AccessData(List.of(10), List.of(20, 30), List.of());
        service.setLocalEditOverride(partnerId, 100, access);

        assertTrue(service.hasLocalEditOverride(partnerId, 100));
        var result = service.getLocalEditOverride(partnerId, 100);
        assertEquals(List.of(10), result.roleIds());
        assertEquals(List.of(20, 30), result.groupIds());
        assertTrue(result.tagIds().isEmpty());
    }

    @Test
    @Order(73)
    void noLocalEditOverride() {
        assertFalse(service.hasLocalEditOverride(partnerId, 99999));
        var result = service.getLocalEditOverride(partnerId, 99999);
        assertTrue(result.roleIds().isEmpty());
    }

    @Test
    @Order(74)
    void overwriteLocalViewOverride() {
        var newAccess = new AccessData(List.of(99), List.of(), List.of());
        service.setLocalViewOverride(partnerId, 100, newAccess);
        var result = service.getLocalViewOverride(partnerId, 100);
        assertEquals(List.of(99), result.roleIds());
        assertTrue(result.groupIds().isEmpty());
    }

    @Test
    @Order(75)
    void overwriteLocalEditOverride() {
        var newAccess = new AccessData(List.of(), List.of(), List.of(77));
        service.setLocalEditOverride(partnerId, 100, newAccess);
        var result = service.getLocalEditOverride(partnerId, 100);
        assertTrue(result.roleIds().isEmpty());
        assertEquals(List.of(77), result.tagIds());
    }

    // -- Unshare --

    @Test
    @Order(80)
    void unshareBoardRemovesEverything() {
        service.unshareBoard(boardId);
        assertTrue(service.findShare(boardId).isEmpty());
        assertTrue(service.findShareTargets(boardId).isEmpty());
        assertFalse(service.isSharedWith(boardId, partnerId));
    }

    // -- PartnerShareConfig record --

    @Test
    @Order(90)
    void partnerShareConfigRecord() {
        var config = new PartnerShareConfig(partnerId, BoardShareMode.FULL);
        assertEquals(partnerId, config.partnerId());
        assertEquals(BoardShareMode.FULL, config.shareMode());
    }

    // ============================================================
    // FederatedBoardNotificationService tests
    // ============================================================

    @Test
    @Order(100)
    void notifyFederatedWatchersGroupsByPartnerAndOnlySendsToFull() {
        // Re-share the board: partnerId=FULL, partner2Id=READ_ONLY
        service.shareBoard(
                boardId,
                List.of(
                        new PartnerShareConfig(partnerId, BoardShareMode.FULL),
                        new PartnerShareConfig(partner2Id, BoardShareMode.READ_ONLY)));

        // Watchers: partnerId has remote-watcher-1, partner2Id has remote-watcher-3
        // (from earlier tests, watcher-2 was removed)

        reset(webhookService);
        notificationService.notifyFederatedWatchers(ticketId, boardId, "FTB-1", "Updated title");

        // Only partnerId (FULL) should be notified, not partner2Id (READ_ONLY)
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_TICKET_CHANGED),
                        argThat(map -> map.get("ticketKey").equals("FTB-1")
                                && map.get("changeDescription").equals("Updated title")
                                && ((List<?>) map.get("remoteMemberIds")).contains("remote-watcher-1")));
        verify(webhookService, never())
                .fireEventToPartner(eq(partner2Id), eq(WebhookEvent.BOARD_TICKET_CHANGED), any());
    }

    @Test
    @Order(101)
    void notifyFederatedWatchersNoWatchers() {
        // Create a second ticket with no watchers
        var ticket2 = ticketService.createTicket(
                boardId, laneId, "No watchers", null, null, TicketPriority.LOW, null, member.id());
        reset(webhookService);
        notificationService.notifyFederatedWatchers(ticket2.id(), boardId, "FTB-2", "change");
        verifyNoInteractions(webhookService);
        ticketService.deleteTicket(ticket2.id());
    }

    @Test
    @Order(110)
    void notifyMentionOnlyForFullMode() {
        reset(webhookService);
        // partnerId is FULL
        notificationService.notifyMention(partnerId, boardId, ticketId, "FTB-1", "remote-member-1");
        verify(webhookService).fireEventToPartner(eq(partnerId), eq(WebhookEvent.BOARD_MENTION), argThat(map -> map.get(
                        "remoteMemberId")
                .equals("remote-member-1")));

        reset(webhookService);
        // partner2Id is READ_ONLY
        notificationService.notifyMention(partner2Id, boardId, ticketId, "FTB-1", "remote-member-2");
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(120)
    void notifyAssignment() {
        reset(webhookService);
        notificationService.notifyAssignment(partnerId, boardId, ticketId, "FTB-1", "remote-assignee");
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId), eq(WebhookEvent.BOARD_ASSIGNMENT), argThat(map -> map.get("remoteMemberId")
                                .equals("remote-assignee")));
    }

    @Test
    @Order(121)
    void notifyAssignmentReadOnlySkipped() {
        reset(webhookService);
        notificationService.notifyAssignment(partner2Id, boardId, ticketId, "FTB-1", "remote-assignee");
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(130)
    void notifyUnassignment() {
        reset(webhookService);
        notificationService.notifyUnassignment(partnerId, boardId, ticketId, "FTB-1", "remote-assignee");
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId), eq(WebhookEvent.BOARD_UNASSIGNMENT), argThat(map -> map.get("remoteMemberId")
                                .equals("remote-assignee")));
    }

    @Test
    @Order(131)
    void notifyUnassignmentReadOnlySkipped() {
        reset(webhookService);
        notificationService.notifyUnassignment(partner2Id, boardId, ticketId, "FTB-1", "remote-assignee");
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(140)
    void notifyBoardRenamed() {
        reset(webhookService);
        notificationService.notifyBoardRenamed(boardId, "New Name", "NN");
        // Should be called for both share targets
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_RENAMED),
                        argThat(map -> map.get("newName").equals("New Name")
                                && map.get("newShortKey").equals("NN")));
        verify(webhookService)
                .fireEventToPartner(eq(partner2Id), eq(WebhookEvent.BOARD_RENAMED), argThat(map -> map.get("newName")
                        .equals("New Name")));
    }

    @Test
    @Order(150)
    void notifyBoardUnshared() {
        reset(webhookService);
        notificationService.notifyBoardUnshared(boardId);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId), eq(WebhookEvent.BOARD_UNSHARED), argThat(map -> map.containsKey("boardId")));
        verify(webhookService)
                .fireEventToPartner(
                        eq(partner2Id), eq(WebhookEvent.BOARD_UNSHARED), argThat(map -> map.containsKey("boardId")));
    }

    @Test
    @Order(160)
    void notifyShareModeChanged() {
        reset(webhookService);
        notificationService.notifyShareModeChanged(partnerId, boardId, BoardShareMode.READ_ONLY);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId), eq(WebhookEvent.BOARD_SHARE_MODE_CHANGED), argThat(map -> map.get("shareMode")
                                .equals("READ_ONLY")));
    }

    @Test
    @Order(170)
    void notifyBoardRenamedNoTargets() {
        // Unshare and verify no notifications sent
        service.unshareBoard(boardId);
        reset(webhookService);
        notificationService.notifyBoardRenamed(boardId, "Nobody cares", "NC");
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(171)
    void notifyBoardUnsharedNoTargets() {
        reset(webhookService);
        notificationService.notifyBoardUnshared(boardId);
        verifyNoInteractions(webhookService);
    }
}
