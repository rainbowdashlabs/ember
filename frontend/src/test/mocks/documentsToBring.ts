/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    DocumentTemplateKind,
    PaperState,
    PartnerAgreementState,
    RequirementSignatureState,
    RequirementStatus,
    type ParticipantDocuments,
    type PaperSubmission,
    type PartnerSigner,
    type RequiredDocumentStatus,
    type RequiredTemplate,
    type RequirementSignature,
} from '@/api/generated/schema'

/**
 * The documents an appointment asks participants to bring, as the tests of the appointment page use them:
 * one consent form, template 8 of appointment 3 on 8 October 2026, and Lena Schmidt (member 11) who
 * brings it.
 */
export const CONSENT: RequiredTemplate = {
    templateId: 8,
    name: 'Einverständnis',
    kind: DocumentTemplateKind.PDF,
    version: 1,
    archived: false,
    lastUsedAt: null,
}

/** The scan 20 of Lena's signed paper copy, filed as document 40, in the given state. */
export function scan(state: PaperState, rejectReason: string | null = null): PaperSubmission {
    return {
        id: 20,
        eventId: 3,
        eventDate: '2026-10-08',
        templateId: 8,
        memberId: 11,
        documentId: 40,
        state,
        submittedAt: '2026-10-07T10:00:00Z',
        reviewedAt: state === PaperState.SUBMITTED ? null : '2026-10-07T12:00:00Z',
        rejectReason,
    }
}

/** Lena's copy of the consent, not generated unless a document is named. */
export function consentCopy(overrides: Partial<RequiredDocumentStatus> = {}): RequiredDocumentStatus {
    return {
        templateId: 8,
        name: 'Einverständnis',
        status: RequirementStatus.NOT_GENERATED,
        documentId: null,
        generatedAt: null,
        outdated: false,
        paper: null,
        signature: null,
        agreementOffered: false,
        ...overrides,
    }
}

/** Lena with her copy of the consent. */
export function lena(
    paper: PaperSubmission | null = null,
    signature: RequirementSignature | null = null,
    agreementOffered = false,
): ParticipantDocuments {
    return {
        memberId: 11,
        name: 'Lena Schmidt',
        documents: [consentCopy({paper, signature, agreementOffered})],
        agreementWithdrawnAt: null,
    }
}

/** Lena's copy asks her and her first guardian; the reader may sign the participant field where `yours`. */
export function asked(
    participant: RequirementSignatureState,
    guardian: RequirementSignatureState,
    yours = false,
): RequirementSignature {
    const fields = [
        {id: 70, name: 'participant', signerName: 'Lena Schmidt', state: participant, yours, nobodyCanSign: false},
        {id: 71, name: 'guardian1', signerName: 'Anna Schmidt', state: guardian, yours: false, nobodyCanSign: false},
    ]
    const open = fields.some(field => field.state === RequirementSignatureState.OPEN)
    return {
        templateId: 8,
        memberId: 11,
        requestUid: '0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e10',
        state: open ? RequirementSignatureState.OPEN : RequirementSignatureState.SIGNED,
        fields,
        withdrawable: false,
        withdrawnAt: null,
    }
}

/** A member of a partner station, registration 90, standing with the consent as given. */
export function partnerSigner(state: PartnerAgreementState, member: PartnerSigner['member'] = null): PartnerSigner {
    return {
        registrationId: 90,
        member,
        documents: [{
            templateId: 8,
            name: 'Einverständnis',
            state,
            complete: state === PartnerAgreementState.SIGNED,
            agreementId: state === PartnerAgreementState.MISSING ? null : 5,
            copies: state === PartnerAgreementState.SIGNED ? 1 : 0,
            confirmedByName: null,
        }],
    }
}
