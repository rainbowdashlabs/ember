/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {RegistrationStatus, type EnrichedFederationRegistration} from '@/api/generated/schema'
import {useDatePlaces} from './useDatePlaces'

function partner(id: number, partnerId: number, status: RegistrationStatus, queuePosition: number | null): EnrichedFederationRegistration {
    return {
        registration: {
            id,
            eventId: 4,
            partnerId,
            remoteMemberId: `remote-${id}`,
            eventDate: '2026-12-04',
            status,
            createdAt: '2026-11-01T10:00:00Z',
            agreementWithdrawnAt: null,
            standing: true,
            comingOrAsking: status !== RegistrationStatus.QUEUED,
        },
        memberIdentity: null,
        queuePosition,
    }
}

/** One date's places and waiting list, partners' members counted, and who decides about them. */
describe('useDatePlaces', () => {
    const partners = [
        partner(1, 3, RegistrationStatus.ACCEPTED, null),
        partner(2, 5, RegistrationStatus.QUEUED, 1),
    ]
    const date = useDatePlaces({
        limit: () => 1,
        registrations: () => [],
        partners: () => partners,
        partnerPlaces: () => [{partnerId: 5, partnerConfirms: true, slotBudget: null, taken: null}],
    })

    it('counts partners\' members among the places and the waiting list', () => {
        expect(date.places.value).toEqual({taken: 1, limit: 1, waiting: 1, full: true})
        expect(date.queue.value.map(entry => [entry.kind, entry.id, entry.position])).toEqual([['partner', 2, 1]])
    })

    it('says which partners decide about their own people', () => {
        expect(date.partnerDecides(5)).toBe(true)
        expect(date.partnerDecides(3)).toBe(false)
    })
})
