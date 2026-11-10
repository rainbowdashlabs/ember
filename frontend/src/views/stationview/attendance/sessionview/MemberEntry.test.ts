/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import MemberEntry from './MemberEntry.vue'
import type {MemberNotes, MemberWithName, SwapNote} from '@/api/generated/schema'
import {createMember} from '@/test/mocks/factories'

vi.mock('@/composables/useSession', () => ({useSession: () => ({stationTimezone: ref('Europe/Berlin')})}))

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

function member(overrides: Partial<MemberWithName> = {}): MemberWithName {
    return createMember({id: 1, name: 'Max Hoffmann', firstName: 'Max', lastName: 'Hoffmann', ...overrides})
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

    /**
     * A sheet that starts half an hour after midnight at the station is stored on the day before
     * in UTC. A member who joined that day was there, as the server counts it.
     */
    it('offers a member who joined on the day the sheet starts at the station', () => {
        const wrapper = mount(MemberEntry, {
            props: {
                member: member({joinDate: '2026-11-11'}),
                memberName: 'Hoffmann',
                sessionStart: '2026-11-10T23:30:00Z',
            },
            ...harness,
        })

        expect(wrapper.text()).not.toContain('beigetreten')
        expect(wrapper.findComponent({name: 'MemberEntryStatusButtons'}).exists()).toBe(true)
    })

    it('offers nothing for a member who joined after the day the sheet starts at the station', () => {
        const wrapper = mount(MemberEntry, {
            props: {
                member: member({joinDate: '2026-11-12'}),
                memberName: 'Hoffmann',
                sessionStart: '2026-11-10T23:30:00Z',
            },
            ...harness,
        })

        expect(wrapper.findComponent({name: 'MemberEntryStatusButtons'}).exists()).toBe(false)
    })
})
