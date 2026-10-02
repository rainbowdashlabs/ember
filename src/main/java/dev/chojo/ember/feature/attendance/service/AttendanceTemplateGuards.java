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
 * Whether an attendance template, or one of its fields, may be chosen for something new.
 *
 * <p>Deleting a template only archives it, because the sheets made from it are history and keep
 * reading it. Everything that starts from a template asks here first: a new sheet, and an
 * appointment or an appointment template that is to take its sheets from one. A field is archived
 * the same way, and changing it or filling it in from an appointment asks here too.
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

    /**
     * Refuses a template field that was deleted. Deleting only archives a field, so the sheets that
     * answered it keep the answer, but it is neither changed nor used for anything new.
     *
     * @param templateId the template the field is named on
     * @param fieldId    the field named
     * @throws io.javalin.http.HttpResponseException {@link AttendanceRefusal#ATTENDANCE_FIELD_ARCHIVED}
     */
    public void requireFieldInUse(int templateId, int fieldId) {
        if (attendanceRepository.isFieldArchived(templateId, fieldId)) {
            throw AttendanceRefusal.ATTENDANCE_FIELD_ARCHIVED.raise();
        }
    }
}
