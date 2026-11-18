/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RegistrationStatus, type Places as PlacesResponse, type RegistrationCount} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

/**
 * How the places of one date of an appointment with a participant limit stand.
 *
 * <p>Only accepted sign-ups take a place, members of the station and partners' members alike. A
 * date is full once every place is taken, and also while anybody still waits on the waiting list:
 * a place that falls free goes to the first of them, not to whoever signs up next.
 */
export interface Places {
    /** The places taken, which may lie above the limit where a manager went beyond it. */
    taken: number
    limit: number
    /** How many wait on the waiting list. */
    waiting: number
    full: boolean
}

/**
 * The places of a date from what is taken and what waits, with the one rule for when it is full.
 *
 * @param taken   the accepted sign-ups
 * @param limit   the participant limit
 * @param waiting the sign-ups on the waiting list
 */
export function places(taken: number, limit: number, waiting: number): Places {
    return {taken, limit, waiting, full: taken >= limit || waiting > 0}
}

/** Anything that carries a sign-up's status, a member's or a partner's. */
type WithStatus = {status: RegistrationStatus | null}

/**
 * The places of a date, read from its sign-ups.
 *
 * @param limit    the appointment's participant limit, null or zero where it has none
 * @param statuses the sign-ups of the date, members' and partners' together
 * @return the places, or null where the appointment limits nothing
 */
export function placesOf(limit: number | null | undefined, statuses: WithStatus[]): Places | null {
    if (!limit) return null
    const countOf = (status: RegistrationStatus) => statuses.filter(entry => entry.status === status).length
    return places(countOf(RegistrationStatus.ACCEPTED), limit, countOf(RegistrationStatus.QUEUED))
}

/**
 * The places of a date as the server counts them, partners' members included.
 *
 * @return the places, or null where the appointment limits nothing
 */
export function placesFromResponse(response: PlacesResponse): Places | null {
    if (!response.limit) return null
    return places(response.accepted, response.limit, response.queued)
}

/**
 * The places of a date, read from the counts the station's sign-up list keeps per status.
 *
 * @param limit  the appointment's participant limit, null or zero where it has none
 * @param counts the counts of the date
 * @return the places, or null where the appointment limits nothing
 */
export function placesFromCounts(
    limit: number | null | undefined,
    counts: Pick<RegistrationCount, 'status' | 'count'>[],
): Places | null {
    if (!limit) return null
    const countOf = (status: RegistrationStatus) =>
        counts.filter(entry => entry.status === status).reduce((sum, entry) => sum + entry.count, 0)
    return places(countOf(RegistrationStatus.ACCEPTED), limit, countOf(RegistrationStatus.QUEUED))
}

/**
 * The line saying how the places stand: how many are taken while some are free, and once none is,
 * that the date is full and how many wait.
 *
 * <p>A date taken beyond its limit, where a manager let somebody in anyway, says the count as it is
 * ("13 von 12") rather than only full, so the overrun stays visible.
 */
export function placesLabel(state: Places, t: Translate): string {
    const counted = {taken: state.taken, limit: state.limit, waiting: state.waiting}
    if (state.taken > state.limit) {
        return state.waiting > 0
            ? t('registrationQueue.placesTakenWaiting', counted)
            : t('registrationQueue.placesTaken', counted)
    }
    if (!state.full) return t('registrationQueue.placesTaken', counted)
    if (state.waiting === 0) return t('registrationQueue.full')
    return t('registrationQueue.fullWaiting', counted)
}

/**
 * Whether a member signing up now would land on the waiting list rather than get a place.
 *
 * <p>Only where the appointment takes sign-ups as they come. One that confirms its sign-ups leaves
 * every sign-up waiting for the decision, full or not.
 */
export function signUpQueues(state: Places | null, requiresConfirmation: boolean): boolean {
    return state !== null && state.full && !requiresConfirmation
}
