/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventGroupPart;
import dev.chojo.ember.feature.events.entity.EventGroupRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventGroupRepository;
import dev.chojo.ember.feature.events.service.EventGroupRules.SetResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How the participants of an event group actually attended: the parts each was marked present at on the
 * parts' own attendance sheets, and whether that meets every set's minimum. A part called off counts
 * as it does for the sign-up, so nobody falls short through a cancellation.
 */
@Singleton
public class EventGroupAttendanceService {
    private final EventGroupService eventGroups;
    private final EventGroupRegistrationService signUps;
    private final EventGroupRepository repository;

    @Inject
    public EventGroupAttendanceService(
            EventGroupService eventGroups, EventGroupRegistrationService signUps, EventGroupRepository repository) {
        this.eventGroups = eventGroups;
        this.signUps = signUps;
        this.repository = repository;
    }

    /**
     * Where every accepted participant of an event group stands after the parts took place.
     *
     * @param stationId the station asking
     * @param eventGroupId  the event group
     * @return one row per accepted participant, in the order they signed up
     */
    public List<AttendanceRow> summary(int stationId, int eventGroupId) {
        eventGroups.requireEventGroup(stationId, eventGroupId);
        var parts = eventGroups.findParts(eventGroupId).stream()
                .map(EventGroupPart::eventId)
                .collect(Collectors.toSet());
        var present = repository.findPresent(eventGroupId);
        var evaluate = signUps.evaluator(eventGroupId);
        return signUps.findByEventGroup(eventGroupId).stream()
                .filter(signUp -> signUp.status() == RegistrationStatus.ACCEPTED)
                .map(EventGroupRegistration::memberId)
                .map(memberId -> {
                    var attended = new HashSet<>(present.getOrDefault(memberId, Set.of()));
                    attended.retainAll(parts);
                    var sets = evaluate.apply(attended);
                    return new AttendanceRow(
                            memberId,
                            attended.stream().sorted().toList(),
                            sets,
                            sets.stream().allMatch(SetResult::met));
                })
                .toList();
    }

    /**
     * One participant's attendance.
     *
     * @param memberId the participant
     * @param attended the parts they were present at, in id order
     * @param sets     how those parts meet every set
     * @param complete whether every set is met
     */
    public record AttendanceRow(int memberId, List<Integer> attended, List<SetResult> sets, boolean complete) {}
}
