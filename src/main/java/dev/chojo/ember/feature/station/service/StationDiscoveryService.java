/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.service;

import dev.chojo.ember.api.refusal.DiscoveryRefusal;
import dev.chojo.ember.api.refusal.Refusal;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.discovery.entity.PublishedRemoteStation;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService;
import dev.chojo.ember.feature.discovery.service.RemoteStationListingService.RemoteStation;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.PairRequest;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService;
import dev.chojo.ember.feature.federation.service.OutgoingPairRequestService.RemoteTarget;
import dev.chojo.ember.feature.station.entity.PublicOffer;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.entity.StationAddresses;
import dev.chojo.ember.util.WebOrigins;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The stations of this instance as the discovery page shows them, and the two ways to reach out to
 * one: an invitation code, and a request to federate.
 *
 * <p>Visitors without a session only see stations that opted into public visibility; signed-in users
 * additionally see instance-visible stations and stations exposing public content.
 *
 * <p>Each card says for the one reading it whether it may be asked to federate and whether its invite
 * code may be had, so the page draws its buttons from the card alone.
 */
@Singleton
public class StationDiscoveryService {
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
     * The discovery page: this instance's stations, the reader's own station first, then the stations
     * other instances publish, by name. The remote ones are read from the local cache only and are the
     * same for everybody, since they are what those instances publish to the world.
     *
     * @param viewer who reads the page
     * @return the cards
     */
    public List<DiscoveryEntry> list(Viewer viewer) {
        var standing = standingOf(viewer);
        List<DiscoveryEntry> entries = localEntries(viewer, standing);
        for (var remote : remoteStations.list()) {
            entries.add(toRemoteEntry(remote, standing));
        }
        return entries;
    }

    private Standing standingOf(Viewer viewer) {
        Integer stationId = viewer.stationId();
        if (stationId == null) return new Standing(viewer, List.of(), List.of());
        return new Standing(
                viewer,
                federationService.findPartners(stationId),
                viewer.mayRequest() ? outgoingRequests.waiting(stationId) : List.of());
    }

    private List<DiscoveryEntry> localEntries(Viewer viewer, Standing standing) {
        var stations = localStations(viewer);
        var ids = stations.stream().map(Station::id).toList();
        var clusters = clusterRepository.findByStations(ids);
        var offers = publicStationInfo.offers(stations);
        List<DiscoveryEntry> entries = new ArrayList<>(stations.size());
        for (var station : stations) {
            entries.add(toEntry(
                    station,
                    clusters.get(station.id()),
                    Objects.requireNonNull(offers.get(station.id()), "every listed station has an offer"),
                    standing));
        }
        return entries;
    }

    /**
     * The local stations the reader sees: their own station first where they have one, then every
     * discoverable station and, for somebody signed in, every station with public content, each once.
     */
    private List<Station> localStations(Viewer viewer) {
        Integer stationId = viewer.stationId();
        int exclude = stationId == null ? 0 : stationId;
        var discoverable = viewer.signedIn()
                ? stationService.findDiscoverable(exclude)
                : stationService.findPubliclyDiscoverable(exclude);
        var withPublicContent = viewer.signedIn() ? stationService.findWithPublicContent(exclude) : List.<Station>of();
        var seen = new HashSet<Integer>();
        List<Station> stations = new ArrayList<>();
        if (viewer.signedIn() && stationId != null) {
            stationService.findById(stationId).ifPresent(own -> {
                seen.add(own.id());
                stations.add(own);
            });
        }
        for (var station : discoverable) {
            if (seen.add(station.id())) stations.add(station);
        }
        for (var station : withPublicContent) {
            if (seen.add(station.id())) stations.add(station);
        }
        return stations;
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
        return switch (resolveTarget(stationUid, signedIn, DiscoveryRefusal.STATION_NOT_OPEN_TO_INVITES)) {
            case Target.Local local ->
                federationService.generatePairingCode(local.station().uid());
            case Target.Remote remote ->
                federationService.generateRemotePairingCode(
                        stationUid, remote.published().instanceBaseUrl());
        };
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
        switch (resolveTarget(stationUid, true, DiscoveryRefusal.STATION_NOT_OPEN_TO_FEDERATION)) {
            case Target.Remote remote ->
                outgoingRequests.send(
                        stationId,
                        new RemoteTarget(
                                stationUid,
                                remote.published().instanceBaseUrl(),
                                remote.published().instancePublicKey()));
            case Target.Local local -> requestLocally(stationId, local.station());
        }
    }

