/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PaperState,
    type AppointmentDocuments,
    type PaperSubmission,
    type ParticipantDocuments,
} from '@/api/generated/schema'

/**
 * The message confirming a scan just handed in: it waits for the event managers, or a manager's own
 * scan counts as confirmed at once.
 *
 * @param paper the submission the server answered with
 * @returns the translation key of the message
 */
export function scanReceiptKey(paper: PaperSubmission): string {
    return paper.state === PaperState.CONFIRMED
        ? 'events.documents.scanReceivedConfirmed'
        : 'events.documents.scanReceived'
}

function withPaper(participants: ParticipantDocuments[], paper: PaperSubmission): ParticipantDocuments[] {
    return participants.map(participant => participant.memberId !== paper.memberId ? participant : {
        ...participant,
        documents: participant.documents.map(document =>
            document.templateId === paper.templateId ? {...document, paper} : document),
    })
}

/**
 * The documents of an appointment with a scan just handed in standing on the participant's copy, so the
 * screen shows it at once, before the documents are read again.
 *
 * @param documents what the server last answered
 * @param paper     the submission the server answered the hand-in with
 * @returns the documents with the scan on the copy it was handed in for
 */
export function withScan(documents: AppointmentDocuments, paper: PaperSubmission): AppointmentDocuments {
    return {
        ...documents,
        own: withPaper(documents.own, paper),
        participants: documents.participants ? withPaper(documents.participants, paper) : null,
    }
}
