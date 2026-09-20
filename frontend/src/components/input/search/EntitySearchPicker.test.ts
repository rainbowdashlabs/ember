/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import EntitySearchPicker from './EntitySearchPicker.vue'

interface Gear {
    id: number
    name: string
    reachable?: boolean
}

const GEAR: Gear[] = [
    {id: 1, name: 'Helme'},
    {id: 2, name: 'Jacken', reachable: false},
    {id: 3, name: 'Stiefel'},
]

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {common: {empty: 'leer', delete: 'weg'}}}})

function picker(extra: Record<string, unknown> = {}) {
    return mount(EntitySearchPicker, {
        global: {
            plugins: [i18n],
            stubs: {'font-awesome-icon': true, Spinner: true},
        },
        props: {
            searchFn: (q: string) => Promise.resolve(GEAR.filter(g => g.name.toLowerCase().includes(q.toLowerCase()))),
            displayFn: (item: unknown) => (item as Gear).name,
            keyFn: (item: unknown) => (item as Gear).id,
            isSelectableFn: (item: unknown) => (item as Gear).reachable !== false,
            ...extra,
        },
    })
}

async function opened(extra: Record<string, unknown> = {}) {
    const wrapper = picker(extra)
    await wrapper.find('input').trigger('focusin')
    await vi.waitUntil(() => wrapper.findAll('[role="option"]').length > 0)
    return wrapper
}

function highlightedText(wrapper: ReturnType<typeof picker>): string {
    const row = wrapper.findAll('[role="option"]').find(node => node.attributes('aria-selected') === 'true')
    return row?.text() ?? ''
}

describe('EntitySearchPicker keyboard', () => {
    it('highlights the first row when the list arrives', async () => {
        const wrapper = await opened()

        expect(highlightedText(wrapper)).toContain('Helme')
    })

    it('walks down and up and stops at both ends', async () => {
        const wrapper = await opened()
        const input = wrapper.find('input')

        await input.trigger('keydown', {key: 'ArrowUp'})
        expect(highlightedText(wrapper)).toContain('Helme')

        await input.trigger('keydown', {key: 'ArrowDown'})
        expect(highlightedText(wrapper)).toContain('Stiefel')

        await input.trigger('keydown', {key: 'ArrowDown'})
        expect(highlightedText(wrapper)).toContain('Stiefel')

        await input.trigger('keydown', {key: 'ArrowUp'})
        expect(highlightedText(wrapper)).toContain('Helme')
    })

    /** A row nobody may pick is drawn, and the keyboard walks past it. */
    it('skips what cannot be picked', async () => {
        const wrapper = await opened()
        const input = wrapper.find('input')

        await input.trigger('keydown', {key: 'ArrowDown'})

        expect(highlightedText(wrapper)).not.toContain('Jacken')
    })

    it('takes the highlighted row on Enter', async () => {
        const wrapper = await opened()
        const input = wrapper.find('input')

        await input.trigger('keydown', {key: 'ArrowDown'})
        await input.trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')?.[0]?.[0]).toMatchObject({name: 'Stiefel'})
    })

    it('closes on Escape and leaves what was typed', async () => {
        const wrapper = await opened()
        const input = wrapper.find('input')
        await input.setValue('Stie')

        await input.trigger('keydown', {key: 'Escape'})

        expect(wrapper.find('[role="listbox"]').exists()).toBe(false)
        expect((input.element as HTMLInputElement).value).toBe('Stie')
        expect(wrapper.emitted('pick')).toBeUndefined()
    })

    it('names the highlighted row for a screen reader', async () => {
        const wrapper = await opened()

        const active = wrapper.find('input').attributes('aria-activedescendant')
        const row = wrapper.findAll('[role="option"]').find(node => node.attributes('aria-selected') === 'true')

        expect(active).toBeTruthy()
        expect(row?.attributes('id')).toBe(active)
    })

    it('draws a row from the slot when one is given, and keeps the badge beside it', async () => {
        const wrapper = await opened({
            badgeFn: () => ({text: 'frei', variant: 'neutral' as const}),
        })

        expect(wrapper.text()).toContain('frei')

        const slotted = await opened({})
        expect(slotted.findAll('[role="option"]').length).toBe(3)
    })
})

describe('EntitySearchPicker with a list still loading', () => {
    it('asks again for what was typed once the rows arrive', async () => {
        const wrapper = picker({searchFn: () => Promise.resolve([])})
        const input = wrapper.find('input')
        await input.trigger('focusin')
        await input.setValue('helm')
        await new Promise(resolve => setTimeout(resolve, 300))
        expect(wrapper.findAll('[role="option"]')).toHaveLength(0)

        await wrapper.setProps({
            searchFn: (q: string) => Promise.resolve(GEAR.filter(g => g.name.toLowerCase().includes(q.toLowerCase()))),
        })

        await vi.waitUntil(() => wrapper.findAll('[role="option"]').length > 0)
        expect(wrapper.findAll('[role="option"]').map(row => row.text()).join()).toContain('Helme')
    })
})
