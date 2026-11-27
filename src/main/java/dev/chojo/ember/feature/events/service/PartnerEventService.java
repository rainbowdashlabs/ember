/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.api.refusal.FederationRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventRegistrationFieldValue;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.SharedEvent;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventRoutes.RemoteRegistrationStatus;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationPartner.FederationStatus;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PartnerCalls;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.signing.repository.PartnerSigningRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The partner side of shared appointments: how this station's members read, join and leave the
 * appointments partner stations share with it.
 *
 * <p>Every answer here is the partner's: this station asks the station holding the appointment through
 * the federation transport, whether that station sits on this instance or another one, and passes on
 * what it answered.
 *
 * <p>The station holding the appointment notifies this station when it decides about one of its members, or
 * when the member moves up from its waiting list. That is believed only for a member this station
 * signed up, taken in only where it is newer than what this station knows, and passed on to the member's
 * household only where the status changed. Confirming one of its own members notifies the household the same
 * way.
 */
@Singleton
public class PartnerEventService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(PartnerEventService.class);

    private final FederationService federationService;
    private final FederationTransport transport;
    private final FederationRepository partnerRepository;
    private final StationRepository stationRepository;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;
    private final PartnerSigningRepository signups;
    private final StationMemberRepository members;
    private final PartnerStatusNotices notices;

    @Inject
    public PartnerEventService(
            FederationService federationService,
            FederationTransport transport,
            FederationRepository partnerRepository,
            StationRepository stationRepository,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            PartnerSigningRepository signups,
            StationMemberRepository members,
            PartnerStatusNotices notices) {
        this.federationService = federationService;
        this.transport = transport;
        this.partnerRepository = partnerRepository;
        this.stationRepository = stationRepository;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
        this.signups = signups;
        this.members = members;
        this.notices = notices;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteRegistrationStatus, Void>serve(
                RemoteEventRoutes.REGISTRATION_STATUS_WEBHOOK, (partner, params, body) -> {
                    statusChanged(partner, body);
                    return null;
                });
    }

    /**
     * Takes in a decision a partner sent about one of this station's members and lets their household know
     * where the status changed.
     */
    private void statusChanged(ServingPartner partner, RemoteRegistrationStatus told) {
        var holder = partner.askingStationUid();
        var member = members.findByUid(partner.row().stationId(), told.memberUid())
                .filter(found -> signups.signedUp(found.id(), holder, told.eventId(), told.eventDate()))
                .orElse(null);
        if (member == null) return;
        var before = signups.knownStatus(member.id(), holder, told.eventId(), told.eventDate());
        if (!signups.learnStatus(
                member.id(), holder, told.eventId(), told.eventDate(), told.status(), told.changedAt())) {
            return;
        }
        if (before.filter(told.status()::equals).isPresent()) return;
        notices.tell(
                member.id(),
                told.eventName(),
                told.status(),
                told.queuePosition(),
                told.movedUp(),
                NotificationLinks.federatedEvent(holder, told.eventId()));
        log.info("Partner {} decided about member {} on event {}", holder, member.id(), told.eventId());
    }

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
     * here and a remote one answers for itself, and both have to report the same things, or a member
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
     * Registers one of our members for a partner's appointment, and reports what they answered.
     *
     * <p>The status is theirs to decide and worth carrying back: an appointment that asks for no
     * confirmation accepts at once, and showing our own member they are waiting for one would be
     * showing them something the other station never sent. A partner that cannot be reached is a
     * registration that was not taken.
     *
     * @param answers the member's answers to the questions the partner asks, empty where it asks none
     * @return the status the partner recorded
     */
    public RegistrationStatus registerForFederatedEvent(
            int stationId,
            UUID partnerStationUid,
            int eventId,
            UUID remoteMemberId,
            LocalDate eventDate,
            List<EventRegistrationFieldValue> answers) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var registered = PartnerCalls.answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventRoutes.REGISTER.at(eventId),
                        new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate, answers),
                        EventFederationRegistration.class),
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
        log.info(
                "Station {} registered a member for event {} at partner {} as {}",
                stationId,
                eventId,
                partner.id(),
                registered.status());
        members.findByUid(stationId, remoteMemberId)
                .ifPresent(member -> signups.recordStatus(
                        stationId, member.id(), partnerStationUid, eventId, eventDate, registered.status()));
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
            transport.deliver(
                    partner,
                    RemoteEventRoutes.WITHDRAW.at(eventId),
                    new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate));
            log.info("Station {} withdrew a registration for event {} at partner {}", stationId, eventId, partner.id());
        } catch (RefusalResponse e) {
            if (e.refusal() != FederationRefusal.FEDERATION_PARTNER_DID_NOT_ANSWER) throw e;
            log.warn("Withdrawal for event {} at partner {} did not arrive", eventId, partner.id());
        }
    }

    /**
     * Confirms one of our own members for a partner's appointment, where they handed us that decision.
     *
     * <p>The places are theirs and so is the counting, so this asks rather than decides. A refusal
     * means either that they never handed the decision over or that the places they gave are full,
     * and both are things to show the person pressing the button rather than retry.
     */
    public void confirmOwnFederatedMember(
            int stationId, UUID partnerStationUid, int eventId, UUID remoteMemberId, LocalDate eventDate) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var confirmed = PartnerCalls.answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventRoutes.CONFIRM_OWN.at(eventId),
                        new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate),
                        RemoteEventRoutes.RemoteConfirmedOwn.class),
                EventRefusal.NO_PLACES_LEFT_AT_HOLDER);
        log.info("Station {} confirmed one of its own for event {} at partner {}", stationId, eventId, partner.id());
        tellConfirmed(stationId, partnerStationUid, eventId, remoteMemberId, eventDate, confirmed);
    }

    /** Records where a member this station confirmed stands now, and lets their household know where it changed. */
    private void tellConfirmed(
            int stationId,
            UUID partnerStationUid,
            int eventId,
            UUID memberUid,
            LocalDate eventDate,
            RemoteEventRoutes.RemoteConfirmedOwn confirmed) {
        var member = members.findByUid(stationId, memberUid).orElse(null);
        if (member == null) return;
        var before = signups.knownStatus(member.id(), partnerStationUid, eventId, eventDate);
        signups.recordStatus(stationId, member.id(), partnerStationUid, eventId, eventDate, confirmed.status());
        if (before.filter(confirmed.status()::equals).isPresent()) return;
        notices.tell(
                member.id(),
                confirmed.eventName(),
                confirmed.status(),
                null,
                false,
                NotificationLinks.federatedEvent(partnerStationUid, eventId));
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
        PartnerCalls.deliveredOr(
                () -> transport.deliver(
                        partner,
                        RemoteEventRoutes.UNDO_WITHDRAWAL.at(eventId),
                        new RemoteEventRoutes.RemoteRegistrationRequest(remoteMemberId, eventDate)),
                EventRefusal.FEDERATED_WITHDRAWAL_NO_LONGER_UNDONE);
        log.info("Station {} took a withdrawal back for event {} at partner {}", stationId, eventId, partner.id());
    }

    private List<FederatedEventItem> browsePartner(FederationPartner partner) {
        String name = FederationDisplayNames.partnerName(stationRepository, partner, "?");
        return transport.getList(partner, RemoteEventRoutes.LIST_EVENTS.at(), SharedEvent.class).stream()
                .map(event -> new FederatedEventItem(
                        partner.id(), name, partner.partnerStationId().toString(), event))
                .toList();
    }

    /**
     * One appointment a partner shares with this station, with the partner that shares it.
     *
     * @param partnerId          this station's record of the partner
     * @param partnerStationName the partner station's name
     * @param partnerStationUid  the partner station's id
     * @param event              the appointment as the partner describes it
     */
    public record FederatedEventItem(
            int partnerId, String partnerStationName, String partnerStationUid, SharedEvent event) {}
}
