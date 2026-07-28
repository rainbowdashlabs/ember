/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.requireOwnedOrNotFound;

/**
 * Local routes that configure how this station's own events are shared with federation partners
 * and that moderate the registrations arriving from them. The partner-facing counterparts live in
 * {@link FederatedEventRoutes} and {@link RemoteEventRoutes}.
 */
@Singleton
public class EventSharingRoutes implements Routes {
    private final EventService eventService;
    private final EventFederationService eventFederationService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public EventSharingRoutes(
            EventService eventService,
            EventFederationService eventFederationService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.eventService = eventService;
        this.eventFederationService = eventFederationService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
        this.memberIdentityFactory = memberIdentityFactory;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/events/{id}/federation", this::getFederationShare, StationPermission.EVENTS_FEDERATE);
        routes.put(prefix + "/events/{id}/federation", this::setFederationShare, StationPermission.EVENTS_FEDERATE);
        routes.delete(
                prefix + "/events/{id}/federation", this::removeFederationShare, StationPermission.EVENTS_FEDERATE);
        routes.get(
                prefix + "/events/{id}/federation-registrations",
                this::listFederationRegistrations,
                StationPermission.EVENT_REGISTRATION);
        routes.put(
                prefix + "/events/federation-registrations/{id}/status",
                this::updateFederationRegistrationStatus,
                StationPermission.EVENT_REGISTRATION);
    }

    private void getFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventService::findById, StationEvent::stationId);
        var share = eventFederationService.findShareByEvent(id);
        if (share.isEmpty()) {
            ctx.json(new FederationShareResponse(false, null, null));
            return;
        }
        var targets = eventFederationService.findShareTargets(share.get().id());
        ctx.json(new FederationShareResponse(true, share.get().scope(), targets));
    }

    private void setFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventService::findById, StationEvent::stationId);
        var req = ctx.bodyAsClass(SetFederationShareRequest.class);
        eventFederationService.setShare(id, req.scope(), req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new FederationShareResponse(true, req.scope(), null));
    }

    private void removeFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventService::findById, StationEvent::stationId);
        eventFederationService.removeShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFederationRegistrations(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, eventService::findById, StationEvent::stationId);
        String dateParam = ctx.queryParam("date");
        LocalDate date = dateParam != null ? LocalDate.parse(dateParam) : null;
        var registrations = eventFederationService.findRegistrations(id, date);
        ctx.json(registrations.stream()
                .map(r -> new EnrichedFederationRegistration(r, resolveMemberIdentity(r)))
                .toList());
    }

    /**
     * Resolves the member behind a federated registration: preferably as a real local member when
     * the partner lives on this instance, otherwise as a federated identity carrying the cached
     * display name and the partner station's name.
     */
    private MemberIdentity resolveMemberIdentity(EventFederationRegistration registration) {
        var partner =
                federationRepository.findPartnerById(registration.partnerId()).orElse(null);
        UUID partnerStationUid = partner != null ? partner.partnerStationId() : null;
        if (partnerStationUid == null) return null;

        var partnerStation = stationRepository.findByUid(partnerStationUid);
        if (partnerStation.isPresent()) {
            var localMember =
                    stationMemberRepository.findByUid(partnerStation.get().id(), registration.remoteMemberId());
            if (localMember.isPresent()) {
                return memberIdentityFactory.local(
                        localMember.get().stationId(), localMember.get().id());
            }
        }

        String cachedName = eventFederationService
                .getCachedName(registration.partnerId(), registration.remoteMemberId())
                .orElse(null);
        String stationName = stationRepository
                .findByUid(partnerStationUid)
                .map(Station::name)
                .orElse(null);
        return new MemberIdentity(partnerStationUid, registration.remoteMemberId())
                .withDisplay(cachedName, stationName, null, null);
    }

    private void updateFederationRegistrationStatus(Context ctx) {
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(EventRegistrationRoutes.StatusUpdateRequest.class);
        var reg = eventFederationService.findRegistrationById(id).orElseThrow(NotFoundResponse::new);
        requireOwnedOrNotFound(ctx, reg.eventId(), eventService::findById, StationEvent::stationId);
        eventFederationService.updateRegistrationStatus(id, req.status());
        ctx.json(new MessageResponse("Status updated"));
    }

    public record SetFederationShareRequest(ShareScope scope, List<Integer> partnerIds) {}

    public record FederationShareResponse(boolean shared, ShareScope scope, List<Integer> partnerIds) {}

    public record EnrichedFederationRegistration(
            EventFederationRegistration registration, MemberIdentity memberIdentity) {}
}
