/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {AccountOverview} from '@/api/generated/schema'
import {associationLabel, passwordStateOf, PasswordState} from './accountColumns'

function account(overrides: Partial<AccountOverview> = {}): AccountOverview {
    return {
        id: 1, uid: '00000000-0000-0000-0000-000000000001', name: 'Lena Weber', email: null, loginName: 'lena',
        instanceUserType: 'USER', lastSignInAt: null, stations: [], associations: [],
        hasPassword: true, passwordSignIn: true, oneTimePasswordExpiresAt: null, passkeys: 0, twoFactor: false,
        ...overrides,
    }
}

describe('passwordStateOf', () => {
    it('tells an account without a password from one whose password is switched off', () => {
        expect(passwordStateOf(account({hasPassword: false, passwordSignIn: false}))).toBe(PasswordState.NONE)
        expect(passwordStateOf(account({passwordSignIn: false}))).toBe(PasswordState.OFF)
    })

    it('names a one-time password still waiting to be replaced', () => {
        expect(passwordStateOf(account({oneTimePasswordExpiresAt: '2026-10-09T12:00:00Z'}))).toBe(PasswordState.ONE_TIME)
        expect(passwordStateOf(account())).toBe(PasswordState.ON)
    })
})

describe('associationLabel', () => {
    it('writes the association with what the account is there', () => {
        const label = associationLabel({association: 'Kreisverband', role: 'CLUSTER_ADMIN'}, key => key)

        expect(label).toBe('Kreisverband (clusterOverview.role.CLUSTER_ADMIN)')
    })
})
