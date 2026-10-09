/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.events.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.PartnerAgreementOffer;
import dev.chojo.ember.feature.events.entity.PartnerDocumentToSign;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The documents a partner station's appointment asks the members of this station to sign. Signing owns them;
 * a registration at a partner only asks for them, and a withdrawal lets the open ones go. An appointment without
 * registrations offers them on its page instead, and signing one says the member will come.
 */
public interface PartnerAppointmentSignatures {

    /** Knows of no documents to sign: a registration at a partner asks for nothing, and nothing is offered. */
    PartnerAppointmentSignatures NONE = new PartnerAppointmentSignatures() {
        @Override
        public List<PartnerDocumentToSign> registered(
                StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {
            return List.of();
        }

        @Override
        public void withdrawn(
                StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {}

        @Override
        public List<PartnerAgreementOffer> offers(
                StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate) {
            return List.of();
        }

        @Override
        public List<PartnerDocumentToSign> offered(
                StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid) {
            throw EventRefusal.AGREEMENT_NOTHING_TO_SIGN.raise();
        }
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

    /**
     * The documents a partner's appointment without registrations offers on its page for a date, as the partner
     * hands them out.
     *
     * @param session           the reader
     * @param partnerStationUid the station holding the appointment
     * @param eventId           the appointment, by its id there
     * @param eventDate         the date
     * @return the documents; none where the appointment asks nothing to be signed, or this station keeps no
     *         documents or no longer has the partnership
     */
    List<PartnerAgreementOffer> offers(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate);

    /**
     * Takes on the documents a partner's appointment without registrations offers for a member on a date, the
     * same way as a registration does, so the reader can sign them; signing says the member will come. Offering
     * again while they stand asked for or signed asks nothing twice.
     *
     * @param session           the reader: the member, or a guardian of theirs
     * @param partnerStationUid the station holding the appointment
     * @param eventId           the appointment, by its id there
     * @param eventDate         the date
     * @param memberUid         the member it is signed for
     * @return each document filed for the member, with where its signatures stand
     */
    List<PartnerDocumentToSign> offered(
            StationSession session, UUID partnerStationUid, int eventId, LocalDate eventDate, UUID memberUid);
}
