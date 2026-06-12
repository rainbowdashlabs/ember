/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import dev.chojo.ember.feature.comment.route.EventCommentRoutes;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service providing business logic for federated event sharing and registrations.
 */
@Singleton
public class EventFederationService {
    private static final Logger log = LoggerFactory.getLogger(EventFederationService.class);

    private final EventFederationRepository federationRepository;
    private final FederationService federationService;
    private final FederationHttpClient httpClient;
    private final FederationRepository partnerRepository;
    private final StationRepository stationRepository;
    private final EventService eventService;
    private final CommentService commentService;
    private final EventCommentRepository commentRepository;
    private final MemberNameResolver memberNameResolver;

    @Inject
    public EventFederationService(
            EventFederationRepository federationRepository,
            FederationService federationService,
            FederationHttpClient httpClient,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            EventService eventService,
            CommentService commentService,
            EventCommentRepository commentRepository,
            MemberNameResolver memberNameResolver) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.partnerRepository = partnerRepository;
        this.stationRepository = stationRepository;
        this.eventService = eventService;
        this.commentService = commentService;
        this.commentRepository = commentRepository;
        this.memberNameResolver = memberNameResolver;
    }

    // -- Share management --

    /**
     * Configures federation sharing for an event.
     *
     * @param eventId    the event ID
     * @param scope      the sharing scope
     * @param partnerIds the partner IDs to target (used when scope is SPECIFIC)
     * @return the created or updated share
     */
    public EventFederationShare setShare(int eventId, String scope, List<Integer> partnerIds) {
        var share = federationRepository.setShare(eventId, scope);
        federationRepository.setShareTargets(share.id(), partnerIds);
        return share;
    }

    /**
     * Removes federation sharing for an event.
     *
     * @param eventId the event ID
     */
    public void removeShare(int eventId) {
        federationRepository.removeShare(eventId);
    }

    /**
     * Finds the federation share configuration for an event.
     *
     * @param eventId the event ID
     * @return the share, if configured
     */
    public Optional<EventFederationShare> findShareByEvent(int eventId) {
        return federationRepository.findShareByEvent(eventId);
    }

    /**
     * Retrieves the partner IDs targeted by a share.
     *
     * @param shareId the share ID
     * @return the list of partner IDs
     */
    public List<Integer> findShareTargets(int shareId) {
        return federationRepository.findShareTargets(shareId);
    }

    /**
     * Finds event IDs shared with a partner for a given station.
     *
     * @param partnerId the federation partner ID
     * @param stationId the station ID
     * @return the list of shared event IDs
     */
    public List<Integer> findSharedEventIds(int partnerId, int stationId) {
        return federationRepository.findSharedEventIds(partnerId, stationId);
    }

    // -- Registration --

    /**
     * Registers a federated member for an event occurrence.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return the created registration
     */
    public EventFederationRegistration registerFederated(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return federationRepository.createRegistration(eventId, partnerId, remoteMemberId, eventDate);
    }

    public List<EventFederationRegistration> findRegistrationsByRemoteMember(UUID remoteMemberId) {
        return federationRepository.findRegistrationsByRemoteMember(remoteMemberId);
    }

    /**
     * Finds all federated event registrations for the given member UIDs.
     * Queries local federation registrations directly and remote partners via HTTP.
     */
    @SuppressWarnings("unchecked")
    public List<MyFederatedRegistration> findMyRegistrations(int stationId, List<UUID> memberUids) {
        var result = new ArrayList<MyFederatedRegistration>();

        // Local registrations (stored on owning stations that are local partners)
        for (var uid : memberUids) {
            var regs = federationRepository.findRegistrationsByRemoteMember(uid);
            for (var reg : regs) {
                result.add(new MyFederatedRegistration(
                        reg.eventId(),
                        reg.remoteMemberId().toString(),
                        reg.eventDate().toString(),
                        reg.status(),
                        reg.partnerId()));
            }
        }

        // Remote registrations — query each remote partner
        var partners = partnerRepository.findPartners(stationId).stream()
                .filter(p -> p.isRemote() && p.status() == FederationStatus.ACTIVE)
                .toList();
        for (var partner : partners) {
            try {
                var station = stationRepository.findById(stationId).orElse(null);
                if (station == null) continue;
                for (var uid : memberUids) {
                    var remoteRegs = httpClient.getList(
                            partner.remoteHost(),
                            "/remote/registrations/" + uid,
                            stationId,
                            station.federationPrivateKey(),
                            MyFederatedRegistration.class);
                    result.addAll(remoteRegs);
                }
            } catch (Exception e) {
                // Remote partner unavailable — skip silently
            }
        }

        return result;
    }

    /**
     * Updates the status of a federated registration.
     *
     * @param id     the registration ID
     * @param status the new status
     * @return true if a row was updated
     */
    public boolean updateRegistrationStatus(int id, RegistrationStatus status) {
        return federationRepository.updateRegistrationStatus(id, status);
    }

    /**
     * Finds a federated registration by its ID.
     *
     * @param id the registration ID
     * @return the registration, if found
     */
    public Optional<EventFederationRegistration> findRegistrationById(int id) {
        return federationRepository.findRegistrationById(id);
    }

    /**
     * Finds all federated registrations for an event on a specific date.
     *
     * @param eventId   the event ID
     * @param eventDate the event occurrence date
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return federationRepository.findRegistrations(eventId, eventDate);
    }

    /**
     * Finds all federated registrations by partner.
     *
     * @param partnerId the federation partner ID
     * @return the list of registrations
     */
    public List<EventFederationRegistration> findRegistrationsByPartner(int partnerId) {
        return federationRepository.findRegistrationsByPartner(partnerId);
    }

    /**
     * Withdraws a federated registration by its composite key.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return true if a registration was deleted
     */
    public boolean withdrawRegistration(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return federationRepository.deleteRegistration(eventId, partnerId, remoteMemberId, eventDate);
    }

    // -- Name cache --

    /**
     * Caches the display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param displayName    the display name to cache
     */
    public void cacheName(int partnerId, UUID remoteMemberId, String displayName) {
        federationRepository.cacheName(partnerId, remoteMemberId, displayName);
    }

    /**
     * Retrieves the cached display name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @return the display name, if cached
     */
    public Optional<String> getCachedName(int partnerId, UUID remoteMemberId) {
        return federationRepository.getCachedName(partnerId, remoteMemberId);
    }

    /**
     * Invalidates the cached name for a federated member.
     *
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     */
    public void invalidateName(int partnerId, UUID remoteMemberId) {
        federationRepository.invalidateName(partnerId, remoteMemberId);
    }

    // -- Federated browsing (parallel fetch from all partners) --

    /**
     * Browses federated events from all active partners with parallel fetching.
     * Local partners are queried via direct DB, remote partners via HTTP.
     */
    public List<FederatedEventItem> browseFederatedEvents(int stationId) {
        var station = stationRepository.findById(stationId).orElseThrow();
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();

        var futures = new ArrayList<CompletableFuture<List<FederatedEventItem>>>();
        for (var partner : partners) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                if (partner.isRemote()) {
                    return browseEventsViaHttp(station, partner);
                } else {
                    return browseEventsDirect(partner);
                }
            }));
        }
        return collectResults(futures);
    }

    private List<FederatedEventItem> browseEventsDirect(FederationPartner partner) {
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
        var eventIds = findSharedEventIds(partner.id(), partnerStationId);
        var items = new ArrayList<FederatedEventItem>();
        for (int eventId : eventIds) {
            eventService
                    .findById(eventId)
                    .ifPresent(e -> items.add(new FederatedEventItem(
                            partner.id(),
                            partnerStationName(partner),
                            partner.partnerStationId().toString(),
                            toEventMap(e))));
        }
        return items;
    }

    private List<FederatedEventItem> browseEventsViaHttp(Station localStation, FederationPartner partner) {
        var remoteEvents =
                fetchFederatedEvents(partner.remoteHost(), localStation.id(), localStation.federationPrivateKey());
        return remoteEvents.stream()
                .map(event -> new FederatedEventItem(
                        partner.id(),
                        partnerStationName(partner),
                        partner.partnerStationId().toString(),
                        event))
                .toList();
    }

    /**
     * Fetches a single federated event by partner station UUID and event ID.
     * Transparently handles local and remote partners.
     */
    public Object getFederatedEvent(int localStationId, UUID partnerStationUid, int eventId) {
        var partner = partnerRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
        if (partner.status() != FederationStatus.ACTIVE) {
            throw new IllegalArgumentException("Partner is not active");
        }
        if (partner.isRemote()) {
            var result = httpClient.get(
                    partner.remoteHost(),
                    "/remote/events/" + eventId,
                    localStationId,
                    stationRepository
                            .findById(localStationId)
                            .map(Station::federationPrivateKey)
                            .orElse(null),
                    RemoteEventSummary.class);
            if (result == null) throw new IllegalStateException("Failed to fetch event from remote partner");
            return result;
        }
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElseThrow();
        var eventIds = findSharedEventIds(partner.id(), partnerStationId);
        if (!eventIds.contains(eventId)) {
            throw new IllegalArgumentException("Event not shared with this partner");
        }
        return eventService.findById(eventId).map(this::toEventMap).orElseThrow();
    }

    private String partnerStationName(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::name)
                .orElse("?");
    }

    private RemoteEventSummary toEventMap(StationEvent e) {
        return new RemoteEventSummary(
                e.id(),
                e.name(),
                e.description() != null ? e.description() : "",
                e.eventType() != null ? e.eventType().name() : "",
                e.dayOfWeek() != null ? e.dayOfWeek() : 0,
                e.startTime() != null ? e.startTime().toString() : "",
                e.endTime() != null ? e.endTime().toString() : "",
                e.requiresRegistration(),
                true);
    }

    private <T> List<T> collectResults(List<CompletableFuture<List<T>>> futures) {
        var allFuture = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        try {
            allFuture.join();
        } catch (Exception e) {
            log.error("Error during parallel federation event fetch", e);
        }
        var result = new ArrayList<T>();
        for (var future : futures) {
            try {
                result.addAll(future.get());
            } catch (Exception e) {
                log.error("Error collecting federation event results", e);
            }
        }
        return result;
    }

    public record MyFederatedRegistration(
            int eventId, String remoteMemberId, String eventDate, String status, int partnerId) {}

    public record RemoteEventSummary(
            int id,
            String name,
            String description,
            String eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    public record FederatedEventItem(
            int partnerId, String partnerStationName, String partnerStationUid, Object event) {}

    // -- Comment support --

    /**
     * Converts a comment to an enriched response with federated author information.
     */
    public EventCommentRoutes.CommentResponse toCommentResponse(Comment comment) {
        String eventDate = comment.eventDate() != null ? comment.eventDate().toString() : null;
        if (comment.deleted()) {
            return new EventCommentRoutes.CommentResponse(
                    comment.id(), comment.parentId(), null, null, "", true, comment.createdAt(), null, eventDate);
        }
        var resolved = memberNameResolver.resolveDisplay(comment.author());
        String displayName = resolved.name() != null ? resolved.name() : "";
        return new EventCommentRoutes.CommentResponse(
                comment.id(),
                comment.parentId(),
                resolved.identity(),
                displayName,
                comment.content(),
                false,
                comment.createdAt(),
                comment.updatedAt(),
                eventDate);
    }

    /**
     * Lists comments for an event, enriched with federated author info.
     */
    public List<EventCommentRoutes.CommentResponse> listComments(int eventId) {
        return commentService.findByEvent(eventId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    /**
     * Creates a comment from a remote federated partner.
     */
    public EventCommentRoutes.CommentResponse createRemoteComment(
            FederationPartner partner,
            int eventId,
            UUID remoteMemberUid,
            String displayName,
            Integer parentId,
            String content,
            java.time.LocalDate eventDate) {
        var author = new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
        var comment = commentRepository.create(eventId, parentId, author, content, eventDate);
        federationRepository.cacheName(partner.id(), remoteMemberUid, displayName);
        return toCommentResponse(comment);
    }

    /**
     * Updates a comment from a remote federated partner after verifying ownership.
     */
    public EventCommentRoutes.CommentResponse updateRemoteComment(
            FederationPartner partner, int commentId, UUID remoteMemberUid, String content) {
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
        if (comment.author() == null || !comment.author().equals(expectedIdentity)) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        commentRepository.update(commentId, content);
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        return toCommentResponse(updated);
    }

    /**
     * Deletes a comment from a remote federated partner after verifying ownership.
     */
    public boolean deleteRemoteComment(FederationPartner partner, int commentId, UUID remoteMemberUid) {
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
        if (comment.author() == null || !comment.author().equals(expectedIdentity)) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        return commentService.delete(commentId);
    }

    /**
     * Lists comments for a federated event. Local partners use direct DB, remote partners use HTTP.
     *
     * @return JSON string for remote partners, or null for local (caller should use listComments instead)
     */
    public FederatedCommentResult listFederatedComments(int stationId, UUID partnerStationUid, int eventId) {
        var partner = resolveActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            var result = httpClient.getList(
                    partner.remoteHost(),
                    "/remote/events/" + eventId + "/comments",
                    station.id(),
                    station.federationPrivateKey(),
                    EventCommentRoutes.CommentResponse.class);
            return FederatedCommentResult.ofList(result);
        }
        return FederatedCommentResult.ofList(listComments(eventId));
    }

    /**
     * Creates a comment on a federated event. Local partners use direct DB, remote partners use HTTP.
     */
    public FederatedCommentResult createFederatedComment(
            int stationId,
            UUID partnerStationUid,
            int eventId,
            UUID memberUid,
            String displayName,
            Integer parentId,
            String content,
            java.time.LocalDate eventDate) {
        var partner = resolveActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            var body = new RemoteCommentRequest(
                    memberUid.toString(),
                    displayName,
                    parentId != null ? parentId : 0,
                    content,
                    eventDate != null ? eventDate.toString() : null);
            var result = httpClient.post(
                    partner.remoteHost(),
                    "/remote/events/" + eventId + "/comments",
                    body,
                    station.id(),
                    station.federationPrivateKey(),
                    EventCommentRoutes.CommentResponse.class);
            if (result == null) throw new IllegalStateException("Failed to create comment on partner");
            return FederatedCommentResult.ofSingle(result);
        }
        var author = new MemberIdentity(partner.partnerStationId(), memberUid);
        var comment = commentRepository.create(eventId, parentId, author, content, eventDate);
        federationRepository.cacheName(partner.id(), memberUid, displayName);
        return FederatedCommentResult.ofSingle(toCommentResponse(comment));
    }

    /**
     * Updates a comment on a federated event. Local partners use direct DB, remote partners use HTTP.
     */
    public FederatedCommentResult updateFederatedComment(
            int stationId, UUID partnerStationUid, int commentId, UUID memberUid, String content) {
        var partner = resolveActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            var body = new RemoteCommentUpdateRequest(memberUid.toString(), content);
            var result = httpClient.put(
                    partner.remoteHost(),
                    "/remote/events/comments/" + commentId,
                    body,
                    station.id(),
                    station.federationPrivateKey(),
                    EventCommentRoutes.CommentResponse.class);
            if (result == null) throw new IllegalStateException("Failed to update comment on partner");
            return FederatedCommentResult.ofSingle(result);
        }
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), memberUid);
        if (comment.author() == null || !comment.author().equals(expectedIdentity)) {
            throw new ForbiddenResponse("You can only edit your own comments");
        }
        commentRepository.update(commentId, content);
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        return FederatedCommentResult.ofSingle(toCommentResponse(updated));
    }

    /**
     * Deletes a comment on a federated event. Local partners use direct DB, remote partners use HTTP.
     */
    public boolean deleteFederatedComment(int stationId, UUID partnerStationUid, int commentId, UUID memberUid) {
        var partner = resolveActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            boolean success = httpClient.delete(
                    partner.remoteHost(),
                    "/remote/events/comments/" + commentId,
                    station.id(),
                    station.federationPrivateKey());
            if (!success) throw new IllegalStateException("Failed to delete comment on partner");
            return true;
        }
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), memberUid);
        if (comment.author() == null || !comment.author().equals(expectedIdentity)) {
            throw new ForbiddenResponse("You can only delete your own comments");
        }
        return commentService.delete(commentId);
    }

    private FederationPartner resolveActivePartner(int stationId, UUID partnerStationUid) {
        return partnerRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerStationUid)
                .orElseThrow(() -> new IllegalArgumentException("Unknown partner"));
    }

    /**
     * Result wrapper for federated comment operations.
     * Contains typed response objects for both local and remote partners.
     */
    public sealed interface FederatedCommentResult {
        static FederatedCommentResult ofList(List<EventCommentRoutes.CommentResponse> comments) {
            return new ListResult(comments);
        }

        static FederatedCommentResult ofSingle(EventCommentRoutes.CommentResponse comment) {
            return new SingleResult(comment);
        }

        record ListResult(List<EventCommentRoutes.CommentResponse> comments) implements FederatedCommentResult {}

        record SingleResult(EventCommentRoutes.CommentResponse comment) implements FederatedCommentResult {}
    }

    /**
     * Payload for {@code POST /remote/events/{eventId}/comments}. {@code eventDate} is the
     * occurrence date for date-scoped comments on recurring events; {@code null} for
     * one-time events or whole-event comments. Older peers that omit the field continue to
     * work — Jackson maps the absent property to {@code null} on the receiving side.
     */
    private record RemoteCommentRequest(
            String remoteMemberUid, String displayName, int parentId, String content, String eventDate) {}

    private record RemoteCommentUpdateRequest(String remoteMemberUid, String content) {}

    // -- Federation HTTP convenience methods --

    public List<RemoteFederatedEvent> fetchFederatedEvents(
            String remoteHost, int localStationId, String localPrivateKeyBase64) {
        return httpClient.getList(
                remoteHost, "/remote/events", localStationId, localPrivateKeyBase64, RemoteFederatedEvent.class);
    }

    public boolean registerForFederatedEvent(
            String remoteHost,
            int eventId,
            UUID remoteMemberId,
            String eventDate,
            int localStationId,
            String localPrivateKeyBase64) {
        return httpClient.post(
                remoteHost,
                "/remote/events/" + eventId + "/register",
                new FederatedRegBody(remoteMemberId, eventDate),
                localStationId,
                localPrivateKeyBase64);
    }

    public boolean withdrawFederatedRegistration(
            String remoteHost,
            int eventId,
            UUID remoteMemberId,
            String eventDate,
            int localStationId,
            String localPrivateKeyBase64) {
        return httpClient.delete(
                remoteHost,
                "/remote/events/" + eventId + "/register",
                new FederatedRegBody(remoteMemberId, eventDate),
                localStationId,
                localPrivateKeyBase64);
    }

    public record RemoteFederatedEvent(
            int id,
            String name,
            String description,
            String eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    private record FederatedRegBody(UUID remoteMemberId, String eventDate) {}
}
