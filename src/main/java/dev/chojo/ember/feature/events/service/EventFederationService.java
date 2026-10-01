/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.Refusal;
import dev.chojo.ember.api.RefusalResponse;
import dev.chojo.ember.conf.file.elements.Api;
import dev.chojo.ember.feature.comment.entity.Comment;
import dev.chojo.ember.feature.comment.entity.CommentEntityType;
import dev.chojo.ember.feature.comment.repository.CommentRepository;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.comment.service.CommentService;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.EventField;
import dev.chojo.ember.feature.events.entity.EventPartnerPlaces;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.route.FederatedEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.media.service.MediaLibraryService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Federated event sharing and registrations: what this station serves its partners about the
 * appointments it shares, and how its members read, join and comment on the appointments partners
 * share with it.
 *
 * <p>What a partner may see and do is decided in the serving functions registered in
 * {@link #serveOn}. A partner on another instance reaches them through the {@code /remote} routes, a
 * partner on this instance through the local transport, and both are held to the share targets and
 * the rows of the station holding the appointment.
 */
@Singleton
public class EventFederationService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(EventFederationService.class);

    private final EventFederationRepository federationRepository;
    private final FederationService federationService;
    private final FederationTransport transport;
    private final FederationRepository partnerRepository;
    private final StationRepository stationRepository;
    private final EventCrudService crudService;
    private final CommentService commentService;
    private final CommentRepository commentRepository;
    private final MemberNameResolver memberNameResolver;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;
    private final EventAttachmentService attachmentService;
    private final EventFieldService fieldService;
    private final OccurrenceCalendar occurrenceCalendar;
    private final MediaLibraryService media;
    private final Api apiConfig;

    @Inject
    public EventFederationService(
            EventFederationRepository federationRepository,
            FederationService federationService,
            FederationTransport transport,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            EventCrudService crudService,
            CommentService commentService,
            CommentRepository commentRepository,
            MemberNameResolver memberNameResolver,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            EventAttachmentService attachmentService,
            EventFieldService fieldService,
            OccurrenceCalendar occurrenceCalendar,
            MediaLibraryService media,
            Api apiConfig) {
        this.federationRepository = federationRepository;
        this.federationService = federationService;
        this.transport = transport;
        this.partnerRepository = partnerRepository;
        this.stationRepository = stationRepository;
        this.crudService = crudService;
        this.commentService = commentService;
        this.commentRepository = commentRepository;
        this.memberNameResolver = memberNameResolver;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
        this.attachmentService = attachmentService;
        this.fieldService = fieldService;
        this.occurrenceCalendar = occurrenceCalendar;
        this.media = media;
        this.apiConfig = apiConfig;
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
     * <p>The status is the event's to decide, exactly as it is for a member of this station: an event
     * that asks for no confirmation accepts at once, and one that asks holds the registration until
     * somebody answers. Without this the row took the column default and every visitor waited for a
     * confirmation on events that ask nobody for one, which nobody at the host was ever prompted to
     * give.
     *
     * <p>A partner with places of its own is the other reason somebody has to choose. Accepting at
     * once there would hand out more places than the partner was given, since nothing would ever
     * count them: the arrangement is itself the reason to pick, whatever the appointment asks of this
     * station's own members.
     *
     * <p>The door is asked here rather than at the route, because two stations that happen to sit on
     * one instance never go through the remote one. Asking it in the one place both paths share is
     * what keeps a visitor's answer the same wherever their station is kept.
     *
     * @param eventId        the event ID
     * @param partnerId      the federation partner ID
     * @param remoteMemberId the remote member UUID
     * @param eventDate      the event occurrence date
     * @return the created registration
     */
    public EventFederationRegistration registerFederated(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        var event = crudService.findById(eventId).orElseThrow(NotFoundResponse::new);
        requireOpenForRegistration(event, eventDate);
        boolean somebodyChooses = event.requiresConfirmation()
                || federationRepository.findPartnerPlaces(eventId, partnerId).partnerConfirms();
        var status = somebodyChooses ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
        var registration =
                federationRepository.createRegistration(eventId, partnerId, remoteMemberId, eventDate, status);
        log.info(
                "Registered federated member for event {} from partner {} on {} as {}",
                eventId,
                partnerId,
                eventDate,
                status);
        return registration;
    }

    /**
     * The questions a member of this station answers before they are on a list, asked of a visitor
     * too.
     *
     * <p>Being shared with is what makes somebody eligible from another station, and that is checked
     * before this. Everything else the local door asks applies just as much to a visitor: an event
     * that takes no registrations has no list to join, a date the appointment does not fall on, or
     * that was called off on its own or with its whole series, is not one to join, and a deadline that
     * has passed has passed for everybody. Without these the host's list filled up with people its own
     * door would have turned away.
     *
     * <p>The date is asked the way the local door asks it, and only asked: what a partner names is
     * still what the registration is filed under, so the wire format stays as it was.
     *
     * <p>There is no equivalent of the eligibility check. Restrictions are written in terms of this
     * station's members and groups, and a visitor is in none of them; the host said who may come when
     * it chose whom to share with.
     */
    private void requireOpenForRegistration(StationEvent event, LocalDate eventDate) {
        if (!event.requiresRegistration()) {
            throw new BadRequestResponse("Event does not require registration");
        }
        occurrenceCalendar.dateToAnswerFor(event, eventDate);
        if (event.registrationDeadline() != null && Instant.now().isAfter(event.registrationDeadline())) {
            throw new BadRequestResponse("Registration has closed; ask whoever runs the event");
        }
    }

    public List<EventFederationRegistration> findRegistrationsByRemoteMember(UUID remoteMemberId) {
        return federationRepository.findRegistrationsByRemoteMember(remoteMemberId);
    }

    /**
     * Every partner station's appointment these members stand on, asked of each partner.
     *
     * <p>Each partner answers only the places it keeps for this station and drops the ones that were
     * taken back, or a member who signed off a partner's appointment would find themselves back on it
     * at the next reload, with no way to sign up again. A partner that does not answer loses only its
     * own entries.
     */
    public List<RemoteEventRoutes.RemoteMemberRegistration> findMyRegistrations(int stationId, List<UUID> memberUids) {
        var partners = partnerRepository.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();
        return fanout.fanOut(partners, partner -> memberUids.stream()
                        .flatMap(uid -> transport
                                .getList(
                                        partner,
                                        RemoteEventRoutes.LIST_MEMBER_REGISTRATIONS.at(uid),
                                        RemoteEventRoutes.RemoteMemberRegistration.class)
                                .stream())
                        .toList())
                .items();
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
        if (federationRepository.withdrawRegistration(eventId, partnerId, remoteMemberId, eventDate)) {
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

    /** One partner's member on one date. */
    public Optional<EventFederationRegistration> findRegistration(
            int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        return federationRepository.findRegistration(eventId, partnerId, remoteMemberId, eventDate);
    }

    /** What a partner may do with a shared appointment: how many places, and who decides. */
    public EventPartnerPlaces partnerPlaces(int eventId, int partnerId) {
        return federationRepository.findPartnerPlaces(eventId, partnerId);
    }

    /** Everything said per partner about one appointment, for the screen that sets it. */
    public List<EventPartnerPlaces> partnerPlaces(int eventId) {
        return federationRepository.findPartnerPlaces(eventId);
    }

    /**
     * Hands a partner a number of places, or takes the arrangement back.
     *
     * <p>A budget means the partner decides: handing somebody five places and then choosing their
     * five for them is not a thing anybody wants, and the database refuses the combination outright.
     */
    public void setPartnerPlaces(int eventId, int partnerId, Integer slotBudget, boolean partnerConfirms) {
        federationRepository.setPartnerPlaces(eventId, partnerId, slotBudget, partnerConfirms);
        log.info(
                "Event {} now gives partner {} {} places, decided by {}",
                eventId,
                partnerId,
                slotBudget == null ? "as many as it likes" : slotBudget,
                partnerConfirms ? "the partner" : "this station");
    }

    /**
     * How many of a partner's places are taken on one date, and how many it may take.
     *
     * @return the places filled, and the budget, which is empty where there is no cap
     */
    public PartnerPlaceCount countPartnerPlaces(int eventId, int partnerId, LocalDate eventDate) {
        var places = federationRepository.findPartnerPlaces(eventId, partnerId);
        return new PartnerPlaceCount(
                federationRepository.countAcceptedForPartner(eventId, partnerId, eventDate),
                places.slotBudget(),
                places.partnerConfirms());
    }

    /**
     * Gives a partner's member one of that partner's places.
     *
     * <p>The budget is counted and spent in one statement, so two people at the partner confirming at
     * the same moment cannot both take the last place. Whoever asked, the count is this station's,
     * because the rows are.
     *
     * @return true where a place was granted, false where the budget was already spent
     */
    public boolean acceptWithinBudget(int registrationId, int eventId, int partnerId, LocalDate eventDate) {
        boolean granted = federationRepository.acceptWithinBudget(registrationId, eventId, partnerId, eventDate);
        if (!granted) {
            log.info("Partner {} has no places left on event {} for {}", partnerId, eventId, eventDate);
        }
        return granted;
    }

    /**
     * What a partner has and may have on one date.
     *
     * @param taken  how many places are filled
     * @param budget how many it may fill, or {@code null} for no cap
     * @param decidedByPartner whether the partner decides rather than this station
     */
    public record PartnerPlaceCount(int taken, Integer budget, boolean decidedByPartner) {}

    /**
     * Puts a partner's member back on the list, for as long as their withdrawal can be taken back.
     *
     * <p>Two instances keep two clocks, and a window either could measure is a window neither agrees
     * on. The row lives here, so this station's stamp decides and the partner only asks. A partner
     * whose clock is minutes out gets this station's answer either way, and its member is told what
     * this station said.
     *
     * @return true where the place was restored, false where the window had closed
     */
    public boolean undoWithdrawal(int eventId, int partnerId, UUID remoteMemberId, LocalDate eventDate) {
        boolean restored = federationRepository.restoreRegistration(
                eventId, partnerId, remoteMemberId, eventDate, EventRegistrationService.UNDO_WINDOW);
        if (restored) {
            log.info(
                    "Took back the withdrawal of a federated registration for event {} from partner {} on {}",
                    eventId,
                    partnerId,
                    eventDate);
            return true;
        }
        log.info(
                "A federated withdrawal for event {} from partner {} on {} can no longer be taken back",
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
        var partners = federationService.findPartners(stationId).stream()
                .filter(p -> p.status() == FederationStatus.ACTIVE)
                .toList();
        return fanout.fanOut(partners, this::browsePartner).items();
    }

    /**
     * One partner station's appointment, as that station describes it.
     *
     * <p>What comes back is the same shape either way: the appointment, the questions it asks openly,
     * and the places it set aside for us where it set any aside. A partner on this instance is read
     * here and a remote one answers for itself, and both have to say the same things, or a member
     * would find the choosing handed to their station on one and not on the other.
     *
     * <p>The places hang off the holder's own record of us, never ours of them, which is what the
     * serving function reads.
     */
    public RemoteEventRoutes.RemoteEventDetail getFederatedEvent(
            int localStationId, UUID partnerStationUid, int eventId) {
        var partner = entityResolver.requireActivePartner(localStationId, partnerStationUid);
        return transport.get(
                partner, RemoteEventRoutes.GET_EVENT.at(eventId), RemoteEventRoutes.RemoteEventDetail.class);
    }

    /**
     * The files a partner's event hands over, as that partner answers them.
     *
     * <p>What comes back is what the owning station is willing to hand out: the open files and
     * nothing else. This instance does not filter again, because it is not the one that knows.
     */
    public List<RemoteEventRoutes.RemoteAttachment> listFederatedAttachments(
            int localStationId, UUID partnerStationUid, int eventId) {
        var partner = entityResolver.requireActivePartner(localStationId, partnerStationUid);
        return transport.getList(
                partner, RemoteEventRoutes.LIST_ATTACHMENTS.at(eventId), RemoteEventRoutes.RemoteAttachment.class);
    }

    /**
     * One such file, bytes and all, as the owning station hands it over.
     */
    public RemoteEventRoutes.RemoteAttachmentContent getFederatedAttachment(
            int localStationId, UUID partnerStationUid, int eventId, int attachmentId) {
        var partner = entityResolver.requireActivePartner(localStationId, partnerStationUid);
        return transport.get(
                partner,
                RemoteEventRoutes.GET_ATTACHMENT_CONTENT.at(eventId, attachmentId),
                RemoteEventRoutes.RemoteAttachmentContent.class);
    }

    // -- Serving partners --

    /**
     * The appointments this station shares with a partner.
     *
     * @param partner the partnership the request arrived on
     * @return each shared appointment as a partner may see it
     */
    public List<SharedEvent> serveEvents(ServingPartner partner) {
        return sharedWith(partner).stream()
                .map(crudService::findById)
                .flatMap(Optional::stream)
                .map(SharedEvent::of)
                .toList();
    }

    /**
     * One shared appointment with the questions it asks openly and the places it set aside for the
     * partner, where it set any aside.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment
     * @return the appointment as the partner may see it
     */
    public RemoteEventRoutes.RemoteEventDetail serveEvent(ServingPartner partner, int eventId) {
        requireShared(partner, eventId);
        var event = crudService.findById(eventId).orElseThrow(Refusal.SHARED_EVENT_NOT_HERE::raise);
        var fields = fieldService
                .findByEvent(eventId, occurrenceCalendar.dateInView(event).orElse(null))
                .stream()
                .filter(EventField::isPublic)
                .toList();
        var places = partnerPlaces(eventId, partner.partnerId());
        return new RemoteEventRoutes.RemoteEventDetail(
                SharedEvent.of(event),
                fields,
                places.partnerConfirms() ? new RemoteEventRoutes.RemotePlaces(places.slotBudget(), true) : null);
    }

    /**
     * The open files of a shared appointment.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment
     * @return the files it hands over
     */
    public List<RemoteEventRoutes.RemoteAttachment> serveAttachments(ServingPartner partner, int eventId) {
        requireShared(partner, eventId);
        return attachmentService.listOpen(eventId).stream()
                .map(RemoteEventRoutes.RemoteAttachment::of)
                .toList();
    }

    /**
     * One open file of a shared appointment, encoded into the answer.
     *
     * <p>The same two questions are asked here as at home: is this appointment shared with the
     * partner asking, and is the file one it hands out at all. A file kept back is answered as absent
     * rather than refused, so asking for one by id says no more than asking for a file that is gone.
     *
     * @param partner      the partnership the request arrived on
     * @param eventId      the appointment
     * @param attachmentId the file
     * @return the file with its bytes
     */
    public RemoteEventRoutes.RemoteAttachmentContent serveAttachmentContent(
            ServingPartner partner, int eventId, int attachmentId) {
        requireShared(partner, eventId);
        var attachment = attachmentService
                .find(attachmentId)
                .filter(found -> found.eventId() == eventId)
                .filter(found -> !found.internal())
                .orElseThrow(Refusal.SHARED_EVENT_FILE_NOT_HERE::raise);
        EventAttachmentService.requireSizeToTravel(attachment.fileSize(), apiConfig.maxUploadSizeBytes());
        var event = crudService.findById(eventId).orElseThrow(Refusal.EVENT_NOT_HERE_BEHIND_SHARED_FILE::raise);
        var file = media.read(event.stationId(), attachment.contentHash())
                .orElseThrow(Refusal.SHARED_EVENT_FILE_CONTENT_NOT_HERE::raise);
        return new RemoteEventRoutes.RemoteAttachmentContent(
                attachment.id(),
                attachment.displayName(),
                attachment.fileName(),
                file.contentType(),
                Base64.getEncoder().encodeToString(file.data()));
    }

    /**
     * A partner's member signing up for a shared appointment, filed under this station's record of
     * the partner.
     */
    private EventFederationRegistration serveRegistration(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        requireShared(partner, eventId);
        return registerFederated(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate());
    }

    private void serveWithdrawal(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        requireShared(partner, eventId);
        withdrawRegistration(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate());
    }

    /**
     * A partner asking for one of its members to be put back after a withdrawal.
     *
     * <p>Whether it is still possible is this station's to answer, because this station holds the
     * row and the clock that measures the window. A refusal here is not a failure: it means the
     * few minutes have passed, and the partner tells its member so.
     */
    private void serveUndoWithdrawal(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        requireShared(partner, eventId);
        if (!undoWithdrawal(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate())) {
            throw Refusal.PARTNER_WITHDRAWAL_NO_LONGER_UNDONE.raise();
        }
    }

    /**
     * A partner confirming one of its own members, where this station handed it that decision.
     *
     * <p>Two refusals, and they say different things. Without an arrangement the partner is asking for
     * something it was never given, which is forbidden. With one, but with its places already filled,
     * the answer is that there is no room, which is an ordinary thing to be told and not a fault.
     */
    private void serveConfirmOwn(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteRegistrationRequest request) {
        requireShared(partner, eventId);
        if (!partnerPlaces(eventId, partner.partnerId()).partnerConfirms()) {
            throw Refusal.PARTNER_DOES_NOT_CONFIRM_ITS_OWN.raise();
        }
        var registration = findRegistration(eventId, partner.partnerId(), request.remoteMemberId(), request.eventDate())
                .orElseThrow(Refusal.PARTNER_REGISTRATION_NOT_HERE::raise);
        if (!acceptWithinBudget(registration.id(), eventId, partner.partnerId(), request.eventDate())) {
            throw Refusal.NO_PLACES_LEFT_FOR_PARTNER.raise();
        }
    }

    /**
     * Who from this partner is coming, which is not the same as who has a row.
     *
     * <p>A withdrawal keeps its row, so the refusals are filtered out here rather than sent. A partner
     * reading this list treats a row as somebody coming, and an older one has never heard of a
     * withdrawn status at all.
     */
    private List<EventFederationRegistration> serveRegistrations(ServingPartner partner, int eventId) {
        requireShared(partner, eventId);
        return findRegistrationsByPartner(partner.partnerId()).stream()
                .filter(r -> r.eventId() == eventId)
                .filter(EventFederationRegistration::isStanding)
                .toList();
    }

    /**
     * The appointments of this station one of the partner's members stands on.
     *
     * @param partner   the partnership the request arrived on
     * @param memberUid the partner's member
     * @return the standing registrations of that member here
     */
    public List<RemoteEventRoutes.RemoteMemberRegistration> serveMemberRegistrations(
            ServingPartner partner, UUID memberUid) {
        return findRegistrationsByRemoteMember(memberUid).stream()
                .filter(r -> r.partnerId() == partner.partnerId())
                .filter(EventFederationRegistration::isStanding)
                .map(r -> new RemoteEventRoutes.RemoteMemberRegistration(
                        r.eventId(),
                        r.remoteMemberId().toString(),
                        r.eventDate().toString(),
                        r.status(),
                        r.partnerId()))
                .toList();
    }

    private CommentResponse serveNewComment(
            ServingPartner partner, int eventId, RemoteEventRoutes.RemoteCommentRequest request) {
        requireShared(partner, eventId);
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.PARTNER_COMMENT_NEEDS_TEXT.raise();
        }
        return createRemoteComment(
                partner.row(),
                eventId,
                request.remoteMemberUid(),
                request.displayName(),
                request.parentId(),
                request.content(),
                commentDay(request.eventDate()));
    }

    /**
     * Reads the optional occurrence date a comment is scoped to. Older peers omit the field
     * entirely, which keeps the comment attached to the whole event rather than one date.
     */
    private static LocalDate commentDay(String eventDate) {
        if (eventDate == null || eventDate.isBlank()) return null;
        try {
            return LocalDate.parse(eventDate);
        } catch (DateTimeParseException e) {
            throw Refusal.PARTNER_COMMENT_DAY_NOT_A_DATE.raise();
        }
    }

    private CommentResponse serveCommentEdit(
            ServingPartner partner, int commentId, RemoteEventRoutes.RemoteCommentUpdateRequest request) {
        if (request.content() == null || request.content().isBlank()) {
            throw Refusal.PARTNER_COMMENT_CHANGE_NEEDS_TEXT.raise();
        }
        return updateRemoteComment(partner.row(), commentId, request.remoteMemberUid(), request.content());
    }

    private void serveCommentDeletion(
            ServingPartner partner, int commentId, RemoteEventRoutes.RemoteCommentDeleteRequest request) {
        if (!deleteRemoteComment(partner.row(), commentId, request.remoteMemberUid())) {
            throw Refusal.PARTNER_COMMENT_NOT_DELETED.raise();
        }
    }

    /**
     * Confirms the partner is allowed to see the given event, i.e. it is in the set this station
     * shares with that partner. Guards every read and write so a partner cannot address
     * never-federated events by enumerating ids.
     */
    private void requireShared(ServingPartner partner, int eventId) {
        if (!sharedWith(partner).contains(eventId)) {
            throw Refusal.EVENT_NOT_SHARED_WITH_PARTNER.raise();
        }
    }

    private List<Integer> sharedWith(ServingPartner partner) {
        return findSharedEventIds(partner.partnerId(), partner.servingStationId());
    }

    /**
     * Registers the serving functions of the appointments this station shares.
     */
    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.serve(RemoteEventRoutes.LIST_EVENTS, (partner, params, body) -> serveEvents(partner));
        endpoints.serve(
                RemoteEventRoutes.GET_EVENT, (partner, params, body) -> serveEvent(partner, params.integer("id")));
        endpoints.serve(
                RemoteEventRoutes.LIST_ATTACHMENTS,
                (partner, params, body) -> serveAttachments(partner, params.integer("eventId")));
        endpoints.serve(
                RemoteEventRoutes.GET_ATTACHMENT_CONTENT,
                (partner, params, body) ->
                        serveAttachmentContent(partner, params.integer("eventId"), params.integer("attachmentId")));
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, EventFederationRegistration>serve(
                RemoteEventRoutes.REGISTER,
                (partner, params, body) -> serveRegistration(partner, params.integer("id"), body));
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, Void>serve(
                RemoteEventRoutes.WITHDRAW, (partner, params, body) -> {
                    serveWithdrawal(partner, params.integer("id"), body);
                    return null;
                });
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, Void>serve(
                RemoteEventRoutes.UNDO_WITHDRAWAL, (partner, params, body) -> {
                    serveUndoWithdrawal(partner, params.integer("id"), body);
                    return null;
                });
        endpoints.<RemoteEventRoutes.RemoteRegistrationRequest, Void>serve(
                RemoteEventRoutes.CONFIRM_OWN, (partner, params, body) -> {
                    serveConfirmOwn(partner, params.integer("id"), body);
                    return null;
                });
        endpoints.serve(
                RemoteEventRoutes.LIST_REGISTRATIONS,
                (partner, params, body) -> serveRegistrations(partner, params.integer("id")));
        endpoints.serve(
                RemoteEventRoutes.LIST_MEMBER_REGISTRATIONS,
                (partner, params, body) -> serveMemberRegistrations(partner, params.uuid("memberUid")));
        endpoints.serve(
                RemoteEventRoutes.REGISTRATION_STATUS_WEBHOOK,
                (partner, params, body) -> new FederatedEventRoutes.StatusResponse("ok"));
        endpoints.serve(RemoteEventRoutes.LIST_COMMENTS, (partner, params, body) -> {
            requireShared(partner, params.integer("eventId"));
            return listComments(params.integer("eventId"));
        });
        endpoints.<RemoteEventRoutes.RemoteCommentRequest, CommentResponse>serve(
                RemoteEventRoutes.CREATE_COMMENT,
                (partner, params, body) -> serveNewComment(partner, params.integer("eventId"), body));
        endpoints.<RemoteEventRoutes.RemoteCommentUpdateRequest, CommentResponse>serve(
                RemoteEventRoutes.UPDATE_COMMENT,
                (partner, params, body) -> serveCommentEdit(partner, params.integer("commentId"), body));
        endpoints.<RemoteEventRoutes.RemoteCommentDeleteRequest, Void>serve(
                RemoteEventRoutes.DELETE_COMMENT, (partner, params, body) -> {
                    serveCommentDeletion(partner, params.integer("commentId"), body);
                    return null;
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
        var comment = commentRepository.create(CommentEntityType.EVENT, eventId, eventDate, parentId, author, content);
        federationRepository.cacheName(partner.id(), remoteMemberUid, displayName);
        log.info("Comment {} created on event {} (partner {})", comment.id(), eventId, partner.id());
        return toCommentResponse(comment);
    }

    /**
     * Updates a comment from a remote federated partner after verifying ownership.
     */
    public CommentResponse updateRemoteComment(
            FederationPartner partner, int commentId, UUID remoteMemberUid, String content) {
        requireCommentAuthor(commentId, partner, remoteMemberUid, "edit");
        commentRepository.update(CommentEntityType.EVENT, commentId, content);
        log.info("Comment {} on an event edited (partner {})", commentId, partner.id());
        var updated = commentService.findById(commentId).orElseThrow(NotFoundResponse::new);
        return toCommentResponse(updated);
    }

    /**
     * Deletes a comment from a remote federated partner after verifying ownership.
     */
    public boolean deleteRemoteComment(FederationPartner partner, int commentId, UUID remoteMemberUid) {
        requireCommentAuthor(commentId, partner, remoteMemberUid, "delete");
        boolean deleted = commentService.delete(commentId);
        if (deleted) log.info("Comment {} on an event deleted (partner {})", commentId, partner.id());
        else log.warn("Delete for event comment {} affected zero rows", commentId);
        return deleted;
    }

    /**
     * Lists the comments on a partner's appointment, as the partner answers them.
     */
    public List<CommentResponse> listFederatedComments(int stationId, UUID partnerStationUid, int eventId) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        return transport.getList(partner, RemoteEventRoutes.LIST_COMMENTS.at(eventId), CommentResponse.class);
    }

    /**
     * Comments on a partner's appointment as one of this station's members.
     */
    public CommentResponse createFederatedComment(
            int stationId,
            UUID partnerStationUid,
            int eventId,
            UUID memberUid,
            String displayName,
            Integer parentId,
            String content,
            LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var body = new RemoteEventRoutes.RemoteCommentRequest(
                memberUid, displayName, parentId, content, eventDate != null ? eventDate.toString() : null);
        var created =
                transport.send(partner, RemoteEventRoutes.CREATE_COMMENT.at(eventId), body, CommentResponse.class);
        log.info("Station {} commented on event {} at partner {}", stationId, eventId, partner.id());
        return created;
    }

    /**
     * Edits a comment one of this station's members wrote on a partner's appointment.
     */
    public CommentResponse updateFederatedComment(
            int stationId, UUID partnerStationUid, int commentId, UUID memberUid, String content) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var updated = transport.send(
                partner,
                RemoteEventRoutes.UPDATE_COMMENT.at(commentId),
                new RemoteEventRoutes.RemoteCommentUpdateRequest(memberUid, content),
                CommentResponse.class);
        log.info("Station {} edited its comment {} at partner {}", stationId, commentId, partner.id());
        return updated;
    }

    /**
     * Deletes a comment one of this station's members wrote on a partner's appointment.
     */
    public void deleteFederatedComment(int stationId, UUID partnerStationUid, int commentId, UUID memberUid) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        transport.send(
                partner,
                RemoteEventRoutes.DELETE_COMMENT.at(commentId),
                new RemoteEventRoutes.RemoteCommentDeleteRequest(memberUid),
                Void.class);
        log.info("Station {} deleted its comment {} at partner {}", stationId, commentId, partner.id());
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

    /**
     * Registers one of our members for a partner's appointment, and reports what they said about it.
     *
     * <p>The status is theirs to decide and worth carrying back: an appointment that asks for no
     * confirmation accepts at once, and telling our own member they are waiting for one would be
     * telling them something the other station never said. A partner that cannot be reached is a
     * registration that was not taken.
     *
     * @return the status the partner recorded
     */
    public RegistrationStatus registerForFederatedEvent(
            int stationId, UUID partnerStationUid, int eventId, UUID remoteMemberId, LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var registered = answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventRoutes.REGISTER.at(eventId),
                        new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate),
                        EventFederationRegistration.class),
                Refusal.FEDERATED_REGISTRATION_NOT_TAKEN);
        log.info(
                "Station {} registered a member for event {} at partner {} as {}",
                stationId,
                eventId,
                partner.id(),
                registered.status());
        return registered.status();
    }

    /**
     * Gives up a place one of our members held at a partner's appointment. A partner that cannot be
     * reached is only noted, as the member's own list no longer shows the place either way.
     */
    public void withdrawFederatedRegistration(
            int stationId, UUID partnerStationUid, int eventId, UUID remoteMemberId, LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        try {
            transport.send(
                    partner,
                    RemoteEventRoutes.WITHDRAW.at(eventId),
                    new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate),
                    Void.class);
            log.info("Station {} withdrew a registration for event {} at partner {}", stationId, eventId, partner.id());
        } catch (RefusalResponse e) {
            if (e.refusal() != Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER) throw e;
            log.warn("Withdrawal for event {} at partner {} did not arrive", eventId, partner.id());
        }
    }

    /**
     * Confirms one of our own members for a partner's appointment, where they handed us that decision.
     *
     * <p>The places are theirs and so is the counting, so this asks rather than decides. A refusal
     * means either that they never handed the decision over or that the places they gave are full,
     * and both are things to tell the person pressing the button rather than retry.
     */
    public void confirmOwnFederatedMember(
            int stationId, UUID partnerStationUid, int eventId, UUID remoteMemberId, LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventRoutes.CONFIRM_OWN.at(eventId),
                        new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate),
                        Void.class),
                Refusal.NO_PLACES_LEFT_AT_HOLDER);
        log.info("Station {} confirmed one of its own for event {} at partner {}", stationId, eventId, partner.id());
    }

    /**
     * Asks the station that holds the appointment to put one of our members back after a withdrawal.
     *
     * <p>Their clock decides, not ours: two instances keep two clocks, and the row is theirs. A
     * refusal here is the ordinary answer once the few minutes have passed.
     */
    public void undoFederatedWithdrawal(
            int stationId, UUID partnerStationUid, int eventId, UUID remoteMemberId, LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventRoutes.UNDO_WITHDRAWAL.at(eventId),
                        new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate),
                        Void.class),
                Refusal.FEDERATED_WITHDRAWAL_NO_LONGER_UNDONE);
        log.info("Station {} took a withdrawal back for event {} at partner {}", stationId, eventId, partner.id());
    }

    /**
     * Runs a call to a partner and names a partner that did not answer in the words of the feature.
     * A partner on another instance answers every refusal with silence, so what reaches the reader
     * is the sentence this station had for it; a partner on this instance says what it refused.
     */
    private static <T> T answeredOr(Supplier<T> call, Refusal unanswered) {
        try {
            return call.get();
        } catch (RefusalResponse e) {
            if (e.refusal() == Refusal.FEDERATION_PARTNER_DID_NOT_ANSWER) throw unanswered.raise();
            throw e;
        }
    }

    private List<FederatedEventItem> browsePartner(FederationPartner partner) {
        String name = FederationDisplayNames.partnerName(stationRepository, partner, "?");
        return transport.getList(partner, RemoteEventRoutes.LIST_EVENTS.at(), SharedEvent.class).stream()
                .map(event -> new FederatedEventItem(
                        partner.id(), name, partner.partnerStationId().toString(), event))
                .toList();
    }

    public record FederatedEventItem(
            int partnerId, String partnerStationName, String partnerStationUid, SharedEvent event) {}
}
