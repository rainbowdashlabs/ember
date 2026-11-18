/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import type {EventGroupPartState} from '@/api/generated/schema'
import EventGroupProbeVisitorsPanel from './EventGroupProbeVisitorsPanel.vue'

const visitors = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({waitingList: {visitors}}))
vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: ref('Europe/Berlin')})}))

function part(eventId: number, name: string, startTime: string): EventGroupPartState {
    return {eventId, setId: 1, name, startTime, endTime: startTime, calledOff: false, tookPlace: false, required: false, accepted: 0, places: null}
}

/** Probe visitors are gathered per part on the station's day of that part, and parts nobody comes to are left out. */
describe('EventGroupProbeVisitorsPanel', () => {
    beforeEach(() => visitors.mockReset())

    it('lists who comes to each part under that part', async () => {
        visitors.mockImplementation(async (eventId: number) => eventId === 11
            ? [{entryId: 9, listId: 5, listName: 'Jugend', firstname: 'Mia', lastname: 'Muster', date: '2026-12-13', arrivalTime: null}]
            : [])

        const wrapper = mount(EventGroupProbeVisitorsPanel, {props: {parts: [
            part(10, 'Samstag, Übung', '2026-12-12T08:00:00Z'),
            part(11, 'Sonntag, Übung', '2026-12-12T23:30:00Z'),
        ]}})
        await flushPromises()

        expect(visitors).toHaveBeenCalledWith(10, '2026-12-12')
        expect(visitors).toHaveBeenCalledWith(11, '2026-12-13')
        expect(wrapper.find('[data-testid="event-group-probe-part-11"]').text()).toContain('Mia Muster')
        expect(wrapper.find('[data-testid="event-group-probe-part-10"]').exists()).toBe(false)
    })

    it('reads the visitors again only when the parts change, not when the same parts come anew', async () => {
        visitors.mockResolvedValue([])
        const parts = [part(10, 'Samstag, Übung', '2026-12-12T08:00:00Z')]
        const wrapper = mount(EventGroupProbeVisitorsPanel, {props: {parts}})
        await flushPromises()

        await wrapper.setProps({parts: parts.map(entry => ({...entry}))})
        await flushPromises()
        expect(visitors).toHaveBeenCalledTimes(1)

        await wrapper.setProps({parts: [...parts, part(11, 'Sonntag, Übung', '2026-12-13T08:00:00Z')]})
        await flushPromises()
        expect(visitors).toHaveBeenCalledTimes(3)
    })

    it('shows a part under its current name after a refresh that renames it', async () => {
        visitors.mockResolvedValue([{entryId: 9, listId: 5, listName: 'Jugend', firstname: 'Mia', lastname: 'Muster', date: '2026-12-12', arrivalTime: null}])
        const wrapper = mount(EventGroupProbeVisitorsPanel, {props: {parts: [part(10, 'Samstag', '2026-12-12T08:00:00Z')]}})
        await flushPromises()

        await wrapper.setProps({parts: [part(10, 'Samstag, Übung', '2026-12-12T08:00:00Z')]})
        await flushPromises()

        expect(wrapper.find('[data-testid="event-group-probe-part-10"]').text()).toContain('Samstag, Übung')
    })

    it('shows nothing where nobody comes, also when a part cannot be read', async () => {
        visitors.mockRejectedValueOnce(new Error('gone')).mockResolvedValue([])

        const wrapper = mount(EventGroupProbeVisitorsPanel, {props: {parts: [
            part(10, 'Samstag, Übung', '2026-12-12T08:00:00Z'),
            part(11, 'Sonntag, Übung', '2026-12-13T08:00:00Z'),
        ]}})
        await flushPromises()

        expect(wrapper.find('[data-testid="event-group-probe-visitors"]').exists()).toBe(false)
    })
})
