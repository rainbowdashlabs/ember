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
 *
 * <p>An event carries two audiences, and they answer different questions. The registration audience
 * says who the appointment is for; everybody else still sees it in the calendar and simply cannot
 * answer it. The view audience says who may know it exists at all, and for everybody else the event
 * is absent from every list, feed and notification.
 *
 * <p>Seeing contains registering: {@link #canRegister} reads both, so no caller has to remember the
 * rule.
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
     * Retrieves who may register for an event.
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
     * Retrieves who may know the event exists.
     *
     * @param eventId the event ID
     * @return the restriction set
     */
    public RestrictionSet findViewRestrictions(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        RestrictionMode mode = event != null ? event.viewRestrictionMode() : RestrictionMode.AND;
        return restrictionService.findRestrictionSet(RestrictionType.EVENT_VIEW, eventId, mode);
    }

    /**
     * Sets who may register for an event, replacing any existing selection.
     *
     * @param eventId   the event ID
     * @param selection the restriction selection to persist
     */
    public void setRestrictions(int eventId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.EVENT, eventId, selection);
        log.info("Set registration audience for event {}", eventId);
    }

    /**
     * Sets who may know the event exists, replacing any existing selection.
     *
     * @param eventId   the event ID
     * @param selection the restriction selection to persist
     */
    public void setViewRestrictions(int eventId, RestrictionSelection selection) {
        restrictionService.setRestrictions(RestrictionType.EVENT_VIEW, eventId, selection);
        log.info("Set view audience for event {}", eventId);
    }

    /**
     * Updates how the parts of the registration audience combine.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     */
    public void updateRestrictionMode(int eventId, RestrictionMode mode) {
        eventRepository.updateRestrictionMode(eventId, mode);
        log.info("Updated registration restriction mode for event {} to {}", eventId, mode);
    }

    /**
     * Updates how the parts of the view audience combine.
     *
     * @param eventId the event ID
     * @param mode    the restriction mode
     */
    public void updateViewRestrictionMode(int eventId, RestrictionMode mode) {
        eventRepository.updateViewRestrictionMode(eventId, mode);
        log.info("Updated view restriction mode for event {} to {}", eventId, mode);
    }

    /**
     * Whether a member may know the event exists.
     *
     * @param eventId  the event to check
     * @param memberId the member ID
     */
    public boolean canView(int eventId, int memberId, Set<StationPermission> memberPermissions) {
        return restrictionService.checkRestriction(RestrictionType.EVENT_VIEW, eventId, memberId, memberPermissions);
    }

    /**
     * Whether a member may answer an event. Seeing contains registering, so an event somebody is not
     * allowed to know about is never one they may answer, however the registration audience reads.
     *
     * @param eventId  the event to check
     * @param memberId the member ID
     */
    public boolean canRegister(int eventId, int memberId, Set<StationPermission> memberPermissions) {
        return canView(eventId, memberId, memberPermissions)
                && restrictionService.checkRestriction(RestrictionType.EVENT, eventId, memberId, memberPermissions);
    }

    /**
     * Whether an event carries a view audience at all, which is what the lock in the lists stands
     * for. An event only narrowed for registration carries none.
     *
     * @param eventId the event to check
     */
    public boolean hasViewRestrictions(int eventId) {
        return restrictionService.hasRestrictions(RestrictionType.EVENT_VIEW, eventId);
    }
}
