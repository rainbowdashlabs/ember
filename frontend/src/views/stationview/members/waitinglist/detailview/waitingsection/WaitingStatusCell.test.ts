/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import type {WaitingListEntryWithScore} from '@/api/generated/schema'
import WaitingStatusCell from './WaitingStatusCell.vue'

function item(datesGone: string | null, answered: boolean): WaitingListEntryWithScore {
    return {
        entry: {
            status: 'INVITED',
            invitationDatesGoneAt: datesGone,
            answer: answered ? {answer: 'COMING', answeredAt: '2026-11-01T10:00:00Z', note: '', position: 0} : null,
        },
        belowJoinAge: false,
    } as unknown as WaitingListEntryWithScore
}

/** An invitation whose dates all passed without an answer is marked, so the station sees whose turn it is. */
describe('WaitingStatusCell', () => {
    it('marks an invitation that has no date left', () => {
        const wrapper = mount(WaitingStatusCell, {props: {item: item('2026-11-10T08:00:00Z', false)}})

        expect(wrapper.get('[data-testid="waitlist-no-dates-left"]').text()).toBe('Keine Termine mehr offen')
    })

    it('shows the answer instead once there is one, and nothing while dates are left', () => {
        expect(mount(WaitingStatusCell, {props: {item: item('2026-11-10T08:00:00Z', true)}})
            .find('[data-testid="waitlist-no-dates-left"]').exists()).toBe(false)
        expect(mount(WaitingStatusCell, {props: {item: item(null, false)}})
            .find('[data-testid="waitlist-no-dates-left"]').exists()).toBe(false)
    })
})
