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
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService.RemoteTarget;
import dev.chojo.ember.feature.station.entity.PublicOffer;
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
    private static final String LOCAL_PAGE_PATH = "/public/station/";
    private static final int LOGO_SIZE = 128;

    private final StationService stationService;
    private final StationLogoService logoService;
    private final FederationService federationService;
    private final ClusterRepository clusterRepository;
    private final RemoteStationListingService remoteStations;
    private final PublicStationInfoService publicStationInfo;
    private final OutgoingPairRequestService outgoingRequests;

    @Inject
    public StationDiscoveryService(
            StationService stationService,
            StationLogoService logoService,
            FederationService federationService,
            ClusterRepository clusterRepository,
            RemoteStationListingService remoteStations,
            PublicStationInfoService publicStationInfo,
            OutgoingPairRequestService outgoingRequests) {
        this.stationService = stationService;
        this.logoService = logoService;
        this.federationService = federationService;
        this.clusterRepository = clusterRepository;
        this.remoteStations = remoteStations;
        this.publicStationInfo = publicStationInfo;
        this.outgoingRequests = outgoingRequests;
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
        List<FederationPartner> partners = stationId == null ? List.of() : federationService.findPartners(stationId);
        List<DiscoveryEntry> entries = localEntries(signedIn, stationId, partners);
        for (var remote : remoteStations.list()) {
            entries.add(toRemoteEntry(remote, partners));
        }
        return entries;
    }

    private List<DiscoveryEntry> localEntries(
            boolean signedIn, @Nullable Integer stationId, List<FederationPartner> partners) {
        int exclude = stationId == null ? 0 : stationId;
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
        var local = candidates.stream().filter(s -> s.uid().equals(stationUid)).findFirst();
        if (local.isPresent())
            return federationService.generatePairingCode(local.get().uid());
        return remoteStations
                .findPublished(stationUid)
                .map(remote -> federationService.generateRemotePairingCode(stationUid, remote.instanceBaseUrl()))
                .orElseThrow(DiscoveryRefusal.STATION_NOT_OPEN_TO_INVITES::raise);
    }

    /**
     * Asks a discoverable station to federate. The target has to accept, so what is created is a
     * pending request, and neither a second request nor one to a partner already paired is made.
     *
     * <p>A station of another instance that the page lists is asked over the wire: the request goes
     * to its instance, which keeps it until a manager there answers.
     *
     * @param stationId  the asking station
     * @param stationUid the station asked
     */
    public void requestFederation(int stationId, UUID stationUid) {
        if (stationUid == null) throw DiscoveryRefusal.FEDERATION_REQUEST_NEEDS_A_STATION.raise();
        var local = stationService.findDiscoverable(stationId).stream()
                .filter(s -> s.uid().equals(stationUid))
                .findFirst();
        if (local.isEmpty()) {
            var remote = remoteStations
                    .findPublished(stationUid)
                    .orElseThrow(DiscoveryRefusal.STATION_NOT_OPEN_TO_FEDERATION::raise);
            outgoingRequests.send(
                    stationId, new RemoteTarget(stationUid, remote.instanceBaseUrl(), remote.instancePublicKey()));
            return;
        }
        var target = local.get();
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
     * cluster carries none. The public offers follow the same checks as the card this instance
     * publishes to other instances, so a station looks the same wherever it is listed.
     */
    private DiscoveryEntry toEntry(Station s, List<FederationPartner> partners, boolean isOwnStation) {
        Optional<Cluster> cluster = clusterRepository.findByStation(s.id());
        boolean hasLogo = logoService.exists(s.id());
        PublicOffer offer = publicStationInfo.offer(s);
        PublicOffer shown = offer.inDiscoveryOf(s);
        return new DiscoveryEntry(
                s.uid(),
                s.name(),
                s.discoveryDescription(),
                hasLogo,
                hasLogo ? LOCAL_LOGO_PATH + s.uid() + "/logo?size=" + LOGO_SIZE : null,
                shown.knowledgeBase(),
                shown.calendar(),
                shown.blog(),
                shown.waitlist(),
                s.acceptsFederation(),
                partners.stream().anyMatch(p -> p.partnersWith(s.uid(), null)),
                isOwnStation,
                s.publicSlug(),
                offer.isEmpty() ? null : LOCAL_PAGE_PATH + (s.publicSlug() != null ? s.publicSlug() : s.uid()),
                s.addressLine(),
                s.city(),
                s.country(),
                degrees(s.latitude()),
                degrees(s.longitude()),
                cluster.map(Cluster::uid).orElse(null),
                cluster.map(Cluster::name).orElse(null),
                null,
                null);
    }

    /**
     * A card of another instance, filled from what that instance publishes the same way a local card
     * is filled from the station itself. A partnership counts when its row names the station and the
     * instance the card was fetched from.
     */
    private static DiscoveryEntry toRemoteEntry(RemoteStation remote, List<FederationPartner> partners) {
        var card = remote.card();
        return new DiscoveryEntry(
                remote.stationUid(),
                card.name(),
                card.slogan(),
                remote.logoUrl() != null,
                remote.logoUrl(),
                card.hasPublicWiki(),
                card.hasPublicCalendar(),
                card.hasPublicBlog(),
                card.waitingListOpen(),
                card.acceptsFederation(),
                partners.stream().anyMatch(p -> p.partnersWith(remote.stationUid(), remote.instanceUrl())),
                false,
                card.publicSlug(),
                remote.publicPageUrl(),
                card.addressLine(),
                card.city(),
                card.country(),
                degrees(card.latitude()),
                degrees(card.longitude()),
                remoteClusterUid(card.clusterUid()),
                card.clusterName(),
                remote.instanceHost(),
                remote.instanceUrl());
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
     * One card on the discovery page, filled the same way for a station of this instance and for one of
     * another instance.
     *
     * <p>{@code publicPageUrl} is the station's public page, set only where that page has something to
     * open: a path on this instance for a local station, the full address on its own instance for a
     * remote one. Each public part lies below it.
     *
     * <p>{@code instanceHost} and {@code instanceUrl} are set for a station of another instance only:
     * the host name of that instance, and the address it is known by here, with its port.
     *
     * <p>{@code logoUrl} is where the page finds the logo, set exactly when {@code hasLogo} is. For a
     * station of another instance it is the copy this instance keeps, never the other instance's own
     * address, so no visitor's browser is sent there.
     *
     * @param hasPublicWiki     whether the tile links to the station's public wiki
     * @param hasPublicCalendar whether it links to the public appointments
     * @param hasPublicBlog     whether it links to the public blog
     * @param waitingListOpen   whether a public waiting list takes registrations
     * @param acceptsFederation whether the station may be asked to federate
     * @param alreadyFederated  whether the asking station holds a partnership with it, in any state
     */
    public record DiscoveryEntry(
            UUID stationUid,
            String name,
            @Nullable String description,
            boolean hasLogo,
            @Nullable String logoUrl,
            boolean hasPublicWiki,
            boolean hasPublicCalendar,
            boolean hasPublicBlog,
            boolean waitingListOpen,
            boolean acceptsFederation,
            boolean alreadyFederated,
            boolean isOwnStation,
            @Nullable String publicSlug,
            @Nullable String publicPageUrl,
            @Nullable String addressLine,
            @Nullable String city,
            @Nullable String country,
            @Nullable Double latitude,
            @Nullable Double longitude,
            @Nullable UUID clusterUid,
            @Nullable String clusterName,
            @Nullable String instanceHost,
            @Nullable String instanceUrl) {}
}
