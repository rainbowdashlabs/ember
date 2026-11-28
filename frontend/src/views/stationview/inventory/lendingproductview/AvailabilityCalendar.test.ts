/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {RemoteDay} from '@/api/generated/schema'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import AvailabilityCalendar from './AvailabilityCalendar.vue'

const getProductDays = vi.fn()
vi.mock('@/api/lending', () => ({
    getProductDays: (...args: unknown[]) => getProductDays(...args),
}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

function june(): RemoteDay[] {
    return Array.from({length: 30}, (_, index) => ({
        day: `2030-06-${String(index + 1).padStart(2, '0')}`,
        blocked: index === 11,
        counts: [
            {artId: 1, itemId: null, singles: false, free: index === 12 ? 0 : 2, offered: 2},
            {artId: 2, itemId: null, singles: false, free: 1, offered: 3},
        ],
    }))
}

async function calendar(from = '2030-06-10', to = '') {
    const wrapper = mount(AvailabilityCalendar, {
        props: {
            stationUid: 'b1', inventoryId: 7, dateFrom: from, dateTo: to,
            rows: [{key: '1', label: 'FuG 8b'}, {key: '2', label: 'Ladestation'}],
            onPick: (dateFrom: string, dateTo: string) => wrapper.setProps({dateFrom, dateTo}),
        },
        global: {plugins: [i18n], stubs: {IconButton: false}},
    })
    await flushPromises()
    return wrapper
}

function dayCell(wrapper: Awaited<ReturnType<typeof calendar>>, date: string) {
    return wrapper.get(`[data-day="${date}"]`)
}

/** The calendar shows the chosen month, colours each day and picks the days with two clicks. */
describe('AvailabilityCalendar', () => {
    beforeEach(() => {
        getProductDays.mockReset().mockResolvedValue({days: june()})
    })

    it('loads the month of the first chosen day and colours the days', async () => {
        const wrapper = await calendar()

        expect(getProductDays).toHaveBeenCalledWith('b1', 7, '2030-06', undefined)
        expect(dayCell(wrapper, '2030-06-12').classes()).toContain('calendar-blocked')
        expect(dayCell(wrapper, '2030-06-01').classes()).toContain('bg-info/30')
        expect(dayCell(wrapper, '2030-06-10').classes()).toContain('ring-2')
    })

    it('narrows the colours to one row', async () => {
        const wrapper = await calendar()

        await wrapper.findAllComponents(SelectionToggleButton)[1]!.vm.$emit('toggle')
        expect(dayCell(wrapper, '2030-06-01').classes()).toContain('bg-success/25')
        expect(dayCell(wrapper, '2030-06-13').classes()).not.toContain('bg-success/25')
    })

    it('picks both days in one event, and starts again after both are set', async () => {
        const wrapper = await calendar('', '')

        await dayCell(wrapper, '2030-06-10').trigger('click')
        expect(wrapper.emitted('pick')?.at(-1)).toEqual(['2030-06-10', ''])
        expect(dayCell(wrapper, '2030-06-10').attributes('aria-pressed')).toBe('true')

        await dayCell(wrapper, '2030-06-14').trigger('click')
        expect(wrapper.emitted('pick')?.at(-1)).toEqual(['2030-06-10', '2030-06-14'])
        await dayCell(wrapper, '2030-06-20').trigger('click')
        expect(wrapper.emitted('pick')?.at(-1)).toEqual(['2030-06-20', ''])
    })

    it('loads the next month on request', async () => {
        const wrapper = await calendar()

        await wrapper.findAllComponents(IconButton)[1]!.vm.$emit('click')
        await flushPromises()
        expect(getProductDays).toHaveBeenLastCalledWith('b1', 7, '2030-07', undefined)
    })

    it('shows no days of the month before while the next one loads', async () => {
        const wrapper = await calendar()
        getProductDays.mockReturnValue(new Promise(() => {}))

        await wrapper.findAllComponents(IconButton)[1]!.vm.$emit('click')
        await flushPromises()
        expect(wrapper.find('[data-day]').exists()).toBe(false)
    })
})
