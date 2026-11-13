/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import de.chojo.sadu.queries.converter.StandardValueConverter;

import java.time.Instant;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;

/**
 * A partner station's member signed up for a shared course. The parts they chose carry a partner
 * registration each, tied to this sign-up.
 *
 * @param id             the sign-up
 * @param courseId       the course
 * @param partnerId      this station's record of the partner the member belongs to
 * @param remoteMemberId the member, by the id their own station gave them
 * @param status         where the sign-up stands
 * @param registeredAt   when they signed up, or last signed up again
 */
public record EventCoursePartnerRegistration(
        int id, int courseId, int partnerId, UUID remoteMemberId, RegistrationStatus status, Instant registeredAt) {

    /** The columns {@link #map()} reads. */
    public static final String COLUMNS = "id, course_id, partner_id, remote_member_id, status, registered_at";

    /** Reads a sign-up from a row holding {@link #COLUMNS}. */
    public static RowMapping<EventCoursePartnerRegistration> map() {
        return row -> new EventCoursePartnerRegistration(
                row.getInt("id"),
                row.getInt("course_id"),
                row.getInt("partner_id"),
                row.get("remote_member_id", StandardValueConverter.UUID_STRING),
                row.getEnum("status", RegistrationStatus.class),
                row.get("registered_at", INSTANT_TIMESTAMP));
    }

    /**
     * Whether the sign-up still holds or waits for a place.
     *
     * @return true for a pending or accepted sign-up
     */
    public boolean active() {
        return status == RegistrationStatus.PENDING || status == RegistrationStatus.ACCEPTED;
    }
}
