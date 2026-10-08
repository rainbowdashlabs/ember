/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {appointmentDocuments} from '@/api'
import {RequirementSignatureState, type ParticipantDocuments, type RequiredDocumentStatus} from '@/api/generated/schema'

/** One participant's copy of a document the appointment asks for that still waits for a signature. */
export interface SigningStepCopy {
    memberId: number
    name: string
    document: RequiredDocumentStatus
}

/** The step a registration ends on: the copies of the people just registered that still need signing. */
export interface SigningStep {
    eventId: number
    date: string
    copies: SigningStepCopy[]
}

function waitingCopies(participants: ParticipantDocuments[], memberIds: number[]): SigningStepCopy[] {
    return participants
        .filter(participant => memberIds.includes(participant.memberId))
        .flatMap(participant => participant.documents
            .filter(document => document.signature?.state === RequirementSignatureState.OPEN)
            .map(document => ({memberId: participant.memberId, name: participant.name, document})))
}

/**
 * The signing step a registration ends on.
 *
 * <p>Registering for an appointment whose documents to bring ask for signatures generates the copies and
 * asks for their signatures on the server, before the registration is answered. Once it is, the copies of
 * the people just registered that still wait for a signature are offered here: signed online now, a scan
 * of the signed paper handed in, or left for later, where they stay among the open tasks. Nothing is
 * offered where nothing waits, or where the copies cannot be read; the registration stands either way.
 */
export function useRegistrationSigningStep() {
    const signingStep = ref<SigningStep | null>(null)

    /**
     * Offers the copies of the people just registered for one date that still wait for a signature.
     *
     * @param eventId   the appointment
     * @param date      the date registered for
     * @param memberIds the people whose registration landed
     */
    async function offerSigning(eventId: number, date: string, memberIds: number[]) {
        if (memberIds.length === 0) return
        try {
            const documents = await appointmentDocuments.documentsToBring(eventId, date)
            const copies = waitingCopies(documents.own, memberIds)
            signingStep.value = copies.length > 0 ? {eventId, date, copies} : null
        } catch {
            signingStep.value = null
        }
    }

    function closeSigningStep() {
        signingStep.value = null
    }

    return {signingStep, offerSigning, closeSigningStep}
}
