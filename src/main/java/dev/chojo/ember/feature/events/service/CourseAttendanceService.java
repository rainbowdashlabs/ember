/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.feature.events.entity.EventCoursePart;
import dev.chojo.ember.feature.events.entity.EventCourseRegistration;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;
import dev.chojo.ember.feature.events.repository.EventCourseRepository;
import dev.chojo.ember.feature.events.service.CourseRules.SetResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * How the participants of a course actually attended: the parts each was marked present at on the
 * parts' own attendance sheets, and whether that meets every set's minimum. A part called off counts
 * as it does for the sign-up, so nobody falls short through a cancellation.
 */
@Singleton
public class CourseAttendanceService {
    private final EventCourseService courses;
    private final EventCourseRegistrationService signUps;
    private final EventCourseRepository repository;

    @Inject
    public CourseAttendanceService(
            EventCourseService courses, EventCourseRegistrationService signUps, EventCourseRepository repository) {
        this.courses = courses;
        this.signUps = signUps;
        this.repository = repository;
    }

    /**
     * Where every accepted participant of a course stands after the parts took place.
     *
     * @param stationId the station asking
     * @param courseId  the course
     * @return one row per accepted participant, in the order they signed up
     */
    public List<AttendanceRow> summary(int stationId, int courseId) {
        courses.requireCourse(stationId, courseId);
        var parts = courses.findParts(courseId).stream()
                .map(EventCoursePart::eventId)
                .collect(Collectors.toSet());
        var present = repository.findPresent(courseId);
        var evaluate = signUps.evaluator(courseId);
        return signUps.findByCourse(courseId).stream()
                .filter(signUp -> signUp.status() == RegistrationStatus.ACCEPTED)
                .map(EventCourseRegistration::memberId)
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
