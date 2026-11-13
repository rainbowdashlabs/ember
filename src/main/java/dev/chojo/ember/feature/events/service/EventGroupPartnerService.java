/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventFederationRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupPartnerRegistration;
import dev.chojo.ember.feature.events.entity.EventGroupShare;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.EventGroupPartnerRepository;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupCancelled;
import dev.chojo.ember.feature.events.service.EventGroupRegistrationService.Standing;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Event groups this station shares with partner stations, and the sign-ups partners' members send for
 * them.
 *
 * <p>Sharing an event group shares every part with the same partners, parts added later included, so a
 * partner sees the appointments it signs up for. A partner's member signs up for the event group as a
 * whole, through the same rules, limit, deadline and confirmation as a member of this station, and
 * every part they choose carries a partner registration tied to that sign-up. Partners answer no
 * event group questions, as they answer none for an appointment.
 */
@Singleton
public class EventGroupPartnerService {
    private static final Logger log = LoggerFactory.getLogger(EventGroupPartnerService.class);

    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService gate;
    private final EventGroupPartnerRepository partners;
    private final EventFederationRepository federation;
    private final OccurrenceCalendar calendar;
    private final EventRequirementService requirements;
    private final FederatedRegistrantService registrants;
    private final FederationRepository federationPartners;
    private final FederationTransport transport;

    @Inject
    public EventGroupPartnerService(
            EventGroupService eventGroups,
            EventGroupRegistrationService gate,
            EventGroupPartnerRepository partners,
            EventFederationRepository federation,
            OccurrenceCalendar calendar,
            EventRequirementService requirements,
            FederatedRegistrantService registrants,
            FederationRepository federationPartners,
            FederationTransport transport) {
        this.eventGroups = eventGroups;
        this.gate = gate;
        this.partners = partners;
        this.federation = federation;
        this.calendar = calendar;
        this.requirements = requirements;
        this.registrants = registrants;
        this.federationPartners = federationPartners;
        this.transport = transport;
    }

    /**
     * How an event group is shared.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @return the share, empty where the event group is not shared
     */
    public Optional<EventGroupShare> findShare(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        return partners.findShare(eventGroupId);
    }

