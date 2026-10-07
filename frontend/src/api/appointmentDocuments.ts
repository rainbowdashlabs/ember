/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {TemplatePages} from './documentTemplates'
import type {AppointmentDocuments, GeneratedDocumentResponse, RequiredTemplate, TemplatePage} from '@/api/generated/schema'

/** Who asks for documents to bring: an appointment or an appointment template, by id. */
export interface RequirementOwner {
    kind: 'events' | 'event-templates'
    id: number
}

/** One page of the document templates appointments may ask participants to bring. */
export const offeredTemplates: TemplatePages = async query => {
    const res = await client.get<TemplatePage>('/document-requirements/templates', {params: query})
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

/**
 * The documents an appointment asks for on a date: the list for every reader, the copies of the reader
 * and the members in their care who take part, and every participant's for whoever manages the
 * registrations.
 */
export async function documentsToBring(eventId: number, date: string): Promise<AppointmentDocuments> {
    const res = await client.get<AppointmentDocuments>(`/events/${eventId}/documents-to-bring`, {params: {date}})
    return res.data
}

/** Where the first page of a document the appointment asks for is served, for whoever sees the appointment. */
export function toBringPictureUrl(eventId: number, templateId: number, size: number): string {
    return `/events/${eventId}/documents-to-bring/${templateId}/picture?size=${size}`
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
