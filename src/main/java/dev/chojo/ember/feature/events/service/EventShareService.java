/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.EventFederationShare;
import dev.chojo.ember.feature.events.entity.EventPartnerPlaces;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.transport.ServingPartner;
import dev.chojo.ember.feature.generator.service.EventRequirementService;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * How this station shares its own appointments with partner stations: the share and its targets, what
 * each partner was given to decide, and the names partners' members are shown under here.
 *
 * <p>Every share of an appointment on its own is written here, whether a manager shares it, a cluster
 * shares it on its own or the demo seeds it, and the share and its targets are written as one. The parts
 * of an event group are shared with their event group, which writes their shares itself. Which appointments a
 * serving partner may see is answered here too, for every serving function that holds a partner to
 * them.
 */
@Singleton
public class EventShareService {
    private static final Logger log = LoggerFactory.getLogger(EventShareService.class);

    private final EventFederationRepository federationRepository;
    private final EventSharingGuards sharingGuards;
    private final EventRequirementService requirements;

    @Inject
    public EventShareService(
            EventFederationRepository federationRepository,
            EventSharingGuards sharingGuards,
            EventRequirementService requirements) {
        this.federationRepository = federationRepository;
        this.sharingGuards = sharingGuards;
        this.requirements = requirements;
    }

    /**
     * Hands one of this station's appointments to partner stations, after asking whether it may be.
     *
     * <p>A part of an event group is shared with its event group and not on its own. An appointment that
     * not every member here may know about is not handed over: its audiences name groups, tags and members
     * of this station, none of which mean anything at the partner, so a shared appointment would stand
     * open to everybody there, the opposite of what restricting it said. Nor is one whose documents to
     * bring partners could not sign alike: such a document names a person, or reads differently for some
     * members, and the partner's members would each need their own.
     *
     * @param event      the appointment, already checked to be the station's
     * @param scope      every partner, or the ones named
     * @param partnerIds the partners named, used when the scope is {@link ShareScope#SPECIFIC}
     * @return the share as written
     */
    public EventFederationShare share(StationEvent event, ShareScope scope, List<Integer> partnerIds) {
        return Transactions.call(() -> {
            sharingGuards.requireSharedOnItsOwn(event.id());
            if (event.restricted()) {
                throw EventRefusal.RESTRICTED_EVENT_NOT_SHARED.raise();
            }
            requirements.requireShareable(event.id());
            return setShare(event.id(), scope, partnerIds);
        });
    }

    /**
     * Writes the share of an appointment and its targets as one, without asking whether it may be shared.
     * For the callers that decided that already, such as a cluster sharing every appointment of its
     * stations.
     *
     * @param eventId    the event ID
     * @param scope      the sharing scope
     * @param partnerIds the partner IDs to target (used when scope is SPECIFIC)
     * @return the created or updated share
     */
    public EventFederationShare setShare(int eventId, ShareScope scope, List<Integer> partnerIds) {
        var share = Transactions.call(() -> {
            var written = federationRepository.setShare(eventId, scope);
            federationRepository.setShareTargets(written.id(), partnerIds);
            return written;
        });
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

    /**
     * Confirms the partner is allowed to see the given event, i.e. it is in the set this station
     * shares with that partner. Guards every read and write so a partner cannot address
     * never-federated events by enumerating ids.
     *
     * @param partner the partnership the request arrived on
     * @param eventId the appointment it names
     */
    public void requireSharedWith(ServingPartner partner, int eventId) {
        if (!sharedWith(partner).contains(eventId)) {
            throw EventRefusal.EVENT_NOT_SHARED_WITH_PARTNER.raise();
        }
    }

    /**
     * The appointments this station shares with the partner a request arrived on.
     *
     * @param partner the partnership the request arrived on
     * @return the ids of the appointments shared with it
     */
    public List<Integer> sharedWith(ServingPartner partner) {
        return findSharedEventIds(partner.partnerId(), partner.servingStationId());
    }

    /**
     * What a partner may do with a shared appointment: how many places, and who decides.
     *
     * @param eventId   the appointment
     * @param partnerId this station's record of the partner
     * @return the arrangement, the host deciding without a cap where none was made
     */
    public EventPartnerPlaces partnerPlaces(int eventId, int partnerId) {
        return federationRepository.findPartnerPlaces(eventId, partnerId);
    }

    /**
     * Everything said per partner about one appointment, for the screen that sets it.
     *
     * @param eventId the appointment
     * @return one arrangement per partner that was given one
     */
    public List<EventPartnerPlaces> partnerPlaces(int eventId) {
        return federationRepository.findPartnerPlaces(eventId);
    }

    /**
     * Hands a partner a number of places, or takes the arrangement back.
     *
     * <p>A budget means the partner decides: handing somebody five places and then choosing their
     * five for them is not a thing anybody wants, and the database refuses the combination outright.
     *
     * @param eventId         the appointment
     * @param partnerId       this station's record of the partner
     * @param slotBudget      how many places the partner fills itself, or {@code null} for no cap
     * @param partnerConfirms whether the partner decides about its own people
     */
    public void setPartnerPlaces(int eventId, int partnerId, @Nullable Integer slotBudget, boolean partnerConfirms) {
        federationRepository.setPartnerPlaces(eventId, partnerId, slotBudget, partnerConfirms);
        log.info(
                "Event {} now gives partner {} {} places, decided by {}",
                eventId,
                partnerId,
                slotBudget == null ? "as many as it likes" : slotBudget,
                partnerConfirms ? "the partner" : "this station");
    }

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
}
