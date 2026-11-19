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

/** A deadline fixed at one moment. */
function fixedAt(at: string): EventSummary['registrationDeadline'] {
    return {kind: 'FIXED', at, offsetMinutes: null, anchor: null}
}

/** A tile for an appointment, signed up for or not, with that many people still to answer. */
function tileFor(event: Partial<EventSummary>, openCount: number) {
    return mountSuspended(DashboardEventTile, {
        props: {
            event: {id: 5, name: 'Übung', startTime: START, ...event} as EventSummary,
            date: START.slice(0, 10),
            dayLabel: 'Montag',
            answers: [],
            openCount,
            showNames: false,
            busy: false,
            to: '/',
        },
    })
}

/** Declining is offered while somebody still owes an answer and the appointment still takes one. */
describe('DashboardEventTile', () => {
    it('offers to decline an appointment that has to be signed up for while registration is open', async () => {
        const tile = await tileFor({requiresRegistration: true, registrationDeadline: fixedAt(FUTURE)}, 1)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(true)
    })

    it('offers no decline once registration has closed', async () => {
        const tile = await tileFor({requiresRegistration: true, registrationDeadline: fixedAt('2020-01-01T00:00:00Z')}, 1)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(false)
    })

    it('offers no decline once everybody has answered', async () => {
        const tile = await tileFor({requiresRegistration: true, registrationDeadline: fixedAt(FUTURE)}, 0)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(false)
    })

    it('offers no decline once the relative deadline of the date shown has passed', async () => {
        const tile = await tileFor({
            requiresRegistration: true,
            registrationDeadline: {kind: 'RELATIVE', at: null, offsetMinutes: 30 * 1440, anchor: 'EXACT'},
        }, 1)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(false)
    })

    it('offers to decline an appointment everybody is expected at', async () => {
        const tile = await tileFor({requiresRegistration: false}, 1)

        expect(tile.find('[data-testid="dashboard-decline-5"]').exists()).toBe(true)
    })
})
