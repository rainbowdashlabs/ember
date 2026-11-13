/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;

/**
 * Sign-ups for a course closed while some still wait for a decision.
 *
 * @param stationId    the station
 * @param courseId     the course
 * @param courseName   its name
 * @param pendingCount how many sign-ups wait for a decision
 */
public record CourseDeadlineExpired(int stationId, int courseId, String courseName, int pendingCount)
        implements DomainEvent {}
