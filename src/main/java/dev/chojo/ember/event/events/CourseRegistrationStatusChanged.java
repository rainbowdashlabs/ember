/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.event.events;

import dev.chojo.ember.event.DomainEvent;
import dev.chojo.ember.feature.events.entity.RegistrationStatus;

/**
 * A course sign-up changed state, for all its parts at once.
 *
 * @param stationId  the station
 * @param courseId   the course
 * @param courseName its name
 * @param memberId   the member signed up
 * @param memberName the member's name, for whoever runs events and cannot resolve an identifier
 * @param newStatus  the status the sign-up holds now
 */
public record CourseRegistrationStatusChanged(
        int stationId, int courseId, String courseName, int memberId, String memberName, RegistrationStatus newStatus)
        implements DomainEvent {}
