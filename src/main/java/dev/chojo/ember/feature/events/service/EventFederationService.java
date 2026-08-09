/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.repository.EventCommentRepository;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
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
    private final EventCrudService crudService;
    private final CommentService commentService;
    private final EventCommentRepository commentRepository;
    private final MemberNameResolver memberNameResolver;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;

    @Inject
    public EventFederationService(
            EventFederationRepository federationRepository,
            FederationService federationService,
            FederationHttpClient httpClient,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            EventCrudService crudService,
            CommentService commentService,
            EventCommentRepository commentRepository,
            MemberNameResolver memberNameResolver,
            FederationFanout fanout,
            FederationEntityResolver entityResolver) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.httpClient = httpClient;
        this.partnerRepository = partnerRepository;
        this.stationRepository = stationRepository;
        this.crudService = crudService;
        this.commentService = commentService;
        this.commentRepository = commentRepository;
        this.memberNameResolver = memberNameResolver;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
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
    public EventFederationShare setShare(int eventId, ShareScope scope, List<Integer> partnerIds) {
        var share = federationRepository.setShare(eventId, scope);
        federationRepository.setShareTargets(share.id(), partnerIds);
        log.info("Set federation share for event {} (scope {}, {} targets)", eventId, scope, partnerIds.size());
        return share;
    }

    /**
     * Removes federation sharing for an event.
     *
     * @param eventId the event ID
     */
    public void removeShare(int eventId) {
        federationRepository.removeShare(eventId);
        log.info("Removed federation share for event {}", eventId);
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
        var registration = federationRepository.createRegistration(eventId, partnerId, remoteMemberId, eventDate);
        log.info("Registered federated member for event {} from partner {} on {}", eventId, partnerId, eventDate);
        return registration;
    }

    public List<EventFederationRegistration> findRegistrationsByRemoteMember(UUID remoteMemberId) {
        return federationRepository.findRegistrationsByRemoteMember(remoteMemberId);
    }

    /**
     * Finds all federated event registrations for the given member UIDs.
     * Queries local federation registrations directly and remote partners via HTTP.
     */
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
                            RemoteEventRoutes.LIST_MEMBER_REGISTRATIONS.at(uid),
                            partner.partnerStationId(),
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
        if (federationRepository.updateRegistrationStatus(id, status)) {
            log.info("Updated federated registration {} status to {}", id, status);
            return true;
        }
        log.warn("Cannot update federated registration status: registration {} not found", id);
        return false;
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
        if (federationRepository.deleteRegistration(eventId, partnerId, remoteMemberId, eventDate)) {
            log.info(
                    "Withdrew federated registration for event {} from partner {} on {}",
                    eventId,
                    partnerId,
                    eventDate);
            return true;
        }
        log.warn(
                "Cannot withdraw federated registration: no registration for event {} from partner {} on {}",
                eventId,
                partnerId,
                eventDate);
        return false;
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
        return fanout.fanOut(partners, this::browseEventsDirect, partner -> browseEventsViaHttp(station, partner));
    }

    /**
     * Fetches a single federated event by partner station UUID and event ID.
     * Transparently handles local and remote partners.
     */
    public Object getFederatedEvent(int localStationId, UUID partnerStationUid, int eventId) {
        return entityResolver.resolve(
                localStationId,
                partnerStationUid,
                RemoteEventRoutes.GET_EVENT.at(eventId),
                RemoteEventSummary.class,
                "event",
                partner -> {
                    int partnerStationId = stationRepository
                            .findByUid(partner.partnerStationId())
                            .map(Station::id)
                            .orElseThrow();
                    var eventIds = findSharedEventIds(partner.id(), partnerStationId);
                    if (!eventIds.contains(eventId)) {
                        throw new BadRequestResponse("Event not shared with this partner");
                    }
                    return crudService.findById(eventId).map(this::toEventMap).orElseThrow();
                });
    }

    /**
     * Converts a comment to an enriched response with federated author information.
     */
    public CommentResponse toCommentResponse(Comment comment) {
        return CommentResponseMapper.fromEvent(memberNameResolver, comment);
    }

    /**
     * Lists comments for an event, enriched with federated author info.
     */
    public List<CommentResponse> listComments(int eventId) {
        return commentService.findByEvent(eventId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    /**
     * Creates a comment from a remote federated partner.
     */
    public CommentResponse createRemoteComment(
            FederationPartner partner,
            int eventId,
            UUID remoteMemberUid,
            String displayName,
            Integer parentId,
            String content,
            LocalDate eventDate) {
        var author = new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
        var comment = commentRepository.create(eventId, parentId, author, content, eventDate);
        federationRepository.cacheName(partner.id(), remoteMemberUid, displayName);
        return toCommentResponse(comment);
    }

    /**
     * Updates a comment from a remote federated partner after verifying ownership.
     */
    public CommentResponse updateRemoteComment(
            FederationPartner partner, int commentId, UUID remoteMemberUid, String content) {
        requireCommentAuthor(commentId, partner, remoteMemberUid, "edit");
        commentRepository.update(commentId, content);
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        return toCommentResponse(updated);
    }

    /**
     * Deletes a comment from a remote federated partner after verifying ownership.
     */
    public boolean deleteRemoteComment(FederationPartner partner, int commentId, UUID remoteMemberUid) {
        requireCommentAuthor(commentId, partner, remoteMemberUid, "delete");
        return commentService.delete(commentId);
    }

    /**
     * Lists comments for a federated event. Local partners use direct DB, remote partners use HTTP.
     *
     * @return JSON string for remote partners, or null for local (caller should use listComments instead)
     */
    public FederatedCommentResult listFederatedComments(int stationId, UUID partnerStationUid, int eventId) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            var result = httpClient.getList(
                    partner.remoteHost(),
                    RemoteEventRoutes.LIST_COMMENTS.at(eventId),
                    partner.partnerStationId(),
                    station.id(),
                    station.federationPrivateKey(),
                    CommentResponse.class);
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
            LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
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
                    RemoteEventRoutes.CREATE_COMMENT.at(eventId),
                    body,
                    partner.partnerStationId(),
                    station.id(),
                    station.federationPrivateKey(),
                    CommentResponse.class);
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
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            var body = new RemoteCommentUpdateRequest(memberUid.toString(), content);
            var result = httpClient.put(
                    partner.remoteHost(),
                    RemoteEventRoutes.UPDATE_COMMENT.at(commentId),
                    body,
                    partner.partnerStationId(),
                    station.id(),
                    station.federationPrivateKey(),
                    CommentResponse.class);
            if (result == null) throw new IllegalStateException("Failed to update comment on partner");
            return FederatedCommentResult.ofSingle(result);
        }
        requireCommentAuthor(commentId, partner, memberUid, "edit");
        commentRepository.update(commentId, content);
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        return FederatedCommentResult.ofSingle(toCommentResponse(updated));
    }

    // -- Comment support --

    /**
     * Deletes a comment on a federated event. Local partners use direct DB, remote partners use HTTP.
     */
    public boolean deleteFederatedComment(int stationId, UUID partnerStationUid, int commentId, UUID memberUid) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var station = stationRepository.findById(stationId).orElseThrow();
        if (partner.isRemote()) {
            boolean success = httpClient.delete(
                    partner.remoteHost(),
                    RemoteEventRoutes.DELETE_COMMENT.at(commentId),
                    partner.partnerStationId(),
                    station.id(),
                    station.federationPrivateKey());
            if (!success) throw new IllegalStateException("Failed to delete comment on partner");
            return true;
        }
        requireCommentAuthor(commentId, partner, memberUid, "delete");
        return commentService.delete(commentId);
    }

    /**
     * Verifies the comment exists and was authored by the given federated member, throwing
     * {@link NotFoundResponse} when absent and {@link ForbiddenResponse} on an author mismatch.
     */
    private void requireCommentAuthor(int commentId, FederationPartner partner, UUID memberUid, String action) {
        var comment = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        var expectedIdentity = new MemberIdentity(partner.partnerStationId(), memberUid);
        if (comment.author() == null || !comment.author().sameMember(expectedIdentity)) {
            throw new ForbiddenResponse("You can only " + action + " your own comments");
        }
    }

    public List<RemoteFederatedEvent> fetchFederatedEvents(
            String remoteHost, UUID partnerStationUid, int localStationId, String localPrivateKeyBase64) {
        return httpClient.getList(
                remoteHost,
                RemoteEventRoutes.LIST_EVENTS.at(),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteFederatedEvent.class);
    }

    public boolean registerForFederatedEvent(
            String remoteHost,
            UUID partnerStationUid,
            int eventId,
            UUID remoteMemberId,
            String eventDate,
            int localStationId,
            String localPrivateKeyBase64) {
        return httpClient.post(
                remoteHost,
                RemoteEventRoutes.REGISTER.at(eventId),
                new FederatedRegBody(remoteMemberId, eventDate),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64);
    }

    public boolean withdrawFederatedRegistration(
            String remoteHost,
            UUID partnerStationUid,
            int eventId,
            UUID remoteMemberId,
            String eventDate,
            int localStationId,
            String localPrivateKeyBase64) {
        return httpClient.delete(
                remoteHost,
                RemoteEventRoutes.WITHDRAW.at(eventId),
                new FederatedRegBody(remoteMemberId, eventDate),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64);
    }

    private List<FederatedEventItem> browseEventsDirect(FederationPartner partner) {
        int partnerStationId = stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
        var eventIds = findSharedEventIds(partner.id(), partnerStationId);
        var items = new ArrayList<FederatedEventItem>();
        for (int eventId : eventIds) {
            crudService
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
        var remoteEvents = fetchFederatedEvents(
                partner.remoteHost(),
                partner.partnerStationId(),
                localStation.id(),
                localStation.federationPrivateKey());
        return remoteEvents.stream()
                .map(event -> new FederatedEventItem(
                        partner.id(),
                        partnerStationName(partner),
                        partner.partnerStationId().toString(),
                        event))
                .toList();
    }

    private String partnerStationName(FederationPartner partner) {
        return FederationDisplayNames.partnerName(stationRepository, partner, "?");
    }

    private RemoteEventSummary toEventMap(StationEvent e) {
        return new RemoteEventSummary(
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

    /**
     * Result wrapper for federated comment operations.
     * Contains typed response objects for both local and remote partners.
     */
    public sealed interface FederatedCommentResult {
        static FederatedCommentResult ofList(List<CommentResponse> comments) {
            return new ListResult(comments);
        }

        static FederatedCommentResult ofSingle(CommentResponse comment) {
            return new SingleResult(comment);
        }

        record ListResult(List<CommentResponse> comments) implements FederatedCommentResult {}

        record SingleResult(CommentResponse comment) implements FederatedCommentResult {}
    }

    public record MyFederatedRegistration(
            int eventId, String remoteMemberId, String eventDate, RegistrationStatus status, int partnerId) {}

    public record RemoteEventSummary(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    // -- Federation HTTP convenience methods --

    public record FederatedEventItem(
            int partnerId, String partnerStationName, String partnerStationUid, Object event) {}

    /**
     * Payload for {@code POST /remote/events/{eventId}/comments}. {@code eventDate} is the
     * occurrence date for date-scoped comments on recurring events; {@code null} for
     * one-time events or whole-event comments. Older peers that omit the field continue to
     * work — Jackson maps the absent property to {@code null} on the receiving side.
     */
    private record RemoteCommentRequest(
            String remoteMemberUid, String displayName, int parentId, String content, String eventDate) {}

    private record RemoteCommentUpdateRequest(String remoteMemberUid, String content) {}

    public record RemoteFederatedEvent(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            int dayOfWeek,
            String startTime,
            String endTime,
            boolean requiresRegistration,
            boolean requiresConfirmation) {}

    private record FederatedRegBody(UUID remoteMemberId, String eventDate) {}
}
