/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import MemberCheckNotes from './MemberCheckNotes.vue'
import type {MemberNotes} from '@/api/attendance'

/** The real translations are in play, so the stories read the German a person would see. */
const i18n = {global: {stubs: {'font-awesome-icon': true}}}

function notes(overrides: Partial<MemberNotes> = {}): MemberNotes {
    return {memberId: 1, swaps: [], foundItems: [], birthdayDaysAgo: null, ...overrides}
}

describe('MemberCheckNotes', () => {
    it('shows nothing at all for a member with nothing outstanding', () => {
        const wrapper = mount(MemberCheckNotes, {props: {notes: notes()}, ...i18n})

        expect(wrapper.find('[data-testid="member-check-notes"]').exists()).toBe(false)
    })

    it('shows nothing where the reader was sent no notes', () => {
        const wrapper = mount(MemberCheckNotes, {props: {}, ...i18n})

        expect(wrapper.find('[data-testid="member-check-notes"]').exists()).toBe(false)
    })

    /**
     * Today is its own sentence rather than "no days ago", and yesterday is its own rather than
     * "vor 1 Tagen", which is not German.
     */
    it('says a birthday today, yesterday and further back differently', () => {
        const today = mount(MemberCheckNotes, {props: {notes: notes({birthdayDaysAgo: 0})}, ...i18n})
        expect(today.find('[data-testid="note-birthday"]').text()).toContain('heute')

        const yesterday = mount(MemberCheckNotes, {props: {notes: notes({birthdayDaysAgo: 1})}, ...i18n})
        expect(yesterday.find('[data-testid="note-birthday"]').text()).toContain('gestern')

        const earlier = mount(MemberCheckNotes, {props: {notes: notes({birthdayDaysAgo: 4})}, ...i18n})
        expect(earlier.find('[data-testid="note-birthday"]').text()).toContain('vor 4 Tagen')
    })

    /**
     * Being told a swap is waiting and being allowed to move it on are different rights. Without the
     * second the note still says what is happening, and offers no button.
     */
    it('shows a swap without a button to a reader who may not move it', () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {
                notes: notes({
                    swaps: [{
                        exchangeId: 7,
                        status: 'ARRIVED',
                        nextStatus: 'DONE',
                        handOverNext: true,
                        inventoryName: 'Einsatzjacke',
                    }],
                }),
            },
            ...i18n,
        })

        expect(wrapper.find('[data-testid="note-swap"]').text()).toContain('Einsatzjacke')
        expect(wrapper.find('[data-testid="note-swap-hand-over"]').exists()).toBe(false)
    })

    /**
     * Where the next move is the handover the button says so, because that is the one the reader is
     * standing in front of the member to do.
     */
    it('offers a handover where that is the next move', async () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {
                canMoveSwap: true,
                notes: notes({
                    swaps: [{
                        exchangeId: 7,
                        status: 'ARRIVED',
                        nextStatus: 'DONE',
                        handOverNext: true,
                        inventoryName: 'Einsatzjacke',
                    }],
                }),
            },
            ...i18n,
        })

        await wrapper.find('[data-testid="note-swap-hand-over"]').trigger('click')
        expect(wrapper.emitted('moveSwap')).toEqual([[7, 'DONE']])
    })

    /**
     * A swap that is waiting on something else can still be moved on, but it is not the handover and
     * does not claim to be.
     */
    it('offers a plain move on where the next step is not the handover', async () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {
                canMoveSwap: true,
                notes: notes({
                    swaps: [{
                        exchangeId: 9,
                        status: 'ANNOUNCED',
                        nextStatus: 'RECEIVED',
                        handOverNext: false,
                        inventoryName: 'Helm',
                    }],
                }),
            },
            ...i18n,
        })

        expect(wrapper.find('[data-testid="note-swap-hand-over"]').exists()).toBe(false)
        await wrapper.find('[data-testid="note-swap-move-on"]').trigger('click')
        expect(wrapper.emitted('moveSwap')).toEqual([[9, 'RECEIVED']])
    })

    it('names a found item and signs it off only where the reader may', async () => {
        const withoutRight = mount(MemberCheckNotes, {
            props: {notes: notes({foundItems: [{itemId: 3, description: 'Blaue Trinkflasche'}]})},
            ...i18n,
        })
        expect(withoutRight.find('[data-testid="note-found"]').text()).toContain('Blaue Trinkflasche')
        expect(withoutRight.find('[data-testid="note-found-sign-off"]').exists()).toBe(false)

        const withRight = mount(MemberCheckNotes, {
            props: {
                canSignOffFound: true,
                notes: notes({foundItems: [{itemId: 3, description: 'Blaue Trinkflasche'}]}),
            },
            ...i18n,
        })
        await withRight.find('[data-testid="note-found-sign-off"]').trigger('click')
        expect(withRight.emitted('signOffFound')).toEqual([[3]])
    })
})
