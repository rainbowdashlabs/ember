/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {describe, expect, it, vi} from 'vitest'
import type {DemoAccount, DemoAccountsResponse} from '@/api/generated/schema'
import {useDemoAccounts} from './useDemoAccounts'

/** Somebody the demo offers, with whatever differs from an ordinary member. */
function account(firstName: string, lastName: string, email: string | null): DemoAccount {
    return {
        email,
        firstName,
        lastName,
        userType: 'MEMBER',
        permissions: [],
        groups: [],
        tags: [],
        profileComplete: true,
        instanceAdministrator: false,
        clusterPermissions: [],
    }
}

const accounts: DemoAccountsResponse = {
    noStationAccounts: [],
    stationGroups: [
        {
            stationId: 'a',
            stationName: 'Wache A',
            accounts: [account('Tim', 'Bauer', null), account('Anna', 'Klein', null), account('Max', 'Muster', 'max@a.de')],
        },
        {stationId: 'b', stationName: 'Wache B', accounts: [account('Max', 'Muster', 'max@b.de')]},
    ],
}

vi.mock('@/api', () => ({
    demo: {
        getDemoStatus: async () => ({demo: true, dev: false}),
        getDemoAccounts: async () => accounts,
    },
}))

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
async function loaded() {
    let api: ReturnType<typeof useDemoAccounts> | null = null
    mount(defineComponent({
        setup() {
            api = useDemoAccounts()
            return () => null
        },
    }))
    const demo = api as unknown as ReturnType<typeof useDemoAccounts>
    await demo.load()
    return demo
}

describe('useDemoAccounts', () => {
    it('searches every station even where somebody has no address', async () => {
        const demo = await loaded()

        demo.search.value = 'max muster'

        expect(demo.view.value.searchGroups.map(group => group.label)).toEqual(['Wache A', 'Wache B'])
    })

    it('offers everybody without an address rather than only the first of them', async () => {
        const demo = await loaded()

        const offered = demo.view.value.roleGroups.flatMap(group => group.accounts.map(a => a.firstName))

        expect(offered).toEqual(['Tim', 'Anna', 'Max'])
    })
})
