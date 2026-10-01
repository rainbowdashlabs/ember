/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.repository.EventRegistrationRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.inventory.service.InventoryService;
import dev.chojo.ember.feature.inventory.service.ItemMovementService;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.members.service.StationMemberService;
import dev.chojo.ember.feature.notifications.entity.Recipient;
import dev.chojo.ember.feature.notifications.service.NotificationInbox;
import dev.chojo.ember.feature.procedure.service.ProcedureService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SidebarCountService {
    private final NotificationInbox notificationInbox;
    private final RequirementsService requirementsService;
    private final ProfileFieldChangeRepository profileFieldChangeRepository;
    private final EventRegistrationRepository eventRegistrationRepository;
    private final LendingRepository lendingRepository;
    private final FederationRepository federationRepository;
    private final WaitingListRepository waitingListRepository;
    private final LostAndFoundRepository lostAndFoundRepository;
    private final StationRepository stationRepository;
    private final InventoryService inventoryService;
    private final ItemMovementService movementService;
    private final ProcedureService procedureService;
    private final StationMemberService stationMemberService;

    @Inject
    public SidebarCountService(
            NotificationInbox notificationInbox,
            RequirementsService requirementsService,
            ProfileFieldChangeRepository profileFieldChangeRepository,
            EventRegistrationRepository eventRegistrationRepository,
            LendingRepository lendingRepository,
            FederationRepository federationRepository,
            WaitingListRepository waitingListRepository,
            LostAndFoundRepository lostAndFoundRepository,
            StationRepository stationRepository,
            InventoryService inventoryService,
            ItemMovementService movementService,
            ProcedureService procedureService,
            StationMemberService stationMemberService) {
        this.notificationInbox = notificationInbox;
        this.requirementsService = requirementsService;
        this.profileFieldChangeRepository = profileFieldChangeRepository;
        this.eventRegistrationRepository = eventRegistrationRepository;
        this.lendingRepository = lendingRepository;
        this.federationRepository = federationRepository;
        this.waitingListRepository = waitingListRepository;
        this.lostAndFoundRepository = lostAndFoundRepository;
        this.stationRepository = stationRepository;
        this.inventoryService = inventoryService;
        this.movementService = movementService;
        this.procedureService = procedureService;
        this.stationMemberService = stationMemberService;
    }

    public SidebarCounts getCounts(StationSession session) {
        int stationId = session.stationId();
        int memberId = session.member().id();
        var roles = session.user().permissions();

        int notifications = notificationInbox.countUnread(Recipient.stationMember(memberId));

        int requirements = requirementsService.countPending(
                memberId, stationId, roles.stream().map(Enum::name).toList());

        int pendingChanges = 0;
        if (roles.contains(StationPermission.MEMBER_MANAGER) || roles.contains(StationPermission.MEMBER_GUARDIAN)) {
            pendingChanges = profileFieldChangeRepository.countPendingChanges(stationId, memberId);
        }

        int pendingRegistrations = 0;
        if (roles.contains(StationPermission.EVENT_MANAGER)) {
            pendingRegistrations = eventRegistrationRepository.countPendingByStation(stationId);
        }

        int lendingRequests = 0;
        if (roles.contains(StationPermission.INVENTORY_MANAGER)
                && roles.contains(StationPermission.STATION_FEDERATION)) {
            lendingRequests = lendingRepository.countActionableRequests(stationRepository.requireUid(stationId));
        }

        int federationRequests = 0;
        if (roles.contains(StationPermission.STATION_FEDERATION)) {
            federationRequests = federationRepository.countPendingRequests(stationRepository.requireUid(stationId));
        }

        // TODO: count open events; the sidebar shows 0 until the query exists.
        int openEvents = 0;

        int waitingListEntries = 0;
        if (roles.contains(StationPermission.WAITLIST_READ)) {
            waitingListEntries = waitingListRepository.countPendingEntries(stationId);
        }

        int lostAndFoundPending = 0;
        if (roles.contains(StationPermission.LOST_AND_FOUND_MANAGER)) {
            lostAndFoundPending = lostAndFoundRepository.countClaimedNotProvided(stationId);
        }

        int myInventoryCount = myInventoryCount(memberId, roles.contains(StationPermission.MEMBER_GUARDIAN));

        int openMovements = 0;
        if (roles.contains(StationPermission.INVENTORY_EDIT)) {
            openMovements = movementService.countOpenByStation(stationId);
        }

        int procedureCount;
        if (roles.contains(StationPermission.PROCEDURE_EDIT)) {
            procedureCount = procedureService.countOpenByStation(stationId);
        } else {
            procedureCount = procedureService.countOpenByAssigneeWithAvailableItems(stationId, memberId);
        }

        return new SidebarCounts(
                notifications,
                requirements,
                pendingChanges,
                pendingRegistrations,
                lendingRequests,
                federationRequests,
                openEvents,
                waitingListEntries,
                lostAndFoundPending,
                myInventoryCount,
                openMovements,
                procedureCount);
    }

    /**
     * The member's own item count. A guardian who owns nothing still gets a non-zero count as soon as
     * one of their managed members owns something, so the entry shows for them.
     */
    private int myInventoryCount(int memberId, boolean guardian) {
        int own = inventoryService.countItemsByMember(memberId);
        if (own > 0 || !guardian) return own;
        for (var managed : stationMemberService.findManaged(memberId)) {
            if (inventoryService.countItemsByMember(managed.id()) > 0) return 1;
        }
        return 0;
    }

    public record SidebarCounts(
            int notifications,
            int requirements,
            int pendingChanges,
            int pendingRegistrations,
            int lendingRequests,
            int federationRequests,
            int openEvents,
            int waitingListEntries,
            int lostAndFoundPending,
            int myInventoryCount,
            /** The station's movements that are still walking a chain, whatever they are for. */
            int openMovements,
            int procedureCount) {}
}
