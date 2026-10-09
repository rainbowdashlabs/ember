/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PaperState,
    PartnerAgreementState,
    RequirementSignatureState,
    type ParticipantDocuments,
    type PartnerSigner,
    type PartnerSignerDocument,
    type RequiredDocumentStatus,
} from '@/api/generated/schema'
import type {ParticipantCopy} from './documentTiles'

/**
 * The three groups a manager's view of one document sorts every person into: what needs the manager now,
 * what is still missing and only needs waiting for, and what is done.
 */
export const DocumentGroup = {
    TODO: 'todo',
    MISSING: 'missing',
    DONE: 'done',
} as const

export type DocumentGroup = typeof DocumentGroup[keyof typeof DocumentGroup]

/** The groups in the order the section shows them. */
export const DOCUMENT_GROUPS: readonly DocumentGroup[] = [DocumentGroup.TODO, DocumentGroup.MISSING, DocumentGroup.DONE]

/** One participant of this station with their copy of the document. */
export interface ParticipantEntry {
    kind: 'participant'
    copy: ParticipantCopy
    /** When a signed agreement was withdrawn while the registration stood, until it is signed anew. */
    withdrawnAt: string | null
}

/** One member a partner station registered, with where they stand with the document. */
export interface PartnerEntry {
    kind: 'partner'
    signer: PartnerSigner
    document: PartnerSignerDocument
}

/** One person on the manager's view of a document. */
export type DocumentEntry = ParticipantEntry | PartnerEntry

/** Every person on the manager's view of a document, by group. */
export type GroupedEntries = Record<DocumentGroup, DocumentEntry[]>

const SETTLED_SIGNATURES: readonly RequirementSignatureState[] = [
    RequirementSignatureState.SIGNED,
    RequirementSignatureState.PAPER_CONFIRMED,
    RequirementSignatureState.WAIVED,
]

/**
 * Whether a copy needs nothing more: its signed paper copy was confirmed, or its signatures are signed,
 * confirmed on paper or waived.
 *
 * @param document the copy
 * @returns true where nothing more is needed
 */
export function isSettled(document: RequiredDocumentStatus): boolean {
    if (document.paper?.state === PaperState.CONFIRMED) return true
    const state = document.signature?.state
    return state !== undefined && SETTLED_SIGNATURES.includes(state)
}

/**
 * Whether a copy has a field still open that nobody can sign, which only confirming it on paper or
 * waiving it settles.
 *
 * @param document the copy
 * @returns true where such a field is open
 */
export function waitsForNobody(document: RequiredDocumentStatus): boolean {
    return document.signature?.fields.some(field =>
        field.nobodyCanSign && field.state === RequirementSignatureState.OPEN) ?? false
}

function needsManager(document: RequiredDocumentStatus, withdrawnAt: string | null): boolean {
    if (document.paper?.state === PaperState.SUBMITTED) return true
    if (document.signature?.state === RequirementSignatureState.REVOKED) return true
    if (waitsForNobody(document)) return true
    return withdrawnAt !== null && !isSettled(document)
}

/**
 * The group of a participant's copy. A scan waiting for review, a withdrawn agreement and a field nobody
 * can sign need the manager; signed, confirmed on paper and waived copies are done; everything else waits
 * for the participant.
 *
 * @param document    the copy
 * @param withdrawnAt when the participant's registration was flagged for a withdrawn agreement, or null
 * @returns the group
 */
export function copyGroup(document: RequiredDocumentStatus, withdrawnAt: string | null): DocumentGroup {
    if (needsManager(document, withdrawnAt)) return DocumentGroup.TODO
    return isSettled(document) ? DocumentGroup.DONE : DocumentGroup.MISSING
}

/**
 * The group of a partner member's document. A missing signature has a paper copy to confirm and a
 * withdrawn one needs looking at; a complete signature and a confirmed paper copy are done; one being
 * signed at the partner waits.
 *
 * @param document where the member stands with the document
 * @returns the group
 */
export function partnerGroup(document: PartnerSignerDocument): DocumentGroup {
    if (document.state === PartnerAgreementState.MISSING || document.state === PartnerAgreementState.WITHDRAWN) {
        return DocumentGroup.TODO
    }
    if (document.state === PartnerAgreementState.PAPER_CONFIRMED) return DocumentGroup.DONE
    const complete = document.state === PartnerAgreementState.SIGNED && document.complete
    return complete ? DocumentGroup.DONE : DocumentGroup.MISSING
}

function groupOf(entry: DocumentEntry): DocumentGroup {
    return entry.kind === 'participant'
        ? copyGroup(entry.copy.document, entry.withdrawnAt)
        : partnerGroup(entry.document)
}

function participantEntries(participants: readonly ParticipantDocuments[], templateId: number): DocumentEntry[] {
    return participants.flatMap(participant => {
        const document = participant.documents.find(candidate => candidate.templateId === templateId)
        if (!document) return []
        const entry: ParticipantEntry = {
            kind: 'participant',
            copy: {memberId: participant.memberId, name: participant.name, document},
            withdrawnAt: participant.agreementWithdrawnAt,
        }
        return [entry]
    })
}

function partnerEntries(signers: readonly PartnerSigner[], templateId: number): DocumentEntry[] {
    return signers.flatMap(signer => {
        const document = signer.documents.find(candidate => candidate.templateId === templateId)
        if (!document) return []
        const entry: PartnerEntry = {kind: 'partner', signer, document}
        return [entry]
    })
}

/**
 * Every person on the manager's view of one document, the station's own participants first and the
 * members of partner stations after them, sorted into the three groups.
 *
 * @param participants every participant of the date and their copies
 * @param signers      the members partner stations registered
 * @param templateId   the document
 * @returns the people of each group, in the order they came
 */
export function groupEntries(
    participants: readonly ParticipantDocuments[],
    signers: readonly PartnerSigner[],
    templateId: number,
): GroupedEntries {
    const grouped: GroupedEntries = {todo: [], missing: [], done: []}
    for (const entry of [...participantEntries(participants, templateId), ...partnerEntries(signers, templateId)]) {
        grouped[groupOf(entry)].push(entry)
    }
    return grouped
}
