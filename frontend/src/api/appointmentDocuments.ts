/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {GeneratedDocumentResponse, ParticipantDocuments, RequiredTemplate} from '@/api/generated/schema'

/** Who asks for documents to bring: an appointment or an appointment template, by id. */
export interface RequirementOwner {
    kind: 'events' | 'event-templates'
    id: number
}

/** The document templates appointments may ask participants to bring. */
export async function offeredTemplates(): Promise<RequiredTemplate[]> {
    const res = await client.get<RequiredTemplate[]>('/document-requirements/templates')
    return res.data
}

/** The documents an appointment or an appointment template asks for, in their order. */
export async function listRequirements(owner: RequirementOwner): Promise<RequiredTemplate[]> {
    const res = await client.get<RequiredTemplate[]>(`/${owner.kind}/${owner.id}/document-requirements`)
    return res.data
}

/** Sets the documents an appointment or an appointment template asks for. */
export async function setRequirements(owner: RequirementOwner, templateIds: number[]): Promise<RequiredTemplate[]> {
    const res = await client.put<RequiredTemplate[]>(`/${owner.kind}/${owner.id}/document-requirements`, {templateIds})
    return res.data
}

/** The documents to bring for the reader and the members in their care who take part on a date. */
export async function documentsToBring(eventId: number, date: string): Promise<ParticipantDocuments[]> {
    const res = await client.get<ParticipantDocuments[]>(`/events/${eventId}/documents-to-bring`, {params: {date}})
    return res.data
}

/** Generates a participant's copy of a document the appointment asks for and files it with them. */
export async function generateToBring(
    eventId: number,
    date: string,
    templateId: number,
    memberId: number,
): Promise<GeneratedDocumentResponse> {
    const res = await client.post<GeneratedDocumentResponse>(
        `/events/${eventId}/documents-to-bring/${templateId}/members/${memberId}`, null, {params: {date}})
    return res.data
}
