/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService.RemoteStation;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.Station;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
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
    private static final String LOCAL_LOGO_PATH = "/api/v1/public/stations/";
    private static final int LOGO_SIZE = 128;

    private final StationService stationService;
    private final StationLogoService logoService;
    private final FederationService federationService;
    private final ClusterRepository clusterRepository;
    private final RemoteStationListingService remoteStations;

    @Inject
    public StationDiscoveryService(
            StationService stationService,
            StationLogoService logoService,
            FederationService federationService,
            ClusterRepository clusterRepository,
            RemoteStationListingService remoteStations) {
        this.stationService = stationService;
        this.logoService = logoService;
        this.federationService = federationService;
        this.clusterRepository = clusterRepository;
        this.remoteStations = remoteStations;
    }

    /**
     * The discovery page: this instance's stations, the asking station first and marked as its own,
     * then the stations other instances publish, by name. The remote ones are read from the local
     * cache only and are the same for everybody, since they are what those instances publish to the
     * world.
     *
     * @param signedIn  whether anybody is signed in at all
     * @param stationId the asking station, or null where none is chosen
     */
    public List<DiscoveryEntry> list(boolean signedIn, @Nullable Integer stationId) {
        List<DiscoveryEntry> entries = localEntries(signedIn, stationId);
        for (var remote : remoteStations.list()) {
            entries.add(toRemoteEntry(remote));
        }
        return entries;
    }

    private List<DiscoveryEntry> localEntries(boolean signedIn, @Nullable Integer stationId) {
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
        if (stationUid == null) throw DiscoveryRefusal.INVITE_NEEDS_A_STATION.raise();
        var candidates = signedIn ? stationService.findDiscoverable(0) : stationService.findPubliclyDiscoverable(0);
        var target = candidates.stream()
                .filter(s -> s.uid().equals(stationUid))
                .findFirst()
                .orElseThrow(DiscoveryRefusal.STATION_NOT_OPEN_TO_INVITES::raise);
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
        if (stationUid == null) throw DiscoveryRefusal.FEDERATION_REQUEST_NEEDS_A_STATION.raise();
        var target = stationService.findDiscoverable(stationId).stream()
                .filter(s -> s.uid().equals(stationUid))
                .findFirst()
                .orElseThrow(DiscoveryRefusal.STATION_NOT_OPEN_TO_FEDERATION::raise);
        if (partnerUids(stationId).contains(target.uid())) {
            throw DiscoveryRefusal.ALREADY_FEDERATED.raise();
        }
        boolean alreadyRequested =
                federationService.findPendingRequests(target.id()).stream().anyMatch(p -> p.stationId() == stationId);
        if (alreadyRequested) {
            throw DiscoveryRefusal.FEDERATION_REQUEST_ALREADY_SENT.raise();
        }
        federationService.createPairRequest(stationId, target.id());
    }

    /**
     * One card. The cluster is carried so the page can group the cards; a station outside any
     * cluster carries none.
     */
    private DiscoveryEntry toEntry(Station s, Set<UUID> partnerUids, boolean isOwnStation) {
        Optional<Cluster> cluster = clusterRepository.findByStation(s.id());
        boolean hasLogo = logoService.exists(s.id());
        return new DiscoveryEntry(
                s.uid(),
                s.name(),
                s.discoveryDescription(),
                hasLogo,
                hasLogo ? LOCAL_LOGO_PATH + s.uid() + "/logo?size=" + LOGO_SIZE : null,
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
                cluster.map(Cluster::name).orElse(null),
                null,
                null);
    }

    private static DiscoveryEntry toRemoteEntry(RemoteStation remote) {
        var card = remote.card();
        return new DiscoveryEntry(
                remote.stationUid(),
                card.name(),
                card.slogan(),
                remote.logoUrl() != null,
                remote.logoUrl(),
                false,
                false,
                false,
                false,
                card.publicSlug(),
                card.city(),
                card.country(),
                degrees(card.latitude()),
                degrees(card.longitude()),
                remoteClusterUid(card.clusterUid()),
                card.clusterName(),
                remote.instanceHost(),
                remote.publicPageUrl());
    }

    private static @Nullable Double degrees(@Nullable BigDecimal coordinate) {
        return coordinate == null ? null : coordinate.doubleValue();
    }

    private static @Nullable UUID remoteClusterUid(@Nullable String clusterUid) {
        if (clusterUid == null) return null;
        try {
            return UUID.fromString(clusterUid);
        } catch (IllegalArgumentException notAnIdentifier) {
            return null;
        }
    }

    /**
     * One card on the discovery page.
     *
     * <p>{@code instanceHost} and {@code publicPageUrl} are set for a station of another instance only:
     * the host name of that instance and the station's public page there.
     *
     * <p>{@code logoUrl} is where the page finds the logo, set exactly when {@code hasLogo} is. For a
     * station of another instance it is the copy this instance keeps, never the other instance's own
     * address, so no visitor's browser is sent there.
     */
    public record DiscoveryEntry(
            UUID stationUid,
            String name,
            @Nullable String description,
            boolean hasLogo,
            @Nullable String logoUrl,
            boolean hasPublicKb,
            boolean hasPublicCalendar,
            boolean alreadyFederated,
            boolean isOwnStation,
            @Nullable String publicSlug,
            @Nullable String city,
            @Nullable String country,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable UUID clusterUid,
            @Nullable String clusterName,
            @Nullable String instanceHost,
            @Nullable String publicPageUrl) {}
}
