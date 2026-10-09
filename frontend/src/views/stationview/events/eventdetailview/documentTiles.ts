/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AppointmentDocuments, ParticipantDocuments, RequiredDocumentStatus} from '@/api/generated/schema'

/** What a scan of a signed paper copy may be: a PDF or a photo. */
export const SCAN_TYPES = 'application/pdf,image/*'

/** One participant's copy of one document. */
export interface ParticipantCopy {
    memberId: number
    name: string
    document: RequiredDocumentStatus
}

/**
 * One person's documents as copies, each naming the person.
 *
 * @param person the person and their documents
 * @returns one copy per document, in the order the appointment asks for them
 */
export function copiesOf(person: ParticipantDocuments): ParticipantCopy[] {
    return person.documents.map(document => ({memberId: person.memberId, name: person.name, document}))
}

/**
 * Whether any copy of the people the reader acts for can be signed online, which the hint above the
 * documents then leads with.
 *
 * @param documents what the server answered for the reader
 * @returns true where a copy carries signature fields or offers its agreement to sign
 */
export function signsOnline(documents: AppointmentDocuments): boolean {
    return documents.own.some(person => person.documents.some(document =>
        document.signature !== null || document.agreementOffered))
}
