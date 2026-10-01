/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import EventSearchPicker from './EventSearchPicker.vue'
import type {BlockAudience} from '@/api/generated/schema'
import {provideBlockAudience} from '@/composables/useBlockAudience'

const searchEvents = vi.fn()
const findEmbeddedEvent = vi.fn()

vi.mock('@/api/events', () => ({
    searchEvents: (query: string, mode: string, limit: number, scope: BlockAudience) =>
        searchEvents(query, mode, limit, scope),
}))

vi.mock('@/components/content/blockeditor/embeddedEventLookup', () => ({
    findEmbeddedEvent: (stationUid: string, eventUid: string, audience: BlockAudience) =>
        findEmbeddedEvent(stationUid, eventUid, audience),
}))

async function picker(modelValue: string | null, audience?: BlockAudience) {
    const host = defineComponent({
        setup() {
            if (audience) provideBlockAudience(audience)
            return () => h(EventSearchPicker, {modelValue, stationUid: 'station-a', mode: 'ALL'})
        },
    })
    return mountSuspended(host)
}

/**
 * The event block picker offers what the block's readers may all see, whoever is picking: on a page
 * the public calendar, in a news or wiki article every appointment every member may see.
 */
describe('EventSearchPicker', () => {
    beforeEach(() => {
        searchEvents.mockReset()
        findEmbeddedEvent.mockReset()
        searchEvents.mockResolvedValue([{eventUid: 'e-1', name: 'Übung', startTime: null, categoryName: null}])
    })

    it('searches the public calendar on a page and says so', async () => {
        const view = await picker(null)

        await view.get('input').trigger('focusin')
        await vi.waitUntil(() => view.findAll('[role="option"]').length === 1)

        expect(searchEvents).toHaveBeenLastCalledWith('', 'ALL', 10, 'PUBLIC')
        expect(view.get('[data-testid="event-picker-hint"]').text()).toContain('öffentlichen Kalender')
    })

    it('searches every appointment every member may see in an article and says so', async () => {
        const view = await picker(null, 'MEMBERS')

        await view.get('input').trigger('focusin')
        await vi.waitUntil(() => view.findAll('[role="option"]').length === 1)

        expect(searchEvents).toHaveBeenLastCalledWith('', 'ALL', 10, 'MEMBERS')
        expect(view.get('[data-testid="event-picker-hint"]').text()).toContain('alle Mitglieder')
    })

    it('shows the name of the appointment already chosen, looked up for the same readers', async () => {
        findEmbeddedEvent.mockResolvedValue({name: 'Dienstabend'})

        const view = await picker('e-9', 'MEMBERS')
        await flushPromises()

        expect(findEmbeddedEvent).toHaveBeenCalledWith('station-a', 'e-9', 'MEMBERS')
        expect(view.text()).toContain('Dienstabend')
    })
})
