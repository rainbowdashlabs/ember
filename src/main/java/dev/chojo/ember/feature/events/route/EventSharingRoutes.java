/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.FederatedRegistrantService;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.openapi.HttpMethod;
import io.javalin.openapi.OpenApi;
import io.javalin.openapi.OpenApiContent;
import io.javalin.openapi.OpenApiParam;
import io.javalin.openapi.OpenApiRequestBody;
import io.javalin.openapi.OpenApiResponse;
import io.javalin.router.JavalinDefaultRoutingApi;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.List;

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
    private final FederatedRegistrantService registrants;

    @Inject
    public EventSharingRoutes(
            EventCrudService crudService,
            EventFederationService eventFederationService,
            FederatedRegistrantService registrants) {
        this.crudService = crudService;
        this.eventFederationService = eventFederationService;
        this.registrants = registrants;
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
        routes.get(
                prefix + "/events/{id}/partner-places", this::listPartnerPlaces, StationPermission.EVENT_REGISTRATION);
        routes.put(
                prefix + "/events/{id}/partner-places/{partnerId}",
                this::setPartnerPlaces,
                StationPermission.EVENT_MANAGER);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/federation",
            methods = HttpMethod.GET,
            summary = "How an event is shared with partner stations",
            tags = {"Events"},
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShareResponse.class)))
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
    @OpenApi(
            path = "/api/v1/events/{id}/federation",
            methods = HttpMethod.PUT,
            summary = "Share an event with partner stations",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetFederationShareRequest.class)),
            responses =
                    @OpenApiResponse(status = "200", content = @OpenApiContent(from = FederationShareResponse.class)))
    private void setFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        var event = requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
        if (event.restricted()) {
            throw Refusal.RESTRICTED_EVENT_NOT_SHARED.raise();
        }
        var req = ctx.bodyAsClass(SetFederationShareRequest.class);
        eventFederationService.setShare(id, req.scope(), req.partnerIds() != null ? req.partnerIds() : List.of());
        ctx.json(new FederationShareResponse(true, req.scope(), null));
    }

    @OpenApi(
            path = "/api/v1/events/{id}/federation",
            methods = HttpMethod.DELETE,
            summary = "Stop sharing an event with partner stations",
            tags = {"Events"},
            responses = @OpenApiResponse(status = "204"))
    private void removeFederationShare(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
        eventFederationService.removeShare(id);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/{id}/federation-registrations",
            methods = HttpMethod.GET,
            summary = "List the registrations partner stations sent for an event",
            tags = {"Events"},
            responses =
                    @OpenApiResponse(
                            status = "200",
                            content = @OpenApiContent(from = EnrichedFederationRegistration[].class)))
    private void listFederationRegistrations(Context ctx) {
        int id = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, id, crudService::findById, StationEvent::stationId);
        String dateParam = ctx.queryParam("date");
        LocalDate date = dateParam != null ? LocalDate.parse(dateParam) : null;
        var registrations = eventFederationService.findRegistrations(id, date);
        ctx.json(registrations.stream()
                .map(r -> new EnrichedFederationRegistration(r, registrants.identify(r)))
                .toList());
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
    @OpenApi(
            path = "/api/v1/events/federation-registrations/{id}/status",
            methods = HttpMethod.PUT,
            summary = "Accept or deny a partner station's registration",
            tags = {"Events"},
            requestBody =
                    @OpenApiRequestBody(
                            content = @OpenApiContent(from = EventRegistrationRoutes.StatusUpdateRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void updateFederationRegistrationStatus(Context ctx) {
        int id = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(EventRegistrationRoutes.StatusUpdateRequest.class);
        var reg = eventFederationService
                .findRegistrationById(id)
                .orElseThrow(Refusal.PARTNER_REGISTRATION_NOT_HERE_ON_DECISION::raise);
        requireOwnedOrNotFound(ctx, reg.eventId(), crudService::findById, StationEvent::stationId);

        var places = eventFederationService.partnerPlaces(reg.eventId(), reg.partnerId());
        if (places.partnerConfirms()) {
            throw Refusal.PARTNER_DECIDES_ITS_OWN.raise();
        }
        if (req.status() == RegistrationStatus.ACCEPTED) {
            if (!eventFederationService.acceptWithinBudget(id, reg.eventId(), reg.partnerId(), reg.eventDate())) {
                throw Refusal.NO_PLACES_LEFT_FOR_THIS_PARTNER.raise();
            }
        } else {
            eventFederationService.updateRegistrationStatus(id, req.status());
        }
        ctx.json(new MessageResponse("Status updated"));
    }

    /**
     * What each partner was given, which whoever handles the registrations has to be able to read.
     *
     * <p>Arranging the places is the event manager's, but the registration screen needs the answer to
     * know whose decision a pending visitor is: without it that screen shows accept and deny buttons
     * beside people it may not decide about, and the refusal only arrives once somebody presses one.
     *
     * <p>Asked with a day, each partner's line also says how many of its places are taken on it.
     */
    @OpenApi(
            path = "/api/v1/events/{id}/partner-places",
            methods = HttpMethod.GET,
            summary = "List what each partner station may do with an event",
            tags = {"Events"},
            queryParams = @OpenApiParam(name = "eventDate", type = LocalDate.class),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = PartnerPlacesView[].class)))
    private void listPartnerPlaces(Context ctx) {
        int eventId = pathInt(ctx, "id");
        requireOwnedOrNotFound(ctx, eventId, crudService::findById, StationEvent::stationId);
        var date = ctx.queryParam("eventDate");
        var day = date == null ? null : LocalDate.parse(date);
        ctx.json(eventFederationService.partnerPlaces(eventId).stream()
                .map(places -> new PartnerPlacesView(
                        places.partnerId(),
                        places.slotBudget(),
                        places.partnerConfirms(),
                        day == null
                                ? null
                                : eventFederationService
                                        .countPartnerPlaces(eventId, places.partnerId(), day)
                                        .taken()))
                .toList());
    }

    /**
     * What one partner may do with an appointment.
     *
     * @param slotBudget      how many places the partner fills itself, or {@code null} for no cap
     * @param partnerConfirms whether the partner decides about its own people
     * @param taken           how many of the partner's places are filled on the day asked about, or
     *                        {@code null} where no day was asked about
     */
    public record PartnerPlacesView(
            int partnerId,
            @Nullable Integer slotBudget,
            boolean partnerConfirms,
            @Nullable Integer taken) {}

    /**
     * Hands a partner a number of places, or takes the arrangement back.
     *
     * <p>A budget means the partner decides who fills it: the two are one choice with an optional
     * number rather than two switches that can contradict each other.
     */
    @OpenApi(
            path = "/api/v1/events/{id}/partner-places/{partnerId}",
            methods = HttpMethod.PUT,
            summary = "Hand a partner station places at an event, or take them back",
            tags = {"Events"},
            requestBody = @OpenApiRequestBody(content = @OpenApiContent(from = SetPartnerPlacesRequest.class)),
            responses = @OpenApiResponse(status = "200", content = @OpenApiContent(from = MessageResponse.class)))
    private void setPartnerPlaces(Context ctx) {
        int eventId = pathInt(ctx, "id");
        int partnerId = pathInt(ctx, "partnerId");
        requireOwnedOrNotFound(ctx, eventId, crudService::findById, StationEvent::stationId);
        var req = ctx.bodyAsClass(SetPartnerPlacesRequest.class);
        Integer slotBudget = req.slotBudget();
        if (slotBudget != null && slotBudget < 0) {
            throw Refusal.PLACES_CANNOT_BE_NEGATIVE.raise();
        }
        boolean decides = req.partnerConfirms() || slotBudget != null;
        eventFederationService.setPartnerPlaces(eventId, partnerId, decides ? slotBudget : null, decides);
        ctx.json(new MessageResponse("Places updated"));
    }

    /**
     * @param slotBudget      how many places the partner may fill, or null for no cap
     * @param partnerConfirms whether the partner decides who fills them
     */
    public record SetPartnerPlacesRequest(@Nullable Integer slotBudget, boolean partnerConfirms) {}

    public record SetFederationShareRequest(ShareScope scope, List<Integer> partnerIds) {}

    public record FederationShareResponse(
            boolean shared,
            @Nullable ShareScope scope,
            @Nullable List<Integer> partnerIds) {}

    public record EnrichedFederationRegistration(
            EventFederationRegistration registration,
            @Nullable MemberIdentity memberIdentity) {}
}
