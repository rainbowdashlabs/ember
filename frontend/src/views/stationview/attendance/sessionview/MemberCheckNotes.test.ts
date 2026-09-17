/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import MemberCheckNotes from './MemberCheckNotes.vue'
import type {MemberNotes, SwapNote} from '@/api/attendance'

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

    /** A swap of the station's, standing on the step that hands the replacement over. */
    function handOver(overrides: Partial<SwapNote> = {}): SwapNote {
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

    /**
     * Being told a swap is waiting and being allowed to move it on are different rights. Without the
     * second the note still says what is happening, and offers no button.
     */
    it('shows a swap without a button to a reader who may not move it', () => {
        const wrapper = mount(MemberCheckNotes, {props: {notes: notes({swaps: [handOver()]})}, ...i18n})

        expect(wrapper.find('[data-testid="note-swap"]').text()).toContain('Einsatzjacke 04')
        expect(wrapper.find('[data-testid="note-swap"]').text()).toContain('Ersatz ausgegeben')
        expect(wrapper.find('[data-testid="note-swap-step"]').exists()).toBe(false)
    })

    /**
     * The button carries the name of the step it walks, the same words the queue gives it, so what is
     * about to happen is read off the button rather than guessed from a generic word.
     */
    it('names the step on the button and sends the piece set aside with it', async () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {canManageSwap: true, notes: notes({swaps: [handOver()]})},
            ...i18n,
        })

        const button = wrapper.find('[data-testid="note-swap-step"]')
        expect(button.text()).toBe('Ersatz ausgegeben')

        await button.trigger('click')
        expect(
            wrapper.emitted('moveSwap'),
            'the piece set aside travels with the step, which refuses to run without it',
        ).toEqual([[7, 31, 42]])
    })

    /** A piece that has a size is read off the shelf by it, so the note carries it beside the name. */
    it('shows the size of the piece where it has one', () => {
        const sized = mount(MemberCheckNotes, {props: {notes: notes({swaps: [handOver()]})}, ...i18n})
        expect(sized.find('[data-testid="note-swap"]').text()).toContain('52')

        const sizeless = mount(MemberCheckNotes, {
            props: {notes: notes({swaps: [handOver({itemSize: null})]})},
            ...i18n,
        })
        expect(sizeless.find('[data-testid="note-swap"]').text()).toContain('Einsatzjacke 04')
    })

    /** A handover whose replacement nobody has picked says so instead of offering a step that refuses. */
    it('says so where the replacement has not been picked', () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {canManageSwap: true, notes: notes({swaps: [handOver({replacementItemId: null})]})},
            ...i18n,
        })

        expect(wrapper.find('[data-testid="note-swap-needs-replacement"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="note-swap-step"]').exists()).toBe(false)
    })

    /**
     * A reader who may see a swap but not move it reads where it stands and presses nothing. Which
     * step it is stays on the row either way, since that is what says what is about to happen.
     */
    it('names the step without a button where the reader may not move it', () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {notes: notes({swaps: [handOver({stepLabel: 'Altes Teil zurückgenommen'})]})},
            ...i18n,
        })

        expect(wrapper.find('[data-testid="note-swap-step"]').exists()).toBe(false)
        expect(wrapper.find('[data-testid="note-swap-waiting"]').text()).toBe('Altes Teil zurückgenommen')
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

    /** Calling one off is the same right as moving it on, and neither is offered without it. */
    it('offers calling a swap off only to a reader who may settle it', () => {
        const wrapper = mount(MemberCheckNotes, {props: {notes: notes({swaps: [handOver()]})}, ...i18n})

        expect(wrapper.find('[data-testid="note-swap-drop"]').exists()).toBe(false)
    })

    /**
     * The sheet is worked through at speed with the member in the room, and a movement removed by a
     * mis-press cannot be got back, so the press asks first and says which piece it is about.
     */
    it('asks before calling a swap off, and names the piece when it does', async () => {
        const wrapper = mount(MemberCheckNotes, {
            props: {canManageSwap: true, notes: notes({swaps: [handOver()]})},
            ...i18n,
        })

        await wrapper.find('[data-testid="note-swap-drop"]').trigger('click')
        expect(wrapper.emitted('dropSwap'), 'nothing is called off on the first press').toBeUndefined()

        const confirm = wrapper.findComponent({name: 'ConfirmDeleteModal'})
        expect(confirm.props('message')).toContain('Einsatzjacke 04')

        confirm.vm.$emit('confirm')
        expect(wrapper.emitted('dropSwap')).toEqual([[7]])
    })
})
