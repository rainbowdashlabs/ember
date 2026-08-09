/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.feature.events.repository.EventRepository;
import dev.chojo.ember.feature.restriction.RestrictionMode;
import dev.chojo.ember.feature.restriction.RestrictionSelection;
import dev.chojo.ember.feature.restriction.RestrictionSet;
import dev.chojo.ember.feature.restriction.RestrictionType;
import dev.chojo.ember.feature.restriction.service.RestrictionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Owns which members an event is visible and open to, bridging events onto the shared restriction
 * machinery.
 */
@Singleton
public class EventRestrictionService {
    private static final Logger log = LoggerFactory.getLogger(EventRestrictionService.class);

    private final EventRepository eventRepository;
    private final RestrictionService restrictionService;

    @Inject
    public EventRestrictionService(EventRepository eventRepository, RestrictionService restrictionService) {
        this.eventRepository = eventRepository;
        this.restrictionService = restrictionService;
    }

    /**
     * Retrieves the restriction set for an event.
     *
     * @param eventId the event ID
     * @return the restriction set
     */
    public RestrictionSet findRestrictions(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        RestrictionMode mode = event != null ? event.restrictionMode() : RestrictionMode.AND;
        return restrictionService.findRestrictionSet(RestrictionType.EVENT, eventId, mode);
    }

    /**
     * Sets all restrictions for an event, replacing any existing restrictions.
     *
     * @param eventId   the event ID
     * @param selection the restriction selection to persist
     */
    public void setRestrictions(int eventId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.EVENT, eventId, selection);
        log.info("Set restrictions for event {}", eventId);
    }

    /**
     * Updates the restriction mode for an event.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     */
    public void updateRestrictionMode(int eventId, RestrictionMode mode) {
        eventRepository.updateRestrictionMode(eventId, mode);
        log.info("Updated restriction mode for event {} to {}", eventId, mode);
    }

    /**
     * Checks if a member is eligible for an event based on its restrictions.
     * Delegates to the DB function which resolves the member's identity internally.
     *
     * @param eventId  the event to check
     * @param memberId the member ID
     */
    public boolean isMemberEligible(int eventId, int memberId, Set<StationPermission> memberPermissions) {
        return restrictionService.checkRestriction(RestrictionType.EVENT, eventId, memberId, memberPermissions);
    }
}
