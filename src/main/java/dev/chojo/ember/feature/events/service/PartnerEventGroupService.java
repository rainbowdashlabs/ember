/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.repository.PartnerEventGroupSignupRepository;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupCancelled;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupConfirmed;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupDetail;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupMember;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupQuery;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupSignUpRequest;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupSignUpStatus;
import dev.chojo.ember.feature.events.service.EventGroupPartnerService.PartnerStanding;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.transport.FederationEndpoints;
import dev.chojo.ember.feature.federation.transport.FederationServer;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.PartnerCalls;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.notifications.entity.Delivery;
import dev.chojo.ember.feature.notifications.entity.NotificationData;
import dev.chojo.ember.feature.notifications.entity.NotificationLinks;
import dev.chojo.ember.feature.notifications.entity.NotificationParams;
import dev.chojo.ember.feature.notifications.entity.NotificationType;
import dev.chojo.ember.feature.notifications.entity.StationAudience;
import dev.chojo.ember.feature.notifications.service.Notifier;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * This station's members and the event groups partner stations share with it: reading an event group and
 * signing up, changing parts and withdrawing, each asked of the station that holds the event group.
 * Whether the partner lives on this instance or another is the transport's to know.
 *
 * <p>This station notes which of its members it signed up, and believes a partner's news about the
 * event group only for those.
 */
@Singleton
public class PartnerEventGroupService implements FederationServer {
    private static final Logger log = LoggerFactory.getLogger(PartnerEventGroupService.class);

    private final FederationTransport transport;
    private final FederationEntityResolver entityResolver;
    private final PartnerEventGroupSignupRepository signedUp;
    private final StationMemberRepository members;
    private final Notifier notifier;
    private final PartnerStatusNotices notices;

    @Inject
    public PartnerEventGroupService(
            FederationTransport transport,
            FederationEntityResolver entityResolver,
            PartnerEventGroupSignupRepository signedUp,
            StationMemberRepository members,
            Notifier notifier,
            PartnerStatusNotices notices) {
        this.transport = transport;
        this.entityResolver = entityResolver;
        this.signedUp = signedUp;
        this.members = members;
        this.notifier = notifier;
        this.notices = notices;
    }

    @Override
    public void serveOn(FederationEndpoints endpoints) {
        endpoints.<RemoteEventGroupCancelled, Void>serve(
                RemoteEventGroupRoutes.EVENT_GROUP_CANCELLED_WEBHOOK, (partner, params, body) -> {
                    cancelled(partner, body);
                    return null;
                });
        endpoints.<RemoteEventGroupSignUpStatus, Void>serve(
                RemoteEventGroupRoutes.EVENT_GROUP_SIGN_UP_STATUS_WEBHOOK, (partner, params, body) -> {
                    statusChanged(partner, body);
                    return null;
                });
    }

    /**
     * Takes in a decision a partner sent about one of this station's members' event group sign-ups and
     * lets their household know where the status changed. Believed only for a member this station signed up,
     * and only where it is newer than what this station knows.
     */
    private void statusChanged(ServingPartner partner, RemoteEventGroupSignUpStatus told) {
        var holder = partner.askingStationUid();
        var member = members.findByUid(partner.row().stationId(), told.memberUid())
                .filter(found -> signedUp.signedUp(found.id(), holder, told.eventGroupId()))
                .orElse(null);
        if (member == null) return;
        var before = signedUp.knownStatus(member.id(), holder, told.eventGroupId());
        if (!signedUp.learnStatus(member.id(), holder, told.eventGroupId(), told.status(), told.changedAt())) return;
        if (before.filter(told.status()::equals).isPresent()) return;
        notices.tell(
                member.id(),
                told.eventGroupName(),
                told.status(),
                told.queuePosition(),
                told.movedUp(),
                NotificationLinks.federatedEventGroup(holder, told.eventGroupId()));
        log.info("Partner {} decided about member {} on event group {}", holder, member.id(), told.eventGroupId());
    }

    /**
     * Notifies this station's members a partner named that the partner's event group was called off. Only
     * members this station signed up for that event group are notified.
     */
    private void cancelled(ServingPartner partner, RemoteEventGroupCancelled cancelled) {
        int stationId = partner.row().stationId();
        var told = cancelled.memberUids().stream()
                .map(uid -> members.findByUid(stationId, uid))
                .flatMap(Optional::stream)
                .map(StationMember::id)
                .filter(memberId -> signedUp.signedUp(memberId, partner.askingStationUid(), cancelled.eventGroupId()))
                .toList();
        if (told.isEmpty()) return;
        notifier.notify(
                StationAudience.household(told),
                NotificationType.EVENT_CANCELLED,
                NotificationData.of(
                        new NotificationParams.EventCancelled(
                                cancelled.eventGroupName(), cancelled.reason(), null, null, true),
                        NotificationLinks.federatedEventGroup(partner.askingStationUid(), cancelled.eventGroupId())),
                Delivery.EVERY_TIME);
        log.info(
                "Partner {} called off event group {}, {} member(s) told",
                partner.askingStationUid(),
                cancelled.eventGroupId(),
                told.size());
    }

