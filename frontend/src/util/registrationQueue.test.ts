/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {
    RegistrationStatus,
    type EnrichedFederationRegistration,
    type RegistrationResponse,
} from '@/api/generated/schema'
import {partnerQueuePosition, queueOf} from './registrationQueue'

function member(id: number, status: RegistrationStatus, queuePosition: number | null): RegistrationResponse {
    return {
        id,
        eventId: 3,
        memberId: id,
        memberName: `Mitglied ${id}`,
        memberIdentity: null,
        eventDate: '2026-12-04',
        registrationClosesAt: null,
        status,
        createdAt: '2026-11-01T10:00:00Z',
        createdByName: null,
        fields: [],
        eventName: null,
        answersMissing: false,
        fromField: false,
        fieldName: null,
        agreementWithdrawnAt: null,
        placeGivenUp: false,
        queuePosition,
        statusChangedAt: '2026-11-01T10:00:00Z',
    }
}

function partner(id: number, status: RegistrationStatus, queuePosition: number | null): EnrichedFederationRegistration {
    return {
        queuePosition,
        registration: {
            id,
            eventId: 3,
            partnerId: 9,
            remoteMemberId: `remote-${id}`,
            eventDate: '2026-12-04',
            status,
            createdAt: '2026-11-02T10:00:00Z',
            agreementWithdrawnAt: null,
            standing: true,
            comingOrAsking: false,
        },
        memberIdentity: null,
    }
}

/** Members and partners' members wait in one list, which the screen draws in the order it moves up in. */
describe('queueOf', () => {
    it('keeps only those waiting, in their place in line', () => {
        const queue = queueOf([
            member(1, RegistrationStatus.QUEUED, 2),
            member(2, RegistrationStatus.ACCEPTED, null),
            member(3, RegistrationStatus.QUEUED, 1),
        ], [])

        expect(queue.map(entry => [entry.id, entry.position])).toEqual([[3, 1], [1, 2]])
    })

    it('takes a partner\'s member at the place the server counts for them', () => {
        const queue = queueOf(
            [member(1, RegistrationStatus.QUEUED, 1), member(2, RegistrationStatus.QUEUED, 3)],
            [partner(7, RegistrationStatus.QUEUED, 2), partner(6, RegistrationStatus.PENDING, null)],
        )

        expect(queue.map(entry => [entry.kind, entry.id, entry.position])).toEqual([
            ['member', 1, 1],
            ['partner', 7, 2],
            ['member', 2, 3],
        ])
        expect(partnerQueuePosition(queue, 7)).toBe(2)
        expect(partnerQueuePosition(queue, 6)).toBeNull()
    })

    /** A reader who sees only part of the list still reads it numbered without holes. */
    it('numbers the places it sees without holes', () => {
        const queue = queueOf([member(1, RegistrationStatus.QUEUED, 2), member(2, RegistrationStatus.QUEUED, 5)], [])

        expect(queue.map(entry => entry.position)).toEqual([1, 2])
    })
})
