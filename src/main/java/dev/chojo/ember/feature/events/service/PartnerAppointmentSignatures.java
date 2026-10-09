/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.feature.events.entity.PartnerDocumentToSign;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The documents a partner station's appointment asks the members of this station to sign. Signing owns them;
 * a registration at a partner only asks for them, and a withdrawal lets the open ones go.
 */
public interface PartnerAppointmentSignatures {

    /** Knows of no documents to sign: a registration at a partner asks for nothing. */
    PartnerAppointmentSignatures NONE = new PartnerAppointmentSignatures() {
        @Override
        public List<PartnerDocumentToSign> registered(
                StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {
            return List.of();
        }

        @Override
        public void withdrawn(
                StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {}
    };

    /**
     * Takes on the documents the partner's appointment asks the member to sign on the date, once the partner
     * took the registration.
     *
     * @param session           who registered the member: the member or a guardian
     * @param partnerStationUid the station holding the appointment
     * @param eventId           the appointment, by its id there
     * @param eventDate         the date
     * @param memberUid         the member registered
     * @return each document filed for the member, with where its signatures stand; none where the appointment
     *         asks nothing to be signed, or the partner or this station cannot do it
     */
    List<PartnerDocumentToSign> registered(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid);

    /**
     * Lets go of the signatures still open on the member's documents for the appointment and date, once they no
     * longer take part. Signed documents stay.
     *
     * @param session           who withdrew the member
     * @param partnerStationUid the station holding the appointment
     * @param eventId           the appointment, by its id there
     * @param eventDate         the date
     * @param memberUid         the member withdrawn
     */
    void withdrawn(StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid);
}
