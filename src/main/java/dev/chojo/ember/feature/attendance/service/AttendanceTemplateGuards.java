/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.api.refusal.AttendanceRefusal;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

/**
 * Whether an attendance template may be chosen for something new.
 *
 * <p>Deleting a template only archives it, because the sheets made from it are history and keep
 * reading it. Everything that starts from a template asks here first: a new sheet, and an
 * appointment or an appointment template that is to take its sheets from one.
 */
@Singleton
public class AttendanceTemplateGuards {
    private final AttendanceRepository attendanceRepository;

    @Inject
    public AttendanceTemplateGuards(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Refuses a template that was deleted.
     *
     * @param templateId the template chosen, null where none was
     * @throws io.javalin.http.HttpResponseException {@link AttendanceRefusal#ATTENDANCE_TEMPLATE_ARCHIVED}
     */
    public void requireOpenForNewWork(@Nullable Integer templateId) {
        if (templateId != null && attendanceRepository.isArchived(templateId)) {
            throw AttendanceRefusal.ATTENDANCE_TEMPLATE_ARCHIVED.raise();
        }
    }
}
