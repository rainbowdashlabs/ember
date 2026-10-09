/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static de.chojo.sadu.queries.converter.StandardValueConverter.INSTANT_TIMESTAMP;
import static de.chojo.sadu.queries.converter.StandardValueConverter.UUID_STRING;

/**
 * Where a document an appointment asks partners to sign stands for one member of a partner station on a
 * date, as recorded by the station holding the appointment.
 *
 * @param id               the row
 * @param templateId       the template, or null once it was deleted
 * @param templateName     what the template was called
 * @param partnerStationUid the member's station
 * @param remoteMemberId   the member, by the id their registration names
 * @param state            where it stands; never {@link PartnerAgreementState#MISSING}, which is the absence
 *                         of a row
 * @param complete         whether the latest copy that came back has every field settled
 * @param confirmedByName  who confirmed a signed paper copy here, or null where nobody did
 * @param updatedAt        when it last changed
 * @param copies           how many sealed copies came back
 */
public record PartnerAgreement(
        int id,
        @Nullable Integer templateId,
        String templateName,
        UUID partnerStationUid,
        UUID remoteMemberId,
        PartnerAgreementState state,
        boolean complete,
        @Nullable String confirmedByName,
        Instant updatedAt,
        int copies) {

    /** The columns {@link #map()} reads from {@code partner_agreement a}, with the count of its copies. */
    public static final String COLUMNS = """
            a.id, a.template_id, a.template_name, a.partner_station_uid, a.remote_member_id, a.state, a.complete,
            a.confirmed_by_name, a.updated_at,
            (SELECT count(*) FROM partner_agreement_copy c WHERE c.agreement_id = a.id)::int AS copies""";

    public static RowMapping<PartnerAgreement> map() {
        return row -> new PartnerAgreement(
                row.getInt("id"),
                row.getObject("template_id", Integer.class),
                row.getString("template_name"),
                row.get("partner_station_uid", UUID_STRING),
                row.get("remote_member_id", UUID_STRING),
                row.getEnum("state", PartnerAgreementState.class),
                row.getBoolean("complete"),
                row.getString("confirmed_by_name"),
                row.get("updated_at", INSTANT_TIMESTAMP),
                row.getInt("copies"));
    }

    /**
     * The row as it is written for a member of a partner station.
     *
     * @param stationId         the station holding the appointment
     * @param eventId           the appointment
     * @param eventDate         the date
     * @param templateId        the template
     * @param templateName      what it is called
     * @param partnerId         the partnership
     * @param partnerStationUid the member's station
     * @param partnerName       what the partnership calls it, or null
     * @param remoteMemberId    the member
     * @param retentionMonths   how long the template keeps signed documents, or null
     * @param retainUntil       until when the row is kept
     */
    public record Key(
            int stationId,
            int eventId,
            LocalDate eventDate,
            int templateId,
            String templateName,
            int partnerId,
            UUID partnerStationUid,
            @Nullable String partnerName,
            UUID remoteMemberId,
            @Nullable Integer retentionMonths,
            Instant retainUntil) {}
}
