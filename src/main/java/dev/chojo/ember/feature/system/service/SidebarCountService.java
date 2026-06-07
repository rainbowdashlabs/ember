/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.roles.StationPermission;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.repository.LendingRepository;
import dev.chojo.ember.feature.lostandfound.repository.LostAndFoundRepository;
import dev.chojo.ember.feature.members.repository.ProfileFieldChangeRepository;
import dev.chojo.ember.feature.notifications.service.NotificationService;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.waitinglist.repository.WaitingListRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.UUID;

@Singleton
public class SidebarCountService {
    private final NotificationService notificationService;
    private final RequirementsService requirementsService;
    private final ProfileFieldChangeRepository profileFieldChangeRepository;
    private final EventRepository eventRepository;
    private final LendingRepository lendingRepository;
    private final FederationRepository federationRepository;
    private final WaitingListRepository waitingListRepository;
    private final LostAndFoundRepository lostAndFoundRepository;
    private final StationRepository stationRepository;

    @Inject
    public SidebarCountService(
            NotificationService notificationService,
            RequirementsService requirementsService,
            ProfileFieldChangeRepository profileFieldChangeRepository,
            EventRepository eventRepository,
            LendingRepository lendingRepository,
            FederationRepository federationRepository,
            WaitingListRepository waitingListRepository,
            LostAndFoundRepository lostAndFoundRepository,
            StationRepository stationRepository) {
        this.notificationService = notificationService;
        this.requirementsService = requirementsService;
        this.profileFieldChangeRepository = profileFieldChangeRepository;
        this.eventRepository = eventRepository;
        this.lendingRepository = lendingRepository;
        this.federationRepository = federationRepository;
        this.waitingListRepository = waitingListRepository;
        this.lostAndFoundRepository = lostAndFoundRepository;
        this.stationRepository = stationRepository;
    }

    public SidebarCounts getCounts(UserSession session) {
        int stationId = session.stationId();
        int memberId = session.member().id();
        var roles = session.permissions();

        int notifications = notificationService.countUnacknowledged(memberId);

        int requirements = requirementsService.countPending(
                memberId, stationId, roles.stream().map(Enum::name).toList());

        int pendingChanges = 0;
        if (roles.contains(StationPermission.MEMBER_MANAGER) || roles.contains(StationPermission.MEMBER_GUARDIAN)) {
            pendingChanges = profileFieldChangeRepository.countPendingChanges(stationId, memberId);
        }

        int pendingRegistrations = 0;
        if (roles.contains(StationPermission.EVENT_MANAGER)) {
            pendingRegistrations = eventRepository.countPendingRegistrations(stationId);
        }

        int lendingRequests = 0;
        if (roles.contains(StationPermission.INVENTORY_MANAGER)
                && roles.contains(StationPermission.STATION_FEDERATION)) {
            lendingRequests = lendingRepository.countActionableRequests(stationId);
        }

        int federationRequests = 0;
        if (roles.contains(StationPermission.STATION_FEDERATION)) {
            UUID stationUid = stationRepository.resolveUid(stationId);
            federationRequests = federationRepository.countPendingRequests(stationUid);
        }

        // openEvents: TODO - complex query, return 0 for now
        int openEvents = 0;

        int waitingListEntries = 0;
        if (roles.contains(StationPermission.WAITLIST_READ)) {
            waitingListEntries = waitingListRepository.countPendingEntries(stationId);
        }

        int lostAndFoundPending = 0;
        if (roles.contains(StationPermission.LOST_AND_FOUND_MANAGER)) {
            lostAndFoundPending = lostAndFoundRepository.countClaimedNotProvided(stationId);
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
                lostAndFoundPending);
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
            int lostAndFoundPending) {}
}
