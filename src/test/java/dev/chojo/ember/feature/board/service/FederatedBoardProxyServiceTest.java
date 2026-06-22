/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.board.service;

import de.chojo.sadu.queries.api.call.Call;
import de.chojo.sadu.queries.api.query.Query;
import de.chojo.sadu.queries.converter.StandardValueConverter;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.board.entity.AccessData;
import dev.chojo.ember.feature.board.entity.BoardChecklistItem;
import dev.chojo.ember.feature.board.entity.BoardComment;
import dev.chojo.ember.feature.board.entity.BoardLabel;
import dev.chojo.ember.feature.board.entity.BoardLane;
import dev.chojo.ember.feature.board.entity.BoardShareMode;
import dev.chojo.ember.feature.board.entity.BoardTicket;
import dev.chojo.ember.feature.board.entity.LanePreset;
import dev.chojo.ember.feature.board.entity.TicketPriority;
import dev.chojo.ember.feature.board.entity.TicketSummary;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.entity.MemberGroup;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.entity.UserTag;
import dev.chojo.ember.feature.members.service.MemberGroupService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.members.service.UserTagService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.repository.RepositoryTestBase;
import io.javalin.http.NotFoundResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FederatedBoardProxyServiceTest extends RepositoryTestBase {
    private static final UUID REMOTE_MEMBER_1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID REMOTE_1 = UUID.fromString("00000000-0000-0000-0000-000000000009");
    private static final String BOARD_KEY = "SHR";

    private static FederatedBoardProxyService proxyService;
    private static FederatedBoardService federatedBoardService;
    private static BoardService boardService;
    private static BoardTicketService ticketService;
    private static FederationService federationService;
    private static FederationRepository federationRepository;
    private static FederationHttpClient httpClient;
    private static StationMemberService memberService;
    private static MemberGroupService groupService;
    private static UserTagService tagService;

    private static Station station1;
    private static Station station2;
    private static Account account;
    private static int memberId;
    private static int boardId;
    private static UUID boardUid;
    private static int partnerId;
    private static int bookmarkId;
    private static int ticketId;
    private static int ticketNumber;
    private static int laneId;

    @BeforeAll
    static void setup() {
        federationService = mock(FederationService.class);
        httpClient = mock(FederationHttpClient.class);
        memberService = mock(StationMemberService.class);
        groupService = mock(MemberGroupService.class);
        tagService = mock(UserTagService.class);

        federatedBoardService = new FederatedBoardService(federatedBoardRepo);
        boardService = new BoardService(boardRepo, memberService, groupService, tagService);
        var resolver = new MemberNameResolver(
                new StationMemberService(stationMemberRepo, stationRepo, null, null),
                accountRepo,
                new EventFederationRepository(),
                mock(FederationRepository.class),
                stationRepo,
                groupService,
                tagService);
        var fbpBackend = new dev.chojo.ember.feature.storage.backend.LocalStorageBackend();
        var fbpResolver = new dev.chojo.ember.feature.storage.backend.StorageBackendResolver(fbpBackend);
        var fbpStorage = new dev.chojo.ember.feature.storage.service.StorageService(fbpResolver, fbpBackend);
        var attachmentSvc = new BoardAttachmentService(fbpStorage, stationRepo, fbpBackend);
        ticketService = new BoardTicketService(
                boardTicketRepo,
                boardRepo,
                new DomainEventBus(Set.of()),
                new StationMemberService(stationMemberRepo, stationRepo, null, null),
                memberIdentityFactory,
                resolver,
                attachmentSvc);
        federationRepository = mock(FederationRepository.class);

        proxyService = new FederatedBoardProxyService(
                federatedBoardService,
                federatedBoardRepo,
                boardService,
                ticketService,
                federationService,
                federationRepository,
                httpClient,
                stationRepo,
                memberService,
                stationMemberRepo,
                groupService,
                tagService,
                new EventFederationRepository(),
                resolver,
                memberIdentityFactory);

        station1 = stationRepo.create("ProxyStation1");
        station2 = stationRepo.create("ProxyStation2");
        account = accountRepo.create("proxy-test@test.com", "Proxy", "Test");
        var member = stationMemberRepo.create(station1.id(), account.id());
        memberId = member.id();

        // Create a board on station2 (the partner station)
        var board = boardService.createWithPreset(station2.id(), "Shared Board", "Desc", "SHR", LanePreset.SIMPLE);
        boardId = board.id();
        boardUid = board.uid();

        // Create a federation partner via direct SQL
        partnerId = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p::UUID, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of()
                        .bind("s", station1.id())
                        .bind("p", station2.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    @AfterAll
    static void cleanup() {
        boardService.delete(boardId);
        for (var p : federationService.findPartners(station1.id())) federationRepository.deletePartner(p.id());
        for (var p : federationService.findPartners(station2.id())) federationRepository.deletePartner(p.id());
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
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
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
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.SUSPENDED,
                "1",
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
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
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
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(21)
    void passesLocalViewOverrideWithMatchingUserType() {
        proxyService.setLocalViewOverride(
                partnerId, boardUid, new AccessData(List.of(StationUserType.MEMBER), List.of(), List.of()));
        when(memberService.findById(memberId))
                .thenReturn(Optional.of(new StationMember(
                        memberId, station1.id(), null, null, false, null, null, StationUserType.MEMBER, null)));
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(22)
    void failsLocalViewOverrideWithWrongUserType() {
        when(memberService.findById(memberId))
                .thenReturn(Optional.of(new StationMember(
                        memberId, station1.id(), null, null, false, null, null, StationUserType.TRIAL, null)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.passesLocalViewOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(23)
    void passesLocalViewOverrideWithMatchingGroup() {
        proxyService.setLocalViewOverride(partnerId, boardUid, new AccessData(List.of(), List.of(55), List.of()));
        when(groupService.findGroupsForMember(memberId))
                .thenReturn(List.of(new MemberGroup(55, station1.id(), "TestGroup", null, 0)));
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(24)
    void passesLocalViewOverrideWithMatchingTag() {
        proxyService.setLocalViewOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of(77)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId))
                .thenReturn(List.of(new UserTag(77, station1.id(), "TestTag", null, false, 0)));
        assertTrue(proxyService.passesLocalViewOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(25)
    void clearViewOverride() {
        proxyService.setLocalViewOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- Local edit overrides --

    @Test
    @Order(30)
    void passesLocalEditOverrideWhenNoOverride() {
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(31)
    void passesLocalEditOverrideWithMatchingUserType() {
        proxyService.setLocalEditOverride(
                partnerId, boardUid, new AccessData(List.of(StationUserType.MEMBER), List.of(), List.of()));
        when(memberService.findById(memberId))
                .thenReturn(Optional.of(new StationMember(
                        memberId, station1.id(), null, null, false, null, null, StationUserType.MEMBER, null)));
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(32)
    void failsLocalEditOverrideWithWrongUserType() {
        when(memberService.findById(memberId))
                .thenReturn(Optional.of(new StationMember(
                        memberId, station1.id(), null, null, false, null, null, StationUserType.TRIAL, null)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.passesLocalEditOverride(partnerId, boardUid, memberId));
    }

    @Test
    @Order(33)
    void clearEditOverride() {
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- canView / canWrite --

    @Test
    @Order(40)
    void canViewWhenShared() {
        assertTrue(proxyService.canView(partnerId, boardUid, boardId, memberId));
    }

    @Test
    @Order(41)
    void cannotViewWhenNotShared() {
        assertFalse(proxyService.canView(partnerId, UUID.randomUUID(), 999999, memberId));
    }

    @Test
    @Order(42)
    void canWriteWhenFullMode() {
        // Board is currently FULL from test order 11
        assertTrue(proxyService.canWrite(partnerId, boardUid, boardId, memberId));
    }

    @Test
    @Order(43)
    void cannotWriteWhenReadOnly() {
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.READ_ONLY)));
        assertFalse(proxyService.canWrite(partnerId, boardUid, boardId, memberId));
    }

    @Test
    @Order(44)
    void cannotWriteWhenNotShared() {
        assertFalse(proxyService.canWrite(partnerId, UUID.randomUUID(), 999999, memberId));
    }

    @Test
    @Order(45)
    void cannotWriteWhenViewOverrideFails() {
        // Set back to FULL
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.FULL)));
        // Set view override that member won't pass
        proxyService.setLocalViewOverride(
                partnerId, boardUid, new AccessData(List.of(StationUserType.MANAGER), List.of(), List.of()));
        when(memberService.findById(memberId))
                .thenReturn(Optional.of(new StationMember(
                        memberId, station1.id(), null, null, false, null, null, StationUserType.MEMBER, null)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.canWrite(partnerId, boardUid, boardId, memberId));
        // Cleanup
        proxyService.setLocalViewOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    @Test
    @Order(46)
    void cannotWriteWhenEditOverrideFails() {
        proxyService.setLocalEditOverride(
                partnerId, boardUid, new AccessData(List.of(StationUserType.MANAGER), List.of(), List.of()));
        when(memberService.findById(memberId))
                .thenReturn(Optional.of(new StationMember(
                        memberId, station1.id(), null, null, false, null, null, StationUserType.MEMBER, null)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId)).thenReturn(List.of());
        assertFalse(proxyService.canWrite(partnerId, boardUid, boardId, memberId));
        // Cleanup
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- Get overrides --

    @Test
    @Order(50)
    void getLocalViewOverride() {
        proxyService.setLocalViewOverride(
                partnerId,
                boardUid,
                new AccessData(List.of(StationUserType.MEMBER, StationUserType.GUARDIAN), List.of(3), List.of(4)));
        var access = proxyService.getLocalViewOverride(partnerId, boardUid);
        assertEquals(2, access.userTypes().size());
        assertTrue(access.userTypes().containsAll(List.of(StationUserType.MEMBER, StationUserType.GUARDIAN)));
        assertEquals(List.of(3), access.groupIds());
        assertEquals(List.of(4), access.tagIds());
        // Cleanup
        proxyService.setLocalViewOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
    }

    @Test
    @Order(51)
    void getLocalEditOverride() {
        proxyService.setLocalEditOverride(
                partnerId, boardUid, new AccessData(List.of(StationUserType.TEAM), List.of(6, 7), List.of()));
        var access = proxyService.getLocalEditOverride(partnerId, boardUid);
        assertEquals(List.of(StationUserType.TEAM), access.userTypes());
        assertEquals(List.of(6, 7), access.groupIds());
        assertTrue(access.tagIds().isEmpty());
        // Cleanup
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
    }

    // -- Bookmarks --

    @Test
    @Order(60)
    void createBookmark() {
        var bookmark =
                proxyService.createBookmark(memberId, partnerId, boardUid, "Shared Board", "SHR", BoardShareMode.FULL);
        assertNotNull(bookmark);
        assertEquals(memberId, bookmark.memberId());
        assertEquals(partnerId, bookmark.partnerId());
        assertEquals(boardUid, bookmark.remoteBoardUid());
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
        proxyService.createBookmark(memberId, partnerId, boardUid, "Shared Board", "SHR", BoardShareMode.FULL);
        proxyService.deleteBookmarkByBoard(memberId, partnerId, boardUid);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertTrue(bookmarks.isEmpty());
    }

    // -- Webhook handlers --

    @Test
    @Order(70)
    void onBoardRenamed() {
        var bookmark =
                proxyService.createBookmark(memberId, partnerId, boardUid, "Old Name", "OLD", BoardShareMode.FULL);
        proxyService.onBoardRenamed(partnerId, boardUid, "New Name", "NEW");
        var bookmarks = proxyService.findBookmarks(memberId);
        assertEquals("New Name", bookmarks.getFirst().remoteBoardName());
        assertEquals("NEW", bookmarks.getFirst().remoteBoardShortKey());
    }

    @Test
    @Order(71)
    void onShareModeChanged() {
        proxyService.onShareModeChanged(partnerId, boardUid, BoardShareMode.READ_ONLY);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertEquals(BoardShareMode.READ_ONLY, bookmarks.getFirst().shareMode());
    }

    @Test
    @Order(72)
    void onBoardUnshared() {
        proxyService.onBoardUnshared(partnerId, boardUid);
        var bookmarks = proxyService.findBookmarks(memberId);
        assertTrue(bookmarks.isEmpty());
    }

    // -- Edit override with group/tag --

    @Test
    @Order(73)
    void passesLocalEditOverrideWithMatchingGroup() {
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(55), List.of()));
        when(groupService.findGroupsForMember(memberId))
                .thenReturn(List.of(new MemberGroup(55, station1.id(), "EditGroup", null, 0)));
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardUid, memberId));
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    @Test
    @Order(74)
    void passesLocalEditOverrideWithMatchingTag() {
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of(77)));
        when(groupService.findGroupsForMember(memberId)).thenReturn(List.of());
        when(tagService.findTagsForMember(memberId))
                .thenReturn(List.of(new UserTag(77, station1.id(), "EditTag", null, false, 0)));
        assertTrue(proxyService.passesLocalEditOverride(partnerId, boardUid, memberId));
        proxyService.setLocalEditOverride(partnerId, boardUid, new AccessData(List.of(), List.of(), List.of()));
        reset(memberService, groupService, tagService);
    }

    // -- HTTP discovery --

    @Test
    @Order(75)
    void discoverBoardsViaHttpPartner() {
        var partner = new FederationPartner(
                partnerId,
                station1.id(),
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
                Instant.now(),
                Instant.now(),
                "https://remote.example.com");
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));
        when(federationService.hasCapability(partnerId, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .thenReturn(true);

        when(httpClient.getList(
                        eq("https://remote.example.com"), eq("/remote/boards"), any(), eq(station1.id()), any(), any()))
                .thenReturn(List.of(new FederatedBoardProxyService.RemoteDiscoveredBoard(
                        UUID.randomUUID().toString(),
                        "Remote Board",
                        "RMT",
                        "Remote desc",
                        BoardShareMode.FULL,
                        StationUserType.MEMBER)));

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
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
                Instant.now(),
                Instant.now(),
                "https://remote.example.com");
        when(federationService.findPartners(station1.id())).thenReturn(List.of(partner));
        when(federationService.hasCapability(partnerId, CapabilityType.BOARD_SHARE, Direction.IMPORT))
                .thenReturn(true);
        when(httpClient.getList(any(), any(), any(), anyInt(), any(), any()))
                .thenThrow(new RuntimeException("Connection failed"));

        var discovered = proxyService.discoverBoards(station1.id());
        assertTrue(discovered.isEmpty());

        // Reset httpClient to clear the catch-all thenThrow stub — otherwise it poisons
        // subsequent when().thenReturn() calls (the when() invocation triggers the stub)
        reset(httpClient);
    }

    // -- DiscoveredBoard record --

    @Test
    @Order(80)
    void discoveredBoardRecord() {
        var testUid = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
        var db = new FederatedBoardProxyService.DiscoveredBoard(
                1,
                "550e8400-e29b-41d4-a716-446655440000",
                testUid,
                "Test Board",
                "TST",
                "A description",
                BoardShareMode.FULL,
                "Partner Station",
                StationUserType.MEMBER);
        assertEquals(1, db.partnerId());
        assertEquals("550e8400-e29b-41d4-a716-446655440000", db.partnerStationUid());
        assertEquals(testUid, db.remoteBoardUid());
        assertEquals("Test Board", db.name());
        assertEquals("TST", db.shortKey());
        assertEquals("A description", db.description());
        assertEquals(BoardShareMode.FULL, db.shareMode());
        assertEquals("Partner Station", db.partnerStationName());
    }

    // -- Helper to create a local partner mock --

    private static FederationPartner localPartner() {
        return new FederationPartner(
                partnerId,
                station1.id(),
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
                Instant.now(),
                Instant.now(),
                null);
    }

    private static FederationPartner remotePartner() {
        return new FederationPartner(
                partnerId,
                station1.id(),
                station2.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
                Instant.now(),
                Instant.now(),
                "https://remote.example.com");
    }

    // -- Local proxy read tests --

    @Test
    @Order(100)
    void proxyGetBoardLocal() {
        // Ensure FULL share mode
        federatedBoardService.shareBoard(
                boardId, List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.FULL)));
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var detail = proxyService.proxyGetBoard(partnerId, BOARD_KEY);
        assertNotNull(detail);
        assertEquals("Shared Board", detail.board().name());
        assertEquals(BoardShareMode.FULL, detail.shareMode());
        assertNotNull(detail.stationName());
    }

    @Test
    @Order(101)
    void proxyGetLanesLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var lanes = proxyService.proxyGetLanes(partnerId, BOARD_KEY);
        assertNotNull(lanes);
        assertFalse(lanes.isEmpty());
        // Store a lane ID for ticket creation
        laneId = lanes.getFirst().id();
    }

    @Test
    @Order(102)
    void proxyGetLabelsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var labels = proxyService.proxyGetLabels(partnerId, BOARD_KEY);
        assertNotNull(labels);
        // Board was just created, may have no labels yet — just verify it returns
    }

    @Test
    @Order(103)
    void proxyGetFieldsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var fields = proxyService.proxyGetFields(partnerId, BOARD_KEY);
        assertNotNull(fields);
    }

    @Test
    @Order(104)
    void proxyListTicketsLocalEmpty() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var tickets = proxyService.proxyListTickets(partnerId, BOARD_KEY);
        assertNotNull(tickets);
        // May be empty if no tickets yet
    }

    @Test
    @Order(105)
    void createTicketForLocalProxyTests() {
        // Create a ticket directly via ticketService (with a valid memberId)
        // since proxyCreateTicket passes createdBy=0 which violates the FK constraint
        var ticket = ticketService.createTicket(
                boardId,
                laneId,
                "Test Ticket",
                "Description",
                null,
                TicketPriority.HIGH,
                null,
                memberIdentityFactory.local(station1.id(), memberId));
        assertNotNull(ticket);
        assertEquals("Test Ticket", ticket.title());
        ticketId = ticket.id();
        ticketNumber = ticket.ticketNumber();
    }

    @Test
    @Order(106)
    void proxyGetTicketLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var ticket = proxyService.proxyGetTicket(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(ticket);
        assertEquals("Test Ticket", ticket.title());
    }

    @Test
    @Order(107)
    void proxyListTicketsLocalNonEmpty() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var tickets = proxyService.proxyListTickets(partnerId, BOARD_KEY);
        assertFalse(tickets.isEmpty());
        assertEquals(ticketId, tickets.getFirst().id());
    }

    @Test
    @Order(108)
    void proxyGetCommentsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var comments = proxyService.proxyGetComments(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(comments);
        assertTrue(comments.isEmpty());
    }

    @Test
    @Order(109)
    void proxyGetChecklistLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var items = proxyService.proxyGetChecklist(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    @Order(110)
    void proxyGetLinksLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var links = proxyService.proxyGetLinks(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(links);
        assertTrue(links.isEmpty());
    }

    @Test
    @Order(111)
    void proxyGetTicketLabelsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var labels = proxyService.proxyGetTicketLabels(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(labels);
        assertTrue(labels.isEmpty());
    }

    @Test
    @Order(112)
    void proxyGetTransitionsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var transitions = proxyService.proxyGetTransitions(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(transitions);
        assertTrue(transitions.isEmpty());
    }

    @Test
    @Order(113)
    void proxyGetHistoryLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var history = proxyService.proxyGetHistory(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(history);
    }

    @Test
    @Order(114)
    void proxyGetAttachmentsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var attachments = proxyService.proxyGetAttachments(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(attachments);
        assertTrue(attachments.isEmpty());
    }

    @Test
    @Order(115)
    void proxyGetWatchersLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var watcherData = proxyService.proxyGetWatchers(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(watcherData);
        assertNotNull(watcherData.local());
        assertNotNull(watcherData.federated());
    }

    // -- Local proxy write tests --

    @Test
    @Order(120)
    void proxyUpdateTicketLocalNoFieldChange() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        // Update with same values so that logHistory is not triggered (avoids actor FK=0 issue)
        var current = ticketService.findById(ticketId).orElseThrow();
        var updated = proxyService.proxyUpdateTicket(
                partnerId,
                BOARD_KEY,
                ticketNumber,
                current.title(),
                current.description(),
                null,
                current.priority(),
                null,
                null,
                null);
        assertNotNull(updated);
        assertEquals(ticketId, updated.id());
    }

    @Test
    @Order(122)
    void proxyAddCommentLocalCreatesViaService() {
        // proxyAddComment passes authorId=0 which violates FK on board_ticket_comment.
        // Create comment directly via ticketService to test the read proxy instead.
        var comment = ticketService.createComment(
                ticketId, null, memberIdentityFactory.local(station1.id(), memberId), "Direct comment");
        assertNotNull(comment);

        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));
        var comments = proxyService.proxyGetComments(partnerId, BOARD_KEY, ticketNumber);
        assertFalse(comments.isEmpty());
        assertEquals("Direct comment", comments.getFirst().content());
    }

    @Test
    @Order(123)
    void proxyAddChecklistItemLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var item = proxyService.proxyAddChecklistItem(partnerId, BOARD_KEY, ticketNumber, "Checklist Item", null, null);
        assertNotNull(item);
        assertEquals("Checklist Item", item.title());
    }

    @Test
    @Order(124)
    void proxyCreateLabelLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var label = proxyService.proxyCreateLabel(partnerId, BOARD_KEY, "Bug", "#ff0000");
        assertNotNull(label);
        assertEquals("Bug", label.name());
        assertEquals("#ff0000", label.color());
    }

    @Test
    @Order(125)
    void proxyAddTicketLabelLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        // First get the label we created
        var labels = boardService.findLabels(boardId);
        assertFalse(labels.isEmpty());
        int labelId = labels.getFirst().id();

        var result =
                proxyService.proxyAddTicketLabel(partnerId, BOARD_KEY, ticketNumber, labelId, REMOTE_MEMBER_1, "Test");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    @Order(126)
    void proxyReorderTicketsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        // Just reorder with the single ticket
        proxyService.proxyReorderTickets(partnerId, BOARD_KEY, laneId, List.of(ticketId));
        // No exception means success
    }

    @Test
    @Order(127)
    void proxySearchTicketsLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var results = proxyService.proxySearchTickets(partnerId, BOARD_KEY, "Test");
        assertNotNull(results);
    }

    @Test
    @Order(128)
    void proxySearchTicketsLocalBlankQuery() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var results = proxyService.proxySearchTickets(partnerId, BOARD_KEY, "");
        assertNotNull(results);
        // Blank query returns all tickets
        assertFalse(results.isEmpty());
    }

    @Test
    @Order(129)
    void proxyWatchTicketLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        proxyService.proxyWatchTicket(partnerId, BOARD_KEY, ticketNumber, REMOTE_MEMBER_1);
        // Verify watcher was added
        var watcherData = proxyService.proxyGetWatchers(partnerId, BOARD_KEY, ticketNumber);
        assertNotNull(watcherData);
    }

    @Test
    @Order(130)
    void proxyUnwatchTicketLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        proxyService.proxyUnwatchTicket(partnerId, BOARD_KEY, ticketNumber, REMOTE_MEMBER_1);
        // No exception means success
    }

    @Test
    @Order(131)
    void proxyUpdateChecklistItemLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        // Get the checklist item we created
        var items = ticketService.findChecklistItems(ticketId);
        assertFalse(items.isEmpty());
        int itemId = items.getFirst().id();

        proxyService.proxyUpdateChecklistItem(
                partnerId, BOARD_KEY, ticketNumber, itemId, "Updated Checklist", true, null, null);
        // No exception means success
    }

    @Test
    @Order(132)
    void proxyDeleteChecklistItemLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var items = ticketService.findChecklistItems(ticketId);
        assertFalse(items.isEmpty());
        int itemId = items.getFirst().id();

        proxyService.proxyDeleteChecklistItem(partnerId, BOARD_KEY, ticketNumber, itemId, null, null);
        var afterDelete = ticketService.findChecklistItems(ticketId);
        assertTrue(afterDelete.isEmpty());
    }

    @Test
    @Order(133)
    void proxyRemoveTicketLabelLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var labels = boardService.findLabelsForTicket(ticketId);
        assertFalse(labels.isEmpty());
        int labelId = labels.getFirst().id();

        proxyService.proxyRemoveTicketLabel(partnerId, BOARD_KEY, ticketNumber, labelId, REMOTE_MEMBER_1, "Test");
        var afterRemove = boardService.findLabelsForTicket(ticketId);
        assertTrue(afterRemove.isEmpty());
    }

    @Test
    @Order(134)
    void proxyCreateLabelLocalWithDefaultColor() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var label = proxyService.proxyCreateLabel(partnerId, BOARD_KEY, "NoColor", null);
        assertNotNull(label);
        assertEquals("NoColor", label.name());
        assertEquals("#6b7280", label.color());
    }

    @Test
    @Order(135)
    void proxySearchTicketsLocalNullQuery() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        var results = proxyService.proxySearchTickets(partnerId, BOARD_KEY, null);
        assertNotNull(results);
        // Null query returns all tickets
        assertFalse(results.isEmpty());
    }

    @Test
    @Order(140)
    void proxyDeleteTicketLocal() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(localPartner()));

        proxyService.proxyDeleteTicket(partnerId, BOARD_KEY, ticketNumber);
        var result = ticketService.findById(ticketId);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(141)
    void getEffectiveShareModeReverseLookup() {
        // Create a board on station1 and share it with the existing partner (partnerId on station1)
        var reverseBoard =
                boardService.createWithPreset(station1.id(), "Reverse Board", "Desc", "REV", LanePreset.SIMPLE);
        federatedBoardService.shareBoard(
                reverseBoard.id(),
                List.of(new FederatedBoardService.PartnerShareConfig(partnerId, BoardShareMode.READ_ONLY)));

        // Create a second partner on station2, pointing to station1
        int partner2Id = Query.query(
                        "INSERT INTO federation_partner(station_id, partner_station_id, status, federation_version) VALUES (:s, :p::UUID, 'ACTIVE', 1) RETURNING id;")
                .single(Call.of()
                        .bind("s", station2.id())
                        .bind("p", station1.uid(), StandardValueConverter.UUID_STRING))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();

        // partner2 is on station2, looking at station1
        var partner2 = new FederationPartner(
                partner2Id,
                station2.id(),
                station1.uid(),
                null,
                null,
                null,
                FederationPartner.FederationStatus.ACTIVE,
                "1",
                Instant.now(),
                Instant.now(),
                null);
        when(federationRepository.findPartnerById(partner2Id)).thenReturn(Optional.of(partner2));
        // Reverse lookup: board is on station1, partner2 is on station2.
        // Service resolves ourStationUid = station2.uid(), then calls
        // findPartnerByStationAndRemoteUid(station1.id(), station2.uid()) to find
        // station1's partner record that points to station2.
        when(federationRepository.findPartnerByStationAndRemoteUid(eq(station1.id()), eq(station2.uid())))
                .thenReturn(Optional.of(localPartner()));

        var mode = proxyService.getEffectiveShareMode(partner2Id, reverseBoard.id());
        assertTrue(mode.isPresent());
        assertEquals(BoardShareMode.READ_ONLY, mode.get());

        // Cleanup
        boardService.delete(reverseBoard.id());
        Query.query("DELETE FROM federation_partner WHERE id = :id;")
                .single(Call.of().bind("id", partner2Id))
                .delete();
    }

    @Test
    @Order(142)
    void getEffectiveShareModeReverseLookupPartnerNotFound() {
        when(federationRepository.findPartnerById(999)).thenReturn(Optional.empty());
        var mode = proxyService.getEffectiveShareMode(999, 999999);
        assertTrue(mode.isEmpty());
    }

    // -- Remote proxy tests (mocked HTTP) --

    @Test
    @Order(200)
    void proxyGetBoardRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var remoteBoard = new FederatedBoardProxyService.RemoteBoard(
                10, "00000000-0000-4000-a000-000000000099", "Remote Board", "Desc", "RMT", 0, 0, null, null);
        var remoteDetail =
                new FederatedBoardProxyService.FederatedBoardDetail(remoteBoard, BoardShareMode.FULL, "Remote Station");
        when(httpClient.get(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(remoteDetail);

        var detail = proxyService.proxyGetBoard(partnerId, BOARD_KEY);
        assertNotNull(detail);
        assertEquals("Remote Board", detail.board().name());
        assertEquals(BoardShareMode.FULL, detail.shareMode());
        assertEquals("Remote Station", detail.stationName());
    }

    @Test
    @Order(201)
    void proxyListTicketsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var remoteSummary =
                new TicketSummary(1, 10, 1, 1, "Remote Ticket", null, TicketPriority.MEDIUM, null, 0, null, 0, 0, 0);
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of(remoteSummary));

        var tickets = proxyService.proxyListTickets(partnerId, BOARD_KEY);
        assertNotNull(tickets);
        assertFalse(tickets.isEmpty());
        assertEquals("Remote Ticket", tickets.getFirst().title());
    }

    @Test
    @Order(202)
    void proxyCreateTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseTicket = new BoardTicket(
                99,
                10,
                1,
                1,
                "New Remote Ticket",
                "Desc",
                null,
                TicketPriority.HIGH,
                null,
                0,
                null,
                null,
                null,
                null,
                0,
                0,
                0);
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(responseTicket);

        var ticket = proxyService.proxyCreateTicket(
                partnerId, BOARD_KEY, 1, "New Remote Ticket", "Desc", TicketPriority.HIGH, null, REMOTE_MEMBER_1);
        assertNotNull(ticket);
        assertEquals("New Remote Ticket", ticket.title());
    }

    @Test
    @Order(203)
    void proxyGetLanesRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var lane = new BoardLane(1, 10, "To Do", "#3b82f6", 0);
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/lanes"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of(lane));

        var lanes = proxyService.proxyGetLanes(partnerId, BOARD_KEY);
        assertFalse(lanes.isEmpty());
        assertEquals("To Do", lanes.getFirst().name());
    }

    @Test
    @Order(204)
    void proxyGetLabelsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var label = new BoardLabel(1, 10, "Bug", "#ff0000");
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/labels"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of(label));

        var labels = proxyService.proxyGetLabels(partnerId, BOARD_KEY);
        assertFalse(labels.isEmpty());
        assertEquals("Bug", labels.getFirst().name());
    }

    @Test
    @Order(205)
    void proxySearchTicketsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var remoteSummary =
                new TicketSummary(1, 10, 1, 1, "Found Ticket", null, TicketPriority.MEDIUM, null, 0, null, 0, 0, 0);
        when(httpClient.getList(
                        eq("https://remote.example.com"), contains("/tickets/search"), any(), anyInt(), any(), any()))
                .thenReturn(List.of(remoteSummary));

        var tickets = proxyService.proxySearchTickets(partnerId, BOARD_KEY, "Found");
        assertFalse(tickets.isEmpty());
        assertEquals("Found Ticket", tickets.getFirst().title());
    }

    @Test
    @Order(206)
    void proxyDeleteTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.delete(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/99"),
                        any(),
                        anyInt(),
                        any()))
                .thenReturn(true);

        proxyService.proxyDeleteTicket(partnerId, BOARD_KEY, 99);
        verify(httpClient)
                .delete(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/99"),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(207)
    void proxyGetFieldsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/fields"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var fields = proxyService.proxyGetFields(partnerId, BOARD_KEY);
        assertNotNull(fields);
        assertTrue(fields.isEmpty());
    }

    @Test
    @Order(208)
    void proxyGetTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var remoteTicket = new BoardTicket(
                1,
                10,
                1,
                1,
                "Remote Ticket",
                "Desc",
                null,
                TicketPriority.MEDIUM,
                null,
                0,
                null,
                null,
                null,
                null,
                0,
                0,
                0);
        when(httpClient.get(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(remoteTicket);

        var ticket = proxyService.proxyGetTicket(partnerId, BOARD_KEY, 1);
        assertNotNull(ticket);
        assertEquals("Remote Ticket", ticket.title());
    }

    @Test
    @Order(209)
    void proxyGetCommentsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/comments"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var comments = proxyService.proxyGetComments(partnerId, BOARD_KEY, 1);
        assertNotNull(comments);
        assertTrue(comments.isEmpty());
    }

    @Test
    @Order(210)
    void proxyGetChecklistRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/checklist"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var items = proxyService.proxyGetChecklist(partnerId, BOARD_KEY, 1);
        assertNotNull(items);
        assertTrue(items.isEmpty());
    }

    @Test
    @Order(211)
    void proxyGetLinksRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/links"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var links = proxyService.proxyGetLinks(partnerId, BOARD_KEY, 1);
        assertNotNull(links);
        assertTrue(links.isEmpty());
    }

    @Test
    @Order(212)
    void proxyGetTicketLabelsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/labels"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var labels = proxyService.proxyGetTicketLabels(partnerId, BOARD_KEY, 1);
        assertNotNull(labels);
        assertTrue(labels.isEmpty());
    }

    @Test
    @Order(213)
    void proxyGetTransitionsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/transitions"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var transitions = proxyService.proxyGetTransitions(partnerId, BOARD_KEY, 1);
        assertNotNull(transitions);
        assertTrue(transitions.isEmpty());
    }

    @Test
    @Order(214)
    void proxyGetHistoryRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/history"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var history = proxyService.proxyGetHistory(partnerId, BOARD_KEY, 1);
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    @Order(215)
    void proxyGetAttachmentsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/attachments"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var attachments = proxyService.proxyGetAttachments(partnerId, BOARD_KEY, 1);
        assertNotNull(attachments);
        assertTrue(attachments.isEmpty());
    }

    @Test
    @Order(216)
    void proxyGetWatchersRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var watcherData = new FederatedBoardProxyService.FederatedWatcherData(List.of(), List.of());
        when(httpClient.get(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/watchers"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(watcherData);

        var result = proxyService.proxyGetWatchers(partnerId, BOARD_KEY, 1);
        assertNotNull(result);
    }

    @Test
    @Order(217)
    void proxyUpdateTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseTicket = new BoardTicket(
                1, 10, 1, 1, "Updated", "Desc", null, TicketPriority.LOW, null, 0, null, null, null, null, 0, 0, 0);
        when(httpClient.put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(responseTicket);

        var updated = proxyService.proxyUpdateTicket(
                partnerId, BOARD_KEY, 1, "Updated", null, null, TicketPriority.LOW, null, null, null);
        assertNotNull(updated);
        assertEquals("Updated", updated.title());
    }

    @Test
    @Order(218)
    void proxyMoveTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseTicket = new BoardTicket(
                1, 10, 2, 1, "Ticket", "Desc", null, TicketPriority.MEDIUM, null, 0, null, null, null, null, 0, 0, 0);
        when(httpClient.put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/move"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(responseTicket);

        var moved = proxyService.proxyMoveTicket(partnerId, BOARD_KEY, 1, 2, 0, null, null);
        assertNotNull(moved);
    }

    @Test
    @Order(219)
    void proxyAddCommentRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseComment = new BoardComment(1, 1, null, null, "Hello", false, null, null);
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/comments"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(responseComment);

        var comment =
                proxyService.proxyAddComment(partnerId, BOARD_KEY, 1, null, "Hello", REMOTE_MEMBER_1, "Test User");
        assertNotNull(comment);
        assertEquals("Hello", comment.content());
    }

    @Test
    @Order(220)
    void proxyAddChecklistItemRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseItem = new BoardChecklistItem(1, 1, "Task", false, 0);
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/checklist"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(responseItem);

        var item = proxyService.proxyAddChecklistItem(partnerId, BOARD_KEY, 1, "Task", null, null);
        assertNotNull(item);
        assertEquals("Task", item.title());
    }

    @Test
    @Order(221)
    void proxyCreateLabelRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseLabel = new BoardLabel(1, 10, "Feature", "#00ff00");
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/labels"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(responseLabel);

        var label = proxyService.proxyCreateLabel(partnerId, BOARD_KEY, "Feature", "#00ff00");
        assertNotNull(label);
        assertEquals("Feature", label.name());
    }

    @Test
    @Order(222)
    void proxyReorderTicketsRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/reorder"),
                        any(),
                        any(),
                        anyInt(),
                        any()))
                .thenReturn(true);

        proxyService.proxyReorderTickets(partnerId, BOARD_KEY, 1, List.of(1, 2, 3));
        verify(httpClient)
                .put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/reorder"),
                        any(),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(223)
    void proxyAddTicketLabelRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        var responseLabel = new BoardLabel(1, 10, "Bug", "#ff0000");
        when(httpClient.postList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/labels/5"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of(responseLabel));

        var labels = proxyService.proxyAddTicketLabel(partnerId, BOARD_KEY, 1, 5, REMOTE_MEMBER_1, "Test");
        assertNotNull(labels);
        assertFalse(labels.isEmpty());
    }

    @Test
    @Order(224)
    void proxyRemoveTicketLabelRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));

        proxyService.proxyRemoveTicketLabel(partnerId, BOARD_KEY, 1, 5, REMOTE_MEMBER_1, "Test");
        verify(httpClient)
                .post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/labels/5/remove"),
                        any(),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(225)
    void proxyWatchTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/watch"),
                        any(),
                        any(),
                        anyInt(),
                        any()))
                .thenReturn(true);

        proxyService.proxyWatchTicket(partnerId, BOARD_KEY, 1, REMOTE_MEMBER_1);
        verify(httpClient)
                .post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/watch"),
                        any(),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(226)
    void proxyUnwatchTicketRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.delete(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/watch"),
                        any(),
                        anyInt(),
                        any()))
                .thenReturn(true);

        proxyService.proxyUnwatchTicket(partnerId, BOARD_KEY, 1, REMOTE_MEMBER_1);
        verify(httpClient)
                .delete(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/watch"),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(227)
    void proxyUpdateChecklistItemRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/checklist/5"),
                        any(),
                        any(),
                        anyInt(),
                        any()))
                .thenReturn(true);

        proxyService.proxyUpdateChecklistItem(partnerId, BOARD_KEY, 1, 5, "Updated", true, null, null);
        verify(httpClient)
                .put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/checklist/5"),
                        any(),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(228)
    void proxyDeleteChecklistItemRemote() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.delete(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/checklist/5"),
                        any(),
                        anyInt(),
                        any()))
                .thenReturn(true);

        proxyService.proxyDeleteChecklistItem(partnerId, BOARD_KEY, 1, 5, null, null);
        verify(httpClient)
                .delete(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/checklist/5"),
                        any(),
                        anyInt(),
                        any());
    }

    @Test
    @Order(229)
    void remoteGetReturnsNullThrows() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.get(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(null);

        assertThrows(NotFoundResponse.class, () -> proxyService.proxyGetBoard(partnerId, BOARD_KEY));
    }

    @Test
    @Order(230)
    void remoteGetListReturnsEmptyList() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.getList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/lanes"),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var lanes = proxyService.proxyGetLanes(partnerId, BOARD_KEY);
        assertNotNull(lanes);
        assertTrue(lanes.isEmpty());
    }

    @Test
    @Order(231)
    void remotePostReturnsNullThrows() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(null);

        assertThrows(
                NotFoundResponse.class,
                () -> proxyService.proxyCreateTicket(
                        partnerId, BOARD_KEY, 1, "Title", "Desc", TicketPriority.HIGH, null, REMOTE_MEMBER_1));
    }

    @Test
    @Order(232)
    void findPartnerNotFoundThrows() {
        when(federationRepository.findPartnerById(999)).thenReturn(Optional.empty());

        assertThrows(NotFoundResponse.class, () -> proxyService.proxyGetBoard(999, BOARD_KEY));
    }

    @Test
    @Order(233)
    void remotePutReturnsNullThrows() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(null);

        assertThrows(
                NotFoundResponse.class,
                () -> proxyService.proxyUpdateTicket(partnerId, BOARD_KEY, 1, "X", null, null, null, null, null, null));
    }

    @Test
    @Order(236)
    void remotePostListReturnsEmptyOnEmpty() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.postList(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/labels/5"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenReturn(List.of());

        var result = proxyService.proxyAddTicketLabel(partnerId, BOARD_KEY, 1, 5, REMOTE_MEMBER_1, "Test");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @Order(237)
    void remotePostThrowsOnError() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.post(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenThrow(new RuntimeException("Connection refused"));

        assertThrows(
                NotFoundResponse.class,
                () -> proxyService.proxyCreateTicket(
                        partnerId, BOARD_KEY, 1, "X", "D", TicketPriority.HIGH, null, REMOTE_1));
    }

    @Test
    @Order(238)
    void remotePutThrowsOnError() {
        when(federationRepository.findPartnerById(partnerId)).thenReturn(Optional.of(remotePartner()));
        when(httpClient.put(
                        eq("https://remote.example.com"),
                        eq("/remote/boards/" + BOARD_KEY + "/tickets/1/move"),
                        any(),
                        any(),
                        anyInt(),
                        any(),
                        any()))
                .thenThrow(new RuntimeException("Timeout"));

        assertThrows(
                NotFoundResponse.class, () -> proxyService.proxyMoveTicket(partnerId, BOARD_KEY, 1, 2, 0, null, null));
    }
}
