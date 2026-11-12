/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import type {StandingFilter} from '@/composables/useDataTable'
import {accountParams, accountTableState, viewOfState, type AccountTableView} from './accountQuery'

const NORTH = '0b6f3c1e-58a4-4a39-9d0e-3c1b0a7e2f11'
const COUNTY = '7d2e9a40-1c3b-4f6e-8a5d-2b9c0e4f6a13'

function filter(selected: string[], includeEmpty = false): StandingFilter {
    return {selected: new Set(selected), includeEmpty}
}

function view(filters: Record<string, StandingFilter>, sortKey: string | null = 'name', sortDirection: 'asc' | 'desc' = 'asc'): AccountTableView {
    return {sortKey, sortDirection, filters: new Map(Object.entries(filters))}
}

describe('accountParams', () => {
    it('asks for nothing while nothing is filtered and the order is the default', () => {
        expect(accountParams(view({}))).toEqual({})
        expect(accountParams(view({}, null))).toEqual({})
    })

    it('sends the options of a list filter as alternatives', () => {
        expect(accountParams(view({
            role: filter(['ADMINISTRATOR']),
            password: filter(['NONE', 'ONE_TIME']),
        }))).toEqual({role: ['ADMINISTRATOR'], password: ['NONE', 'ONE_TIME']})
    })

    it('sends stations and associations by uid and their empty choice as none', () => {
        expect(accountParams(view({
            stations: filter([NORTH], true),
            associations: filter([COUNTY]),
        }))).toEqual({station: [NORTH], withoutStation: 'true', association: [COUNTY]})
        expect(accountParams(view({associations: filter([], true)}))).toEqual({withoutAssociation: 'true'})
    })

    it('sends a yes or no only where one answer is ticked', () => {
        expect(accountParams(view({confirmed: filter(['false']), twoFactor: filter(['true'])})))
            .toEqual({confirmed: 'false', twoFactor: 'true'})
        expect(accountParams(view({confirmed: filter(['true', 'false'])}))).toEqual({})
    })

    it('sends the empty choice of the confirmation as accounts without an address of their own', () => {
        expect(accountParams(view({confirmed: filter(['false'], true)})))
            .toEqual({confirmed: 'false', withoutAddress: 'true'})
        expect(accountParams(view({twoFactor: filter([], true)}))).toEqual({})
    })

    it('turns the last sign-in range into its first and last day and the empty choice into never', () => {
        expect(accountParams(view({lastSignIn: filter(['from:2026-03-01', 'before:2026-04-01'], true)})))
            .toEqual({signedInFrom: '2026-03-01', signedInTo: '2026-03-31', neverSignedIn: 'true'})
        expect(accountParams(view({lastSignIn: filter([], true)}))).toEqual({neverSignedIn: 'true'})
    })

    it('sends the bounds of the passkey count', () => {
        expect(accountParams(view({passkeys: filter(['min:1', 'max:3'])}))).toEqual({passkeysMin: '1', passkeysMax: '3'})
    })

    it('names the order once it differs from the name upwards', () => {
        expect(accountParams(view({}, 'lastSignIn', 'desc'))).toEqual({sort: 'LAST_SIGN_IN', direction: 'desc'})
        expect(accountParams(view({}, 'name', 'desc'))).toEqual({sort: 'NAME', direction: 'desc'})
        expect(accountParams(view({}, 'passkeys'))).toEqual({sort: 'PASSKEYS', direction: 'asc'})
    })
})

describe('accountTableState', () => {
    it('opens on the filter an overview card links to', () => {
        const state = accountTableState({confirmed: 'false'})

        expect([...state.filters.get('confirmed') ?? []]).toEqual(['false'])
        expect(state.sortKey).toBe('name')
    })

    it('reads a parameter the address repeats as alternatives', () => {
        const state = accountTableState({role: ['ADMINISTRATOR', 'USER'], password: ['NONE', 'OFF']})

        expect([...state.filters.get('role') ?? []]).toEqual(['ADMINISTRATOR', 'USER'])
        expect([...state.filters.get('password') ?? []]).toEqual(['NONE', 'OFF'])
    })

    it('reads back everything it wrote', () => {
        const params = accountParams(view({
            role: filter(['ADMINISTRATOR', 'USER']),
            password: filter(['ON']),
            stations: filter([NORTH], true),
            associations: filter([COUNTY], true),
            twoFactor: filter(['false']),
            confirmed: filter(['false'], true),
            lastSignIn: filter(['from:2026-03-01', 'before:2026-04-01'], true),
            passkeys: filter(['min:2']),
        }, 'lastSignIn', 'desc'))

        expect(accountParams(viewOfState(accountTableState(params)))).toEqual(params)
    })

    it('keeps only the values that can be what the address claims', () => {
        const state = accountTableState({
            role: ['ADMINISTRATOR', 'KING'],
            password: 'SOMETIMES',
            station: [NORTH, 'north'],
            association: 'county',
            twoFactor: 'perhaps',
            signedInFrom: 'yesterday',
            signedInTo: '2026-3-1',
            passkeysMin: 'many',
            passkeysMax: '1.5',
            sort: 'AGE',
        })

        expect([...state.filters.keys()].toSorted()).toEqual(['role', 'stations'])
        expect([...state.filters.get('role') ?? []]).toEqual(['ADMINISTRATOR'])
        expect([...state.filters.get('stations') ?? []]).toEqual([NORTH])
        expect(state.sortKey).toBe('name')
    })
})