    /**
     * A partner's event group with the standing of the given members of this station. A member the partner
     * holds a place for is noted as signed up from here, so a sign-up whose answer was lost on the
     * way still hears when the event group is called off.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId          the event group
     * @param household         the members asked about, by the id partners know them by
     * @return the event group as the partner shows it
     */
    public RemoteEventGroupDetail eventGroup(
            int stationId, UUID partnerStationUid, int eventGroupId, Map<UUID, Integer> household) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var detail = PartnerCalls.answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventGroupRoutes.GET_EVENT_GROUP.at(eventGroupId),
                        new RemoteEventGroupQuery(List.copyOf(household.keySet())),
                        RemoteEventGroupDetail.class),
                EventRefusal.EVENT_GROUP_NOT_HERE);
        for (var standing : detail.standings()) {
            var memberId = household.get(standing.memberUid());
            var status = standing.status();
            if (memberId != null && status != null && status.standing()) {
                signedUp.recordStatus(memberId, partnerStationUid, eventGroupId, status);
            }
        }
        return detail;
    }

    /**
     * Signs a member of this station up for a partner's event group.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId          the event group
     * @param member            the member
     * @param parts             the chosen parts
     * @return where the member stands, as the partner recorded it
     */
    public PartnerStanding signUp(
            int stationId, UUID partnerStationUid, int eventGroupId, StationMember member, Set<Integer> parts) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var standing = PartnerCalls.answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventGroupRoutes.SIGN_UP.at(eventGroupId),
                        new RemoteEventGroupSignUpRequest(member.uid(), List.copyOf(parts)),
                        PartnerStanding.class),
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
        var status = standing.status();
        if (status != null) {
            signedUp.recordStatus(member.id(), partnerStationUid, eventGroupId, status);
        } else {
            signedUp.record(member.id(), partnerStationUid, eventGroupId);
        }
        log.info(
                "Station {} signed a member up for event group {} at partner {}",
                stationId,
                eventGroupId,
                partner.id());
        return standing;
    }

    /**
     * Changes the parts a member of this station chose at a partner's event group.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId          the event group
     * @param memberUid         the member
     * @param parts             the parts chosen from now on
     * @return where the member stands, as the partner recorded it
     */
    public PartnerStanding changeParts(
            int stationId, UUID partnerStationUid, int eventGroupId, UUID memberUid, Set<Integer> parts) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        return PartnerCalls.answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventGroupRoutes.CHANGE_PARTS.at(eventGroupId),
                        new RemoteEventGroupSignUpRequest(memberUid, List.copyOf(parts)),
                        PartnerStanding.class),
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
    }

    /**
     * Confirms one of this station's members for a partner's event group, where the partner handed this
     * station the decision about its own members' sign-ups.
     *
     * <p>The places are the partner's and so is the counting, so this asks rather than decides. A refusal
     * means either that the partner never handed the decision over or that the places it gave are full,
     * and both are things to show the person pressing the button rather than retry.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId      the event group
     * @param memberUid         the member
     * @return where the member stands, as the partner recorded it
     */
    public PartnerStanding confirm(int stationId, UUID partnerStationUid, int eventGroupId, UUID memberUid) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        var confirmed = PartnerCalls.answeredOr(
                () -> transport.send(
                        partner,
                        RemoteEventGroupRoutes.CONFIRM_OWN.at(eventGroupId),
                        new RemoteEventGroupMember(memberUid),
                        RemoteEventGroupConfirmed.class),
                EventRefusal.NO_PLACES_LEFT_AT_HOLDER);
        log.info(
                "Station {} confirmed one of its own for event group {} at partner {}",
                stationId,
                eventGroupId,
                partner.id());
        tellConfirmed(stationId, partnerStationUid, eventGroupId, confirmed);
        return confirmed.standing();
    }

    /** Records where a member this station confirmed stands now, and lets their household know where it changed. */
    private void tellConfirmed(
            int stationId, UUID partnerStationUid, int eventGroupId, RemoteEventGroupConfirmed confirmed) {
        var standing = confirmed.standing();
        var status = standing.status();
        var member = members.findByUid(stationId, standing.memberUid()).orElse(null);
        if (member == null || status == null) return;
        var before = signedUp.knownStatus(member.id(), partnerStationUid, eventGroupId);
        signedUp.recordStatus(member.id(), partnerStationUid, eventGroupId, status);
        if (before.filter(status::equals).isPresent()) return;
        notices.tell(
                member.id(),
                confirmed.eventGroupName(),
                status,
                standing.queuePosition(),
                false,
                NotificationLinks.federatedEventGroup(partnerStationUid, eventGroupId));
    }

    /**
     * Withdraws a member of this station from a partner's event group.
     *
     * @param stationId         this station
     * @param partnerStationUid the partner holding the event group
     * @param eventGroupId          the event group
     * @param member            the member
     */
    public void withdraw(int stationId, UUID partnerStationUid, int eventGroupId, StationMember member) {
        var partner = entityResolver.requireActivePartner(stationId, partnerStationUid);
        PartnerCalls.deliveredOr(
                () -> transport.deliver(
                        partner,
                        RemoteEventGroupRoutes.WITHDRAW.at(eventGroupId),
                        new RemoteEventGroupMember(member.uid())),
                EventRefusal.FEDERATED_REGISTRATION_NOT_TAKEN);
        signedUp.forget(member.id(), partnerStationUid, eventGroupId);
        log.info(
                "Station {} withdrew a member from event group {} at partner {}",
                stationId,
                eventGroupId,
                partner.id());
    }
}
