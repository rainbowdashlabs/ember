/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {appointmentDocuments, documents} from '@/api'
import {RequirementStatus, type RequiredDocumentStatus} from '@/api/generated/schema'
import {downloadAuthed} from '@/util/downloadAuthed'

/**
 * Whether a participant's copy has to be generated before it can be handed over: there is none yet,
 * or the template changed since it was generated.
 */
export function needsGenerating(document: RequiredDocumentStatus): boolean {
    return document.status === RequirementStatus.NOT_GENERATED || document.documentId == null || document.outdated
}

/**
 * Hands a participant their copy of a document the appointment asks for: generates and files it
 * where there is none or the template changed since, and downloads the filed copy.
 *
 * @param eventId  the appointment
 * @param date     the date of the appointment
 * @param memberId the participant
 * @param document the document as it stands for them
 * @return the filed copy
 */
export async function fetchCopy(
    eventId: number,
    date: string,
    memberId: number,
    document: RequiredDocumentStatus,
): Promise<number> {
    const current = needsGenerating(document) ? null : document.documentId ?? null
    const documentId = current
        ?? (await appointmentDocuments.generateToBring(eventId, date, document.templateId, memberId)).documentId
    await downloadAuthed(documents.contentUrl(documentId), `${document.name}.pdf`)
    return documentId
}
