/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RegistrationStatus, type RegistrationCount} from '@/api/generated/schema'
import {placesFromCounts, type Places} from '@/util/eventPlaces'

/** What the sign-ups of one date add up to, as a line of the list of dates shows them. */
export interface RegistrationSummary {
    accepted: number
    pending: number
    declined: number
    /** How many wait on the waiting list. */
    queued: number
    /** Every answer counted, which is whether there is anything to show at all. */
    total: number
    /** How the places stand, null where the appointment limits nothing. */
    places: Places | null
}

/**
 * The sign-ups of one date, added up from the counts the station keeps per status.
 *
 * @param limit  the appointment's participant limit, null where it has none
 * @param counts the counts of the date
 */
export function registrationSummaryOf(
    limit: number | null,
    counts: Pick<RegistrationCount, 'status' | 'count'>[],
): RegistrationSummary {
    const countOf = (status: RegistrationStatus) => counts.find(c => c.status === status)?.count ?? 0
    const accepted = countOf(RegistrationStatus.ACCEPTED)
    const pending = countOf(RegistrationStatus.PENDING)
    const declined = countOf(RegistrationStatus.DECLINED)
    const queued = countOf(RegistrationStatus.QUEUED)
    return {
        accepted,
        pending,
        declined,
        queued,
        total: accepted + pending + declined + queued,
        places: placesFromCounts(limit, counts),
    }
}
