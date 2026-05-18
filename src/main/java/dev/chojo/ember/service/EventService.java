/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.service;

import dev.chojo.ember.api.Roles;
import dev.chojo.ember.entity.EventBreak;
import dev.chojo.ember.entity.EventCategory;
import dev.chojo.ember.entity.EventFieldDefault;
import dev.chojo.ember.entity.EventRegistration;
import dev.chojo.ember.entity.StationEvent;
import dev.chojo.ember.repository.EventRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Singleton
public class EventService {
    private final EventRepository eventRepository;

    @Inject
    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // -- Events --

    public List<StationEvent> findByStation(int stationId) {
        return eventRepository.findByStation(stationId);
    }

    public Optional<StationEvent> findById(int id) {
        return eventRepository.findById(id);
    }

    public StationEvent create(
            int stationId,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId) {
        return eventRepository.create(
                stationId,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                templateId,
                requiresRegistration,
                registrationDeadline,
                requiresConfirmation,
                categoryId);
    }

    public Optional<StationEvent> update(
            int id,
            String name,
            String description,
            StationEvent.EventType eventType,
            Integer dayOfWeek,
            Instant startTime,
            Instant endTime,
            Integer templateId,
            boolean requiresRegistration,
            Instant registrationDeadline,
            boolean requiresConfirmation,
            Integer categoryId) {
        if (eventRepository.update(
                id,
                name,
                description,
                eventType,
                dayOfWeek,
                startTime,
                endTime,
                templateId,
                requiresRegistration,
                registrationDeadline,
                requiresConfirmation,
                categoryId)) {
            return eventRepository.findById(id);
        }
        return Optional.empty();
    }

    public boolean delete(int id) {
        return eventRepository.delete(id);
    }

    public List<StationEvent> findTodayEvents(int stationId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        int dayOfWeek = today.getDayOfWeek().getValue();
        boolean inBreak = eventRepository.isDateInBreak(stationId, today);

        return eventRepository.findByStation(stationId).stream()
                .filter(e -> {
                    if (e.eventType() == StationEvent.EventType.ONE_TIME) {
                        if (e.startTime() == null) return false;
                        LocalDate eventDateUtc =
                                e.startTime().atZone(ZoneOffset.UTC).toLocalDate();
                        return today.equals(eventDateUtc);
                    } else {
                        return !inBreak && e.dayOfWeek() != null && e.dayOfWeek() == dayOfWeek;
                    }
                })
                .toList();
    }

    // -- Categories --

    public List<EventCategory> findCategoriesByStation(int stationId) {
        return eventRepository.findCategoriesByStation(stationId);
    }

    public EventCategory createCategory(int stationId, String name, int position) {
        return eventRepository.createCategory(stationId, name, position);
    }

    public boolean updateCategory(int id, String name, int position) {
        return eventRepository.updateCategory(id, name, position);
    }

    public boolean deleteCategory(int id) {
        return eventRepository.deleteCategory(id);
    }

    // -- Breaks --

    public List<EventBreak> findBreaksByStation(int stationId) {
        return eventRepository.findBreaksByStation(stationId);
    }

    public Optional<EventBreak> findBreakById(int id) {
        return eventRepository.findBreakById(id);
    }

    public EventBreak createBreak(int stationId, String name, LocalDate startDate, LocalDate endDate) {
        return eventRepository.createBreak(stationId, name, startDate, endDate);
    }

    public Optional<EventBreak> updateBreak(int id, String name, LocalDate startDate, LocalDate endDate) {
        if (eventRepository.updateBreak(id, name, startDate, endDate)) {
            return eventRepository.findBreakById(id);
        }
        return Optional.empty();
    }

    public boolean deleteBreak(int id) {
        return eventRepository.deleteBreak(id);
    }

    // -- Restrictions --

    public List<Integer> findRoleRestrictions(int eventId) {
        return eventRepository.findRoleRestrictions(eventId);
    }

    public List<Integer> findGroupRestrictions(int eventId) {
        return eventRepository.findGroupRestrictions(eventId);
    }

    public void setRestrictions(int eventId, List<Integer> roleIds, List<Integer> groupIds) {
        eventRepository.setRoleRestrictions(eventId, roleIds != null ? roleIds : List.of());
        eventRepository.setGroupRestrictions(eventId, groupIds != null ? groupIds : List.of());
    }

