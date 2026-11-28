/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {flushPromises, shallowMount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {LendingCalendar} from '@/api/generated/schema'
import CalendarHeader from './lendingcalendarview/CalendarHeader.vue'
import CalendarTrack from './lendingcalendarview/CalendarTrack.vue'
import CreateBlockModal from './lendingcalendarview/CreateBlockModal.vue'
import LendingCalendarView from './LendingCalendarView.vue'

const getCalendar = vi.fn()
vi.mock('@/api/lending', () => ({getCalendar: (from: string, to: string) => getCalendar(from, to)}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({loaded: ref(true)})}))
const start = ref('2030-06-01')
vi.mock('@/composables/useRouteQueryRef', () => ({useRouteQueryRef: () => start}))

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})

const CALENDAR: LendingCalendar = {
    rows: [{inventoryId: 1, name: 'Funk', pieceCount: 3, kinds: [{artId: 5, name: 'FuG', pieceCount: 2}]}],
    bars: [],
    blocks: [],
}

async function view() {
    const wrapper = shallowMount(LendingCalendarView, {global: {plugins: [i18n], renderStubDefaultSlot: true}})
    await flushPromises()
    return wrapper
}

/** The calendar reads four weeks from its first day and turns a drag into a block period to confirm. */
describe('LendingCalendarView', () => {
    beforeEach(() => {
        start.value = '2030-06-01'
        getCalendar.mockReset().mockResolvedValue(CALENDAR)
    })

    it('reads four weeks from the day in the address, and the next four on request', async () => {
        await view()
        expect(getCalendar).toHaveBeenCalledWith('2030-06-01', '2030-06-28')

        start.value = '2030-06-29'
        await flushPromises()
        expect(getCalendar).toHaveBeenLastCalledWith('2030-06-29', '2030-07-26')
    })

    it('turns a drag over a product into a block period for it, and one over the dates into one for the station', async () => {
        const wrapper = await view()
        const product = wrapper.findComponent(CalendarTrack)

        product.vm.$emit('dragStart', '2030-06-05')
        product.vm.$emit('dragOver', '2030-06-03')
        window.dispatchEvent(new Event('pointerup'))
        await flushPromises()
        const modal = wrapper.findComponent(CreateBlockModal)
        expect(modal.props()).toMatchObject({from: '2030-06-03', to: '2030-06-05', inventoryId: 1, label: 'Funk'})

        wrapper.findComponent(CalendarHeader).vm.$emit('dragStart', '2030-06-10')
        window.dispatchEvent(new Event('pointerup'))
        await flushPromises()
        expect(wrapper.findComponent(CreateBlockModal).props()).toMatchObject({from: '2030-06-10', to: '2030-06-10', inventoryId: null})
    })
})
