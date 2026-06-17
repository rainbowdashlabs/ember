/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.station.route;

import dev.chojo.ember.api.ApiServer;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.PublicKbMode;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.service.StationService;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Routes for federation discovery — listing discoverable stations and requesting federation.
 */
@Singleton
public class DiscoveryRoutes implements Routes {
    private final StationService stationService;
    private final FederationService federationService;

    @Inject
    public DiscoveryRoutes(StationService stationService, FederationService federationService) {
        this.stationService = stationService;
        this.federationService = federationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        // Public endpoints — no auth required
        routes.get(prefix + "/public/discovery", this::listDiscoverable);
        routes.post(prefix + "/public/discovery/invite", this::generateInviteForStation);
        // Authenticated endpoint — requires federation manager role
        routes.post(prefix + "/discovery/request", this::requestFederation, StationPermission.STATION_FEDERATION);
    }

    private void listDiscoverable(Context ctx) {
        // Determine exclude station and partner set (if authenticated)
        int excludeStationId = 0;
        Set<UUID> partnerStationUids = Collections.emptySet();

        UserSession session = ctx.attribute(ApiServer.ATTR_SESSION);
        if (session != null && session.stationId() != null) {
            excludeStationId = session.stationId();
            var existingPartners = federationService.findPartners(excludeStationId);
            partnerStationUids = new HashSet<>(existingPartners.stream()
                    .map(FederationPartner::partnerStationId)
                    .toList());
        }

        // Merge discoverable stations + stations with public content (deduplicated)
        var discoverable = stationService.findDiscoverable(excludeStationId);
        var publicStations = stationService.findWithPublicContent(excludeStationId);
        var seen = new HashSet<Integer>();
        Set<UUID> finalPartnerStationUids = partnerStationUids;

        List<DiscoveryEntry> entries = new ArrayList<>();
        for (var s : discoverable) {
            if (seen.add(s.id())) entries.add(toDiscoveryEntry(s, finalPartnerStationUids, false));
        }
        for (var s : publicStations) {
            if (seen.add(s.id())) entries.add(toDiscoveryEntry(s, finalPartnerStationUids, false));
        }

        // Include own station at the top, marked as own
        if (session != null && session.stationId() != null) {
            stationService
                    .findById(session.stationId())
                    .ifPresent(own -> entries.addFirst(toDiscoveryEntry(own, finalPartnerStationUids, true)));
        }

        ctx.json(entries);
    }

    private void generateInviteForStation(Context ctx) {
        var body = ctx.bodyAsClass(FederationRequestBody.class);
        if (body.stationUid() == null) {
            throw new BadRequestResponse("stationUid is required");
        }

        // Verify the station is discoverable
        var targetStation = stationService.findDiscoverable(0).stream()
                .filter(s -> s.uid().equals(body.stationUid()))
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("Station not found or not discoverable"));

        // Generate a stateless pairing code (no DB storage needed)
        var code = federationService.generatePairingCode(targetStation.uid());
        ctx.json(new InviteResponse(code));
    }

    private void requestFederation(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var body = ctx.bodyAsClass(FederationRequestBody.class);
        if (body.stationUid() == null) {
            throw new BadRequestResponse("stationUid is required");
        }
        // Look up from discoverable stations (same query path as listing)
        var targetStation = stationService.findDiscoverable(session.stationId()).stream()
                .filter(s -> s.uid().equals(body.stationUid()))
                .findFirst()
                .orElseThrow(() -> new NotFoundResponse("Station not found or not discoverable"));

        var existingPartners = federationService.findPartners(session.stationId());
        boolean alreadyFederated =
                existingPartners.stream().anyMatch(p -> p.partnerStationId().equals(targetStation.uid()));
        if (alreadyFederated) {
            throw new BadRequestResponse("Already federated with this station");
        }

        // Also check if there's already a pending request
        var pendingRequests = federationService.findPendingRequests(targetStation.id());
        boolean alreadyRequested = pendingRequests.stream().anyMatch(p -> p.stationId() == session.stationId());
        if (alreadyRequested) {
            throw new BadRequestResponse("Request already pending");
        }

        // Create a pending pair request — the target station must accept
        federationService.createPairRequest(session.stationId(), targetStation.id());
        ctx.json(new MessageResponse("Federation request sent"));
    }

    private DiscoveryEntry toDiscoveryEntry(Station s, Set<UUID> partnerUids, boolean isOwnStation) {
        return new DiscoveryEntry(
                s.uid(),
                s.name(),
                s.discoveryDescription(),
                stationService.getLogo(s.id()).isPresent(),
                s.discoveryShowKb() && s.publicKbMode() != PublicKbMode.OFF,
                s.publicCalendarEnabled(),
                partnerUids.contains(s.uid()),
                isOwnStation,
                s.publicSlug(),
                s.city(),
                s.country(),
                s.latitude() != null ? s.latitude().doubleValue() : null,
                s.longitude() != null ? s.longitude().doubleValue() : null);
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
            Double longitude) {}

    public record FederationRequestBody(UUID stationUid) {}

    public record InviteResponse(String inviteCode) {}
}
