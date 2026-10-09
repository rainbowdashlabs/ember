/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {PartnerSigner, PartnerSignerDocument} from '@/api/generated/schema'

/**
 * Where the members partner stations registered for an appointment on a date stand with the documents it
 * asks them to sign, for whoever manages its registrations.
 */
export async function listSigners(eventId: number, date: string): Promise<PartnerSigner[]> {
    const res = await client.get<PartnerSigner[]>(`/events/${eventId}/partner-agreements`, {params: {date}})
    return res.data
}

/** Confirms a signed paper copy of a document for a member a partner station registered. */
export async function confirmPaper(
    eventId: number,
    registrationId: number,
    templateId: number,
): Promise<PartnerSignerDocument> {
    const res = await client.post<PartnerSignerDocument>(
        `/events/${eventId}/partner-agreements/paper`, {registrationId, templateId})
    return res.data
}

/** Where the newest signed copy that came back from the partner station is served. */
export function copyUrl(eventId: number, agreementId: number): string {
    return `/events/${eventId}/partner-agreements/${agreementId}/copy`
}
