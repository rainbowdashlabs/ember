/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import {describe, expect, it} from 'vitest'
import type {CalendarBar} from '@/api/generated/schema'
import CalendarTrack from './CalendarTrack.vue'
import {daysFrom} from './calendarGrid'

const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': {}, en: {}}, missingWarn: false, fallbackWarn: false})
const days = daysFrom('2030-06-01')

function bar(requestId: number, from: string, to: string, quantity: number, overdue = false): CalendarBar {
    return {requestId, inventoryId: 1, artId: null, partner: 'Nord', quantity, status: 'APPROVED', overdue, from, to}
}

function track(draggable: boolean, bars: CalendarBar[]) {
    return mount(CalendarTrack, {
        props: {row: {inventoryId: 1, artId: null, pieceCount: 2}, days, bars, blocks: [{id: 1, inventoryId: 1, artId: null, itemId: null, pieceName: null, from: '2030-06-10', to: '2030-06-11', reason: 'Fest'}], dragging: null, draggable},
        global: {plugins: [i18n], stubs: {RouterLink: {template: '<a><slot/></a>'}}},
    })
}

/** One row shows its bars, its block periods and its clashes, and starts a drag only where blocks can be set. */
describe('CalendarTrack', () => {
    it('draws a bar per line, an overdue one in red, the block, and marks days asking for too much', () => {
        const wrapper = track(true, [bar(1, '2030-06-02', '2030-06-03', 2), bar(2, '2030-06-03', '2030-06-04', 1, true)])

        const bars = wrapper.findAll('[data-testid="lending-calendar-bar"]')
        expect(bars).toHaveLength(2)
        expect(bars[1]!.classes()).toContain('bg-error')
        expect(wrapper.findAll('[data-testid="lending-calendar-block"]')).toHaveLength(1)
        expect(wrapper.text()).toContain('lendingCalendarPage.blockLabel')
        expect(wrapper.get('[data-day="2030-06-03"]').classes()).toContain('bg-error/15')
        expect(wrapper.get('[data-day="2030-06-02"]').classes()).not.toContain('bg-error/15')
    })

    it('starts a drag on a product row and not on a kind row', async () => {
        const product = track(true, [])
        await product.get('[data-day="2030-06-05"]').trigger('pointerdown')
        expect(product.emitted('dragStart')?.[0]).toEqual(['2030-06-05'])

        await product.get('[data-day="2030-06-06"]').trigger('pointerdown', {button: 2})
        expect(product.emitted('dragStart')).toHaveLength(1)

        const kind = track(false, [])
        await kind.get('[data-day="2030-06-05"]').trigger('pointerdown')
        expect(kind.emitted('dragStart')).toBeUndefined()
    })
})
