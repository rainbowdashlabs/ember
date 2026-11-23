/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {shallowMount} from '@vue/test-utils'
import {afterEach, beforeAll, describe, expect, it} from 'vitest'
import Modal from '@/components/feedback/Modal.vue'
import {followModifierKeys} from '@/util/modifierKeys'
import DetailModals from './DetailModals.vue'

/** The detail page's own handle on the modals. */
interface Opener {
    openFormerModal: () => void
}

function modals(canMarkFormer: boolean) {
    return shallowMount(DetailModals, {
        props: {
            memberDisplayName: 'Lena Weber',
            canMarkFormer,
            formerBlockReasons: canMarkFormer ? [] : ['Hat noch Kleidung'],
            markingFormer: false,
            deletingMember: false,
            allMembers: [],
            memberId: 4,
            memberDisplayNameFn: () => '',
        },
    })
}

function holdShift(held: boolean) {
    window.dispatchEvent(new KeyboardEvent(held ? 'keydown' : 'keyup', {key: 'Shift'}))
}

function formerDialogOpen(wrapper: ReturnType<typeof modals>): boolean {
    return wrapper.findAllComponents(Modal)[0]!.props('modelValue') as boolean
}

/** Marking a member former asks first; shift skips the question, but never the reasons it cannot be done. */
describe('DetailModals', () => {
    beforeAll(() => followModifierKeys())
    afterEach(() => holdShift(false))

    it('asks before marking a member former', async () => {
        const wrapper = modals(true)

        ;(wrapper.vm as unknown as Opener).openFormerModal()
        await wrapper.vm.$nextTick()

        expect(formerDialogOpen(wrapper)).toBe(true)
        expect(wrapper.emitted('markFormer')).toBeUndefined()
    })

    it('marks the member former at once while shift is held', async () => {
        const wrapper = modals(true)

        holdShift(true)
        ;(wrapper.vm as unknown as Opener).openFormerModal()
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted('markFormer')).toHaveLength(1)
        expect(formerDialogOpen(wrapper)).toBe(false)
    })

    it('still shows why a member cannot be marked former while shift is held', async () => {
        const wrapper = modals(false)

        holdShift(true)
        ;(wrapper.vm as unknown as Opener).openFormerModal()
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted('markFormer')).toBeUndefined()
        expect(formerDialogOpen(wrapper)).toBe(true)
    })
})
