/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import PendingEventGroups from './PendingEventGroups.vue'
import {waitingEventGroups} from './waitingEventGroups'
import type {EventGroupSummary} from '@/api/generated/schema'

function group(id: number, name: string, pending: number, cancelled = false): EventGroupSummary {
    return {
        id, cancelled, cancelReason: null, partCount: 3, holding: 0, queued: 0, pending, firstStart: null, lastEnd: null, closesAt: null,
        details: {name} as EventGroupSummary['details'],
    }
}

/** Event group sign-ups are decided on the event group, so the open sign-ups lead there once per event group. */
describe('PendingEventGroups', () => {
    it('lists only event groups with sign-ups waiting, each once with its count', async () => {
        const wrapper = await mountSuspended(PendingEventGroups, {
            props: {groups: waitingEventGroups([group(1, 'Grundlehrgang', 2), group(2, 'Jugendflamme', 0), group(3, 'Abgesagt', 1, true)])},
        })

        const rows = wrapper.findAll('[data-testid="pending-event-group-row"]')
        expect(rows).toHaveLength(1)
        expect(rows[0]!.text()).toContain('Grundlehrgang')
        expect(rows[0]!.text()).toContain('2 offen')
    })

    it('shows nothing where no event group waits', async () => {
        const wrapper = await mountSuspended(PendingEventGroups, {props: {groups: waitingEventGroups([group(2, 'Jugendflamme', 0)])}})

        expect(wrapper.find('[data-testid="pending-event-groups"]').exists()).toBe(false)
    })
})
