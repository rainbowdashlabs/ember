/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import MemberEntry from './MemberEntry.vue'
import type {MemberNotes, SwapNote} from '@/api/attendance'
import type {StationMember} from '@/api/types'

/**
 * What a row of the sheet gives away about a member before anybody asks.
 *
 * <p>A station of forty people with a swap or two each turned a list of names into a page of
 * errands, and whoever is ticking names off came for the names.
 */
const harness = {
    global: {
        stubs: {'font-awesome-icon': true, MemberName: true, MemberEntryStatusButtons: true},
    },
}

function member(): StationMember {
    return {id: 1, identity: {id: 1, firstName: 'Max', lastName: 'Hoffmann'}} as unknown as StationMember
}

function swap(overrides: Partial<SwapNote> = {}): SwapNote {
    return {
        movementId: 7,
        purpose: 'EXCHANGE',
        stepId: 31,
        stepLabel: 'Ersatz ausgegeben',
        stepActor: 'STATION',
        handOverNext: true,
        replacementItemId: 42,
        inventoryName: 'Einsatzjacke',
        itemName: 'Einsatzjacke 04',
        itemSize: '52',
        ...overrides,
    }
}

function notes(overrides: Partial<MemberNotes> = {}): MemberNotes {
    return {memberId: 1, swaps: [], foundItems: [], birthdayDaysAgo: null, ...overrides}
}

function row(memberNotes?: MemberNotes) {
    return mount(MemberEntry, {props: {member: member(), memberName: 'Hoffmann', notes: memberNotes}, ...harness})
}

describe('MemberEntry', () => {
    it('says how much is outstanding rather than listing it', () => {
        const wrapper = row(notes({swaps: [swap(), swap({movementId: 8})]}))

        expect(wrapper.find('[data-testid="member-notes-toggle"]').text()).toContain('2 offene Vorgänge')
        expect(wrapper.find('[data-testid="member-check-notes"]').exists()).toBe(false)
    })

    it('shows what is outstanding once it is asked for', async () => {
        const wrapper = row(notes({swaps: [swap()]}))

        await wrapper.find('[data-testid="member-notes-toggle"]').trigger('click')

        expect(wrapper.find('[data-testid="member-check-notes"]').text()).toContain('Einsatzjacke 04')
    })

    /** Nothing outstanding leaves the row as it was, without a line that opens on nothing. */
    it('offers nothing to open for a member with nothing outstanding', () => {
        expect(row(notes()).find('[data-testid="member-notes-toggle"]').exists()).toBe(false)
        expect(row().find('[data-testid="member-notes-toggle"]').exists()).toBe(false)
    })

    /**
     * A birthday is worth seeing at a glance and is not an errand, so it stands beside the count
     * rather than being counted.
     */
    it('mentions a birthday on the line without counting it as something to do', () => {
        const wrapper = row(notes({birthdayDaysAgo: 0}))

        expect(wrapper.find('[data-testid="member-notes-toggle"]').text()).toContain('Hinweis')
    })
})
