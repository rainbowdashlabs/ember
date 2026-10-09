/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.entity;

import de.chojo.sadu.mapper.rowmapper.RowMapping;

/**
 * A request for signatures on a participant's copy of a document an appointment asks for on one date.
 *
 * @param templateId the document the appointment asks for
 * @param memberId   the participant the copy was generated for
 * @param request    the request
 */
public record AppointmentRequest(int templateId, int memberId, SignatureRequest request) {

    /** Maps a request read with the template and member of its generation beside it. */
    public static RowMapping<AppointmentRequest> map() {
        var requests = SignatureRequest.map();
        return row -> new AppointmentRequest(
                row.getInt("generation_template_id"), row.getInt("generation_member_id"), requests.map(row));
    }
}
