/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.Station;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The stations of this instance as the discovery page shows them, and the two ways to reach out to
 * one: an invitation code, and a request to federate.
 *
 * <p>Visitors without a session only see stations that opted into public visibility; signed-in users
 * additionally see instance-visible stations and stations exposing public content.
 */
@Singleton
public class StationDiscoveryService {
    private final StationService stationService;
    private final StationLogoService logoService;
    private final FederationService federationService;
    private final ClusterRepository clusterRepository;

    @Inject
    public StationDiscoveryService(
            StationService stationService,
            StationLogoService logoService,
            FederationService federationService,
            ClusterRepository clusterRepository) {
        this.stationService = stationService;
        this.logoService = logoService;
        this.federationService = federationService;
        this.clusterRepository = clusterRepository;
    }

    /**
     * The discovery page, the asking station first and marked as its own.
     *
     * @param signedIn  whether anybody is signed in at all
     * @param stationId the asking station, or null where none is chosen
     */
    public List<DiscoveryEntry> list(boolean signedIn, Integer stationId) {
        int exclude = stationId == null ? 0 : stationId;
        Set<UUID> partners = stationId == null ? Set.of() : partnerUids(stationId);
        var discoverable =
                signedIn ? stationService.findDiscoverable(exclude) : stationService.findPubliclyDiscoverable(exclude);
        var withPublicContent = signedIn ? stationService.findWithPublicContent(exclude) : List.<Station>of();
        var seen = new HashSet<Integer>();
        List<DiscoveryEntry> entries = new ArrayList<>();
        for (var station : discoverable) {
            if (seen.add(station.id())) entries.add(toEntry(station, partners, false));
        }
        for (var station : withPublicContent) {
            if (seen.add(station.id())) entries.add(toEntry(station, partners, false));
        }
        if (signedIn && stationId != null) {
            stationService.findById(stationId).ifPresent(own -> entries.addFirst(toEntry(own, partners, true)));
        }
        return entries;
    }

    private Set<UUID> partnerUids(int stationId) {
        return federationService.findPartners(stationId).stream()
                .map(FederationPartner::partnerStationId)
                .collect(Collectors.toSet());
    }

    /**
     * A pairing code for a station open to invitations. Nothing is stored: the code carries what it
     * needs.
     *
     * @param signedIn   whether anybody is signed in, which widens the stations that may be invited
     * @param stationUid the station to be invited to
     */
    public String inviteCode(boolean signedIn, UUID stationUid) {
        if (stationUid == null) throw Refusal.INVITE_NEEDS_A_STATION.raise();
        var candidates = signedIn ? stationService.findDiscoverable(0) : stationService.findPubliclyDiscoverable(0);
        var target = candidates.stream()
                .filter(s -> s.uid().equals(stationUid))
                .findFirst()
                .orElseThrow(Refusal.STATION_NOT_OPEN_TO_INVITES::raise);
        return federationService.generatePairingCode(target.uid());
    }

    /**
     * Asks a discoverable station to federate. The target has to accept, so what is created is a
     * pending request, and neither a second request nor one to a partner already paired is made.
     *
     * @param stationId  the asking station
     * @param stationUid the station asked
     */
    public void requestFederation(int stationId, UUID stationUid) {
        if (stationUid == null) throw Refusal.FEDERATION_REQUEST_NEEDS_A_STATION.raise();
        var target = stationService.findDiscoverable(stationId).stream()
                .filter(s -> s.uid().equals(stationUid))
                .findFirst()
                .orElseThrow(Refusal.STATION_NOT_OPEN_TO_FEDERATION::raise);
        if (partnerUids(stationId).contains(target.uid())) {
            throw Refusal.ALREADY_FEDERATED.raise();
        }
        boolean alreadyRequested =
                federationService.findPendingRequests(target.id()).stream().anyMatch(p -> p.stationId() == stationId);
        if (alreadyRequested) {
            throw Refusal.FEDERATION_REQUEST_ALREADY_SENT.raise();
        }
        federationService.createPairRequest(stationId, target.id());
    }

    /**
     * One card. The cluster is carried so the page can group the cards; a station outside any
     * cluster carries none.
     */
    private DiscoveryEntry toEntry(Station s, Set<UUID> partnerUids, boolean isOwnStation) {
        Optional<Cluster> cluster = clusterRepository.findByStation(s.id());
        return new DiscoveryEntry(
                s.uid(),
                s.name(),
                s.discoveryDescription(),
                logoService.exists(s.id()),
                s.discoveryShowKb() && s.publicKbMode() != PublicKbMode.OFF,
                s.publicCalendarEnabled(),
                partnerUids.contains(s.uid()),
                isOwnStation,
                s.publicSlug(),
                s.city(),
                s.country(),
                s.latitude() != null ? s.latitude().doubleValue() : null,
                s.longitude() != null ? s.longitude().doubleValue() : null,
                cluster.map(Cluster::uid).orElse(null),
                cluster.map(Cluster::name).orElse(null));
    }

    public record DiscoveryEntry(
            UUID stationUid,
            String name,
            String description,
            boolean hasLogo,
            boolean hasPublicKb,
            boolean hasPublicCalendar,
            boolean alreadyFederated,
            boolean isOwnStation,
            String publicSlug,
            String city,
            String country,
            Double latitude,
            Double longitude,
            UUID clusterUid,
            String clusterName) {}
}
