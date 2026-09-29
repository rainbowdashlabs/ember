/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import FeaturedEventCell from './FeaturedEventCell.vue'
import type {FoundEvent} from '../embeddedEventLookup'

const findEmbeddedEvent = vi.fn()

vi.mock('../embeddedEventLookup', () => ({
    findEmbeddedEvent: (stationUid: string, eventUid: string) => findEmbeddedEvent(stationUid, eventUid),
    publicEventsAddress: (stationUid: string) => `/public/station/${stationUid}/calendar`,
}))

function event(over: Partial<FoundEvent> = {}): FoundEvent {
    return {
        name: 'Dienstabend',
        description: 'Knoten und Stiche',
        startTime: '2026-09-01T16:00:00Z',
        endTime: '2026-09-01T18:00:00Z',
        categoryName: null,
        cancelled: false,
        source: {kind: 'MEMBER', eventId: 42},
        ...over,
    }
}

function eventPages() {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: 'event-detail', path: '/station/events/:id', component: {render: () => null}},
            {name: 'event-detail-date', path: '/station/events/:id/:date', component: {render: () => null}},
        ],
    })
}

async function shown(config: Record<string, unknown>) {
    const view = mount(FeaturedEventCell, {
        props: {config: {eventUid: 'e-1', ...config}, stationUid: 'station-a', timezone: 'Europe/Berlin'},
        global: {plugins: [eventPages()]},
    })
    await flushPromises()
    return view
}

/**
 * An event block as a reader sees it: the one day it is about, a link that works for whoever reads
 * it, and nothing about the event where the reader may not see it.
 */
describe('FeaturedEventCell', () => {
    beforeEach(() => {
        findEmbeddedEvent.mockReset()
    })

    it('shows the one day it announces and links a member to that day', async () => {
        findEmbeddedEvent.mockResolvedValue(event())

        const view = await shown({date: '2026-10-13'})

        expect(view.text()).toContain('Dienstag, 13.10.2026, 18:00 bis 20:00')
        expect(view.text()).toContain('Knoten und Stiche')
        expect(view.find('a').attributes('href')).toBe('/station/events/42/2026-10-13')
    })

    it('links an outside reader to the public calendar and prefers the block\'s own text', async () => {
        findEmbeddedEvent.mockResolvedValue(event({source: {kind: 'PUBLIC', stationUid: 'station-a'}}))

        const view = await shown({descriptionOverride: 'Kommt alle'})

        expect(view.find('a').attributes('href')).toBe('/public/station/station-a/calendar')
        expect(view.text()).toContain('Kommt alle')
        expect(view.text()).not.toContain('Knoten und Stiche')
    })

    it('marks a cancelled event', async () => {
        findEmbeddedEvent.mockResolvedValue(event({cancelled: true}))

        expect((await shown({})).text()).toContain('Abgesagt')
    })

    it('says the event is not available where the reader cannot see it', async () => {
        findEmbeddedEvent.mockResolvedValue(null)

        const view = await shown({})

        expect(view.text()).toContain('Dieser Termin ist hier nicht verfügbar.')
        expect(view.text()).not.toContain('Dienstabend')
    })
})
