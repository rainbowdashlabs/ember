/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {createMemoryHistory, createRouter} from 'vue-router'
import {describe, expect, it} from 'vitest'
import {useShopQuery} from './useShopQuery'

async function shopAt(address: string) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: '/station/inventory/lending', component: {template: '<div/>'}}],
    })
    let state!: ReturnType<typeof useShopQuery>
    const Probe = defineComponent({
        setup() {
            state = useShopQuery()
            return () => null
        },
    })
    await router.push(address)
    mount(Probe, {global: {plugins: [router]}})
    return {router, state}
}

/** The shop keeps its filters in the address beside the tab's own station, which it never touches. */
describe('useShopQuery', () => {
    it('reads the partner apart from the own station and falls back on an unknown order', async () => {
        const {state} = await shopAt('/station/inventory/lending?station=own&partner=p1&sort=bogus&q=funk')

        expect(state.station.value).toBe('p1')
        expect(state.sort.value).toBe('best')
        expect(state.search.value).toBe('funk')
    })

    it('writes the order and the partner without dropping the own station, and drops defaults', async () => {
        const {router, state} = await shopAt('/station/inventory/lending?station=own')

        state.sort.value = 'name'
        await flushPromises()
        state.station.value = 'p2'
        await flushPromises()
        expect(router.currentRoute.value.query).toEqual({station: 'own', sort: 'name', partner: 'p2'})

        state.sort.value = 'best'
        await flushPromises()
        state.station.value = ''
        await flushPromises()
        expect(router.currentRoute.value.query).toEqual({station: 'own'})
    })
})
