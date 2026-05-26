/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;

/**
 * Source station routes for browsing and registering for federated events on partner stations.
 * Handles both local (same instance) and remote (cross-instance) partners.
 */
@Singleton
public class FederatedEventRoutes implements Routes {
    private final FederationService federationService;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;
    private final EventFederationService eventFederationService;
    private final EventService eventService;

    @Inject
    public FederatedEventRoutes(
            FederationService federationService,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            EventFederationService eventFederationService,
            EventService eventService) {
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.eventFederationService = eventFederationService;
        this.eventService = eventService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federation/events", this::listFederatedEvents, Roles.USER);
        routes.post(prefix + "/federation/events/register", this::registerForEvent, Roles.USER);
        routes.post(prefix + "/federation/events/withdraw", this::withdrawRegistration, Roles.USER);
    }

    private void listFederatedEvents(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partners = federationService.findPartners(session.stationId()).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();

        var allEvents = new ArrayList<Map<String, Object>>();
        for (var partner : partners) {
            if (partner.isRemote()) {
                var events = httpClient.fetchFederatedEvents(
                        partner.remoteHost(), station.id(), station.federationPrivateKey());
                for (var event : events) {
                    allEvents.add(Map.of(
                            "partnerId", partner.id(),
                            "partnerStationName", partnerStationName(partner),
                            "event", event));
                }
            } else {
                // Local partner — query directly
                var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.partnerStationId());
                for (int eventId : eventIds) {
                    eventService
                            .findById(eventId)
                            .ifPresent(e -> allEvents.add(Map.of(
                                    "partnerId", partner.id(),
                                    "partnerStationName", partnerStationName(partner),
                                    "event", toRemoteEvent(e))));
                }
            }
        }
        ctx.json(allEvents);
    }

    private void registerForEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var req = ctx.bodyAsClass(FederatedRegRequest.class);
        var partner = resolvePartner(req.partnerId(), session.stationId());
        String remoteMemberId = String.valueOf(session.member().id());

        if (partner.isRemote()) {
            boolean success = httpClient.registerForFederatedEvent(
                    partner.remoteHost(),
                    req.eventId(),
                    remoteMemberId,
                    req.eventDate(),
                    station.id(),
                    station.federationPrivateKey());
            if (!success) throw new BadRequestResponse("Registration failed");
        } else {
            eventFederationService.registerFederated(
                    req.eventId(), partner.id(), remoteMemberId, LocalDate.parse(req.eventDate()));
        }
        ctx.status(HttpStatus.CREATED).json(Map.of("status", "PENDING"));
    }

    private void withdrawRegistration(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var req = ctx.bodyAsClass(FederatedRegRequest.class);
        var partner = resolvePartner(req.partnerId(), session.stationId());
        String remoteMemberId = String.valueOf(session.member().id());

        if (partner.isRemote()) {
            httpClient.withdrawFederatedRegistration(
                    partner.remoteHost(),
                    req.eventId(),
                    remoteMemberId,
                    req.eventDate(),
                    station.id(),
                    station.federationPrivateKey());
        } else {
            eventFederationService.withdrawRegistration(
                    req.eventId(), partner.id(), remoteMemberId, LocalDate.parse(req.eventDate()));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private FederationPartner resolvePartner(int partnerId, int stationId) {
        var partner =
                federationService.findPartner(partnerId).orElseThrow(() -> new NotFoundResponse("Partner not found"));
        if (partner.stationId() != stationId) throw new NotFoundResponse();
        if (partner.status() != FederationStatus.ACTIVE) throw new BadRequestResponse("Partner not active");
        return partner;
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findById(partner.partnerStationId())
                .map(Station::name)
                .orElse("?");
    }

    private Map<String, Object> toRemoteEvent(StationEvent e) {
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

    public record FederatedRegRequest(int partnerId, int eventId, String eventDate) {}
}
