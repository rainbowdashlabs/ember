/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.attendance.service;

import dev.chojo.ember.feature.attendance.entity.AttendanceSession;
import dev.chojo.ember.feature.attendance.entity.SessionAudience;
import dev.chojo.ember.feature.attendance.entity.TemplateGroup;
import dev.chojo.ember.feature.attendance.repository.AttendanceRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Whom a sheet expects, answered once for everything that fills, shows or prints it.
 *
 * <p>A sheet started with an audience of its own keeps it. One started with nothing follows its
 * template, whose user types and groups add up the same way a sheet's do.
 */
@Singleton
public class AttendanceAudienceService {
    private final AttendanceRepository attendanceRepository;

    @Inject
    public AttendanceAudienceService(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Whom a template expects on its sheets: the members of its groups and everybody of its user
     * types.
     *
     * @param templateId the template
     * @return its user types and its groups, the groups in their order
     */
    public SessionAudience templateAudience(int templateId) {
        return new SessionAudience(
                attendanceRepository.findTemplateUserTypes(templateId),
                attendanceRepository.findTemplateGroups(templateId).stream()
                        .map(TemplateGroup::groupId)
                        .toList());
    }

    /**
     * Whom a sheet expects: what it was told when it was started, or its template's user types and
     * groups where it was told nothing.
     *
     * @param session the sheet
     * @return the audience that decides who stands on it
     */
    public SessionAudience audienceOf(AttendanceSession session) {
        var own = attendanceRepository.findSessionAudience(session.id());
        return own.namesNobody() ? templateAudience(session.templateId()) : own;
    }
}
