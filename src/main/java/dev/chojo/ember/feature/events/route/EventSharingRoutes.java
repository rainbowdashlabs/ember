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
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.MemberIdentityFactory;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
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
    private final EventCrudService crudService;
    private final EventFederationService eventFederationService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;
    private final MemberIdentityFactory memberIdentityFactory;

    @Inject
    public EventSharingRoutes(
            EventCrudService crudService,
            EventFederationService eventFederationService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository,
            MemberIdentityFactory memberIdentityFactory) {
        this.crudService = crudService;
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
        routes.get(prefix + "/events/{id}/partner-places", this::listPartnerPlaces, StationPermission.EVENT_MANAGER);
        routes.put(
                prefix + "/events/{id}/partner-places/{partnerId}",
                this::setPartnerPlaces,
                StationPermission.EVENT_MANAGER);
    }

    private void getFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
        var share = eventFederationService.findShareByEvent(id);
        if (share.isEmpty()) {
            ctx.json(new FederationShareResponse(false, null, null));
            return;
        }
        var targets = eventFederationService.findShareTargets(share.get().id());
        ctx.json(new FederationShareResponse(true, share.get().scope(), targets));
    }

    /**
     * Hands one of this station's events to partner stations.
     *
     * <p>An event that not every member here may know about is not handed over. The audiences name
     * groups, tags and members of this station, and none of those mean anything at the partner, so a
     * shared event would stand open to everybody there: the opposite of what restricting it said.
     */
    private void setFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        var event = requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
        if (event.restricted()) {
            throw new BadRequestResponse("An event with a restricted audience cannot be shared with partners");
        }
        var req = ctx.bodyAsClass(SetFederationShareRequest.class);
        eventFederationService.setShare(id, req.scope(), req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new FederationShareResponse(true, req.scope(), null));
    }

    private void removeFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
        eventFederationService.removeShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void listFederationRegistrations(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
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

    /**
     * Confirming or turning down a partner's member, which is this station's to do unless it has said
     * otherwise.
     *
     * <p>Where a partner decides its own, this station keeps the list but not the pen: taking the
     * decision back is a change to the arrangement rather than something done one member at a time
     * behind the partner's back. Accepting also spends one of that partner's places, so a host
     * confirming somebody cannot put the partner over the number it was given.
     */
    private void updateFederationRegistrationStatus(Context ctx) {
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(EventRegistrationRoutes.StatusUpdateRequest.class);
        var reg = eventFederationService.findRegistrationById(id).orElseThrow(NotFoundResponse::new);
        requireOwnedOrNotFound(ctx, reg.eventId(), crudService::findById, StationEvent::stationId);

        var places = eventFederationService.partnerPlaces(reg.eventId(), reg.partnerId());
        if (places.partnerConfirms()) {
            throw new ForbiddenResponse("This partner decides its own registrations for this event");
        }
        if (req.status() == RegistrationStatus.ACCEPTED) {
            if (!eventFederationService.acceptWithinBudget(id, reg.eventId(), reg.partnerId(), reg.eventDate())) {
                throw new BadRequestResponse("No places left for this partner");
            }
        } else {
            eventFederationService.updateRegistrationStatus(id, req.status());
        }
        ctx.json(new MessageResponse("Status updated"));
    }

    /**
     * What each partner may do with this appointment, and how much of it is used.
     *
     * <p>The host's list reads this to say which partners decide for themselves, so that a row it
     * cannot act on explains itself rather than simply refusing when somebody presses it.
     */
    private void listPartnerPlaces(Context ctx) {
        int eventId = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, eventId, crudService::findById, StationEvent::stationId);
        var date = ctx.queryParam("eventDate");
        if (date == null) {
            ctx.json(eventFederationService.partnerPlaces(eventId));
            return;
        }
        var day = LocalDate.parse(date);
        ctx.json(eventFederationService.partnerPlaces(eventId).stream()
                .map(places -> {
                    var counted = eventFederationService.countPartnerPlaces(eventId, places.partnerId(), day);
                    return new PartnerPlacesView(
                            places.partnerId(), places.slotBudget(), places.partnerConfirms(), counted.taken());
                })
                .toList());
    }

    /**
     * @param taken how many of the partner's places are filled on the day asked about
     */
    public record PartnerPlacesView(int partnerId, Integer slotBudget, boolean partnerConfirms, int taken) {}

    /**
     * Hands a partner a number of places, or takes the arrangement back.
     *
     * <p>A budget means the partner decides who fills it: the two are one choice with an optional
     * number rather than two switches that can contradict each other.
     */
    private void setPartnerPlaces(Context ctx) {
        int eventId = pathInt(ctx, "id");
        int partnerId = pathInt(ctx, "partnerId");
        requireOwnedOrNotFound(ctx, eventId, crudService::findById, StationEvent::stationId);
        var req = ctx.bodyAsClass(SetPartnerPlacesRequest.class);
        if (req.slotBudget() != null && req.slotBudget() < 0) {
            throw new BadRequestResponse("A number of places cannot be negative");
        }
        boolean decides = req.partnerConfirms() || req.slotBudget() != null;
        eventFederationService.setPartnerPlaces(eventId, partnerId, decides ? req.slotBudget() : null, decides);
        ctx.json(new MessageResponse("Places updated"));
    }

    /**
     * @param slotBudget      how many places the partner may fill, or null for no cap
     * @param partnerConfirms whether the partner decides who fills them
     */
    public record SetPartnerPlacesRequest(Integer slotBudget, boolean partnerConfirms) {}

    public record SetFederationShareRequest(ShareScope scope, List<Integer> partnerIds) {}

    public record FederationShareResponse(boolean shared, ShareScope scope, List<Integer> partnerIds) {}

    public record EnrichedFederationRegistration(
            EventFederationRegistration registration, MemberIdentity memberIdentity) {}
}
