/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed} from 'vue'
import type {EnrichedFederationRegistration, PartnerPlacesView, RegistrationResponse} from '@/api/generated/schema'
import {placesOf} from '@/util/eventPlaces'
import {queueOf} from '@/util/registrationQueue'

/** What one date's sign-up list is drawn from, as a screen holds it. */
export interface DateSignUps {
    /** The appointment's participant limit. */
    limit: () => number | null | undefined
    /** The station's members' sign-ups of the date. */
    registrations: () => RegistrationResponse[]
    /** The partners' members' sign-ups of the date. */
    partners: () => EnrichedFederationRegistration[]
    /** What each partner may do with the appointment. */
    partnerPlaces: () => PartnerPlacesView[]
}

/**
 * How the places and the waiting list of one date stand, for whoever decides its sign-ups.
 *
 * <p>The appointment's sign-up list and the station's page of open sign-ups both draw the same
 * thing from the same three lists: the places counted over members and partners' members, the
 * waiting list in its order, and which partners decide about their own people, whose rows on the
 * list then offer nothing.
 */
export function useDatePlaces(source: DateSignUps) {
    const places = computed(() => placesOf(
        source.limit(),
        [...source.registrations(), ...source.partners().map(entry => entry.registration)]))

    const queue = computed(() => queueOf(source.registrations(), source.partners()))

    /** Whether a partner decides about its own people rather than this station. */
    function partnerDecides(partnerId: number): boolean {
        return source.partnerPlaces().some(place => place.partnerId === partnerId && place.partnerConfirms)
    }

    return {places, queue, partnerDecides}
}