    /**
     * Checks if a member is eligible for an event based on their expanded roles and group memberships.
     *
     * @param eventId        the event to check
     * @param expandedRoles  the member's roles (already expanded via Roles.expand)
     * @param memberGroupIds the member's group IDs
     */
    public boolean isMemberEligible(int eventId, Set<Roles> expandedRoles, List<Integer> memberGroupIds) {
        var roleRestrictionNames = eventRepository.findRoleRestrictionNames(eventId);
        var groupRestrictions = eventRepository.findGroupRestrictions(eventId);

        if (roleRestrictionNames.isEmpty() && groupRestrictions.isEmpty()) return true;

        // Check role restrictions: the member's expanded roles must include at least one restricted role
        for (String roleName : roleRestrictionNames) {
            Roles role = Roles.fromDbName(roleName);
            if (role != null && expandedRoles.contains(role)) return true;
        }

        // Check group restrictions
        for (int groupId : groupRestrictions) {
            if (memberGroupIds.contains(groupId)) return true;
        }

        return false;
    }

    public Map<Integer, List<Integer>> findAllRoleRestrictionsByStation(int stationId) {
        return eventRepository.findAllRoleRestrictionsByStation(stationId);
    }

    public Map<Integer, List<Integer>> findAllGroupRestrictionsByStation(int stationId) {
        return eventRepository.findAllGroupRestrictionsByStation(stationId);
    }

    // -- Field Defaults --

    public List<EventFieldDefault> findFieldDefaults(int eventId) {
        return eventRepository.findFieldDefaults(eventId);
    }

    public void setFieldDefaults(int eventId, List<EventFieldDefault> defaults) {
        eventRepository.setFieldDefaults(eventId, defaults);
    }

    /**
     * Resolves field defaults for an event into concrete values by replacing event property links.
     */
    public Map<Integer, String> resolveFieldDefaults(int eventId) {
        var event = eventRepository.findById(eventId).orElse(null);
        if (event == null) return Map.of();

        var defaults = eventRepository.findFieldDefaults(eventId);
        var result = new HashMap<Integer, String>();
        for (var def : defaults) {
            String resolved =
                    switch (def.source()) {
                        case "VALUE" -> def.value();
                        case "EVENT_NAME" -> event.name();
                        case "EVENT_DESCRIPTION" -> event.description();
                        case "EVENT_START_TIME" -> event.startTime() != null ? "\"" + event.startTime() + "\"" : null;
                        case "EVENT_END_TIME" -> event.endTime() != null ? "\"" + event.endTime() + "\"" : null;
                        default -> null;
                    };
            if (resolved != null) {
                result.put(def.fieldId(), resolved);
            }
        }
        return result;
    }

    // -- Registrations --

    public List<EventRegistration> findPendingRegistrationsByStation(int stationId) {
        return eventRepository.findPendingRegistrationsByStation(stationId);
    }

    public List<EventRegistration> findRegistrations(int eventId, LocalDate eventDate) {
        return eventRepository.findRegistrations(eventId, eventDate);
    }

    public List<EventRegistration> findRegistrationsByMember(int memberId) {
        return eventRepository.findRegistrationsByMember(memberId);
    }

    public EventRegistration register(
            int eventId, int memberId, LocalDate eventDate, boolean autoAccept, Integer createdBy) {
        var registration = eventRepository.createRegistration(
                eventId, memberId, eventDate, EventRegistration.RegistrationStatus.PENDING, createdBy);
        if (autoAccept) {
            eventRepository.updateRegistrationStatus(registration.id(), EventRegistration.RegistrationStatus.ACCEPTED);
            return eventRepository.findRegistrationById(registration.id()).orElse(registration);
        }
        return registration;
    }

    public Optional<EventRegistration> findRegistrationById(int id) {
        return eventRepository.findRegistrationById(id);
    }

    public boolean updateRegistrationStatus(int id, EventRegistration.RegistrationStatus status) {
        return eventRepository.updateRegistrationStatus(id, status);
    }

    public boolean withdrawRegistration(int id) {
        return eventRepository.deleteRegistration(id);
    }

    public EventRegistration decline(int eventId, int memberId, LocalDate eventDate, Integer createdBy) {
        return eventRepository.createRegistration(
                eventId, memberId, eventDate, EventRegistration.RegistrationStatus.DECLINED, createdBy);
    }

    public List<EventRepository.RegistrationCount> findRegistrationCounts(int stationId) {
        return eventRepository.findRegistrationCounts(stationId);
    }

    public List<Integer> findDeclinedMemberIds(int eventId, LocalDate eventDate) {
        return eventRepository.findDeclinedMemberIds(eventId, eventDate);
    }
}
