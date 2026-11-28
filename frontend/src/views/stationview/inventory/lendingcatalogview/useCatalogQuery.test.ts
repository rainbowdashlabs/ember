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
import {useCatalogQuery} from './useCatalogQuery'

async function catalogAt(address: string) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: '/station/inventory/lending', component: {template: '<div/>'}}],
    })
    let state!: ReturnType<typeof useCatalogQuery>
    const Probe = defineComponent({
        setup() {
            state = useCatalogQuery()
            return () => null
        },
    })
    await router.push(address)
    mount(Probe, {global: {plugins: [router]}})
    return {router, state}
}

/** The catalog keeps its filters in the address beside the tab's own station, which it never touches. */
describe('useCatalogQuery', () => {
    it('reads the partner apart from the own station and falls back on an unknown order', async () => {
        const {state} = await catalogAt('/station/inventory/lending?station=own&partner=p1&sort=bogus&q=funk')

        expect(state.station.value).toBe('p1')
        expect(state.sort.value).toBe('best')
        expect(state.search.value).toBe('funk')
    })

    it('writes the order and the partner without dropping the own station, and drops defaults', async () => {
        const {router, state} = await catalogAt('/station/inventory/lending?station=own')

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

    it('sets both days in one step, a missing last day dropping the old one', async () => {
        const {router, state} = await catalogAt('/station/inventory/lending?station=own&from=2030-06-01&to=2030-06-03')

        state.setDays('2030-06-12', '')
        await flushPromises()
        expect(router.currentRoute.value.query).toEqual({station: 'own', from: '2030-06-12'})

        state.setDays('2030-06-12', '2030-06-14')
        await flushPromises()
        expect(state.dateFrom.value).toBe('2030-06-12')
        expect(state.dateTo.value).toBe('2030-06-14')
    })
})
