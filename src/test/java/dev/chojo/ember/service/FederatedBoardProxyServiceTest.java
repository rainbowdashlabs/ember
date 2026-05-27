/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.service.BoardService;
import dev.chojo.ember.feature.board.service.FederatedBoardProxyService;
import dev.chojo.ember.feature.board.service.FederatedBoardService;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.Role;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedBoardProxyServiceTest extends RepositoryTestBase {
    private static FederatedBoardProxyService proxyService;
    private static FederatedBoardService federatedBoardService;
    private static BoardService boardService;
    private static FederationService federationService;
    private static FederationHttpClient httpClient;
    private static StationMemberService memberService;
    private static MemberGroupService groupService;
    private static UserTagService tagService;

    private static Station station1;
    private static Station station2;
    private static Account account;
    private static int memberId;
    private static int boardId;
    private static int partnerId;
    private static int bookmarkId;

    @BeforeAll
    static void setup() {
        federationService = mock(FederationService.class);
        httpClient = mock(FederationHttpClient.class);
        memberService = mock(StationMemberService.class);
        groupService = mock(MemberGroupService.class);
        tagService = mock(UserTagService.class);

        federatedBoardService = new FederatedBoardService(federatedBoardRepo);
        boardService = new BoardService(boardRepo, memberService, groupService, tagService);

        proxyService = new FederatedBoardProxyService(
                federatedBoardService,
                federatedBoardRepo,
                boardService,
                federationService,
                httpClient,
                stationRepo,
                memberService,
                groupService,
                tagService);

        station1 = stationRepo.create("ProxyStation1");
        station2 = stationRepo.create("ProxyStation2");
        account = accountRepo.create("proxy-test@test.com", "Proxy", "Test");
        var member = stationMemberRepo.create(station1.id(), account.id());
        memberId = member.id();

        // Create a board on station1
        var board = boardService.createWithPreset(station1.id(), "Shared Board", "Desc", "SHR", LanePreset.SIMPLE);
        boardId = board.id();

        // Create a federation partner via direct SQL
        partnerId = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of().bind("s", station1.id()).bind("p", station2.id()))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        boardService.delete(boardId);
        Query.query("DELETE FROM federation_partner WHERE id = :id;")
                .single(Call.of().bind("id", partnerId))
                .delete();
        stationRepo.delete(station1.id());
        stationRepo.delete(station2.id());
        accountRepo.delete(account.id());
    }

    // -- Discovery --

    @Test
    @Order(1)
    void discoverBoardsFindsSharedBoard() {
        // Share the board with the partner
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.READ_ONLY)));

        // Mock federation service to return a local partner
        var partner = new FederationPartner(
                partnerId,
                station1.id(),
                station2.id(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now(),
                null);
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));
        when(federationService.hasCapability(partnerId, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .thenReturn(true);

        var discovered = proxyService.discoverBoards(station1.id());
        assertFalse(discovered.isEmpty());
        assertEquals("Shared Board", discovered.getFirst().name());
        assertEquals(BoardShareMode.READ_ONLY, discovered.getFirst().shareMode());
        assertEquals("ProxyStation2", discovered.getFirst().partnerStationName());
    }

    @Test
    @Order(2)
    void discoverBoardsSkipsInactivePartner() {
        var partner = new FederationPartner(
                partnerId,
                station1.id(),
                station2.id(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.SUSPENDED,
                1,
                Instant.now(),
                Instant.now(),
                null);
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));

        var discovered = proxyService.discoverBoards(station1.id());
        assertTrue(discovered.isEmpty());
    }

    @Test
    @Order(3)
    void discoverBoardsSkipsPartnerWithoutCapability() {
        var partner = new FederationPartner(
                partnerId,
                station1.id(),
                station2.id(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now(),
                null);
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));
        when(federationService.hasCapability(partnerId, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .thenReturn(false);

        var discovered = proxyService.discoverBoards(station1.id());
        assertTrue(discovered.isEmpty());
    }

    // -- Effective share mode --

    @Test
    @Order(10)
    void getEffectiveShareModeReadOnly() {
        var mode = proxyService.getEffectiveShareMode(partnerId, boardId);
        assertTrue(mode.isPresent());
        assertEquals(BoardShareMode.READ_ONLY, mode.get());
    }

    @Test
    @Order(11)
    void getEffectiveShareModeFull() {
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.FULL)));
        var mode = proxyService.getEffectiveShareMode(partnerId, boardId);
        assertTrue(mode.isPresent());
        assertEquals(BoardShareMode.FULL, mode.get());
    }

    @Test
    @Order(12)
    void getEffectiveShareModeEmpty() {
        var mode = proxyService.getEffectiveShareMode(partnerId, 999999);
        assertTrue(mode.isEmpty());
    }

    // -- Local view overrides --

    @Test
    @Order(20)
    void passesLocalViewOverrideWhenNoOverride() {
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(21)
    void passesLocalViewOverrideWithMatchingRole() {
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(42), List.of(), List.of()));
        when(memberService.findRoles(memberId)).thenReturn(List.of(new Role(42, dev.chojo.ember.api.Roles.USER)));
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(22)
    void failsLocalViewOverrideWithWrongRole() {
        when(memberService.findRoles(memberId)).thenReturn(List.of(new Role(1, dev.chojo.ember.api.Roles.LOGIN)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.passesLocalViewOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(23)
    void passesLocalViewOverrideWithMatchingGroup() {
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(), List.of(55), List.of()));
        when(memberService.findRoles(memberId)).thenReturn(List.of());
        when(groupService.findGroupsForMember(memberId))
                .thenReturn(List.of(new MemberGroup(55, station1.id(), "TestGroup")));
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(24)
    void passesLocalViewOverrideWithMatchingTag() {
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of(77)));
        when(memberService.findRoles(memberId)).thenReturn(List.of());
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of(new UserTag(77, station1.id(), "TestTag")));
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(25)
    void clearViewOverride() {
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- Local edit overrides --

    @Test
    @Order(30)
    void passesLocalEditOverrideWhenNoOverride() {
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(31)
    void passesLocalEditOverrideWithMatchingRole() {
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(42), List.of(), List.of()));
        when(memberService.findRoles(memberId)).thenReturn(List.of(new Role(42, dev.chojo.ember.api.Roles.USER)));
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(32)
    void failsLocalEditOverrideWithWrongRole() {
        when(memberService.findRoles(memberId)).thenReturn(List.of(new Role(1, dev.chojo.ember.api.Roles.LOGIN)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.passesLocalEditOverride(partnerId, boardId, memberId));
    }

    @Test
    @Order(33)
    void clearEditOverride() {
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- canView / canWrite --

    @Test
    @Order(40)
    void canViewWhenShared() {
        assertTrue(proxyService.canView(partnerId, boardId, memberId));
    }

    @Test
    @Order(41)
    void cannotViewWhenNotShared() {
        assertFalse(proxyService.canView(partnerId, 999999, memberId));
    }

    @Test
    @Order(42)
    void canWriteWhenFullMode() {
        // Board is currently FULL from test order 11
        assertTrue(proxyService.canWrite(partnerId, boardId, memberId));
    }

    @Test
    @Order(43)
    void cannotWriteWhenReadOnly() {
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.READ_ONLY)));
        assertFalse(proxyService.canWrite(partnerId, boardId, memberId));
    }

    @Test
    @Order(44)
    void cannotWriteWhenNotShared() {
        assertFalse(proxyService.canWrite(partnerId, 999999, memberId));
    }

    @Test
    @Order(45)
    void cannotWriteWhenViewOverrideFails() {
        // Set back to FULL
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.FULL)));
        // Set view override that member won't pass
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(999), List.of(), List.of()));
        when(memberService.findRoles(memberId)).thenReturn(List.of(new Role(1, dev.chojo.ember.api.Roles.LOGIN)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.canWrite(partnerId, boardId, memberId));
        // Cleanup
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    @Test
    @Order(46)
    void cannotWriteWhenEditOverrideFails() {
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(999), List.of(), List.of()));
        when(memberService.findRoles(memberId)).thenReturn(List.of(new Role(1, dev.chojo.ember.api.Roles.LOGIN)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.canWrite(partnerId, boardId, memberId));
        // Cleanup
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- Get overrides --

    @Test
    @Order(50)
    void getLocalViewOverride() {
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(1, 2), List.of(3), List.of(4)));
        var access = proxyService.getLocalViewOverride(partnerId, boardId);
        assertEquals(List.of(1, 2), access.roleIds());
        assertEquals(List.of(3), access.groupIds());
        assertEquals(List.of(4), access.tagIds());
        // Cleanup
        proxyService.setLocalViewOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
    }

    @Test
    @Order(51)
    void getLocalEditOverride() {
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(5), List.of(6, 7), List.of()));
        var access = proxyService.getLocalEditOverride(partnerId, boardId);
        assertEquals(List.of(5), access.roleIds());
        assertEquals(List.of(6, 7), access.groupIds());
        assertTrue(access.tagIds().isEmpty());
        // Cleanup
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
    }

    // -- Bookmarks --

    @Test
    @Order(60)
    void createBookmark() {
        var bookmark =
                proxyService.createBookmark(memberId, partnerId, boardId, "Shared Board", "SHR", BoardShareMode.FULL);
        assertNotNull(bookmark);
        assertEquals(memberId, bookmark.memberId());
        assertEquals(partnerId, bookmark.partnerId());
        assertEquals(boardId, bookmark.remoteBoardId());
        assertEquals("Shared Board", bookmark.remoteBoardName());
        assertEquals("SHR", bookmark.remoteBoardShortKey());
        assertEquals(BoardShareMode.FULL, bookmark.shareMode());
        bookmarkId = bookmark.id();
    }

    @Test
    @Order(61)
    void findBookmarks() {
        var bookmarks = proxyService.findBookmarks(memberId);
        assertEquals(1, bookmarks.size());
        assertEquals(bookmarkId, bookmarks.getFirst().id());
    }

    @Test
    @Order(62)
    void deleteBookmark() {
        proxyService.deleteBookmark(bookmarkId);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertTrue(bookmarks.isEmpty());
    }

    @Test
    @Order(63)
    void deleteBookmarkByBoard() {
        // Recreate a bookmark, then delete by board
        proxyService.createBookmark(memberId, partnerId, boardId, "Shared Board", "SHR", BoardShareMode.FULL);
        proxyService.deleteBookmarkByBoard(memberId, partnerId, boardId);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertTrue(bookmarks.isEmpty());
    }

    // -- Webhook handlers --

    @Test
    @Order(70)
    void onBoardRenamed() {
        var bookmark =
                proxyService.createBookmark(memberId, partnerId, boardId, "Old Name", "OLD", BoardShareMode.FULL);
        proxyService.onBoardRenamed(partnerId, boardId, "New Name", "NEW");
        var bookmarks = proxyService.findBookmarks(memberId);
        assertEquals("New Name", bookmarks.getFirst().remoteBoardName());
        assertEquals("NEW", bookmarks.getFirst().remoteBoardShortKey());
    }

    @Test
    @Order(71)
    void onShareModeChanged() {
        proxyService.onShareModeChanged(partnerId, boardId, BoardShareMode.READ_ONLY);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertEquals(BoardShareMode.READ_ONLY, bookmarks.getFirst().shareMode());
    }

    @Test
    @Order(72)
    void onBoardUnshared() {
        proxyService.onBoardUnshared(partnerId, boardId);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertTrue(bookmarks.isEmpty());
    }

    // -- Edit override with group/tag --

    @Test
    @Order(73)
    void passesLocalEditOverrideWithMatchingGroup() {
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(55), List.of()));
        when(memberService.findRoles(memberId)).thenReturn(List.of());
        when(groupService.findGroupsForMember(memberId))
                .thenReturn(List.of(new MemberGroup(55, station1.id(), "EditGroup")));
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardId, memberId));
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    @Test
    @Order(74)
    void passesLocalEditOverrideWithMatchingTag() {
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of(77)));
        when(memberService.findRoles(memberId)).thenReturn(List.of());
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of(new UserTag(77, station1.id(), "EditTag")));
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardId, memberId));
        proxyService.setLocalEditOverride(partnerId, boardId, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- HTTP discovery --

    @Test
    @Order(75)
    void discoverBoardsViaHttpPartner() {
        var partner = new FederationPartner(
                partnerId,
                station1.id(),
                station2.id(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now(),
                "https://remote.example.com");
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));
        when(federationService.hasCapability(partnerId, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .thenReturn(true);

        when(httpClient.signedGetList(
                        eq("https://remote.example.com"), eq("/federation/remote/boards"), eq(station1.id()), any()))
                .thenReturn(List.of(java.util.Map.of(
                        "id",
                        100,
                        "name",
                        "Remote Board",
                        "shortKey",
                        "RMT",
                        "description",
                        "Remote desc",
                        "shareMode",
                        "FULL")));

        var discovered = proxyService.discoverBoards(station1.id());
        assertFalse(discovered.isEmpty());
        assertEquals("Remote Board", discovered.getFirst().name());
        assertEquals(BoardShareMode.FULL, discovered.getFirst().shareMode());
    }

    @Test
    @Order(76)
    void discoverBoardsViaHttpError() {
        var partner = new FederationPartner(
                partnerId,
                station1.id(),
                station2.id(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                1,
                Instant.now(),
                Instant.now(),
                "https://remote.example.com");
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));
        when(federationService.hasCapability(partnerId, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .thenReturn(true);
        when(httpClient.signedGetList(any(), any(), anyInt(), any()))
                .thenThrow(new RuntimeException("Connection failed"));

        var discovered = proxyService.discoverBoards(station1.id());
        assertTrue(discovered.isEmpty());
    }

    // -- DiscoveredBoard record --

    @Test
    @Order(80)
    void discoveredBoardRecord() {
        var db = new FederatedBoardProxyService.DiscoveredBoard(
                1, 2, "Test Board", "TST", "A description", BoardShareMode.FULL, "Partner Station");
        assertEquals(1, db.partnerId());
        assertEquals(2, db.remoteBoardId());
        assertEquals("Test Board", db.name());
        assertEquals("TST", db.shortKey());
        assertEquals("A description", db.description());
        assertEquals(BoardShareMode.FULL, db.shareMode());
        assertEquals("Partner Station", db.partnerStationName());
    }
}
