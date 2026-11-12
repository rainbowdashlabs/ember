/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import EventProbeVisitors from './EventProbeVisitors.vue'

const visitors = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({waitingList: {visitors}}))

/** Who comes to a date for a probe is listed with when they come and from which list, and nothing shows when nobody comes. */
describe('EventProbeVisitors', () => {
    beforeEach(() => visitors.mockReset())

    it('lists who comes to the date', async () => {
        visitors.mockResolvedValue([{entryId: 9, listId: 5, listName: 'Jugend', firstname: 'Mia', lastname: 'Muster',
            date: '2026-06-02', arrivalTime: '17:45:00'}])

        const wrapper = mount(EventProbeVisitors, {props: {eventId: 3, date: '2026-06-02'}})
        await flushPromises()

        expect(visitors).toHaveBeenCalledWith(3, '2026-06-02')
        expect(wrapper.text()).toContain('Kommen zur Probe')
        expect(wrapper.text()).toContain('Mia Muster')
        expect(wrapper.text()).toContain('Warteliste Jugend')
    })

    it('shows nothing where nobody comes', async () => {
        visitors.mockResolvedValue([])

        const wrapper = mount(EventProbeVisitors, {props: {eventId: 3, date: '2026-06-02'}})
        await flushPromises()

        expect(wrapper.find('[data-testid="probe-visitors"]').exists()).toBe(false)
    })
})
