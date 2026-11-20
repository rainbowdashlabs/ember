/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import DashboardEventTile from './DashboardEventTile.vue'
import type {EventSummary} from '@/api/generated/schema'

const FUTURE = new Date(Date.now() + 7 * 86400000).toISOString()
const START = new Date(Date.now() + 14 * 86400000).toISOString()

/** A tile for a date of an appointment, closing for sign-up at that moment, with that many people still to answer. */
function tileFor(event: Partial<EventSummary>, openCount: number, registrationClosesAt: string | null = null, cancelled = false) {
    return mountSuspended(DashboardEventTile, {
        props: {
            event: {id: 5, name: 'Übung', startTime: START, ...event} as EventSummary,
            date: START.slice(0, 10),
            dayLabel: 'Montag',
            registrationClosesAt,
            cancelled,
            answers: [],
            openCount,
            showNames: false,
            busy: false,
            to: '/',
        },
    })
}

/** Declining is offered while somebody still owes an answer and the date still takes one, as the server says. */
describe('DashboardEventTile', () => {
    it('offers to decline an appointment that has to be signed up for while registration is open', async () => {
        const tile = await tileFor({requiresRegistration: true}, 1, FUTURE)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(true)
    })

    it('offers no decline once the closing time the server sends for the date has passed', async () => {
        const tile = await tileFor({requiresRegistration: true}, 1, '2020-01-01T00:00:00Z')

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(false)
    })

    it('offers no decline once everybody has answered', async () => {
        const tile = await tileFor({requiresRegistration: true}, 0, FUTURE)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(false)
    })

    it('offers to decline an appointment everybody is expected at', async () => {
        const tile = await tileFor({requiresRegistration: false}, 1)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(true)
    })

    it('says a date was called off and offers nothing to decline on it', async () => {
        const tile = await tileFor({requiresRegistration: false}, 1, null, true)

        expect(tile.find('[data-testid="dashboard-cancelled-5"]').text()).toBe('Abgesagt')
        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(false)
    })
})
