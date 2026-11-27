/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.ErrorResponseWrapper;
import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.MessageResponse;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventPartnerRegistrationService;
import dev.chojo.ember.feature.events.service.EventRegistrationService;
import dev.chojo.ember.feature.events.service.EventShareService;
import dev.chojo.ember.feature.events.service.EventSharingGuards;
import dev.chojo.ember.feature.events.service.FederatedRegistrantService;
import dev.chojo.ember.feature.events.service.PartnerPlacesService;
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
    private final EventShareService shares;
    private final EventPartnerRegistrationService partnerRegistrations;
    private final PartnerPlacesService places;
    private final FederatedRegistrantService registrants;
    private final EventRegistrationService registrationService;
    private final EventSharingGuards sharingGuards;

    @Inject
    public EventSharingRoutes(
            EventCrudService crudService,
            EventShareService shares,
            EventPartnerRegistrationService partnerRegistrations,
            PartnerPlacesService places,
            FederatedRegistrantService registrants,
            EventRegistrationService registrationService,
            EventSharingGuards sharingGuards) {
        this.crudService = crudService;
        this.shares = shares;
        this.partnerRegistrations = partnerRegistrations;
        this.places = places;
        this.registrants = registrants;
        this.registrationService = registrationService;
        this.sharingGuards = sharingGuards;
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
        routes.post(
                prefix + "/events/federation-registrations/queue",
                this::moveFederationRegistrationsToQueue,
                StationPermission.EVENT_REGISTRATION);
        routes.put(
                prefix + "/events/federation-registrations/{id}/queue-position",
                this::moveFederationRegistrationInQueue,
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
        var share = shares.findShareByEvent(id);
        if (share.isEmpty()) {
            ctx.json(new FederationShareResponse(false, null, null));
            return;
        }
        var targets = shares.findShareTargets(share.get().id());
        ctx.json(new FederationShareResponse(true, share.get().scope(), targets));
    }

    /**
     * Hands one of this station's events to partner stations, where {@link EventShareService#share} lets
     * it be shared.
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
        var req = ctx.bodyAsClass(SetFederationShareRequest.class);
        shares.share(event, req.scope(), req.partnerIds() != null ? req.partnerIds() : List.of());
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
        sharingGuards.requireSharedOnItsOwn(id);
        shares.removeShare(id);
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
        var registrations = partnerRegistrations.findRegistrations(id, date);
        var ranks = registrationService.partnerQueueRanksOf(registrations.stream()
                .filter(r -> r.status() == RegistrationStatus.QUEUED)
                .map(EventFederationRegistration::id)
                .toList());
        ctx.json(registrations.stream()
                .map(r -> new EnrichedFederationRegistration(r, registrants.identify(r), ranks.get(r.id())))
                .toList());
    }

    /** Confirming or turning down a partner's member, as {@link EventPartnerRegistrationService#decide} allows. */
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
        requireOwnFederationRegistration(ctx, id);
        partnerRegistrations.decide(id, req.status(), Boolean.TRUE.equals(req.overLimit()));
        ctx.json(new MessageResponse("Status updated"));
    }

    /**
     * What each partner was given, which whoever handles the registrations has to be able to read.
     *
     * <p>Arranging the places is the event manager's, but the registration screen needs the answer to
     * know whose decision a pending visitor is: without it that screen shows accept and deny buttons
     * beside people it may not decide about, and the refusal only arrives once somebody presses one.
     *
     * <p>Asked with a day, each partner's line also shows how many of its places are taken on it.
     */
    @OpenApi(
            path = "/api/v1/events/federation-registrations/queue",
            methods = HttpMethod.POST,
            summary = "Put partners' members waiting for this station's decision onto the waiting list",
            tags = {"Events"},
            requestBody =
                    @OpenApiRequestBody(content = @OpenApiContent(from = EventRegistrationRoutes.QueueRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveFederationRegistrationsToQueue(Context ctx) {
        var req = ctx.bodyAsClass(EventRegistrationRoutes.QueueRequest.class);
        var ids = req.registrationIds() == null ? List.<Integer>of() : req.registrationIds();
        for (int id : ids) requireOwnFederationRegistration(ctx, id);
        partnerRegistrations.moveToQueue(ids);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    @OpenApi(
            path = "/api/v1/events/federation-registrations/{id}/queue-position",
            methods = HttpMethod.PUT,
            summary = "Move a partner's member to another place in line on the waiting list",
            tags = {"Events"},
            pathParams = @OpenApiParam(name = "id", type = Integer.class, required = true),
            requestBody =
                    @OpenApiRequestBody(
                            content = @OpenApiContent(from = EventRegistrationRoutes.QueuePositionRequest.class)),
            responses = {
                @OpenApiResponse(status = "204"),
                @OpenApiResponse(status = "400", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "404", content = @OpenApiContent(from = ErrorResponseWrapper.class)),
                @OpenApiResponse(status = "409", content = @OpenApiContent(from = ErrorResponseWrapper.class))
            })
    private void moveFederationRegistrationInQueue(Context ctx) {
        int id = pathInt(ctx, "id");
        var position = ctx.bodyAsClass(EventRegistrationRoutes.QueuePositionRequest.class)
                .position();
        if (position == null) throw EventRefusal.QUEUE_POSITION_MISSING.raise();
        requireOwnFederationRegistration(ctx, id);
        partnerRegistrations.moveInQueue(id, position);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /** Refuses a partner's registration that is gone or belongs to an appointment of another station. */
    private void requireOwnFederationRegistration(Context ctx, int id) {
        var registration = partnerRegistrations
                .findRegistrationById(id)
                .orElseThrow(EventRefusal.PARTNER_REGISTRATION_NOT_HERE_ON_DECISION::raise);
        requireOwnedOrNotFound(ctx, registration.eventId(), crudService::findById, StationEvent::stationId);
    }

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
        ctx.json(places.arrangements(PlacesKind.APPOINTMENT, eventId).stream()
                .map(places -> new PartnerPlacesView(
                        places.partnerId(),
                        places.slotBudget(),
                        places.partnerConfirms(),
                        day == null
                                ? null
                                : partnerRegistrations
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
     * Hands a partner a number of places, or takes the arrangement back, as
     * {@link PartnerPlacesService#arrange} allows.
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
        var event = requireOwnedOrNotFound(ctx, eventId, crudService::findById, StationEvent::stationId);
        sharingGuards.requireSharedOnItsOwn(eventId);
        var req = ctx.bodyAsClass(SetPartnerPlacesRequest.class);
        places.arrange(
                PlacesKind.APPOINTMENT, event.stationId(), eventId, partnerId, req.slotBudget(), req.partnerConfirms());
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
            @Nullable MemberIdentity memberIdentity,
            @Nullable Integer queuePosition) {}
}
