/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {RegistrationStatus, type RegistrationResponse} from '@/api/generated/schema'
import {queueableByAge} from './registrationGroups'

function pending(id: number, createdAt: string, fromField = false): RegistrationResponse {
    return {
        id,
        eventId: 4,
        memberId: id,
        memberName: `Mitglied ${id}`,
        memberIdentity: null,
        eventDate: '2026-12-04',
        status: RegistrationStatus.PENDING,
        createdAt,
        createdByName: null,
        fields: [],
        eventName: null,
        answersMissing: false,
        fromField,
        fieldName: fromField ? 'Leitung' : null,
        agreementWithdrawnAt: null,
        placeGivenUp: false,
        queuePosition: null,
        statusChangedAt: createdAt,
    }
}

/**
 * Sign-ups go onto the waiting list oldest first. A place a question gives is no sign-up, and the
 * server refuses the whole batch for one, so it is never handed along.
 */
describe('queueableByAge', () => {
    it('hands the sign-ups along oldest first and leaves places held by a question out', () => {
        expect(queueableByAge([
            pending(3, '2026-11-03T10:00:00Z'),
            pending(1, '2026-11-01T10:00:00Z', true),
            pending(2, '2026-11-02T10:00:00Z'),
        ])).toEqual([2, 3])
    })
})
