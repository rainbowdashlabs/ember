/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
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
import dev.chojo.ember.feature.members.service.StationMemberService;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedBoardServiceTest extends RepositoryTestBase {
    private static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE_MEMBER_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID REMOTE_AUTHOR_1 = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID REMOTE_CREATOR_1 = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID REMOTE_WATCHER_1 = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final UUID REMOTE_WATCHER_2 = UUID.fromString("00000000-0000-0000-0000-000000000006");
    private static final UUID REMOTE_WATCHER_3 = UUID.fromString("00000000-0000-0000-0000-000000000007");
    private static final UUID REMOTE_ASSIGNEE = UUID.fromString("00000000-0000-0000-0000-000000000008");
    private static final UUID REMOTE_BOARD_UID_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID REMOTE_BOARD_UID_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID REMOTE_BOARD_UID_3 = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REMOTE_BOARD_UID_NONEXIST = UUID.fromString("99999999-9999-9999-9999-999999999999");

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
        ticketService = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                new DomainEventBus(Set.of()),
                new StationMemberService(stationMemberRepo, stationRepo, null, null),
                memberIdentityFactory,
                memberNameResolver);

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
                boardId,
                laneId,
                "Fed Ticket",
                "Desc",
                null,
                TicketPriority.MEDIUM,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
        ticketId = ticket.id();

        // Create a comment
        var comment = ticketService.createComment(
                ticketId, null, memberIdentityFactory.local(station.id(), member.id()), "Fed comment");
        commentId = comment.id();

        // Create federation partners via direct SQL
        partnerId = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p::uuid, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of()
                        .bind("s", station.id())
                        .bind("p", partnerStation.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();

        // Create second partner station and partner for multi-partner tests
        var partnerStation2 = stationRepo.create("FedBoardPartner2");
        partner2Id = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p::uuid, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of()
                        .bind("s", station.id())
                        .bind("p", partnerStation2.uid(), StandardValueConverter.UUID_STRING))
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
    void canFederatedEditNoUserTypesRestriction() {
        // partner2 is FULL, no edit user types set => any user type can edit
        assertTrue(service.canFederatedEdit(boardId, partner2Id, List.of("MEMBER", "GUARDIAN", "TEAM")));
    }

    @Test
    @Order(15)
    void canFederatedEditWithUserTypesRestriction() {
        service.setFederatedEditUserTypes(boardId, List.of("TEAM", "MANAGER"));
        // partner2 has FULL mode, user type TEAM is allowed
        assertTrue(service.canFederatedEdit(boardId, partner2Id, List.of("TEAM")));
        // user type MEMBER is not allowed
        assertFalse(service.canFederatedEdit(boardId, partner2Id, List.of("MEMBER")));
    }

    @Test
    @Order(16)
    void canFederatedEditReadOnlyDenied() {
        // partnerId is READ_ONLY, should be denied even with matching user types
        assertFalse(service.canFederatedEdit(boardId, partnerId, List.of("TEAM")));
    }

    @Test
    @Order(17)
    void findFederatedEditUserTypes() {
        var userTypes = service.findFederatedEditUserTypes(boardId);
        assertEquals(2, userTypes.size());
        assertTrue(userTypes.contains("TEAM"));
        assertTrue(userTypes.contains("MANAGER"));
    }

    @Test
    @Order(18)
    void clearFederatedEditUserTypes() {
        service.setFederatedEditUserTypes(boardId, List.of());
        var userTypes = service.findFederatedEditUserTypes(boardId);
        assertTrue(userTypes.isEmpty());
    }

    // Satellite table tests removed — identity is now inline in board_ticket columns

    // -- Bookmarks --

    @Test
    @Order(60)
    void createAndFindBookmarks() {
        var bookmark = service.createBookmark(
                member.id(), partnerId, REMOTE_BOARD_UID_1, "Remote Board", "RB", BoardShareMode.FULL);
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
        service.updateBookmarkName(partnerId, REMOTE_BOARD_UID_1, "Renamed Board", "RNB");
        var bookmarks = service.findBookmarks(member.id());
        assertEquals("Renamed Board", bookmarks.getFirst().remoteBoardName());
        assertEquals("RNB", bookmarks.getFirst().remoteBoardShortKey());
    }

    @Test
    @Order(62)
    void updateBookmarkShareMode() {
        service.updateBookmarkShareMode(partnerId, REMOTE_BOARD_UID_1, BoardShareMode.READ_ONLY);
        var bookmarks = service.findBookmarks(member.id());
        assertEquals(BoardShareMode.READ_ONLY, bookmarks.getFirst().shareMode());
    }

    @Test
    @Order(63)
    void deleteBookmarkByBoard() {
        // Create another bookmark first
        service.createBookmark(member.id(), partnerId, REMOTE_BOARD_UID_2, "Another Board", "AB", BoardShareMode.FULL);
        service.deleteBookmarkByBoard(member.id(), partnerId, REMOTE_BOARD_UID_2);
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
        service.createBookmark(member.id(), partnerId, REMOTE_BOARD_UID_3, "Board 300", "B3", BoardShareMode.FULL);
        service.deleteBookmarksByBoard(partnerId, REMOTE_BOARD_UID_3);
        assertTrue(service.findBookmarks(member.id()).isEmpty());
    }

    // -- Local Overrides --

    @Test
    @Order(70)
    void setAndGetLocalViewOverride() {
        var access = new AccessData(List.of("MEMBER", "GUARDIAN"), List.of(3), List.of(4, 5));
        service.setLocalViewOverride(partnerId, REMOTE_BOARD_UID_1, access);

        assertTrue(service.hasLocalViewOverride(partnerId, REMOTE_BOARD_UID_1));
        var result = service.getLocalViewOverride(partnerId, REMOTE_BOARD_UID_1);
        assertEquals(2, result.userTypes().size());
        assertTrue(result.userTypes().containsAll(List.of("MEMBER", "GUARDIAN")));
        assertEquals(List.of(3), result.groupIds());
        assertEquals(List.of(4, 5), result.tagIds());
    }

    @Test
    @Order(71)
    void noLocalViewOverride() {
        assertFalse(service.hasLocalViewOverride(partnerId, REMOTE_BOARD_UID_NONEXIST));
        var result = service.getLocalViewOverride(partnerId, REMOTE_BOARD_UID_NONEXIST);
        assertTrue(result.userTypes().isEmpty());
    }

    @Test
    @Order(72)
    void setAndGetLocalEditOverride() {
        var access = new AccessData(List.of("TEAM"), List.of(20, 30), List.of());
        service.setLocalEditOverride(partnerId, REMOTE_BOARD_UID_1, access);

        assertTrue(service.hasLocalEditOverride(partnerId, REMOTE_BOARD_UID_1));
        var result = service.getLocalEditOverride(partnerId, REMOTE_BOARD_UID_1);
        assertEquals(List.of("TEAM"), result.userTypes());
        assertEquals(List.of(20, 30), result.groupIds());
        assertTrue(result.tagIds().isEmpty());
    }

    @Test
    @Order(73)
    void noLocalEditOverride() {
        assertFalse(service.hasLocalEditOverride(partnerId, REMOTE_BOARD_UID_NONEXIST));
        var result = service.getLocalEditOverride(partnerId, REMOTE_BOARD_UID_NONEXIST);
        assertTrue(result.userTypes().isEmpty());
    }

    @Test
    @Order(74)
    void overwriteLocalViewOverride() {
        var newAccess = new AccessData(List.of("MANAGER"), List.of(), List.of());
        service.setLocalViewOverride(partnerId, REMOTE_BOARD_UID_1, newAccess);
        var result = service.getLocalViewOverride(partnerId, REMOTE_BOARD_UID_1);
        assertEquals(List.of("MANAGER"), result.userTypes());
        assertTrue(result.groupIds().isEmpty());
    }

    @Test
    @Order(75)
    void overwriteLocalEditOverride() {
        var newAccess = new AccessData(List.of(), List.of(), List.of(77));
        service.setLocalEditOverride(partnerId, REMOTE_BOARD_UID_1, newAccess);
        var result = service.getLocalEditOverride(partnerId, REMOTE_BOARD_UID_1);
        assertTrue(result.userTypes().isEmpty());
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
        assertEquals("USER", config.requiredRole());

        var configWithRole = new PartnerShareConfig(partnerId, BoardShareMode.FULL, "MANAGER");
        assertEquals("MANAGER", configWithRole.requiredRole());
    }

    // ============================================================
    // FederatedBoardNotificationService tests
    // ============================================================

    @Test
    @Order(100)
    void notifyFederatedWatchersNoWatchers() {
        // Re-share the board for later tests
        service.shareBoard(
                boardId,
                List.of(
                        new PartnerShareConfig(partnerId, BoardShareMode.FULL),
                        new PartnerShareConfig(partner2Id, BoardShareMode.READ_ONLY)));

        // Create a second ticket with no watchers
        var ticket2 = ticketService.createTicket(
                boardId,
                laneId,
                "No watchers",
                null,
                null,
                TicketPriority.LOW,
                null,
                memberIdentityFactory.local(station.id(), member.id()));
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
        notificationService.notifyMention(partnerId, boardId, ticketId, "FTB-1", REMOTE_MEMBER_1);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_MENTION),
                        argThat(payload -> ((FederatedBoardNotificationService.TicketMemberPayload) payload)
                                .remoteMemberId()
                                .equals(REMOTE_MEMBER_1)));

        reset(webhookService);
        // partner2Id is READ_ONLY
        notificationService.notifyMention(partner2Id, boardId, ticketId, "FTB-1", REMOTE_MEMBER_2);
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(120)
    void notifyAssignment() {
        reset(webhookService);
        notificationService.notifyAssignment(partnerId, boardId, ticketId, "FTB-1", REMOTE_ASSIGNEE);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_ASSIGNMENT),
                        argThat(payload -> ((FederatedBoardNotificationService.TicketMemberPayload) payload)
                                .remoteMemberId()
                                .equals(REMOTE_ASSIGNEE)));
    }

    @Test
    @Order(121)
    void notifyAssignmentReadOnlySkipped() {
        reset(webhookService);
        notificationService.notifyAssignment(partner2Id, boardId, ticketId, "FTB-1", REMOTE_ASSIGNEE);
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(130)
    void notifyUnassignment() {
        reset(webhookService);
        notificationService.notifyUnassignment(partnerId, boardId, ticketId, "FTB-1", REMOTE_ASSIGNEE);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_UNASSIGNMENT),
                        argThat(payload -> ((FederatedBoardNotificationService.TicketMemberPayload) payload)
                                .remoteMemberId()
                                .equals(REMOTE_ASSIGNEE)));
    }

    @Test
    @Order(131)
    void notifyUnassignmentReadOnlySkipped() {
        reset(webhookService);
        notificationService.notifyUnassignment(partner2Id, boardId, ticketId, "FTB-1", REMOTE_ASSIGNEE);
        verifyNoInteractions(webhookService);
    }

    @Test
    @Order(140)
    void notifyBoardRenamed() {
        reset(webhookService);
        notificationService.notifyBoardRenamed(boardId, "New Name", "NN");
        // Should be called for both share targets
        verify(webhookService).fireEventToPartner(eq(partnerId), eq(WebhookEvent.BOARD_RENAMED), argThat(payload -> {
            var p = (FederatedBoardNotificationService.BoardRenamedPayload) payload;
            return p.newName().equals("New Name") && p.newShortKey().equals("NN");
        }));
        verify(webhookService)
                .fireEventToPartner(
                        eq(partner2Id),
                        eq(WebhookEvent.BOARD_RENAMED),
                        argThat(payload -> ((FederatedBoardNotificationService.BoardRenamedPayload) payload)
                                .newName()
                                .equals("New Name")));
    }

    @Test
    @Order(150)
    void notifyBoardUnshared() {
        reset(webhookService);
        notificationService.notifyBoardUnshared(boardId);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_UNSHARED),
                        argThat(payload -> payload instanceof FederatedBoardNotificationService.BoardIdPayload));
        verify(webhookService)
                .fireEventToPartner(
                        eq(partner2Id),
                        eq(WebhookEvent.BOARD_UNSHARED),
                        argThat(payload -> payload instanceof FederatedBoardNotificationService.BoardIdPayload));
    }

    @Test
    @Order(160)
    void notifyShareModeChanged() {
        reset(webhookService);
        notificationService.notifyShareModeChanged(partnerId, boardId, BoardShareMode.READ_ONLY);
        verify(webhookService)
                .fireEventToPartner(
                        eq(partnerId),
                        eq(WebhookEvent.BOARD_SHARE_MODE_CHANGED),
                        argThat(payload -> ((FederatedBoardNotificationService.ShareModeChangedPayload) payload)
                                .shareMode()
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
