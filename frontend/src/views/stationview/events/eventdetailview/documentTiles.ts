/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AppointmentDocuments, ParticipantDocuments, RequiredDocumentStatus, RequiredTemplate} from '@/api/generated/schema'

/** One participant's copy of one document. */
export interface ParticipantCopy {
    memberId: number
    name: string
    document: RequiredDocumentStatus
}

/**
 * One document an appointment asks for, as its tile shows it: the copies of the participants the
 * reader acts for, and for an event manager every participant's.
 */
export interface DocumentTile {
    template: RequiredTemplate
    own: ParticipantCopy[]
    /** Every participant's copy, or null where the reader is no event manager. */
    participants: ParticipantCopy[] | null
}

function copiesOf(participants: readonly ParticipantDocuments[], templateId: number): ParticipantCopy[] {
    return participants.flatMap(participant => {
        const document = participant.documents.find(candidate => candidate.templateId === templateId)
        return document ? [{memberId: participant.memberId, name: participant.name, document}] : []
    })
}

/**
 * The documents of an appointment turned from one list per participant into one tile per document.
 *
 * @param documents what the server answered for the reader
 * @returns one tile per document, in the order the appointment asks for them
 */
export function documentTiles(documents: AppointmentDocuments): DocumentTile[] {
    return documents.required.map(template => ({
        template,
        own: copiesOf(documents.own, template.templateId),
        participants: documents.participants ? copiesOf(documents.participants, template.templateId) : null,
    }))
}
