/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service providing business logic for federated event sharing and registrations.
 */
@Singleton
public class EventFederationService {
    private static final Logger log = LoggerFactory.getLogger(EventFederationService.class);

    private final EventFederationRepository federationRepository;
    private final FederationService federationService;
    private final FederationHttpClient httpClient;
    private final FederationRepository partnerRepository;
    private final StationRepository stationRepository;
    private final EventService eventService;

    @Inject
    public EventFederationService(
            EventFederationRepository federationRepository,
            FederationService federationService,
            FederationHttpClient httpClient,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            EventService eventService) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.partnerRepository = partnerRepository;
        this.stationRepository = stationRepository;
        this.eventService = eventService;
    }

    // -- Share management --

    /**
     * Configures federation sharing for an event.
     *
     * @param eventId    the event ID
     * @param scope      the sharing scope
     * @param partnerIds the partner IDs to target (used when scope is SPECIFIC)
     * @return the created or updated share
     */
    public EventFederationShare setShare(int eventId, String scope, List<Integer> partnerIds) {
        var share = federationRepository.setShare(eventId, scope);
        federationRepository.setShareTargets(share.id(), partnerIds);
        return share;
    }

    /**
     * Removes federation sharing for an event.
     *
     * @param eventId the event ID
     */
    public void removeShare(int eventId) {
        federationRepository.removeShare(eventId);
    }

    /**
     * Finds the federation share configuration for an event.
     *
     * @param eventId the event ID
     * @return the share, if configured
     */
    public Optional<EventFederationShare> findShareByEvent(int eventId) {
        return federationRepository.findShareByEvent(eventId);
    }

    /**
     * Retrieves the partner IDs targeted by a share.
     *
     * @param shareId the share ID
     * @return the list of partner IDs
     */
    public List<Integer> findShareTargets(int shareId) {
        return federationRepository.findShareTargets(shareId);
    }

    /**
     * Finds event IDs shared with a partner for a given station.
     *
     * @param partnerId the federation partner ID
     * @param stationId the station ID
     * @return the list of shared event IDs
     */
    public List<Integer> findSharedEventIds(int partnerId, int stationId) {
        return federationRepository.findSharedEventIds(partnerId, stationId);
    }

    // -- Registration --

    /**
     * Registers a federated member for an event occurrence.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member identifier
     * @param eventDate      the event occurrence date
     * @return the created registration
     */
    public EventFederationRegistration registerFederated(
            int eventId, int partnerId, String remoteMemberId, LocalDate eventDate) {
        return federationRepository.createRegistration(eventId, partnerId, remoteMemberId, eventDate);
    }

    /**
     * Updates the status of a federated registration.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if a row was updated
     */
    public boolean updateRegistrationStatus(int id, String status) {
        return federationRepository.updateRegistrationStatus(id, status);
    }

    /**
     * Finds a federated registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventFederationRegistration> findRegistrationById(int id) {
        return federationRepository.findRegistrationById(id);
    }

    /**
     * Finds all federated registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return federationRepository.findRegistrations(eventId, eventDate);
    }

    /**
     * Finds all federated registrations by partner.
     *
     * @param partnerId the federation partner ID
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrationsByPartner(int partnerId) {
        return federationRepository.findRegistrationsByPartner(partnerId);
    }

    /**
     * Withdraws a federated registration by its composite key.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member identifier
     * @param eventDate      the event occurrence date
     * @return true if a registration was deleted
     */
    public boolean withdrawRegistration(int eventId, int partnerId, String remoteMemberId, LocalDate eventDate) {
        return federationRepository.deleteRegistration(eventId, partnerId, remoteMemberId, eventDate);
    }

    // -- Name cache --

    /**
     * Caches the display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member identifier
     * @param displayName    the display name to cache
     */
    public void cacheName(int partnerId, String remoteMemberId, String displayName) {
        federationRepository.cacheName(partnerId, remoteMemberId, displayName);
    }

    /**
     * Retrieves the cached display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member identifier
     * @return the display name, if cached
     */
    public Optional<String> getCachedName(int partnerId, String remoteMemberId) {
        return federationRepository.getCachedName(partnerId, remoteMemberId);
    }

    /**
     * Invalidates the cached name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member identifier
     */
    public void invalidateName(int partnerId, String remoteMemberId) {
        federationRepository.invalidateName(partnerId, remoteMemberId);
    }

    // -- Federated browsing (parallel fetch from all partners) --

    /**
     * Browses federated events from all active partners with parallel fetching.
     * Local partners are queried via direct DB, remote partners via HTTP.
     */
    public List<FederatedEventItem> browseFederatedEvents(int stationId) {
        var station = stationRepository.findById(stationId).orElseThrow();
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();

        var futures = new ArrayList<CompletableFuture<List<FederatedEventItem>>>();
        for (var partner : partners) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return browseEventsViaHttp(station, partner);
                } else {
                    return browseEventsDirect(partner);
                }
            }));
        }
        return collectResults(futures);
    }

    private List<FederatedEventItem> browseEventsDirect(FederationPartner partner) {
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
        var eventIds = findSharedEventIds(partner.id(), partnerStationId);
        var items = new ArrayList<FederatedEventItem>();
        for (int eventId : eventIds) {
            eventService
                    .findById(eventId)
                    .ifPresent(e -> items.add(
                            new FederatedEventItem(partner.id(), partnerStationName(partner), toEventMap(e))));
        }
        return items;
    }

    private List<FederatedEventItem> browseEventsViaHttp(Station localStation, FederationPartner partner) {
        var remoteEvents = httpClient.fetchFederatedEvents(
                partner.remoteHost(), localStation.id(), localStation.federationPrivateKey());
        return remoteEvents.stream()
                .map(event -> new FederatedEventItem(partner.id(), partnerStationName(partner), event))
                .toList();
    }

    /**
     * Fetches a single federated event by partner station UUID and event ID.
     * Transparently handles local and remote partners.
     */
    public Map<String, Object> getFederatedEvent(int localStationId, UUID partnerStationUid, int eventId) {
        var partner = partnerRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        if (partner.isRemote()) {
            String json = httpClient.signedGetJson(
                    partner.remoteHost(),
                    "/remote/events/" + eventId,
                    localStationId,
                    stationRepository
                            .findById(localStationId)
                            .map(Station::federationPrivateKey)
                            .orElse(null));
            if (json == null) throw new IllegalStateException("Failed to fetch event from remote partner");
            try {
                return httpClient.getMapper().readValue(json, Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to parse remote event response", e);
            }
        }
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElseThrow();
        var eventIds = findSharedEventIds(partner.id(), partnerStationId);
        if (!eventIds.contains(eventId)) {
            throw new IllegalArgumentException("Event not shared with this partner");
        }
        return eventService.findById(eventId).map(this::toEventMap).orElseThrow();
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .orElse("?");
    }

    private Map<String, Object> toEventMap(StationEvent e) {
        return Map.of(
                "id", e.id(),
                "name", e.name(),
                "description", e.description() != null ? e.description() : "",
                "eventType", e.eventType() != null ? e.eventType().name() : "",
                "dayOfWeek", e.dayOfWeek() != null ? e.dayOfWeek() : 0,
                "startTime", e.startTime() != null ? e.startTime().toString() : "",
                "endTime", e.endTime() != null ? e.endTime().toString() : "",
                "requiresRegistration", e.requiresRegistration(),
                "requiresConfirmation", true);
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation event fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation event results", e);
            }
        }
        return result;
    }

    public record FederatedEventItem(int partnerId, String partnerStationName, Object event) {}
}
