/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.FederationSession;
import dev.chojo.ember.api.Routes;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventCrudService;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
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
import java.util.Objects;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * Server-to-server event routes. They serve this station's shared events, their registrations and
 * their comments to a federation partner, authenticated by the RSA signature that
 * {@code AccessManager} already verified.
 */
@Singleton
public class RemoteEventRoutes implements Routes {
    private final EventCrudService crudService;
    private final EventFieldService eventFieldService;
    private final EventFederationService eventFederationService;

    @Inject
    public RemoteEventRoutes(
            EventCrudService crudService,
            EventFieldService eventFieldService,
            EventFederationService eventFederationService) {
        this.crudService = crudService;
        this.eventFieldService = eventFieldService;
        this.eventFederationService = eventFederationService;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/remote/events", this::remoteListEvents);
        routes.get(prefix + "/remote/events/{id}", this::remoteGetEvent);
        routes.post(prefix + "/remote/events/{id}/register", this::remoteRegister);
        routes.delete(prefix + "/remote/events/{id}/register", this::remoteWithdraw);
        routes.get(prefix + "/remote/events/{id}/registrations", this::remoteListRegistrations);
        routes.get(prefix + "/remote/registrations/{memberUid}", this::remoteListMemberRegistrations);
        routes.post(prefix + "/remote/webhook/event-registration-status", this::remoteOnRegistrationStatus);

        routes.get(prefix + "/remote/events/{eventId}/comments", this::remoteListComments);
        routes.post(prefix + "/remote/events/{eventId}/comments", this::remoteCreateComment);
        routes.put(prefix + "/remote/events/comments/{commentId}", this::remoteUpdateComment);
        routes.delete(prefix + "/remote/events/comments/{commentId}", this::remoteDeleteComment);
    }

    private void remoteListEvents(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        var events = eventIds.stream()
                .map(id -> crudService.findById(id).orElse(null))
                .filter(Objects::nonNull)
                .map(this::toRemoteEvent)
                .toList();
        ctx.json(events);
    }

    private void remoteGetEvent(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var event = crudService.findById(eventId).orElseThrow(NotFoundResponse::new);
        var fields = eventFieldService.findByEvent(eventId).stream()
                .filter(EventField::isPublic)
                .toList();
        ctx.json(new RemoteEventDetail(toRemoteEvent(event), fields));
    }

    private void remoteRegister(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        var reg =
                eventFederationService.registerFederated(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.CREATED).json(reg);
    }

    private void remoteWithdraw(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(RemoteRegistrationRequest.class);
        eventFederationService.withdrawRegistration(eventId, partner.id(), req.remoteMemberId(), req.eventDate());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void remoteListRegistrations(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = pathInt(ctx, "id");
        requireSharedEvent(partner, eventId);
        var registrations = eventFederationService.findRegistrationsByPartner(partner.id()).stream()
                .filter(r -> r.eventId() == eventId)
                .toList();
        ctx.json(registrations);
    }

    private void remoteListMemberRegistrations(Context ctx) {
        var partner = requireFederationPartner(ctx);
        var memberUid = pathUuid(ctx, "memberUid");
        var registrations = eventFederationService.findRegistrationsByRemoteMember(memberUid).stream()
                .filter(r -> r.partnerId() == partner.id())
                .toList();
        ctx.json(registrations.stream()
                .map(r -> new RemoteMemberRegistration(
                        r.eventId(),
                        r.remoteMemberId().toString(),
                        r.eventDate().toString(),
                        r.status(),
                        r.partnerId()))
                .toList());
    }

    private void remoteOnRegistrationStatus(Context ctx) {
        requireFederationPartner(ctx);
        ctx.json(new FederatedEventRoutes.StatusResponse("ok"));
    }

    private void remoteListComments(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireSharedEvent(partner, eventId);
        ctx.json(eventFederationService.listComments(eventId));
    }

    private void remoteCreateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int eventId = pathInt(ctx, "eventId");
        requireSharedEvent(partner, eventId);
        var req = ctx.bodyAsClass(RemoteCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        ctx.status(HttpStatus.CREATED)
                .json(eventFederationService.createRemoteComment(
                        partner,
                        eventId,
                        req.remoteMemberUid(),
                        req.displayName(),
                        req.parentId(),
                        req.content(),
                        parseCommentDate(req.eventDate())));
    }

    /**
     * Reads the optional occurrence date a comment is scoped to. Older peers omit the field
     * entirely, which keeps the comment attached to the whole event rather than one date.
     */
    private LocalDate parseCommentDate(String eventDate) {
        if (eventDate == null || eventDate.isBlank()) return null;
        try {
            return LocalDate.parse(eventDate);
        } catch (Exception e) {
            throw new BadRequestResponse("eventDate must be ISO yyyy-MM-dd");
        }
    }

    private void remoteUpdateComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteCommentUpdateRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        ctx.json(eventFederationService.updateRemoteComment(partner, commentId, req.remoteMemberUid(), req.content()));
    }

    private void remoteDeleteComment(Context ctx) {
        var partner = requireFederationPartner(ctx);
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(RemoteCommentDeleteRequest.class);
        if (eventFederationService.deleteRemoteComment(partner, commentId, req.remoteMemberUid())) {
            ctx.status(HttpStatus.NO_CONTENT);
        } else {
            throw new NotFoundResponse();
        }
    }

    private FederationPartner requireFederationPartner(Context ctx) {
        var session = FederationSession.from(ctx);
        if (session == null) {
            throw new ForbiddenResponse("Missing or invalid federation signature");
        }
        return session.partner();
    }

    /**
     * Confirms the partner is allowed to see the given event, i.e. it is in the set
     * this station shares with that partner. Guards every {@code /remote/events}
     * read/write so a partner cannot address never-federated events by enumerating
     * ids.
     */
    private void requireSharedEvent(FederationPartner partner, int eventId) {
        var eventIds = eventFederationService.findSharedEventIds(partner.id(), partner.stationId());
        if (!eventIds.contains(eventId)) {
            throw new NotFoundResponse();
        }
    }

    private RemoteEvent toRemoteEvent(StationEvent e) {
        return new RemoteEvent(
                e.id(),
                e.name(),
                e.description() != null ? e.description() : "",
                e.eventType(),
                e.dayOfWeek() != null ? e.dayOfWeek() : 0,
                e.startTime() != null ? e.startTime().toString() : "",
                e.endTime() != null ? e.endTime().toString() : "",
                e.requiresRegistration(),
                true);
    }

    public record RemoteEvent(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    public record RemoteEventDetail(RemoteEvent event, List<EventField> publicFields) {}

    public record RemoteMemberRegistration(
            int eventId, String remoteMemberId, String eventDate, RegistrationStatus status, int partnerId) {}

    public record RemoteRegistrationRequest(UUID remoteMemberId, LocalDate eventDate) {}

    public record RemoteCommentRequest(
            UUID remoteMemberUid, String displayName, Integer parentId, String content, String eventDate) {}

    public record RemoteCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    public record RemoteCommentDeleteRequest(UUID remoteMemberUid) {}
}
