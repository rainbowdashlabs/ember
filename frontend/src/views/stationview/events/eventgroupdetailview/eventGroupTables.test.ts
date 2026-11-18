/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {RegistrationStatus, type EventGroupSignUp, type SetResult} from '@/api/generated/schema'
import {createIdentity} from '@/test/mocks/factories'
import {isSignUpComplete, signUpColumns} from './eventGroupTables'

const t = (key: string) => key

function signUp(sets: SetResult[], answersMissing: boolean): EventGroupSignUp {
    return {memberId: 1, member: createIdentity({name: 'Anna'}), status: RegistrationStatus.PENDING, registeredAt: null, parts: [1],
        sets, answers: [], answersMissing, mayRegister: true, queuePosition: null}
}

const MET: SetResult = {setId: 1, name: 'Übung', required: 1, chosen: 1, missingRequired: [], maximum: null, tooMany: 0}
const SHORT: SetResult = {setId: 2, name: 'Prüfung', required: 1, chosen: 0, missingRequired: [], maximum: null, tooMany: 0}

describe('isSignUpComplete', () => {
    it('holds a sign-up that meets every set and owes no answer', () => {
        expect(isSignUpComplete(signUp([MET], false))).toBe(true)
    })

    it('leaves out a sign-up short of a set or owing an answer', () => {
        expect(isSignUpComplete(signUp([MET, SHORT], false))).toBe(false)
        expect(isSignUpComplete(signUp([MET], true))).toBe(false)
    })
})

describe('signUpColumns', () => {
    it('offers the statuses in the order the filter lists them', () => {
        const status = signUpColumns(t).find(column => column.key === 'status')!
        expect(status.options?.map(option => option.value)).toEqual([
            RegistrationStatus.PENDING, RegistrationStatus.ACCEPTED, RegistrationStatus.QUEUED, RegistrationStatus.DENIED,
        ])
    })
})