    /**
     * Shares an event group with partners, and every part with it.
     *
     * <p>An event group whose audiences name anybody is not shared: they name groups, tags and members of
     * this station, which mean nothing at a partner, so the event group would stand open to everybody
     * there. Nor is one with a part whose documents partners could not sign alike.
     *
     * @param stationId  the station asking
     * @param eventGroupId   the event group
     * @param scope      every partner, or the ones named
     * @param partnerIds the partners named
     */
    public void share(int stationId, int eventGroupId, ShareScope scope, List<Integer> partnerIds) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        var targets = scope == ShareScope.SPECIFIC ? List.copyOf(partnerIds) : List.<Integer>of();
        Transactions.run(() -> {
            gate.lockEventGroup(stationId, eventGroupId);
            if (!eventGroups.viewAudience(eventGroupId).namesNobody()
                    || !eventGroups.registrationAudience(eventGroupId).namesNobody()) {
                throw EventRefusal.RESTRICTED_EVENT_NOT_SHARED.raise();
            }
            for (var part : eventGroups.findParts(eventGroupId)) {
                requirements.requireShareable(part.eventId());
                shareAppointment(part.eventId(), scope, targets);
            }
            partners.setShare(eventGroupId, scope, targets);
        });
        log.info("Event group {} shared with partners ({}, {} named)", eventGroupId, scope, targets.size());
    }

    /**
     * Stops sharing an event group and its parts. Partner sign-ups already made stay.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     */
    public void unshare(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        Transactions.run(() -> {
            gate.lockEventGroup(stationId, eventGroupId);
            for (var part : eventGroups.findParts(eventGroupId)) {
                federation.removeShare(part.eventId());
            }
            partners.removeShare(eventGroupId);
        });
        log.info("Event group {} no longer shared with partners", eventGroupId);
    }

    private void shareAppointment(int eventId, ShareScope scope, List<Integer> targets) {
        var share = federation.setShare(eventId, scope);
        federation.setShareTargets(share.id(), targets);
    }

    /**
     * The event groups of this station shared with a partner.
     *
     * @param partnerId this station's record of the partner
     * @param stationId this station
     * @return the ids of the shared event groups
     */
    public List<Integer> sharedEventGroupIds(int partnerId, int stationId) {
        return partners.findSharedEventGroupIds(partnerId, stationId);
    }

    /**
     * Whether a partner's member holds or waits for a place in an event group. A member who does may still
     * withdraw after the event group stopped being shared with their station, so no place stays taken that
     * nobody can give back.
     *
     * @param eventGroupId       the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @return true for a pending or accepted sign-up
     */
    public boolean holdsPlace(int eventGroupId, int partnerId, UUID remoteMemberId) {
        return partners.find(eventGroupId, partnerId, remoteMemberId)
                .filter(EventGroupPartnerRegistration::active)
                .isPresent();
    }

    /**
     * Signs a partner's member up for an event group with a choice of its parts.
     *
     * @param stationId      this station
     * @param eventGroupId       the event group
     * @param partnerId      the partner the member belongs to
     * @param remoteMemberId the member
     * @param partIds        the chosen parts
     * @return where the member stands now
     */
    public PartnerStanding register(
            int stationId, int eventGroupId, int partnerId, UUID remoteMemberId, Set<Integer> partIds) {
        Transactions.run(() -> {
            var eventGroup = gate.lockOpenEventGroup(stationId, eventGroupId, false);
            var existing = partners.find(eventGroupId, partnerId, remoteMemberId);
            if (existing.filter(EventGroupPartnerRegistration::active).isPresent()) {
                throw EventRefusal.EVENT_GROUP_ALREADY_SIGNED_UP.raise();
            }
            Integer limit = eventGroup.registrationLimit();
            if (limit != null && gate.holding(eventGroupId) >= limit) {
                throw EventRefusal.EVENT_GROUP_FULL.raise();
            }
            var parts = gate.lockParts(eventGroupId);
            var standing = Standing.of(stationId, parts, calendar);
            EventGroupRegistrationService.requireChoosable(partIds, parts.keySet(), standing, Set.of(), false);
            gate.requireRulesMet(eventGroupId, partIds, standing, false);
            var status = eventGroup.requiresConfirmation() ? RegistrationStatus.PENDING : RegistrationStatus.ACCEPTED;
            existing.ifPresent(earlier ->
                    partners.findLinked(earlier.id()).forEach(registration -> partners.unlink(registration.id())));
            var stored = partners.signUp(eventGroupId, partnerId, remoteMemberId, status);
            for (int partId : EventGroupRegistrationService.ascending(partIds)) {
                place(parts.get(partId), stored, status);
            }
        });
        log.info("A member of partner {} signed up for event group {}", partnerId, eventGroupId);
        return standing(eventGroupId, partnerId, remoteMemberId);
    }

    /**
     * Changes the parts a partner's member chose. A part already past cannot be given up.
     *
     * @param stationId      this station
     * @param eventGroupId       the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @param partIds        the parts chosen from now on
     * @return where the member stands now
     */
    public PartnerStanding changeParts(
            int stationId, int eventGroupId, int partnerId, UUID remoteMemberId, Set<Integer> partIds) {
        Transactions.run(() -> {
            gate.lockOpenEventGroup(stationId, eventGroupId, false);
            var current = requireActive(eventGroupId, partnerId, remoteMemberId);
            var parts = gate.lockParts(eventGroupId);
            var standing = Standing.of(stationId, parts, calendar);
            var held = heldParts(current);
            EventGroupRegistrationService.requireChoosable(partIds, parts.keySet(), standing, held.keySet(), false);
            var dropped = new HashSet<>(held.keySet());
            dropped.removeAll(partIds);
            if (dropped.stream().anyMatch(standing::tookPlace)) throw EventRefusal.EVENT_GROUP_PART_TOOK_PLACE.raise();
            gate.requireRulesMet(eventGroupId, partIds, standing, false);
            for (int partId : EventGroupRegistrationService.ascending(partIds)) {
                if (!held.containsKey(partId)) place(parts.get(partId), current, current.status());
            }
            for (int partId : dropped) {
                var registration = held.get(partId);
                partners.restate(registration.id(), EventGroupRegistrationService.givenUp(registration.status()));
                partners.unlink(registration.id());
            }
        });
        return standing(eventGroupId, partnerId, remoteMemberId);
    }

    /**
     * Takes a partner member's sign-up back with every part. Parts already past keep their record.
     *
     * @param stationId      this station
     * @param eventGroupId       the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     */
    public void withdraw(int stationId, int eventGroupId, int partnerId, UUID remoteMemberId) {
        Transactions.run(() -> {
            gate.lockEventGroup(stationId, eventGroupId);
            var current = requireActive(eventGroupId, partnerId, remoteMemberId);
            var parts = gate.lockParts(eventGroupId);
            var standing = Standing.of(stationId, parts, calendar);
            partners.updateStatus(current.id(), EventGroupRegistrationService.givenUp(current.status()));
            for (var registration : partners.findLinked(current.id())) {
                if (!standing.tookPlace(registration.eventId())) {
                    partners.restate(registration.id(), EventGroupRegistrationService.givenUp(registration.status()));
                }
                partners.unlink(registration.id());
            }
        });
        log.info("A member of partner {} withdrew from event group {}", partnerId, eventGroupId);
    }

    /**
     * Accepts or turns down a partner member's sign-up, and every part chosen with it.
     *
     * @param stationId this station
     * @param eventGroupId  the event group
     * @param signUpId  the partner sign-up
     * @param decision  {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     */
    public void decide(int stationId, int eventGroupId, int signUpId, RegistrationStatus decision) {
        if (decision != RegistrationStatus.ACCEPTED && decision != RegistrationStatus.DENIED) {
            throw EventRefusal.EVENT_GROUP_DECISION_UNKNOWN.raise();
        }
        Transactions.run(() -> {
            var eventGroup = gate.lockEventGroup(stationId, eventGroupId);
            if (decision == RegistrationStatus.ACCEPTED && eventGroup.cancelled()) {
                throw EventRefusal.EVENT_GROUP_CANCELLED.raise();
            }
            var current = partners.findById(signUpId)
                    .filter(found -> found.eventGroupId() == eventGroupId)
                    .filter(found -> found.active() || found.status() == RegistrationStatus.DENIED)
                    .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
            gate.lockParts(eventGroupId);
            partners.updateStatus(current.id(), decision);
            for (var registration : partners.findLinked(current.id())) {
                partners.restate(registration.id(), decision);
            }
        });
        log.info("Partner sign-up {} for event group {} is now {}", signUpId, eventGroupId, decision);
    }

    /**
     * Tells every partner whose members hold or wait for a place that the event group was called off,
     * naming those members. A partner that cannot be reached misses the notice; the event group page
     * still shows the event group as called off.
     *
     * @param eventGroupId   the event group
     * @param eventGroupName its name
     * @param reason     why, as told to the people signed up, or null
     */
    public void tellCancelled(int eventGroupId, String eventGroupName, @Nullable String reason) {
        var byPartner = partners.findByEventGroup(eventGroupId).stream()
                .filter(EventGroupPartnerRegistration::active)
                .collect(Collectors.groupingBy(
                        EventGroupPartnerRegistration::partnerId,
                        Collectors.mapping(EventGroupPartnerRegistration::remoteMemberId, Collectors.toList())));
        byPartner.forEach((partnerId, members) -> federationPartners
                .findPartnerById(partnerId)
                .ifPresent(partner -> transport.notify(
                        partner,
                        RemoteEventGroupRoutes.EVENT_GROUP_CANCELLED_WEBHOOK.at(),
                        new RemoteEventGroupCancelled(eventGroupId, eventGroupName, reason, members))));
        if (!byPartner.isEmpty())
            log.info("Told {} partner(s) that event group {} was called off", byPartner.size(), eventGroupId);
    }

    /**
     * Where the given members of one partner stand in an event group.
     *
     * @param eventGroupId        the event group
     * @param partnerId       the partner
     * @param remoteMemberIds the members asked about
     * @return one standing per member, in the order asked
     */
    public List<PartnerStanding> standings(int eventGroupId, int partnerId, List<UUID> remoteMemberIds) {
        return remoteMemberIds.stream()
                .map(member -> standing(eventGroupId, partnerId, member))
                .toList();
    }

    /**
     * Every partner sign-up of an event group that holds, waits for or was turned down for a place, with the
     * member as this station shows them.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @return the partner sign-ups, oldest first
     */
    public List<PartnerSignUp> signUps(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        return partners.findByEventGroup(eventGroupId).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .map(signUp -> new PartnerSignUp(
                        signUp.id(),
                        registrants.identify(signUp.partnerId(), signUp.remoteMemberId()),
                        standing(eventGroupId, signUp)))
                .toList();
    }

    private PartnerStanding standing(int eventGroupId, int partnerId, UUID remoteMemberId) {
        return partners.find(eventGroupId, partnerId, remoteMemberId)
                .map(signUp -> standing(eventGroupId, signUp))
                .orElseGet(() -> new PartnerStanding(remoteMemberId, null, null, List.of(), List.of()));
    }

    private PartnerStanding standing(int eventGroupId, EventGroupPartnerRegistration signUp) {
        var counted = partners.findLinked(signUp.id()).stream()
                .filter(registration -> registration.status() == RegistrationStatus.PENDING
                        || registration.status() == RegistrationStatus.ACCEPTED
                        || (registration.status() == RegistrationStatus.DENIED
                                && signUp.status() == RegistrationStatus.DENIED))
                .map(EventFederationRegistration::eventId)
                .collect(Collectors.toSet());
        return new PartnerStanding(
                signUp.remoteMemberId(),
                signUp.status(),
                signUp.registeredAt(),
                counted.stream().sorted().toList(),
                gate.evaluate(eventGroupId, counted));
    }

    private EventGroupPartnerRegistration requireActive(int eventGroupId, int partnerId, UUID remoteMemberId) {
        return partners.find(eventGroupId, partnerId, remoteMemberId)
                .filter(EventGroupPartnerRegistration::active)
                .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
    }

    private Map<Integer, EventFederationRegistration> heldParts(EventGroupPartnerRegistration signUp) {
        return partners.findLinked(signUp.id()).stream()
                .collect(Collectors.toMap(
                        EventFederationRegistration::eventId, Function.identity(), (first, second) -> first));
    }

    private void place(StationEvent part, EventGroupPartnerRegistration signUp, RegistrationStatus status) {
        var registration = federation.createRegistration(
                part.id(), signUp.partnerId(), signUp.remoteMemberId(), gate.dayOf(part), status);
        partners.link(registration.id(), signUp.id());
    }

    /**
     * Where a partner's member stands in an event group, as the partner is told.
     *
     * @param memberUid    the member, by the id their own station gave them
     * @param status       the sign-up's status, or null where they are not signed up
     * @param registeredAt when they signed up, or null
     * @param parts        the parts that count for the rules, in id order
     * @param sets         how those parts meet every set
     */
    public record PartnerStanding(
            UUID memberUid,
            @Nullable RegistrationStatus status,
            @Nullable Instant registeredAt,
            List<Integer> parts,
            List<SetResult> sets) {}

    /**
     * A partner member's sign-up as this station's event group page lists it.
     *
     * @param id       the partner sign-up, which decisions name
     * @param member   the member, or null where the partnership is gone
     * @param standing where they stand
     */
    public record PartnerSignUp(int id, @Nullable MemberIdentity member, PartnerStanding standing) {}
}
