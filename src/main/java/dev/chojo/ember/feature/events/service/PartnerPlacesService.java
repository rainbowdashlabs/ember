/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.PartnerPlaces;
import dev.chojo.ember.feature.events.entity.PlacesKind;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.events.repository.PartnerPlacesRepository;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * What a host hands each partner station it shares something with: a number of places, and whether the
 * partner decides its own sign-ups. One rule set for every {@link PlacesKind}; counting the places and
 * confirming within them stay with each owner, since an appointment counts per date and an event group
 * per group.
 *
 * <p>An arrangement only stands for a partner the owner is shared with. It is refused for anybody else,
 * and it goes with the share: when the share stops naming a partner, or stops altogether, the arrangements
 * it no longer covers are dropped.
 */
@Singleton
public class PartnerPlacesService {
    private static final Logger log = LoggerFactory.getLogger(PartnerPlacesService.class);

    private final PartnerPlacesRepository places;
    private final EventFederationRepository appointmentShares;
    private final FederationRepository partners;

    @Inject
    public PartnerPlacesService(
            PartnerPlacesRepository places,
            EventFederationRepository appointmentShares,
            FederationRepository partners) {
        this.places = places;
        this.appointmentShares = appointmentShares;
        this.partners = partners;
    }

    /**
     * What a partner may do with one appointment or event group.
     *
     * @param kind      what the arrangement is about
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     * @return the arrangement, the host deciding without a cap where none was made
     */
    public PartnerPlaces arrangement(PlacesKind kind, int ownerId, int partnerId) {
        return places.find(kind, ownerId, partnerId);
    }

    /**
     * Everything arranged per partner about one appointment or event group, for the screen that sets it.
     *
     * @param kind    what the arrangements are about
     * @param ownerId the appointment or event group
     * @return one arrangement per partner that was given one
     */
    public List<PartnerPlaces> arrangements(PlacesKind kind, int ownerId) {
        return places.list(kind, ownerId);
    }

    /**
     * Hands a partner a number of places, or takes the arrangement back.
     *
     * <p>A budget means the partner decides: handing somebody five places and then choosing their five
     * for them is not a thing anybody wants. Taking the decision back is always possible; handing it
     * over only to a partner the owner is shared with, since nobody else could ever fill the places. The
     * check and the write run in one transaction holding the owner, as every share write does, so a share
     * changed at the same moment either sees the new arrangement or is seen by it.
     *
     * @param kind            what the arrangement is about
     * @param stationId       the station holding the owner, already checked to be the caller's
     * @param ownerId         the appointment or event group
     * @param partnerId       the station's own record of the partner
     * @param slotBudget      how many places the partner fills itself, or {@code null} for no cap
     * @param partnerConfirms whether the partner decides about its own people
     */
    public void arrange(
            PlacesKind kind,
            int stationId,
            int ownerId,
            int partnerId,
            @Nullable Integer slotBudget,
            boolean partnerConfirms) {
        var requested = PartnerPlaces.requested(ownerId, partnerId, slotBudget, partnerConfirms);
        Transactions.run(() -> {
            places.lockOwner(kind, ownerId);
            if (!requested.decidedByHost() && !sharedWith(kind, stationId, ownerId, partnerId)) {
                throw EventRefusal.PLACES_PARTNER_NOT_SHARED_WITH.raise();
            }
            places.set(kind, requested);
        });
        log.info(
                "{} {} now gives partner {} {} places, decided by {}",
                kind,
                ownerId,
                partnerId,
                requested.slotBudget() == null ? "as many as it likes" : requested.slotBudget(),
                requested.partnerConfirms() ? "the partner" : "this station");
    }

    /**
     * Refuses the host deciding about a partner's member where it handed that decision to the partner.
     *
     * @param kind      what the arrangement is about
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     */
    public void requireHostDecides(PlacesKind kind, int ownerId, int partnerId) {
        if (!arrangement(kind, ownerId, partnerId).decidedByHost()) {
            throw kind.decidesItsOwn().raise();
        }
    }

    /**
     * Refuses a partner confirming its own member where the host did not hand it that decision.
     *
     * @param kind      what the arrangement is about
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     * @return the arrangement the partner confirms within
     */
    public PartnerPlaces requirePartnerDecides(PlacesKind kind, int ownerId, int partnerId) {
        var arranged = arrangement(kind, ownerId, partnerId);
        if (arranged.decidedByHost()) {
            throw kind.doesNotConfirm().raise();
        }
        return arranged;
    }

    /**
     * Holds a partner's arrangement still until the running transaction ends, so places are counted
     * and taken against it one taker at a time.
     *
     * @param kind      what the arrangement is about
     * @param ownerId   the appointment or event group
     * @param partnerId the host's own record of the partner
     */
    public void lock(PlacesKind kind, int ownerId, int partnerId) {
        places.lock(kind, ownerId, partnerId);
    }

    /**
     * Holds the appointment or event group itself until the running transaction ends. Whatever changes
     * who it is shared with takes this first, before it touches the share, so the order is always the
     * owner, then the share, then the arrangements, as it is when the owner is deleted.
     *
     * @param kind    what the owner is
     * @param ownerId the appointment or event group
     */
    public void lockOwner(PlacesKind kind, int ownerId) {
        places.lockOwner(kind, ownerId);
    }

    /**
     * Drops the arrangements a share no longer covers: a share with every partner keeps all of them, one
     * with named partners keeps theirs, and no share keeps none. Runs in the transaction writing the
     * share, whose caller already holds the owner ({@link #lockOwner}).
     *
     * @param kind    what the arrangements are about
     * @param ownerId the appointment or event group
     * @param scope   how it is now shared, or {@code null} where it is no longer shared
     * @param targets the partners named, used when the scope is {@link ShareScope#SPECIFIC}
     */
    public void retainSharedWith(PlacesKind kind, int ownerId, @Nullable ShareScope scope, List<Integer> targets) {
        if (scope == ShareScope.ALL_PARTNERS) return;
        places.retainOnly(kind, ownerId, scope == ShareScope.SPECIFIC ? targets : List.of());
    }

    private boolean sharedWith(PlacesKind kind, int stationId, int ownerId, int partnerId) {
        boolean ownPartner = partners.findPartnerById(partnerId)
                .filter(partner -> partner.stationId() == stationId)
                .isPresent();
        return ownPartner
                && switch (kind) {
                    case APPOINTMENT ->
                        appointmentShares
                                .findSharedEventIds(partnerId, stationId)
                                .contains(ownerId);
                };
    }
}
