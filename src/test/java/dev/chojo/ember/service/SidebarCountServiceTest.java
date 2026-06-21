/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.service.ExchangeService;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.RequirementsService;
import dev.chojo.ember.feature.system.service.SidebarCountService;
import dev.chojo.ember.feature.system.service.SidebarCountService.SidebarCounts;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SidebarCountServiceTest {

    private NotificationService notificationService;
    private RequirementsService requirementsService;
    private ProfileFieldChangeRepository profileFieldChangeRepository;
    private EventRepository eventRepository;
    private LendingRepository lendingRepository;
    private FederationRepository federationRepository;
    private WaitingListRepository waitingListRepository;
    private LostAndFoundRepository lostAndFoundRepository;
    private StationRepository stationRepository;
    private InventoryService inventoryService;
    private ExchangeService exchangeService;
    private ProcedureService procedureService;
    private StationMemberService stationMemberService;

    private SidebarCountService service;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 42;
    private static final UUID STATION_UID = UUID.randomUUID();

    @BeforeEach
    void setup() {
        notificationService = mock(NotificationService.class);
        requirementsService = mock(RequirementsService.class);
        profileFieldChangeRepository = mock(ProfileFieldChangeRepository.class);
        eventRepository = mock(EventRepository.class);
        lendingRepository = mock(LendingRepository.class);
        federationRepository = mock(FederationRepository.class);
        waitingListRepository = mock(WaitingListRepository.class);
        lostAndFoundRepository = mock(LostAndFoundRepository.class);
        stationRepository = mock(StationRepository.class);
        inventoryService = mock(InventoryService.class);
        exchangeService = mock(ExchangeService.class);
        procedureService = mock(ProcedureService.class);
        stationMemberService = mock(StationMemberService.class);
        when(stationRepository.resolveUid(STATION_ID)).thenReturn(STATION_UID);

        service = new SidebarCountService(
                notificationService,
                requirementsService,
                profileFieldChangeRepository,
                eventRepository,
                lendingRepository,
                federationRepository,
                waitingListRepository,
                lostAndFoundRepository,
                stationRepository,
                inventoryService,
                exchangeService,
                procedureService,
                stationMemberService);
    }

    private UserSession sessionWithPermissions(Set<StationPermission> permissions) {
        var account = new Account(1, "test@test.com", "Test", "User", true, InstanceUserType.USER, "Test User");
        var member = new StationMember(
                MEMBER_ID, STATION_ID, UUID.randomUUID(), 1, false, null, "Test User", StationUserType.MEMBER, null);
        var expanded = StationPermission.expand(EnumSet.copyOf(permissions));
        return new UserSession(
                account, 0, STATION_ID, STATION_UID, member, expanded, EnumSet.noneOf(InstancePermission.class), null);
    }

    @Test
    void getCountsWithAllRoles() {
        var roles = Set.of(
                StationPermission.LOGIN,
                StationPermission.MEMBER_MANAGER,
                StationPermission.EVENT_MANAGER,
                StationPermission.INVENTORY_MANAGER,
                StationPermission.STATION_FEDERATION,
                StationPermission.WAITLIST_MANAGER,
                StationPermission.LOST_AND_FOUND_MANAGER);
        var session = sessionWithPermissions(roles);

        when(notificationService.countUnacknowledged(MEMBER_ID)).thenReturn(5);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(3);
        when(profileFieldChangeRepository.countPendingChanges(STATION_ID, MEMBER_ID))
                .thenReturn(2);
        when(eventRepository.countPendingRegistrations(STATION_ID)).thenReturn(7);
        when(lendingRepository.countActionableRequests(STATION_ID)).thenReturn(4);
        when(waitingListRepository.countPendingEntries(STATION_ID)).thenReturn(6);
        when(lostAndFoundRepository.countClaimedNotProvided(STATION_ID)).thenReturn(1);

        when(federationRepository.countPendingRequests(STATION_UID)).thenReturn(8);
        when(inventoryService.countItemsByMember(MEMBER_ID)).thenReturn(9);
        when(exchangeService.countPendingByStation(STATION_ID)).thenReturn(3);

        var counts = service.getCounts(session);

        assertEquals(5, counts.notifications());
        assertEquals(3, counts.requirements());
        assertEquals(2, counts.pendingChanges());
        assertEquals(7, counts.pendingRegistrations());
        assertEquals(4, counts.lendingRequests());
        assertEquals(8, counts.federationRequests());
        assertEquals(0, counts.openEvents());
        assertEquals(6, counts.waitingListEntries());
        assertEquals(1, counts.lostAndFoundPending());
        assertEquals(9, counts.myInventoryCount());
        assertEquals(3, counts.pendingExchanges());
    }

    @Test
    void getCountsWithLoginOnly() {
        var roles = Set.of(StationPermission.LOGIN);
        var session = sessionWithPermissions(roles);

        when(notificationService.countUnacknowledged(MEMBER_ID)).thenReturn(10);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(2);

        var counts = service.getCounts(session);

        assertEquals(10, counts.notifications());
        assertEquals(2, counts.requirements());
        assertEquals(0, counts.pendingChanges());
        assertEquals(0, counts.pendingRegistrations());
        assertEquals(0, counts.lendingRequests());
        assertEquals(0, counts.federationRequests());
        assertEquals(0, counts.openEvents());
        assertEquals(0, counts.waitingListEntries());
        assertEquals(0, counts.lostAndFoundPending());
        assertEquals(0, counts.myInventoryCount());
        assertEquals(0, counts.pendingExchanges());

        verifyNoInteractions(profileFieldChangeRepository);
        verifyNoInteractions(eventRepository);
        verifyNoInteractions(lendingRepository);
        verifyNoInteractions(federationRepository);
        verifyNoInteractions(waitingListRepository);
        verifyNoInteractions(lostAndFoundRepository);
        verifyNoInteractions(exchangeService);
    }

    @Test
    void getCountsGuardianGetsPendingChanges() {
        var roles = Set.of(StationPermission.LOGIN, StationPermission.MEMBER_GUARDIAN);
        var session = sessionWithPermissions(roles);

        when(notificationService.countUnacknowledged(MEMBER_ID)).thenReturn(0);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(0);
        when(profileFieldChangeRepository.countPendingChanges(STATION_ID, MEMBER_ID))
                .thenReturn(3);

        var counts = service.getCounts(session);

        assertEquals(3, counts.pendingChanges());
        verifyNoInteractions(eventRepository);
    }

    @Test
    void getCountsInventoryOnlyNoLendingRequests() {
        // Only INVENTORY_MANAGER without FEDERATION_MANAGER should NOT get lending requests
        var roles = Set.of(StationPermission.LOGIN, StationPermission.INVENTORY_MANAGER);
        var session = sessionWithPermissions(roles);

        when(notificationService.countUnacknowledged(MEMBER_ID)).thenReturn(0);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(0);

        var counts = service.getCounts(session);

        assertEquals(0, counts.lendingRequests());
        verifyNoInteractions(lendingRepository);
    }

    @Test
    void getCountsFederationOnlyNoLendingRequests() {
        // Only FEDERATION_MANAGER without INVENTORY_MANAGER should NOT get lending requests
        var roles = Set.of(StationPermission.LOGIN, StationPermission.STATION_FEDERATION);
        var session = sessionWithPermissions(roles);

        when(notificationService.countUnacknowledged(MEMBER_ID)).thenReturn(0);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(0);

        when(federationRepository.countPendingRequests(STATION_UID)).thenReturn(2);

        var counts = service.getCounts(session);

        assertEquals(0, counts.lendingRequests());
        assertEquals(2, counts.federationRequests());
        verifyNoInteractions(lendingRepository);
    }

    @Test
    void sidebarCountsRecord() {
        var counts = new SidebarCounts(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

        assertEquals(1, counts.notifications());
        assertEquals(2, counts.requirements());
        assertEquals(3, counts.pendingChanges());
        assertEquals(4, counts.pendingRegistrations());
        assertEquals(5, counts.lendingRequests());
        assertEquals(6, counts.federationRequests());
        assertEquals(7, counts.openEvents());
        assertEquals(8, counts.waitingListEntries());
        assertEquals(9, counts.lostAndFoundPending());
        assertEquals(10, counts.myInventoryCount());
        assertEquals(11, counts.pendingExchanges());
        assertEquals(12, counts.procedureCount());
    }

    @Test
    void sidebarCountsZeroed() {
        var counts = new SidebarCounts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

        assertEquals(0, counts.notifications());
        assertEquals(0, counts.requirements());
        assertEquals(0, counts.pendingChanges());
        assertEquals(0, counts.pendingRegistrations());
        assertEquals(0, counts.lendingRequests());
        assertEquals(0, counts.federationRequests());
        assertEquals(0, counts.openEvents());
        assertEquals(0, counts.waitingListEntries());
        assertEquals(0, counts.lostAndFoundPending());
        assertEquals(0, counts.myInventoryCount());
        assertEquals(0, counts.pendingExchanges());
        assertEquals(0, counts.procedureCount());
    }

    @Test
    void sidebarCountsEquality() {
        var a = new SidebarCounts(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        var b = new SidebarCounts(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
