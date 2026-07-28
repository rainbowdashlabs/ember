/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.route;

import dev.chojo.ember.api.Routes;
import dev.chojo.ember.api.UserSession;
import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.service.EventFederationService;
import dev.chojo.ember.feature.events.service.EventFieldService;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
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
import java.util.List;
import java.util.UUID;

import static dev.chojo.ember.api.RouteSupport.pathInt;
import static dev.chojo.ember.api.RouteSupport.pathUuid;

/**
 * User-facing federated event routes: the aggregated view over every federation partner plus the
 * per-partner event detail, registration and comment proxies. Partners on this instance are served
 * from the database and remote ones over signed HTTP — the service decides, never these handlers.
 */
@Singleton
public class FederatedEventRoutes implements Routes {
    private final EventFederationService eventFederationService;
    private final EventFieldService eventFieldService;
    private final FederationRepository federationRepository;
    private final StationRepository stationRepository;
    private final StationMemberRepository stationMemberRepository;

    @Inject
    public FederatedEventRoutes(
            EventFederationService eventFederationService,
            EventFieldService eventFieldService,
            FederationRepository federationRepository,
            StationRepository stationRepository,
            StationMemberRepository stationMemberRepository) {
        this.eventFederationService = eventFederationService;
        this.eventFieldService = eventFieldService;
        this.federationRepository = federationRepository;
        this.stationRepository = stationRepository;
        this.stationMemberRepository = stationMemberRepository;
    }

    @Override
    public void register(JavalinDefaultRoutingApi routes, String prefix) {
        routes.get(prefix + "/federated/events", this::federatedListEvents, StationPermission.USER);
        routes.get(prefix + "/federated/{stationuid}/events/{id}", this::federatedGetEvent, StationPermission.USER);
        routes.post(
                prefix + "/federated/{stationuid}/events/{id}/register",
                this::federatedRegister,
                StationPermission.USER);
        routes.delete(
                prefix + "/federated/{stationuid}/events/{id}/register",
                this::federatedWithdraw,
                StationPermission.USER);
        routes.get(prefix + "/federated/my-registrations", this::federatedMyRegistrations, StationPermission.USER);

        routes.get(
                prefix + "/federated/{stationuid}/events/{eventId}/comments",
                this::federatedListComments,
                StationPermission.LOGIN);
        routes.post(
                prefix + "/federated/{stationuid}/events/{eventId}/comments",
                this::federatedCreateComment,
                StationPermission.LOGIN);
        routes.put(
                prefix + "/federated/{stationuid}/events/comments/{commentId}",
                this::federatedUpdateComment,
                StationPermission.LOGIN);
        routes.delete(
                prefix + "/federated/{stationuid}/events/comments/{commentId}",
                this::federatedDeleteComment,
                StationPermission.LOGIN);
    }

    private void federatedListEvents(Context ctx) {
        UserSession session = UserSession.from(ctx);
        ctx.json(eventFederationService.browseFederatedEvents(session.stationId()));
    }

