/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.InstancePermission;
import dev.chojo.ember.api.auth.InstanceUserType;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.system.service.SidebarCountService.SidebarCounts;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SidebarCountServiceTest {

    private NotificationInbox notificationInbox;
    private RequirementsService requirementsService;
    private ProfileFieldChangeRepository profileFieldChangeRepository;
    private EventRegistrationRepository eventRegistrationRepository;
    private LendingRepository lendingRepository;
    private FederationRepository federationRepository;
    private WaitingListRepository waitingListRepository;
    private LostAndFoundRepository lostAndFoundRepository;
    private InventoryService inventoryService;
    private ItemMovementService movementService;

    private SidebarCountService service;

    private static final int STATION_ID = 1;
    private static final int MEMBER_ID = 42;
    private static final UUID STATION_UID = UUID.randomUUID();

    private ProcedureService procedureService;
    private StationMemberService stationMemberService;
    private StationMemberRepository memberRepository;

    @BeforeEach
    void setup() {
        notificationInbox = mock(NotificationInbox.class);
        requirementsService = mock(RequirementsService.class);
        profileFieldChangeRepository = mock(ProfileFieldChangeRepository.class);
        eventRegistrationRepository = mock(EventRegistrationRepository.class);
        lendingRepository = mock(LendingRepository.class);
        federationRepository = mock(FederationRepository.class);
        waitingListRepository = mock(WaitingListRepository.class);
        lostAndFoundRepository = mock(LostAndFoundRepository.class);
        StationRepository stationRepository = mock(StationRepository.class);
        inventoryService = mock(InventoryService.class);
        movementService = mock(ItemMovementService.class);
        procedureService = mock(ProcedureService.class);
        stationMemberService = mock(StationMemberService.class);
        memberRepository = mock(StationMemberRepository.class);
        when(stationRepository.requireUid(STATION_ID)).thenReturn(STATION_UID);

        service = new SidebarCountService(
                notificationInbox,
                requirementsService,
                profileFieldChangeRepository,
                eventRegistrationRepository,
                lendingRepository,
                federationRepository,
                waitingListRepository,
                lostAndFoundRepository,
                stationRepository,
                inventoryService,
                movementService,
                procedureService,
                stationMemberService,
                new GuardianPolicy(memberRepository));
    }

    private StationSession sessionWithPermissions(Set<StationPermission> permissions) {
        var account = new Account(
                1,
                UUID.randomUUID(),
                "test@test.com",
                null,
                "Test",
                "User",
                true,
                InstanceUserType.USER,
                "Test User",
                null,
                null);
        var member = new StationMember(
                MEMBER_ID, STATION_ID, UUID.randomUUID(), 1, false, null, "Test User", StationUserType.MEMBER, null);
        var expanded = StationPermission.expand(EnumSet.copyOf(permissions));
        return StationSession.of(new UserSession(
                account, 0, STATION_ID, STATION_UID, member, expanded, EnumSet.noneOf(InstancePermission.class), null));
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

        when(notificationInbox.countUnread(Recipient.stationMember(MEMBER_ID))).thenReturn(5);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(3);
        when(profileFieldChangeRepository.countPendingChanges(STATION_ID, MEMBER_ID))
                .thenReturn(2);
        when(eventRegistrationRepository.countPendingByStation(STATION_ID)).thenReturn(7);
        when(lendingRepository.countActionableRequests(STATION_UID)).thenReturn(4);
        when(waitingListRepository.countPendingEntries(STATION_ID)).thenReturn(6);
        when(lostAndFoundRepository.countClaimedNotProvided(STATION_ID)).thenReturn(1);

        when(federationRepository.countPendingRequests(STATION_UID)).thenReturn(8);
        when(inventoryService.countItemsByMember(MEMBER_ID)).thenReturn(9);
        when(movementService.countOpenByStation(STATION_ID)).thenReturn(3);

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
        assertEquals(3, counts.openMovements());
    }

    @Test
    void getCountsWithLoginOnly() {
        var roles = Set.of(StationPermission.LOGIN);
        var session = sessionWithPermissions(roles);

        when(notificationInbox.countUnread(Recipient.stationMember(MEMBER_ID))).thenReturn(10);
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
        assertEquals(0, counts.openMovements());

        verifyNoInteractions(profileFieldChangeRepository);
        verifyNoInteractions(eventRegistrationRepository);
        verifyNoInteractions(lendingRepository);
        verifyNoInteractions(federationRepository);
        verifyNoInteractions(waitingListRepository);
        verifyNoInteractions(lostAndFoundRepository);
        verifyNoInteractions(movementService);
    }

    @Test
    void getCountsGuardianGetsPendingChanges() {
        var roles = Set.of(StationPermission.LOGIN, StationPermission.MEMBER_GUARDIAN);
        var session = sessionWithPermissions(roles);

        when(notificationInbox.countUnread(Recipient.stationMember(MEMBER_ID))).thenReturn(0);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(0);
        when(profileFieldChangeRepository.countPendingChanges(STATION_ID, MEMBER_ID))
                .thenReturn(3);

        var counts = service.getCounts(session);

        assertEquals(3, counts.pendingChanges());
        verifyNoInteractions(eventRegistrationRepository);
    }

    @Test
    void getCountsInventoryOnlyNoLendingRequests() {
        var roles = Set.of(StationPermission.LOGIN, StationPermission.INVENTORY_MANAGER);
        var session = sessionWithPermissions(roles);

        when(notificationInbox.countUnread(Recipient.stationMember(MEMBER_ID))).thenReturn(0);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(0);

        var counts = service.getCounts(session);

        assertEquals(0, counts.lendingRequests());
        verifyNoInteractions(lendingRepository);
    }

    @Test
    void getCountsFederationOnlyNoLendingRequests() {
        var roles = Set.of(StationPermission.LOGIN, StationPermission.STATION_FEDERATION);
        var session = sessionWithPermissions(roles);

        when(notificationInbox.countUnread(Recipient.stationMember(MEMBER_ID))).thenReturn(0);
        when(requirementsService.countPending(eq(MEMBER_ID), eq(STATION_ID), anyList()))
                .thenReturn(0);

        when(federationRepository.countPendingRequests(STATION_UID)).thenReturn(2);

        var counts = service.getCounts(session);

        assertEquals(0, counts.lendingRequests());
        assertEquals(2, counts.federationRequests());
        verifyNoInteractions(lendingRepository);
    }

    /**
     * A member who runs no procedure of the station still counts the open procedures about them and the
     * members in their care, decided by the relation alone, and those they run are not counted twice.
     */
    @Test
    void procedureCountTakesInWhatIsAboutTheMemberAndTheirWards() {
        var session = sessionWithPermissions(Set.of(StationPermission.LOGIN));
        var ward = new StationMember(
                99, STATION_ID, UUID.randomUUID(), null, false, null, "Kind", StationUserType.MEMBER, null);
        when(memberRepository.findManaged(MEMBER_ID)).thenReturn(List.of(ward));
        when(procedureService.countOpenRunByWithAvailableItems(STATION_ID, MEMBER_ID))
                .thenReturn(2);
        when(procedureService.countOpenPublicAboutNotRunBy(STATION_ID, MEMBER_ID, List.of(MEMBER_ID, 99)))
                .thenReturn(1);

        assertEquals(3, service.getCounts(session).procedureCount());
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
        assertEquals(11, counts.openMovements());
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
        assertEquals(0, counts.openMovements());
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
