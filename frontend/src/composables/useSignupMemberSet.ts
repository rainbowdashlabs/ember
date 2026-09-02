/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type ComputedRef} from 'vue'
import {RegistrationStatus, type EventRegistrationEntry, type FederatedEventRegistration, type StationEvent} from '@/api/events'

/**
 * The people holding a place on one evening, ready to be handed to anything that works from a list
 * of members.
 *
 * <p>Everything a caller has to say about the set is here, including what it could not hold: a count
 * that quietly leaves people out is worse than no count at all.
 */
export interface SignupMemberSet {
    /** The members of this station who hold a place, each one once. */
    memberIds: number[]
    /** How many of them there are, which is how many rows a list made from this will have. */
    count: number
    /** Places held by members of partner stations, who are not members here and cannot be listed. */
    guestCount: number
    /** Places held by people who have since left, who are named in a list but never become a row. */
    formerCount: number
    /** Whether there is anybody at all to make something from. */
    usable: boolean
}

const EMPTY: SignupMemberSet = {memberIds: [], count: 0, guestCount: 0, formerCount: 0, usable: false}

/**
 * What a caller has to read for the set to be resolved. Every entry is a getter rather than a ref so
 * that props, refs and computed values can all be handed over without wrapping.
 */
export interface SignupMemberSetSources {
    /** The appointment being looked at, or null while it is still loading. */
    event: () => StationEvent | null
    /** The one date the screen is focused on. Without it there is no occurrence to read. */
    effectiveDate: () => string | null
    /** Every sign-up of the appointment, across all of its occurrences. */
    registrations: () => EventRegistrationEntry[]
    /** Every sign-up made from a partner station, across all of its occurrences. */
    federatedRegistrations: () => FederatedEventRegistration[]
    /** The members this station has today, which is who a list can be materialised for. */
    currentMemberIds: () => number[]
}

/**
 * Turns one occurrence of an appointment into the people who hold a place on it.
 *
 * <p>The occurrence is the point. Sign-ups are kept per appointment <em>and</em> date, and the list
 * the tab reads is asked for without a date, so it carries every Tuesday there has ever been. Taking
 * it as it comes would make "the fourteen who are coming" the sum of a year of Tuesdays, and nothing
 * downstream would notice. The date decides, and only rows carrying it count.
 *
 * <p>Only a place actually taken counts, which is the same set the appointment measures its minimum
 * attendance against. An appointment that does not have to be signed up for has no such set at all:
 * its tab holds refusals, and every row there is somebody who is not coming.
 *
 * @param sources where to read the appointment, the date and the lists from
 * @return the set, recomputed whenever any of its sources changes
 */
export function useSignupMemberSet(sources: SignupMemberSetSources): ComputedRef<SignupMemberSet> {
    return computed(() => {
        const event = sources.event()
        const date = sources.effectiveDate()
        if (!event?.requiresRegistration || !date) return EMPTY

        const current = new Set(sources.currentMemberIds())
        const seen = new Set<number>()
        const memberIds: number[] = []
        let formerCount = 0

        for (const registration of sources.registrations()) {
            if (registration.status !== RegistrationStatus.ACCEPTED) continue
            if (registration.eventDate !== date) continue
            if (seen.has(registration.memberId)) continue
            seen.add(registration.memberId)
            if (current.has(registration.memberId)) memberIds.push(registration.memberId)
            else formerCount++
        }

        const guestCount = sources.federatedRegistrations().filter(
            entry => entry.registration.status === RegistrationStatus.ACCEPTED
                && entry.registration.eventDate === date,
        ).length

        return {memberIds, count: memberIds.length, guestCount, formerCount, usable: memberIds.length > 0}
    })
}
