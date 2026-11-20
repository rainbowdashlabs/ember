/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {RemotePlaces} from '@/api/generated/schema'

type Translate = (key: string, values?: Record<string, unknown>) => string

/** What the station holding an appointment or an event group gave this one, in words, and whether the reader chooses. */
export interface OurPartnerPlaces {
    /** What was given, or empty where the holder decides about each of this station's members as always. */
    summary: string
    /** Whether the reader may confirm this station's own sign-ups there. */
    weDecide: boolean
}

/**
 * Reads the places the holding station handed this one, for an appointment or for a whole event group.
 *
 * <p>Absent places are the ordinary arrangement: the holder decides about each of this station's
 * members. Present means this station chooses who comes, and where a number was named, how many. The
 * summary never claims how many are left: only the holder can count that, since this station sees its
 * own members' places and not anybody else's, and its answer when they are full is the one that counts.
 * Choosing is offered only to a reader who keeps the station's sign-ups, or the holder would refuse it.
 *
 * @param givenKey the text for a named number of places, which an event group words for all of it
 */
export function ourPartnerPlaces(
    places: RemotePlaces | null | undefined,
    keepsSignUps: boolean,
    t: Translate,
    givenKey = 'eventsUpcoming.placesGiven',
): OurPartnerPlaces {
    if (!places) return {summary: '', weDecide: false}
    const summary = places.slotBudget === null
        ? t('eventsUpcoming.placesUncapped')
        : t(givenKey, {total: places.slotBudget})
    return {summary, weDecide: places.decidesItself && keepsSignUps}
}
