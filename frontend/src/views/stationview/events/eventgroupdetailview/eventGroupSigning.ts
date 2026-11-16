/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {appointmentDocuments} from '@/api'
import {RequirementSignatureState, type EventGroupPartState} from '@/api/generated/schema'
import type {SigningStep} from '@/composables/useRegistrationSigningStep'
import {stationDayOf} from '@/util/format'

/**
 * The signing step a sign-up for an event group ends on: the event group's documents the member still has to
 * sign. Such a document is signed once for the whole event group, so the step is the first chosen part, in
 * date order, that shows a copy of one still waiting; documents a part asks for on its own are signed on
 * that part's page. Null where nothing waits, or where the documents cannot be read; the sign-up stands
 * either way.
 *
 * @param parts    every part of the event group
 * @param chosen   the parts the member holds now
 * @param memberId the member signed up
 * @param timezone the station's timezone, which names the day of a part
 * @returns the step, or null
 */
export async function eventGroupSigningStep(
    parts: EventGroupPartState[],
    chosen: number[],
    memberId: number,
    timezone: string | null | undefined,
): Promise<SigningStep | null> {
    const held = parts
        .filter(part => chosen.includes(part.eventId) && !part.calledOff)
        .sort((a, b) => a.startTime.localeCompare(b.startTime))
    try {
        for (const part of held) {
            const date = stationDayOf(new Date(part.startTime), timezone)
            const documents = await appointmentDocuments.documentsToBring(part.eventId, date)
            const ofEventGroup = new Set(documents.required.filter(required => required.fromEventGroup).map(required => required.templateId))
            const copies = documents.own
                .filter(person => person.memberId === memberId)
                .flatMap(person => person.documents
                    .filter(document => ofEventGroup.has(document.templateId))
                    .filter(document => document.signature?.state === RequirementSignatureState.OPEN)
                    .map(document => ({memberId: person.memberId, name: person.name, document})))
            if (copies.length > 0) return {eventId: part.eventId, date, copies}
        }
    } catch {
        return null
    }
    return null
}
