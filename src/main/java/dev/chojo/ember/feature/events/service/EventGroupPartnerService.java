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
import dev.chojo.ember.feature.events.repository.RegistrationQueueRepository.QueueOwner;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes;
import dev.chojo.ember.feature.events.route.RemoteEventGroupRoutes.RemoteEventGroupCancelled;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.transport.FederationTransport;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
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
    private final EventGroupSignUpRules rules;
    private final EventGroupPartnerRepository partners;
    private final EventFederationRepository federation;
    private final EventRequirementService requirements;
    private final FederatedRegistrantService registrants;
    private final FederationRepository federationPartners;
    private final FederationTransport transport;
    private final EventGroupQueue queue;

    @Inject
    public EventGroupPartnerService(
            EventGroupService eventGroups,
            EventGroupSignUpRules rules,
            EventGroupPartnerRepository partners,
            EventFederationRepository federation,
            EventRequirementService requirements,
            FederatedRegistrantService registrants,
            FederationRepository federationPartners,
            FederationTransport transport,
            EventGroupQueue queue) {
        this.queue = queue;
        this.eventGroups = eventGroups;
        this.rules = rules;
        this.partners = partners;
        this.federation = federation;
        this.requirements = requirements;
        this.registrants = registrants;
        this.federationPartners = federationPartners;
        this.transport = transport;
    }

    /**
     * How an event group is shared.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
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
     * there. Nor is one whose own documents, or a part's, partners could not sign alike.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @param scope        every partner, or the ones named
     * @param partnerIds   the partners named
     */
    public void share(int stationId, int eventGroupId, ShareScope scope, List<Integer> partnerIds) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        var targets = scope == ShareScope.SPECIFIC ? List.copyOf(partnerIds) : List.<Integer>of();
        Transactions.run(() -> {
            eventGroups.lockEventGroup(stationId, eventGroupId);
            if (!eventGroups.viewAudience(eventGroupId).namesNobody()
                    || !eventGroups.registrationAudience(eventGroupId).namesNobody()) {
                throw EventRefusal.RESTRICTED_EVENT_NOT_SHARED.raise();
            }
            requirements.requireEventGroupShareable(eventGroupId);
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
     * @param stationId    the station asking
     * @param eventGroupId the event group
     */
    public void unshare(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        Transactions.run(() -> {
            eventGroups.lockEventGroup(stationId, eventGroupId);
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
     * Holds a partner's request to the event groups this station shares with it.
     *
     * @param partner      the partnership the request arrived on
     * @param eventGroupId the event group asked for
     * @return the event group's id
     */
    public int requireSharedWith(ServingPartner partner, int eventGroupId) {
        if (!partners.isSharedWith(eventGroupId, partner.partnerId(), partner.servingStationId())) {
            throw EventRefusal.EVENT_GROUP_NOT_HERE.raise();
        }
        return eventGroupId;
    }

    /**
     * Whether a partner's member holds or waits for a place in an event group. A member who does may still
     * withdraw after the event group stopped being shared with their station, so no place stays taken that
     * nobody can give back.
     *
     * @param eventGroupId   the event group
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
     * @param eventGroupId   the event group
     * @param partnerId      the partner the member belongs to
     * @param remoteMemberId the member
     * @param partIds        the chosen parts
     * @return where the member stands now
     */
    public PartnerStanding register(
            int stationId, int eventGroupId, int partnerId, UUID remoteMemberId, Set<Integer> partIds) {
        Transactions.run(() -> {
            var eventGroup = rules.lockOpen(stationId, eventGroupId, false);
            var existing = partners.find(eventGroupId, partnerId, remoteMemberId);
            EventGroupSignUpRules.requireNoActiveSignUp(
                    existing.filter(EventGroupPartnerRegistration::active).isPresent());
            var parts = rules.lockParts(eventGroupId);
            var standing = rules.standing(stationId, parts);
            EventGroupSignUpRules.requireChoosable(partIds, parts.keySet(), standing, Set.of(), false);
            rules.requireRulesMet(eventGroupId, partIds, standing, false);
            var status = queue.statusFor(eventGroup, partIds, parts, standing, false, null);
            existing.ifPresent(earlier ->
                    partners.findLinked(earlier.id()).forEach(registration -> partners.unlink(registration.id())));
            var stored = partners.signUp(eventGroupId, partnerId, remoteMemberId, status);
            for (int partId : EventGroupSignUpRules.ascending(partIds)) {
                place(parts.get(partId), stored, status);
            }
            if (status == RegistrationStatus.QUEUED) {
                queue.enqueue(eventGroup, QueueOwner.ofPartner(stored.id()), null);
            }
        });
        log.info("A member of partner {} signed up for event group {}", partnerId, eventGroupId);
        return standingOf(eventGroupId, partnerId, remoteMemberId);
    }

    /**
     * Changes the parts a partner's member chose. A part already past cannot be given up.
     *
     * @param stationId      this station
     * @param eventGroupId   the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     * @param partIds        the parts chosen from now on
     * @return where the member stands now
     */
    public PartnerStanding changeParts(
            int stationId, int eventGroupId, int partnerId, UUID remoteMemberId, Set<Integer> partIds) {
        Transactions.run(() -> {
            var eventGroup = rules.lockOpen(stationId, eventGroupId, false);
            var current = requireActive(eventGroupId, partnerId, remoteMemberId);
            var parts = rules.lockParts(eventGroupId);
            var standing = rules.standing(stationId, parts);
            var held = heldParts(current);
            EventGroupSignUpRules.requireChoosable(partIds, parts.keySet(), standing, held.keySet(), false);
            var dropped = EventGroupSignUpRules.requireDroppable(held.keySet(), partIds, standing, false);
            rules.requireRulesMet(eventGroupId, partIds, standing, false);
            if (current.status() == RegistrationStatus.ACCEPTED) {
                var added = new HashSet<>(partIds);
                added.removeAll(held.keySet());
                rules.requireRoomOnAdded(added, parts, standing, false);
            }
            for (int partId : EventGroupSignUpRules.ascending(partIds)) {
                if (!held.containsKey(partId)) place(parts.get(partId), current, current.status());
            }
            for (int partId : dropped) {
                var registration = held.get(partId);
                partners.restate(registration.id(), EventGroupSignUpRules.givenUp(registration.status()));
                partners.unlink(registration.id());
            }
            queue.fill(eventGroup, parts, standing);
        });
        return standingOf(eventGroupId, partnerId, remoteMemberId);
    }

    /**
     * Takes a partner member's sign-up back with every part. Parts already past keep their record.
     *
     * @param stationId      this station
     * @param eventGroupId   the event group
     * @param partnerId      the partner
     * @param remoteMemberId the member
     */
    public void withdraw(int stationId, int eventGroupId, int partnerId, UUID remoteMemberId) {
        Transactions.run(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            var current = requireActive(eventGroupId, partnerId, remoteMemberId);
            var parts = rules.lockParts(eventGroupId);
            var standing = rules.standing(stationId, parts);
            partners.updateStatus(current.id(), EventGroupSignUpRules.givenUp(current.status()));
            for (var registration : partners.findLinked(current.id())) {
                if (!standing.tookPlace(registration.eventId())) {
                    partners.restate(registration.id(), EventGroupSignUpRules.givenUp(registration.status()));
                }
                partners.unlink(registration.id());
            }
            if (current.status() == RegistrationStatus.ACCEPTED) queue.fill(eventGroup, parts, standing);
        });
        log.info("A member of partner {} withdrew from event group {}", partnerId, eventGroupId);
    }

    /**
     * Accepts or turns down a partner member's sign-up, and every part chosen with it, an acceptance going
     * beyond the limit without being asked; routes use the form that takes the choice.
     *
     * @param stationId    this station
     * @param eventGroupId the event group
     * @param signUpId     the partner sign-up
     * @param decision     {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     */
    public void decide(int stationId, int eventGroupId, int signUpId, RegistrationStatus decision) {
        decide(stationId, eventGroupId, signUpId, decision, true);
    }

    /**
     * Accepts or turns down a partner member's sign-up, and every part chosen with it. Accepting a choice
     * that does not fit refuses unless whoever decides goes beyond the limit.
     *
     * @param stationId    this station
     * @param eventGroupId the event group
     * @param signUpId     the partner sign-up
     * @param decision     {@link RegistrationStatus#ACCEPTED} or {@link RegistrationStatus#DENIED}
     * @param overLimit    whether an acceptance may go beyond the limit
     */
    public void decide(int stationId, int eventGroupId, int signUpId, RegistrationStatus decision, boolean overLimit) {
        Transactions.run(() -> {
            var eventGroup = rules.lockForDecision(stationId, eventGroupId, decision);
            var current = partners.findById(signUpId)
                    .filter(found -> found.eventGroupId() == eventGroupId)
                    .filter(found -> found.active() || found.status() == RegistrationStatus.DENIED)
                    .orElseThrow(EventRefusal.EVENT_GROUP_NOT_SIGNED_UP::raise);
            var parts = rules.lockParts(eventGroupId);
            var tied = partners.findLinked(current.id());
            if (decision == RegistrationStatus.ACCEPTED && current.status() != RegistrationStatus.ACCEPTED) {
                queue.requireRoomToAccept(
                        eventGroup,
                        tied.stream().map(EventFederationRegistration::eventId).collect(Collectors.toSet()),
                        parts,
                        rules.standing(stationId, parts),
                        overLimit);
            }
            partners.updateStatus(current.id(), decision);
            for (var registration : tied) {
                partners.restate(registration.id(), decision);
            }
            if (current.status() == RegistrationStatus.ACCEPTED && decision != RegistrationStatus.ACCEPTED) {
                queue.fill(eventGroup, parts, rules.standing(stationId, parts));
            }
        });
        log.info("Partner sign-up {} for event group {} is now {}", signUpId, eventGroupId, decision);
    }

    /**
     * Puts partner members' sign-ups waiting for a decision onto the event group's waiting list, in
     * sign-up order, placed by the order the list keeps. Nothing changes unless every one of them waits
     * for a decision.
     *
     * @param stationId    this station
     * @param eventGroupId the event group
     * @param signUpIds    the partner sign-ups
     */
    public void moveToQueue(int stationId, int eventGroupId, Collection<Integer> signUpIds) {
        Transactions.run(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            queue.requireLimit(eventGroup, rules.lockParts(eventGroupId));
            var pending = signUpIds.stream()
                    .distinct()
                    .map(id -> partners.findById(id)
                            .filter(found -> found.eventGroupId() == eventGroupId)
                            .filter(found -> found.status() == RegistrationStatus.PENDING)
                            .orElseThrow(EventRefusal.EVENT_GROUP_NOT_PENDING_FOR_QUEUE::raise))
                    .sorted(Comparator.comparing(EventGroupPartnerRegistration::registeredAt)
                            .thenComparing(EventGroupPartnerRegistration::id))
                    .toList();
            for (var signUp : pending) {
                partners.updateStatus(signUp.id(), RegistrationStatus.QUEUED);
                for (var registration : partners.findLinked(signUp.id())) {
                    partners.restate(registration.id(), RegistrationStatus.QUEUED);
                }
                queue.enqueue(eventGroup, QueueOwner.ofPartner(signUp.id()), null);
            }
        });
    }

    /**
     * Moves a partner member's sign-up to another place in line on the event group's waiting list.
     *
     * @param stationId    this station
     * @param eventGroupId the event group
     * @param signUpId     the partner sign-up, which waits on the list
     * @param rank         the place in line it takes, 1 moving up next; beyond the end it goes last
     */
    public void moveInQueue(int stationId, int eventGroupId, int signUpId, int rank) {
        Transactions.run(() -> {
            var eventGroup = rules.lock(stationId, eventGroupId);
            partners.findById(signUpId)
                    .filter(found -> found.eventGroupId() == eventGroupId)
                    .filter(found -> found.status() == RegistrationStatus.QUEUED)
                    .orElseThrow(EventRefusal.EVENT_GROUP_NOT_QUEUED::raise);
            queue.moveTo(eventGroup, QueueOwner.ofPartner(signUpId), rank);
        });
    }

    /**
     * Tells every partner whose members hold or wait for a place that the event group was called off,
     * naming those members. A partner that cannot be reached misses the notice; the event group page
     * still shows the event group as called off.
     *
     * @param eventGroupId   the event group
     * @param eventGroupName its name
     * @param reason         why, as told to the people signed up, or null
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
     * @param eventGroupId    the event group
     * @param partnerId       the partner
     * @param remoteMemberIds the members asked about
     * @return one standing per member, in the order asked
     */
    public List<PartnerStanding> standings(int eventGroupId, int partnerId, List<UUID> remoteMemberIds) {
        var evaluate = rules.evaluator(eventGroupId);
        var linked = partners.findLinkedByEventGroup(eventGroupId);
        var ranks = queue.ranks(eventGroupId);
        var byMember = partners.findByEventGroup(eventGroupId).stream()
                .filter(signUp -> signUp.partnerId() == partnerId)
                .collect(Collectors.toMap(
                        EventGroupPartnerRegistration::remoteMemberId, Function.identity(), (first, second) -> first));
        return remoteMemberIds.stream()
                .map(member -> Optional.ofNullable(byMember.get(member))
                        .map(signUp -> standing(signUp, linked, evaluate, ranks))
                        .orElseGet(() -> notSignedUp(member)))
                .toList();
    }

    private PartnerStanding standingOf(int eventGroupId, int partnerId, UUID remoteMemberId) {
        return partners.find(eventGroupId, partnerId, remoteMemberId)
                .map(signUp -> standing(
                        signUp,
                        Map.of(signUp.id(), partners.findLinked(signUp.id())),
                        rules.evaluator(eventGroupId),
                        queue.ranks(eventGroupId)))
                .orElseGet(() -> notSignedUp(remoteMemberId));
    }

    private static PartnerStanding notSignedUp(UUID remoteMemberId) {
        return new PartnerStanding(remoteMemberId, null, null, List.of(), List.of(), null);
    }

    /**
     * Every partner sign-up of an event group that holds, waits for or was turned down for a place, with the
     * member as this station shows them.
     *
     * @param stationId    the station asking
     * @param eventGroupId the event group
     * @return the partner sign-ups, oldest first
     */
    public List<PartnerSignUp> signUps(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        var evaluate = rules.evaluator(eventGroupId);
        var linked = partners.findLinkedByEventGroup(eventGroupId);
        var ranks = queue.ranks(eventGroupId);
        return partners.findByEventGroup(eventGroupId).stream()
                .filter(signUp -> signUp.active() || signUp.status() == RegistrationStatus.DENIED)
                .map(signUp -> new PartnerSignUp(
                        signUp.id(),
                        registrants.identify(signUp.partnerId(), signUp.remoteMemberId()),
                        standing(signUp, linked, evaluate, ranks)))
                .toList();
    }

    private static PartnerStanding standing(
            EventGroupPartnerRegistration signUp,
            Map<Integer, List<EventFederationRegistration>> linked,
            Function<Set<Integer>, List<SetResult>> evaluate,
            Map<QueueOwner, Integer> ranks) {
        var counted = linked.getOrDefault(signUp.id(), List.of()).stream()
                .filter(registration -> EventGroupSignUpRules.counts(registration.status(), signUp.status()))
                .map(EventFederationRegistration::eventId)
                .collect(Collectors.toSet());
        return new PartnerStanding(
                signUp.remoteMemberId(),
                signUp.status(),
                signUp.registeredAt(),
                counted.stream().sorted().toList(),
                evaluate.apply(counted),
                ranks.get(QueueOwner.ofPartner(signUp.id())));
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
                part.id(), signUp.partnerId(), signUp.remoteMemberId(), rules.dayOf(part), status);
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
     * @param queuePosition the place in line on the waiting list, or null where the sign-up does not wait
     */
    public record PartnerStanding(
            UUID memberUid,
            @Nullable RegistrationStatus status,
            @Nullable Instant registeredAt,
            List<Integer> parts,
            List<SetResult> sets,
            @Nullable Integer queuePosition) {}

    /**
     * A partner member's sign-up as this station's event group page lists it.
     *
     * @param id       the partner sign-up, which decisions name
     * @param member   the member, or null where the partnership is gone
     * @param standing where they stand
     */
    public record PartnerSignUp(int id, @Nullable MemberIdentity member, PartnerStanding standing) {}
}
