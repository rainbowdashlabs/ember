/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import java.time.LocalDate;

/**
 * Where a participant's copy of a document an appointment asks for belongs.
 *
 * @param eventId    the appointment
 * @param eventDate  the date of it the copy is for
 * @param memberId   the participant
 * @param templateId the document the appointment asks for
 */
public record AppointmentCopy(int eventId, LocalDate eventDate, int memberId, int templateId) {}
