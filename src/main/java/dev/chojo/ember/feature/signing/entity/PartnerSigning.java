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
 * A signing request at a member's home installation for a document a partner station's appointment asks the
 * member to sign, with how far its signed copies have travelled back.
 *
 * @param id               the row
 * @param requestId        the signing request here
 * @param partnerId        this station's partnership with the station holding the appointment, or null once
 *                         it is gone
 * @param partnerStationUid the station holding the appointment
 * @param remoteEventId    the appointment, by its id there
 * @param eventDate        the date
 * @param remoteTemplateId the document, by the id of its template there
 * @param templateVersion  the version of that template
 * @param announcedAt      when the partner was told the document is asked for here, or null
 * @param deliveredSha256  the newest sealed copy the partner took, or null
 * @param deliveryAttempts how many times in a row sending failed
 */
public record PartnerSigning(
        int id,
        int requestId,
        @Nullable Integer partnerId,
        UUID partnerStationUid,
        int remoteEventId,
        LocalDate eventDate,
        int remoteTemplateId,
        int templateVersion,
        @Nullable Instant announcedAt,
        @Nullable String deliveredSha256,
        int deliveryAttempts) {

    /** The columns {@link #map()} reads from {@code partner_signing_request p}. */
    public static final String COLUMNS = """
            p.id, p.request_id, p.partner_id, p.partner_station_uid, p.remote_event_id, p.event_date,
            p.remote_template_id, p.template_version, p.announced_at, p.delivered_sha256, p.delivery_attempts""";

    public static RowMapping<PartnerSigning> map() {
        return row -> new PartnerSigning(
                row.getInt("id"),
                row.getInt("request_id"),
                row.getObject("partner_id", Integer.class),
                row.get("partner_station_uid", UUID_STRING),
                row.getInt("remote_event_id"),
                row.getObject("event_date", LocalDate.class),
                row.getInt("remote_template_id"),
                row.getInt("template_version"),
                row.get("announced_at", INSTANT_TIMESTAMP),
                row.getString("delivered_sha256"),
                row.getInt("delivery_attempts"));
    }

    /**
     * What a request answers, as it is written.
     *
     * @param stationId        the station of the member
     * @param requestId        the signing request here
     * @param partnerId        the partnership with the station holding the appointment
     * @param partnerStationUid that station
     * @param remoteEventId    the appointment there
     * @param eventDate        the date
     * @param remoteTemplateId the document there
     * @param templateVersion  the version of its template
     */
    public record Draft(
            int stationId,
            int requestId,
            int partnerId,
            UUID partnerStationUid,
            int remoteEventId,
            LocalDate eventDate,
            int remoteTemplateId,
            int templateVersion) {}
}