    private void federatedGetEvent(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var stationUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "id");
        var event = eventFederationService.getFederatedEvent(session.stationId(), stationUid, eventId);
        var fields = eventFieldService.findByEvent(eventId).stream()
                .filter(EventField::isPublic)
                .toList();
        ctx.json(new FederatedEventDetail(event, fields));
    }

    private void federatedRegister(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        var partner = fed.partner();
        if (partner.isRemote()) {
            boolean success = eventFederationService.registerForFederatedEvent(
                    partner.remoteHost(),
                    partner.partnerStationId(),
                    fed.eventId(),
                    fed.remoteMemberId(),
                    fed.req().eventDate(),
                    fed.station().id(),
                    fed.station().federationPrivateKey());
            if (!success) throw new BadRequestResponse("Registration failed");
        } else {
            eventFederationService.registerFederated(
                    fed.eventId(),
                    partner.id(),
                    fed.remoteMemberId(),
                    LocalDate.parse(fed.req().eventDate()));
        }
        ctx.status(HttpStatus.CREATED).json(new StatusResponse("PENDING"));
    }

    private void federatedWithdraw(Context ctx) {
        var fed = resolveFederatedRegContext(ctx);
        var partner = fed.partner();
        if (partner.isRemote()) {
            eventFederationService.withdrawFederatedRegistration(
                    partner.remoteHost(),
                    partner.partnerStationId(),
                    fed.eventId(),
                    fed.remoteMemberId(),
                    fed.req().eventDate(),
                    fed.station().id(),
                    fed.station().federationPrivateKey());
        } else {
            eventFederationService.withdrawRegistration(
                    fed.eventId(),
                    partner.id(),
                    fed.remoteMemberId(),
                    LocalDate.parse(fed.req().eventDate()));
        }
        ctx.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Resolves the shared inputs for a federated register or withdraw: the caller's station, the
     * addressed partner, the target event, the request body, and the effective remote member id.
     */
    private FederatedRegContext resolveFederatedRegContext(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var station = stationRepository.findById(session.stationId()).orElseThrow();
        var partner = resolvePartner(ctx, session.stationId());
        int eventId = pathInt(ctx, "id");
        var req = ctx.bodyAsClass(FederatedRegBody.class);
        UUID remoteMemberId =
                req.memberId() != null ? req.memberId() : session.member().uid();
        return new FederatedRegContext(station, partner, eventId, req, remoteMemberId);
    }

    private FederationPartner resolvePartner(Context ctx, int stationId) {
        var partnerUid = pathUuid(ctx, "stationuid");
        return federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    private void federatedMyRegistrations(Context ctx) {
        UserSession session = UserSession.from(ctx);
        if (session.member() == null) {
            ctx.json(List.of());
            return;
        }
        var memberUids = new ArrayList<UUID>();
        memberUids.add(session.member().uid());
        var managed = stationMemberRepository.findManaged(session.member().id());
        for (var m : managed) {
            if (m.uid() != null) memberUids.add(m.uid());
        }
        ctx.json(eventFederationService.findMyRegistrations(session.stationId(), memberUids));
    }

    private void federatedListComments(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "eventId");
        var result = eventFederationService.listFederatedComments(session.stationId(), partnerUid, eventId);
        switch (result) {
            case EventFederationService.FederatedCommentResult.ListResult r -> ctx.json(r.comments());
            case EventFederationService.FederatedCommentResult.SingleResult r -> ctx.json(r.comment());
        }
    }

    private void federatedCreateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int eventId = pathInt(ctx, "eventId");
        var req = ctx.bodyAsClass(EventCommentRoutes.CreateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var result = eventFederationService.createFederatedComment(
                session.stationId(),
                partnerUid,
                eventId,
                session.member().uid(),
                session.account().fullName().trim(),
                req.parentId(),
                req.content(),
                req.eventDate());
        switch (result) {
            case EventFederationService.FederatedCommentResult.SingleResult r ->
                ctx.status(HttpStatus.CREATED).json(r.comment());
            case EventFederationService.FederatedCommentResult.ListResult r ->
                ctx.status(HttpStatus.CREATED).json(r.comments());
        }
    }

    private void federatedUpdateComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        var req = ctx.bodyAsClass(EventCommentRoutes.UpdateCommentRequest.class);
        if (req.content() == null || req.content().isBlank()) {
            throw new BadRequestResponse("content is required");
        }
        var result = eventFederationService.updateFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid(), req.content());
        switch (result) {
            case EventFederationService.FederatedCommentResult.SingleResult r -> ctx.json(r.comment());
            case EventFederationService.FederatedCommentResult.ListResult r -> ctx.json(r.comments());
        }
    }

    private void federatedDeleteComment(Context ctx) {
        UserSession session = UserSession.from(ctx);
        var partnerUid = pathUuid(ctx, "stationuid");
        int commentId = pathInt(ctx, "commentId");
        eventFederationService.deleteFederatedComment(
                session.stationId(), partnerUid, commentId, session.member().uid());
        ctx.status(HttpStatus.NO_CONTENT);
    }

    public record FederatedEventDetail(Object event, List<EventField> publicFields) {}

    public record FederatedRegBody(String eventDate, UUID memberId) {}

    public record StatusResponse(String status) {}

    /**
     * Shared inputs for a federated register or withdraw request.
     */
    private record FederatedRegContext(
            Station station, FederationPartner partner, int eventId, FederatedRegBody req, UUID remoteMemberId) {}
}