    private void requestLocally(int stationId, Station target) {
        if (target.id() == stationId) throw DiscoveryRefusal.STATION_NOT_OPEN_TO_FEDERATION.raise();
        if (federationService.findPartners(stationId).stream().anyMatch(p -> p.partnersWith(target.uid(), null))) {
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
     * The station a code or a request is for, looked up by its identifier: a station of this instance
     * discovery lists for the reader, or else a station another trusted instance publishes.
     */
    private Target resolveTarget(UUID stationUid, boolean signedIn, Refusal notFound) {
        var local = stationService.findDiscoverable(stationUid, signedIn);
        if (local.isPresent()) return new Target.Local(local.get());
        return remoteStations
                .findPublished(stationUid)
                .<Target>map(Target.Remote::new)
                .orElseThrow(notFound::raise);
    }

    /**
     * One card. The cluster is carried so the page can group the cards; a station outside any
     * cluster carries none. The public offers follow the same checks as the card this instance
     * publishes to other instances, so a station looks the same wherever it is listed.
     */
    private DiscoveryEntry toEntry(Station s, @Nullable Cluster cluster, PublicOffer offer, Standing standing) {
        boolean hasLogo = logoService.exists(s.id());
        PublicOffer shown = offer.inDiscoveryOf(s);
        boolean own = standing.isOwn(s);
        boolean federated = standing.partnersWith(s.uid(), null);
        return new DiscoveryEntry(
                s.uid(),
                s.name(),
                s.discoveryDescription(),
                hasLogo,
                hasLogo ? StationAddresses.logo(s) + "?size=" + LOGO_SIZE : null,
                shown.knowledgeBase(),
                shown.calendar(),
                shown.blog(),
                shown.waitlist(),
                s.acceptsFederation(),
                federated,
                own,
                standing.mayRequest(s.acceptsFederation(), federated, own, s.uid(), null),
                Standing.mayInvite(s.acceptsFederation(), federated, own),
                s.publicSlug(),
                offer.isEmpty() ? null : StationAddresses.publicPage(s),
                s.addressLine(),
                s.city(),
                s.country(),
                degrees(s.latitude()),
                degrees(s.longitude()),
                cluster == null ? null : cluster.uid(),
                cluster == null ? null : cluster.name(),
                null,
                null);
    }

    /**
     * A card of another instance, filled from what that instance publishes the same way a local card
     * is filled from the station itself. A partnership counts when its row names the station and the
     * instance the card was fetched from.
     */
    private static DiscoveryEntry toRemoteEntry(RemoteStation remote, Standing standing) {
        var card = remote.card();
        boolean federated = standing.partnersWith(remote.stationUid(), remote.instanceUrl());
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
                federated,
                false,
                standing.mayRequest(
                        card.acceptsFederation(), federated, false, remote.stationUid(), remote.instanceUrl()),
                Standing.mayInvite(card.acceptsFederation(), federated, false),
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
     * Who reads the discovery page.
     *
     * @param signedIn   whether anybody is signed in at all
     * @param stationId  the station they act for, or {@code null} where none is chosen or nobody is
     *                   signed in
     * @param mayRequest whether they may ask stations to federate on behalf of that station
     */
    public record Viewer(boolean signedIn, @Nullable Integer stationId, boolean mayRequest) {
        /** Somebody without a session. */
        public static Viewer anonymous() {
            return new Viewer(false, null, false);
        }
    }

    /**
     * What stands between the reader's station and the stations on the page: its partnerships, in any
     * state, and the requests it sent to stations of other instances that still wait for an answer.
     */
    private record Standing(Viewer viewer, List<FederationPartner> partners, List<PairRequest> waiting) {

        boolean isOwn(Station station) {
            Integer stationId = viewer.stationId();
            return stationId != null && stationId == station.id();
        }

        boolean partnersWith(UUID stationUid, @Nullable String instanceUrl) {
            return partners.stream().anyMatch(partner -> partner.partnersWith(stationUid, instanceUrl));
        }

        /**
         * Whether the reader may ask the station to federate: they hold the permission, the station
         * takes requests, the two are not partners, it is not their own, and no request to it waits.
         */
        boolean mayRequest(
                boolean accepts, boolean federated, boolean own, UUID stationUid, @Nullable String instanceUrl) {
            return viewer.mayRequest() && mayInvite(accepts, federated, own) && !waitsFor(stationUid, instanceUrl);
        }

        private boolean waitsFor(UUID stationUid, @Nullable String instanceUrl) {
            return instanceUrl != null
                    && waiting.stream()
                            .anyMatch(request -> request.remoteStationUid().equals(stationUid)
                                    && WebOrigins.sameOrigin(request.remoteBaseUrl(), instanceUrl));
        }

        /** Whether the station's invite code may be had: it takes requests, is no partner and not the reader's own. */
        static boolean mayInvite(boolean accepts, boolean federated, boolean own) {
            return accepts && !federated && !own;
        }
    }

    /** The station a code or a request is for. */
    private sealed interface Target {
        /** A station of this instance. */
        record Local(Station station) implements Target {}

        /** A station another instance publishes. */
        record Remote(PublishedRemoteStation published) implements Target {}
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
     * @param alreadyFederated  whether the reader's station holds a partnership with it, in any state
     * @param isOwnStation      whether it is the reader's own station
     * @param canRequest        whether the reader may ask it to federate now: they hold the permission,
     *                          it takes requests, it is no partner and not their own, and no request to
     *                          it waits for an answer
     * @param canInvite         whether its invite code may be had: it takes requests, is no partner of
     *                          the reader's station and not their own
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
            boolean canRequest,
            boolean canInvite,
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
