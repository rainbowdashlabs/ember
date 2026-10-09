/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.signing.service;

import dev.chojo.ember.api.StationSession;
import dev.chojo.ember.api.refusal.EventRefusal;
import dev.chojo.ember.feature.events.entity.StationEvent;
import dev.chojo.ember.feature.events.service.EventRestrictionService;
import dev.chojo.ember.feature.generator.entity.RequirementSignature;
import dev.chojo.ember.feature.generator.service.AppointmentDocumentService;
import dev.chojo.ember.feature.members.service.GuardianPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * The agreement an appointment without registrations offers on its page.
 *
 * <p>Nobody registers for such an appointment, so nothing asks for its documents' signatures on its own.
 * Instead, everybody it is meant for, and their guardians for them, may sign a document the appointment asks
 * the participant or a guardian to sign: the participant's copy for the date is filed (the current one where
 * it can still be asked on, else a new one) and its signatures are asked for, exactly as a registration would
 * ({@link AppointmentSignatures#askAgreement}). Signing it counts as "I will come" ({@link AgreementAttendance}),
 * and whoever runs the appointment sees the signers ({@link AgreementSigners}). Offering it again while its
 * signatures stand asked for or signed asks nothing twice.
 */
@Singleton
public class AgreementOffers {
    private final AppointmentDocumentService appointments;
    private final AppointmentSignatures signatures;
    private final GuardianPolicy guardians;
    private final EventRestrictionService audience;
    private final RequirementSignatureStates states;

    @Inject
    public AgreementOffers(
            AppointmentDocumentService appointments,
            AppointmentSignatures signatures,
            GuardianPolicy guardians,
            EventRestrictionService audience,
            RequirementSignatureStates states) {
        this.appointments = appointments;
        this.signatures = signatures;
        this.guardians = guardians;
        this.audience = audience;
        this.states = states;
    }

    /**
     * Asks for the signatures of an appointment's agreement for a member, so the reader can sign it.
     *
     * @param session    the reader: the member, or a guardian of theirs
     * @param event      the appointment, already checked to be one the reader may see
     * @param date       the date
     * @param templateId the document whose agreement is signed
     * @param memberId   the member it is signed for
     * @return where its signatures stand, with the fields the reader can sign now
     */
    public RequirementSignature offer(
            StationSession session, StationEvent event, LocalDate date, int templateId, int memberId) {
        // TODO offer the agreement of a partner's federated event once its requirement travels with the share
        if (event.requiresRegistration()) throw EventRefusal.AGREEMENT_SIGNED_ON_REGISTERING.raise();
        if (!guardians.mayActFor(session.user(), memberId) || !audience.canRegister(event.id(), memberId, Set.of())) {
            throw EventRefusal.AGREEMENT_NOT_FOR_MEMBER.raise();
        }
        var template = appointments.signableFor(event, memberId).stream()
                .filter(candidate -> candidate.id() == templateId)
                .findFirst()
                .orElseThrow(EventRefusal.AGREEMENT_NOTHING_TO_SIGN::raise);
        signatures.askAgreement(
                event, date, template, memberId, session.member().id());
        return states.of(session, event.id(), date, List.of(memberId)).stream()
                .filter(signature -> signature.templateId() == templateId)
                .findFirst()
                .orElseThrow(EventRefusal.AGREEMENT_NOTHING_TO_SIGN::raise);
    }
}
