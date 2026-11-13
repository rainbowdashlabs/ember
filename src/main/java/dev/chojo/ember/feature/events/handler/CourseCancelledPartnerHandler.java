/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.handler;

import dev.chojo.ember.event.DomainEventHandler;
import dev.chojo.ember.event.events.CourseCancelled;
import dev.chojo.ember.feature.events.service.EventCoursePartnerService;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

/**
 * Tells the partner stations whose members held or waited for a place that a shared course was
 * called off, once for the course.
 */
@Singleton
public class CourseCancelledPartnerHandler implements DomainEventHandler<CourseCancelled> {
    private final Provider<EventCoursePartnerService> partners;

    /**
     * @param partners read when a course is called off, since the service reaches the event bus
     *                 this handler is registered on
     */
    @Inject
    public CourseCancelledPartnerHandler(Provider<EventCoursePartnerService> partners) {
        this.partners = partners;
    }

    @Override
    public Class<CourseCancelled> eventType() {
        return CourseCancelled.class;
    }

    @Override
    public void handle(CourseCancelled event) {
        partners.get().tellCancelled(event.courseId(), event.courseName(), event.reason());
    }
}
